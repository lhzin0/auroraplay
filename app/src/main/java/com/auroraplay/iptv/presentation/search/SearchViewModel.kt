package com.auroraplay.iptv.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auroraplay.iptv.domain.model.MediaItem
import com.auroraplay.iptv.domain.repository.ConnectionRepository
import com.auroraplay.iptv.domain.repository.ContentRepository
import com.auroraplay.iptv.domain.repository.ProfileRepository
import com.auroraplay.iptv.domain.repository.SettingsRepository
import com.auroraplay.iptv.domain.repository.WatchProgressRepository
import com.auroraplay.iptv.domain.usecase.SmartCategoryBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class SearchFilter { ALL, MOVIES, SERIES, CHANNELS }

/** Result-list page size for "carregar mais" (audit #20). */
private const val SEARCH_PAGE = 60

data class SearchUiState(
    /** Live text in the field — updated synchronously on every keystroke. */
    val query: String = "",
    /** The (trimmed) query [results] were actually computed for. Lags [query]
     * by the debounce; used to tell "no results" apart from "still typing". */
    val searchedQuery: String = "",
    val filter: SearchFilter = SearchFilter.ALL,
    val results: List<MediaItem> = emptyList(),
    /** True when more matches exist beyond [results] — drives "carregar mais". */
    val hasMoreResults: Boolean = false,
    val suggestions: List<MediaItem> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val isLoading: Boolean = true,
)

