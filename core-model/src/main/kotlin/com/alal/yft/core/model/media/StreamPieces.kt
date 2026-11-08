package com.alal.yft.core.model.media

import java.net.URI
import java.util.Locale

/**
 * Stream piece filter: what an HLS media playlist names as its pieces, read from its text. The
 * page's player fetches them one by one, and each is part of that stream, never another video of
 * the page. A [live] playlist has no end: newer pieces keep coming, in the folders of the ones
 * it names. Holds addresses without their query, so no signed value is kept twice.
 */
class StreamPieces private constructor(
    /** The playlist has no end: a live stream ([MediaKind.HLS] YFT can't save). */
    val live: Boolean,
    /** The named pieces' addresses, without query or fragment. */
    private val files: Set<String>,
) {
    /** The folders of [files] with each file's type: `https://host/a/` to `mp4`. */
    private val folders: Set<Pair<String, String>> = files.mapNotNullTo(HashSet(), ::folderOf)

    val size: Int get() = files.size

    /**
     * Whether [url] is one of these pieces: named by the playlist; or, in a folder of its pieces
     * and of their file type, a newer piece of a live stream or a file of a type only pieces
     * have (`.ts`, `.m4s`). A whole `.mp4` next to a finished stream's pieces is not one.
     */
    fun names(url: String): Boolean {
        val address = addressOf(url) ?: return false
        if (address in files) return true
        val folder = folderOf(address) ?: return false
        return folder in folders && (live || folder.second in PIECE_TYPES)
    }

    override fun equals(other: Any?): Boolean =
        other is StreamPieces && other.live == live && other.files == files

    override fun hashCode(): Int = 31 * live.hashCode() + files.hashCode()

    // Piece addresses may carry signed paths; diagnostics only count them.
    override fun toString(): String = "StreamPieces(live=$live, pieces=${files.size})"

    companion object {
        /** The pieces [pieceUrls] of a media playlist; null when it names none. */
        fun of(live: Boolean, pieceUrls: Collection<String>): StreamPieces? {
            val files = pieceUrls.mapNotNullTo(LinkedHashSet(), ::addressOf)
            return if (files.isEmpty()) null else StreamPieces(live, files)
        }

        /**
         * Whether [candidate] is a piece of one of the page's [streams]: a file only the page's
         * player asked for, named as no video of its own. A site adapter's file, one the page
         * or its player setup names as its video, or one its markup, a download or a pasted link
         * names, never is.
         */
        fun isPiece(candidate: MediaCandidate, streams: Collection<StreamPieces>): Boolean {
            if (streams.isEmpty()) return false
            if (candidate.kind != MediaKind.DIRECT && candidate.kind != MediaKind.UNKNOWN) {
                return false
            }
            if (candidate.videoId != null || candidate.pageVideoKey != null ||
                candidate.pageRole == PageMediaRole.MAIN ||
                candidate.sources.any { it !in PLAYER_SOURCES }
            ) return false
            return streams.any { it.names(candidate.mediaUrl) }
        }

        /** The streams the page's [candidates] read the pieces of. */
        fun streamsOf(candidates: Iterable<MediaCandidate>): List<StreamPieces> =
            candidates.mapNotNull { it.streamPieces }.distinct()

        /** Whether [group] is a live stream: a manifest read showed no end, none states one. */
        fun isLive(group: MediaGroup): Boolean {
            val files = group.candidates
            return files.any { it.streamPieces?.live == true } &&
                files.none { it.videoId != null || (it.durationMillis ?: 0L) > 0L } &&
                files.none { it.streamPieces?.live == false }
        }

        private fun addressOf(url: String): String? {
            val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
            val scheme = uri.scheme?.lowercase(Locale.US)?.takeIf { it == "https" || it == "http" }
                ?: return null
            val host = uri.host?.lowercase(Locale.US) ?: return null
            val port = if (uri.port == -1) "" else ":${uri.port}"
            val path = uri.rawPath?.takeIf(String::isNotEmpty) ?: return null
            return "$scheme://$host$port$path"
        }

        private fun folderOf(address: String): Pair<String, String>? {
            val slash = address.lastIndexOf('/')
            if (slash < 0) return null
            val name = address.substring(slash + 1)
            val type = name.substringAfterLast('.', missingDelimiterValue = "")
                .lowercase(Locale.US)
            return address.substring(0, slash + 1) to type
        }

        /** File types only stream pieces have. */
        private val PIECE_TYPES = setOf("ts", "m4s", "cmfv", "cmfa")
        private val PLAYER_SOURCES = setOf(
            CandidateSource.REQUEST,
            CandidateSource.REDIRECT,
            CandidateSource.MANIFEST,
        )
    }
}
