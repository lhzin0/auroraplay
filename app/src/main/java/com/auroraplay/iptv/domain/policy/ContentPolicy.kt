package com.auroraplay.iptv.domain.policy

import com.auroraplay.iptv.core.util.KidsContentFilter
import com.auroraplay.iptv.core.util.MatureContentFilter
import com.auroraplay.iptv.domain.model.Channel
import com.auroraplay.iptv.domain.model.Movie
import com.auroraplay.iptv.domain.model.Profile
import com.auroraplay.iptv.domain.model.Series
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single place that decides whether a profile is allowed to see a piece of
 * content. Every content-revealing surface — Home, search, live TV, the EPG
 * guide, movie/series grids, detail pages, "more like this", favourites,
 * history, downloads and **the player** — must go through here rather than
 * re-implementing the check.
 *
 * Today the only restriction is a **kids profile**, which sees *only*
 * child-appropriate content (an allowlist against provider category naming, via
 * [KidsContentFilter]) and never anything that looks adult ([MatureContentFilter]).
 * A profile with no synced Kids category therefore sees nothing, which is the
 * safe failure mode. Adult profiles are unrestricted.
 */
@Singleton
class ContentPolicy @Inject constructor() {

    fun restricts(profile: Profile?): Boolean = profile?.isKids == true

    fun allows(isKidsProfile: Boolean, channel: Channel): Boolean =
        !isKidsProfile || kidsSafe(channel.name, channel.categoryName)

    fun allows(isKidsProfile: Boolean, movie: Movie): Boolean =
        !isKidsProfile || kidsSafe(movie.name, movie.categoryName, movie.genre)

    fun allows(isKidsProfile: Boolean, series: Series): Boolean =
        !isKidsProfile || kidsSafe(series.name, series.categoryName, series.genre)

    /** Raw-field overload for callers that only have loose strings (e.g. a
     * history snapshot or an EPG row). */
    fun allowsFields(isKidsProfile: Boolean, vararg fields: String?): Boolean =
        !isKidsProfile || kidsSafe(*fields)

    /**
     * Weaker check for surfaces where only a loose title is available and the
     * full kids allowlist would hide legitimate content (downloads, a history
     * snapshot): for a kids profile, only exclude what *looks adult*. Proper
     * per-profile scoping of those surfaces is audit #3/#8.
     */
    fun visibleLoose(isKidsProfile: Boolean, vararg fields: String?): Boolean =
        !isKidsProfile || !MatureContentFilter.isAdult(*fields)

    fun channels(isKidsProfile: Boolean, list: List<Channel>): List<Channel> =
        if (isKidsProfile) list.filter { allows(true, it) } else list

    fun movies(isKidsProfile: Boolean, list: List<Movie>): List<Movie> =
        if (isKidsProfile) list.filter { allows(true, it) } else list

    fun series(isKidsProfile: Boolean, list: List<Series>): List<Series> =
        if (isKidsProfile) list.filter { allows(true, it) } else list

    private fun kidsSafe(vararg fields: String?): Boolean =
        KidsContentFilter.isKidsAppropriate(*fields) && !MatureContentFilter.isAdult(*fields)
}