private data class SearchCatalog(
    val movies: List<MediaItem.MovieItem> = emptyList(),
    val series: List<MediaItem.SeriesItem> = emptyList(),
    val channels: List<MediaItem.ChannelItem> = emptyList(),
) {
    val all: List<MediaItem>
        get() = movies + series + channels
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val connectionRepository: ConnectionRepository,
    private val contentRepository: ContentRepository,
    private val profileRepository: ProfileRepository,
    private val watchProgressRepository: WatchProgressRepository,
    private val settingsRepository: SettingsRepository,
    private val smartCategoryBuilder: SmartCategoryBuilder,
    private val contentPolicy: com.auroraplay.iptv.domain.policy.ContentPolicy,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(SearchFilter.ALL)
    /** How many results to show. Grows by [SEARCH_PAGE] on "carregar mais"
     * (audit #20); reset whenever the query or filter changes. */
    private val resultLimit = MutableStateFlow(SEARCH_PAGE)
    private var activeProfileId: String? = null

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Rebuild the whole search pipeline when the active profile OR the
            // default connection changes (audit #11); collectLatest cancels the
            // previous playlist's flows and the stale results with them.
            combine(
                profileRepository.observeActiveProfile(),
                connectionRepository.observeDefaultConnection(),
            ) { profile, connection -> profile to connection }
                .distinctUntilChanged()
                .collectLatest { (profile, connection) ->
                    if (connection == null) {
                        _uiState.value = SearchUiState(query = query.value, filter = filter.value, isLoading = false)
                        return@collectLatest
                    }
                    _uiState.update { it.copy(isLoading = true, results = emptyList(), suggestions = emptyList()) }
                    runSearchPipeline(connection, profile)
                }
        }

        // Persist a search only after the user pauses. distinctUntilChanged()
        // prevents duplicate writes for the same paused query.
        viewModelScope.launch {
            query
                .debounce(900)
                .map(String::trim)
                .distinctUntilChanged()
                .collectLatest { q ->
                    val profileId = activeProfileId ?: return@collectLatest
                    if (q.isNotBlank()) {
                        settingsRepository.addRecentSearch(profileId, q)
                    }
                }
        }
    }

    private suspend fun runSearchPipeline(
        connection: com.auroraplay.iptv.domain.model.XtreamConnection,
        profile: com.auroraplay.iptv.domain.model.Profile?,
    ) {
            activeProfileId = profile?.id
            val isKids = profile?.isKids == true

            val watchedParentIds = profile
                ?.let { watchProgressRepository.observeContinueWatching(connection.id, it.id).first() }
                .orEmpty()
                .asSequence()
                .map { it.contentId.substringBefore(":") }
                .toSet()

            val watchedGenres = flow {
                if (profile == null) {
                    emit(emptySet())
                    return@flow
                }

                val movies = contentRepository.observeMovies(connection.id).first()
                val series = contentRepository.observeSeries(connection.id).first()

                emit(
                    (movies.filter { it.id in watchedParentIds }.mapNotNull { it.genre } +
                        series.filter { it.id in watchedParentIds }.mapNotNull { it.genre })
                        .toSet()
                )
            }.flowOn(Dispatchers.Default).first()

            // Heavy catalogue transformation happens once per catalogue emission,
            // not once per keystroke/filter change. The resulting immutable-ish
            // snapshot is then reused by the lightweight search pipeline.
            val catalogFlow = combine(
                contentRepository.observeMovies(connection.id),
                contentRepository.observeSeries(connection.id),
                contentRepository.observeChannels(connection.id),
            ) { movies, series, channels ->
                val cleanMovie = movies
                    .asSequence()
                    .filter { contentPolicy.allows(isKids, it) }
                    .distinctBy { smartCategoryBuilder.cleanTitle(it.name).lowercase() to it.year }
                    .map { MediaItem.MovieItem(it) }
                    .toList()

                val cleanSeries = series
                    .asSequence()
                    .filter { contentPolicy.allows(isKids, it) }
                    .distinctBy { smartCategoryBuilder.cleanTitle(it.name).lowercase() to it.year }
                    .map { MediaItem.SeriesItem(it) }
                    .toList()

                val cleanChannels = channels
                    .asSequence()
                    .filter { contentPolicy.allows(isKids, it) }
                    .map { MediaItem.ChannelItem(it) }
                    .toList()

                SearchCatalog(cleanMovie, cleanSeries, cleanChannels)
            }
                .flowOn(Dispatchers.Default)
                .distinctUntilChanged()
                .conflate()

            val recentFlow = profile?.let {
                settingsRepository.recentSearches(it.id)
                    .distinctUntilChanged()
            } ?: flowOf(emptyList())

            // Only this small pipeline reacts to typing and tab/filter changes.
            // The ViewModel survives MainShell tab swaps, so this state is not
            // recreated merely because SearchScreen leaves/re-enters composition.
            combine(
                catalogFlow,
                query
                    .debounce(250)
                    .map(String::trim),
                filter,
                recentFlow,
                resultLimit,
            ) { catalog, q, activeFilter, recent, limit ->
                val pool = when (activeFilter) {
                    SearchFilter.MOVIES -> catalog.movies
                    SearchFilter.SERIES -> catalog.series
                    SearchFilter.CHANNELS -> catalog.channels
                    SearchFilter.ALL -> catalog.all
                }

                // The full match sequence, lazily evaluated. We pull one extra
                // beyond `limit` so the UI knows whether "carregar mais" has
                // anything left (audit #20).
                val matched: Sequence<MediaItem> = if (q.isBlank()) {
                    emptySequence()
                } else {
                    // "ação, comédia" — a comma means "match BOTH genres". Each
                    // part is expanded with its synonyms; an item is kept only
                    // when its genre/category matches EVERY part.
                    val genreParts = q.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                    if (genreParts.size >= 2) {
                        val termSets = genreParts
                            .map { part -> genreTermsFor(part) }
                            .filter { it.isNotEmpty() }
                        if (termSets.isEmpty()) {
                            emptySequence()
                        } else {
                            pool.asSequence().filter { item ->
                                val hays = haystacksFor(item)
                                termSets.all { terms ->
                                    hays.any { hay ->
                                        terms.any { t ->
                                            com.auroraplay.iptv.core.util.MetadataSanitizer.containsWord(hay, t)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Single term: title match (accent- and case-insensitive,
                        // audit #20) OR a genre/category word match. Title hits
                        // are yielded first so they rank above genre hits.
                        val foldedQuery = com.auroraplay.iptv.core.util.MetadataSanitizer.fold(q)
                        val genreNeedles = genreTermsFor(q)
                        fun titleHit(item: MediaItem) =
                            com.auroraplay.iptv.core.util.MetadataSanitizer.fold(item.title).contains(foldedQuery)
                        fun genreHit(item: MediaItem) =
                            genreNeedles.isNotEmpty() && haystacksFor(item).any { hay ->
                                genreNeedles.any { term ->
                                    com.auroraplay.iptv.core.util.MetadataSanitizer.containsWord(hay, term)
                                }
                            }
                        pool.asSequence().filter { titleHit(it) } +
                            pool.asSequence().filter { !titleHit(it) && genreHit(it) }
                    }
                }
                val head = matched.take(limit + 1).toList()
                val results = head.take(limit)
                val hasMore = head.size > limit

                val suggestionPool = when (activeFilter) {
                    SearchFilter.MOVIES -> catalog.movies
                    SearchFilter.SERIES -> catalog.series
                    SearchFilter.CHANNELS -> emptyList()
                    SearchFilter.ALL -> catalog.movies + catalog.series
                }

                val suggestions = if (watchedGenres.isEmpty()) {
                    suggestionPool
                        .sortedByDescending(::newestFirst)
                        .take(12)
                } else {
                    suggestionPool
                        .asSequence()
                        .filter { item ->
                            item.id !in watchedParentIds &&
                                genreOf(item)?.let(watchedGenres::contains) == true
                        }
                        .take(12)
                        .toList()
                        .ifEmpty {
                            suggestionPool.sortedByDescending(::newestFirst).take(12)
                        }
                }

                SearchUiState(
                    query = q,
                    searchedQuery = q,
                    filter = activeFilter,
                    results = results,
                    hasMoreResults = hasMore,
                    suggestions = suggestions,
                    recentSearches = recent,
                    isLoading = false,
                )
            }
                .flowOn(Dispatchers.Default)
                .distinctUntilChanged()
                .conflate()
                // Merge, never replace: `query` and `filter` are owned by the
                // synchronous setters below so the field / chips react on the
                // keystroke, not 250 ms later when this debounced pipeline
                // catches up. That lag was why the search bar felt dead.
                .collect { snapshot ->
                    _uiState.update {
                        it.copy(
                            searchedQuery = snapshot.searchedQuery,
                            results = snapshot.results,
                            hasMoreResults = snapshot.hasMoreResults,
                            suggestions = snapshot.suggestions,
                            recentSearches = snapshot.recentSearches,
                            isLoading = false,
                        )
                    }
                }
    }

    /** "Carregar mais" — widen the result window by one page. */
    fun loadMoreResults() {
        resultLimit.value += SEARCH_PAGE
    }

    private fun genreOf(item: MediaItem): String? = when (item) {
        is MediaItem.MovieItem -> item.movie.genre
        is MediaItem.SeriesItem -> item.series.genre
        is MediaItem.ChannelItem -> null
    }

    /** A single genre token, folded and expanded with its PT/EN synonyms —
     * the set of words any of which counts as "this item has that genre".
     * Terms shorter than 3 chars are dropped so a stray "a"/"de" is inert. */
    private fun genreTermsFor(part: String): Set<String> {
        val folded = com.auroraplay.iptv.core.util.MetadataSanitizer.fold(part)
        return ((GENRE_SYNONYMS[folded] ?: emptySet()) + folded)
            .filter { it.length >= 3 }
            .toSet()
    }

    /** Folded genre + category strings a genre query is tested against. */
    private fun haystacksFor(item: MediaItem): List<String> {
        val raw = when (item) {
            is MediaItem.MovieItem -> listOf(item.movie.genre, item.movie.categoryName)
            is MediaItem.SeriesItem -> listOf(item.series.genre, item.series.categoryName)
            is MediaItem.ChannelItem -> listOf(item.channel.categoryName)
        }
        return raw.filterNotNull()
            .filter { it.isNotBlank() }
            .map { com.auroraplay.iptv.core.util.MetadataSanitizer.fold(it) }
    }

    private companion object {
        /** Both directions so a PT query hits an EN tag and vice-versa. Values
         * are already folded/lower-case and matched on a word boundary
         * (MetadataSanitizer.containsWord). */
        val GENRE_SYNONYMS: Map<String, Set<String>> = buildGenreSynonyms(
            setOf("acao", "action"),
            setOf("aventura", "adventure"),
            setOf("comedia", "comedy"),
            setOf("comedia romantica", "romantic comedy", "rom-com", "romcom"),
            setOf("romance", "romantico", "romantica", "romantic"),
            setOf("drama", "dramatico", "dramatica"),
            setOf("terror", "horror"),
            setOf("suspense", "thriller"),
            setOf("misterio", "mystery"),
            setOf("crime", "policial", "true crime", "detetive"),
            setOf("ficcao cientifica", "ficcao", "sci-fi", "scifi", "science fiction"),
            setOf("fantasia", "fantasy"),
            setOf("animacao", "animation", "anime", "desenho", "desenho animado", "cartoon"),
            setOf("documentario", "documentary", "docs", "doc"),
            setOf("guerra", "war"),
            setOf("faroeste", "western", "velho oeste"),
            setOf("familia", "family", "familiar"),
            setOf("infantil", "kids", "criancas", "crianca", "para criancas"),
            setOf("musical", "music", "musica"),
            setOf("biografia", "biografico", "cinebiografia", "biography", "biographical", "biopic"),
            setOf("historia", "historico", "historica", "history", "historical"),
            setOf("esporte", "esportes", "sport", "sports"),
            setOf("novela", "novelas", "soap opera"),
            setOf("reality", "reality show", "realities"),
            setOf("dorama", "doramas", "k-drama", "kdrama", "coreano", "coreana", "asiatica", "asiatico"),
            setOf("classico", "classicos", "classic", "classics"),
            setOf("nacional", "brasileiro", "brasileira", "cinema nacional"),
            setOf("religioso", "religiosa", "gospel", "fe", "cristao"),
            setOf("stand up", "stand-up", "standup"),
        )

        /** Expands each equivalence group into a full bidirectional map:
         * every term points at all the others in its group. */
        private fun buildGenreSynonyms(vararg groups: Set<String>): Map<String, Set<String>> {
            val out = HashMap<String, MutableSet<String>>()
            for (group in groups) {
                for (term in group) {
                    out.getOrPut(term) { mutableSetOf() }.addAll(group - term)
                }
            }
            return out
        }
    }

    private fun newestFirst(item: MediaItem): Long = when (item) {
        is MediaItem.MovieItem -> item.movie.addedAtMillis
        is MediaItem.SeriesItem -> item.series.addedAtMillis
        is MediaItem.ChannelItem -> 0L
    }

    fun updateQuery(newQuery: String) {
        query.value = newQuery
        resultLimit.value = SEARCH_PAGE // a new query starts from the first page
        // Reflect the keystroke immediately; the debounced pipeline updates
        // results/searchedQuery a moment later.
        _uiState.update { it.copy(query = newQuery) }
    }

    fun updateFilter(newFilter: SearchFilter) {
        filter.value = newFilter
        resultLimit.value = SEARCH_PAGE
        _uiState.update { it.copy(filter = newFilter) }
    }

    fun clearRecentSearches() {
        val profileId = activeProfileId ?: return
        viewModelScope.launch {
            settingsRepository.clearRecentSearches(profileId)
        }
    }
}
