package com.auroraplay.iptv.domain.usecase

import com.auroraplay.iptv.domain.model.Channel
import com.auroraplay.iptv.domain.model.Favorite
import com.auroraplay.iptv.domain.model.HomeContent
import com.auroraplay.iptv.domain.model.HomeSection
import com.auroraplay.iptv.domain.model.MediaItem
import com.auroraplay.iptv.domain.model.Movie
import com.auroraplay.iptv.domain.model.ResumeInfo
import com.auroraplay.iptv.domain.model.SectionLayout
import com.auroraplay.iptv.domain.model.Series
import com.auroraplay.iptv.domain.model.WatchProgress
import com.auroraplay.iptv.domain.repository.ContentRepository
import com.auroraplay.iptv.domain.repository.FavoriteRepository
import com.auroraplay.iptv.domain.repository.WatchProgressRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

/**
 * Assembles every Home carousel from local (already-synced) data, combining
 * catalog, favorites and watch-progress reactively. Sections with no items
 * are dropped, per spec.
 */
class GetHomeContentUseCase @Inject constructor(
    private val contentRepository: ContentRepository,
    private val favoriteRepository: FavoriteRepository,
    private val watchProgressRepository: WatchProgressRepository,
    private val smartCategoryBuilder: SmartCategoryBuilder,
    private val contentPolicy: com.auroraplay.iptv.domain.policy.ContentPolicy,
) {
    operator fun invoke(connectionId: String, profileId: String, isKids: Boolean = false): Flow<HomeContent> = combine(
        contentRepository.observeChannels(connectionId),
        contentRepository.observeMovies(connectionId),
        contentRepository.observeSeries(connectionId),
        combine(
            watchProgressRepository.observeContinueWatching(profileId),
            watchProgressRepository.observeChannelHistory(profileId),
        ) { progress, channelHistory -> progress to channelHistory },
        favoriteRepository.observeFavorites(profileId),
    ) { channels, movies, series, (progress, channelHistory), favorites ->
        build(channels, movies, series, progress, channelHistory, favorites, isKids)
    }
        // build() maps/filters the whole catalog into carousels — during a
        // pull-to-refresh the Room flows re-emit in bursts as sync writes
        // batches land, and running that on the collector's thread (main) is
        // exactly the reload stutter. Push it to Default, collapse the burst
        // (conflate), and drop rebuilds that produced an identical result.
        .distinctUntilChanged()
        .conflate()
        .flowOn(Dispatchers.Default)

    private fun build(
        rawChannels: List<Channel>,
        rawMovies: List<Movie>,
        rawSeries: List<Series>,
        progress: List<WatchProgress>,
        channelHistory: List<WatchProgress>,
        favorites: List<Favorite>,
        isKids: Boolean,
    ): HomeContent {
        // A Kids profile sees only what's explicitly categorized as kids
        // content — an allowlist, not just adult content hidden — so
        // "Ação"/"Terror"/general genre rails never surface for this profile
        // even though they aren't adult either.
        val channels = contentPolicy.channels(isKids, rawChannels)
        val movies = contentPolicy.movies(isKids, rawMovies)
        val series = contentPolicy.series(isKids, rawSeries)

        val favoriteIds = favorites.map { it.contentId }.toSet()

        val allItems: List<MediaItem> = channels.map { MediaItem.ChannelItem(it) } +
            movies.map { MediaItem.MovieItem(it) } +
            series.map { MediaItem.SeriesItem(it) }
        val byId = allItems.associateBy { it.id }

        val sections = mutableListOf<HomeSection>()
        val resumeMap = mutableMapOf<String, ResumeInfo>()

        // --- Continue watching ---
        // Series progress is stored as "<seriesId>:<episodeId>", so resolve
        // back to the parent series and keep only the most recent episode per
        // series (Netflix shows one card per show, not one per episode).
        val continueItems = mutableListOf<MediaItem>()
        val seenSeries = mutableSetOf<String>()
        progress.sortedByDescending { it.lastWatchedMillis }.forEach { wp ->
            val isEpisode = wp.contentId.contains(":")
            val parentId = if (isEpisode) wp.contentId.substringBefore(":") else wp.contentId
            if (isEpisode && !seenSeries.add(parentId)) return@forEach
            val item = byId[parentId] ?: return@forEach
            continueItems += item
            resumeMap[parentId] = ResumeInfo(
                contentId = wp.contentId,
                fraction = wp.fraction,
                positionMillis = wp.positionMillis,
                seasonNumber = wp.seasonNumber,
                episodeNumber = wp.episodeNumber,
            )
        }
        if (continueItems.isNotEmpty()) {
            sections += HomeSection("continue_watching", "Continuar assistindo", continueItems, SectionLayout.LANDSCAPE)
        }

        // --- Minha lista ---
        // Keep the personalised list immediately after "Continuar assistindo"
        // so saved titles stay close to the primary resume action.
        val favoriteItems = allItems.filter { favoriteIds.contains(it.id) && it !is MediaItem.ChannelItem }
        if (favoriteItems.isNotEmpty()) {
            sections += HomeSection("favorites", "Minha lista", favoriteItems)
        }

        // --- Genre-aware rails ---
        // SmartCategoryBuilder turns the raw catalog into streaming-style
        // rows (Novidades, Em alta, Ação, Comédia, Terror...) instead of the
        // provider's own noisy category names. This is what gives Home more
        // than just one "Filmes" and one "Séries" row.
        val watchedParentIds = progress.map { it.contentId.substringBefore(":") }.toSet()
        val watchedGenres = (movies.filter { it.id in watchedParentIds }.mapNotNull { it.genre } +
            series.filter { it.id in watchedParentIds }.mapNotNull { it.genre }).toSet()

        val movieRails = smartCategoryBuilder.movieRails(movies, watchedGenres, watchedParentIds)
        val seriesRails = smartCategoryBuilder.seriesRails(series, watchedGenres, watchedParentIds)

        // Movies and series rails interleaved by kind (all film rails, then
        // all series rails) reads more predictably than alternating them —
        // someone browsing for a movie doesn't want a series row splitting
        // the film recommendations in two.
        movieRails.forEach { rail ->
            sections += HomeSection(rail.id, rail.title, rail.items.map { MediaItem.MovieItem(it) })
        }
        seriesRails.forEach { rail ->
            sections += HomeSection(rail.id, rail.title, rail.items.map { MediaItem.SeriesItem(it) })
        }

        // --- Canais recentes ---
        // The last channels this profile actually opened (max 10), newest
        // first — a genuine history, not a generic "featured" list. Nothing
        // shows here until the person has watched a channel.
        val channelById = channels.associateBy { it.id }
        val recentChannels = channelHistory
            .mapNotNull { channelById[it.contentId] }
            .distinctBy { it.id }
            .take(10)
        if (recentChannels.isNotEmpty()) {
            sections += HomeSection("channels_recent", "Canais recentes", recentChannels.map { MediaItem.ChannelItem(it) }, SectionLayout.CHANNEL)
        }

        // Prefer a title with real artwork + synopsis for the hero.
        val railItems = movieRails.flatMap { it.items }.map { MediaItem.MovieItem(it) } +
            seriesRails.flatMap { it.items }.map { MediaItem.SeriesItem(it) }
        val hero = continueItems.firstOrNull()
            ?: (movies.filter { !it.plot.isNullOrBlank() && it.backdropUrl != null }.map { MediaItem.MovieItem(it) } +
                series.filter { !it.plot.isNullOrBlank() && it.backdropUrl != null }.map { MediaItem.SeriesItem(it) })
                .randomOrNull()
            ?: favoriteItems.firstOrNull()
            ?: railItems.randomOrNull()

        // A handful of highlights so the hero can rotate; the first is the
        // same pick the single-hero build used, so behavior is unchanged for
        // anyone with only one eligible title. HeroCarousel itself also caps
        // at 5, but capping here too keeps this list meaningful on its own.
        val heroPool = (listOfNotNull(hero) + railItems)
            .distinctBy { it.id }
            .take(5)

        return HomeContent(heroItems = heroPool, sections = sections, resumeByItemId = resumeMap)
    }
}
