package com.alal.yft.download

import com.alal.yft.core.download.DownloadDestinationKind
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.MediaTrackType
import com.alal.yft.feature.detectedmedia.LookupOwner
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Fresh link renewal: where a download's bytes are, so a download the app closed can go on from
 * them. The kind, the file the platform named ([uri]; a pending MediaStore item or a temporary
 * document) and, for a file in app storage, the name of its `.part` file; nothing else is needed
 * to open the same file again.
 */
data class SavedDestination(
    val kind: DownloadDestinationKind,
    val uri: String?,
    val displayName: String,
    val partialName: String? = null,
) {
    /** What is saved is only what can be reopened: a document's address, or the partial's name. */
    val reopenable: Boolean
        get() = when (kind) {
            DownloadDestinationKind.APP_PRIVATE -> !partialName.isNullOrBlank()
            DownloadDestinationKind.MEDIA_STORE,
            DownloadDestinationKind.SAF_DOCUMENT,
            -> !uri.isNullOrBlank()
        }

    override fun toString(): String =
        "SavedDestination(kind=$kind, uriPresent=${!uri.isNullOrBlank()}, " +
            "partialPresent=${!partialName.isNullOrBlank()})"

    companion object {
        /**
         * The destination [prepared] made, as the download keeps it: a file in app storage is known
         * by its partial file's name, a public one by the address the platform gave it.
         */
        fun of(prepared: PreparedDestination): SavedDestination = SavedDestination(
            kind = prepared.spec.kind,
            uri = prepared.spec.uri,
            displayName = prepared.fileName,
            partialName = prepared.partialName,
        )
    }
}

/**
 * Fresh link renewal: a download's [DownloadSource] and its [SavedDestination], written as one
 * text for the download's record, so a Retry after the app closed can look the same file up again
 * and go on from the bytes it kept. The text holds the page's address and the facts that name the
 * file there; it is written for the app's own use, is never shown, and goes with Clear browsing
 * data ([DownloadSources.clear]) and with the download's record.
 *
 * The fields are URL-encoded and joined by a unit separator, so no title, address or label can
 * break the line; `~` stands for a field the source does not state. The file's query values are
 * kept hashed (Same-file check), so the card holds no signature. A text of another version is
 * no card: the download then needs its page reopened, as before.
 */
object RenewalCard {
    private const val VERSION = "1"
    private const val SEPARATOR = "\u001F"
    private const val NONE = "~"

    fun encode(source: DownloadSource): String {
        val key = source.quality
        val fields = listOf(
            VERSION,
            source.pageUrl,
            source.videoId,
            source.candidateKind.name,
            source.owner.name,
            source.userAgent,
            source.session.toString(),
            key.kind.name,
            key.trackType.name,
            key.merged.toString(),
            key.height?.toString(),
            key.width?.toString(),
            key.framesPerSecond?.toString(),
            key.container,
            key.codecs.sorted().joinToString(","),
            key.soundCodecs.sorted().joinToString(","),
            key.label,
            key.bitrateBitsPerSecond?.toString(),
            source.conversion.audioFromVideo.toString(),
            source.conversion.mp3Kbps?.toString(),
            source.label,
            source.title,
            source.durationMillis?.toString(),
            source.fileName,
            source.networkChanges.toString(),
            source.mediaPath,
            source.saved?.kind?.name,
            source.saved?.uri,
            source.saved?.displayName,
            source.saved?.partialName,
            // Same-file check: added after the first cards, so an older card ends before it.
            source.mediaQuery,
        )
        return fields.joinToString(SEPARATOR) { field -> field?.let(::encodeField) ?: NONE }
    }

    /** The card [text] holds, or null when it is not one this version reads. */
    fun decode(text: String): Pair<DownloadSource, SavedDestination?>? {
        val fields = text.split(SEPARATOR)
        if (fields.size < FIELDS || decodeField(fields[0]) != VERSION) return null
        val values = fields.map(::decodeField)
        fun at(index: Int): String? = values.getOrNull(index)
        fun long(index: Int): Long? = at(index)?.toLongOrNull()
        fun int(index: Int): Int? = at(index)?.toIntOrNull()
        fun bool(index: Int): Boolean? = at(index)?.toBooleanStrictOrNull()
        val pageUrl = at(1)?.takeIf(String::isNotBlank) ?: return null
        val kind = enumOrNull<MediaKind>(at(7)) ?: return null
        val trackType = enumOrNull<MediaTrackType>(at(8)) ?: return null
        val owner = enumOrNull<LookupOwner>(at(4)) ?: LookupOwner.BROWSER
        val destination = at(26)?.let { name ->
            enumOrNull<DownloadDestinationKind>(name)?.let { destinationKind ->
                SavedDestination(
                    kind = destinationKind,
                    uri = at(27),
                    displayName = at(28) ?: return null,
                    partialName = at(29),
                )
            }
        }
        val source = DownloadSource(
            pageUrl = pageUrl,
            videoId = at(2),
            candidateKind = enumOrNull<MediaKind>(at(3)) ?: kind,
            owner = owner,
            userAgent = at(5),
            session = bool(6) ?: false,
            quality = DownloadQualityKey(
                kind = kind,
                trackType = trackType,
                merged = bool(9) ?: false,
                height = int(10),
                width = int(11),
                framesPerSecond = int(12),
                container = at(13),
                codecs = setOfNotNull(at(14)),
                soundCodecs = setOfNotNull(at(15)),
                label = at(16),
                bitrateBitsPerSecond = long(17),
            ),
            conversion = DownloadConversion(
                audioFromVideo = bool(18) ?: false,
                mp3Kbps = int(19),
            ),
            label = at(20),
            title = at(21),
            durationMillis = long(22),
            fileName = at(23)?.takeIf(String::isNotBlank) ?: return null,
            networkChanges = long(24) ?: 0L,
            mediaPath = at(25),
            mediaQuery = at(25)?.let { at(30) },
            saved = destination,
        )
        return source to destination
    }

    private fun setOfNotNull(field: String?): Set<String> =
        field?.split(',')?.filter(String::isNotBlank)?.toSet().orEmpty()

    private inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
        name?.let { value -> enumValues<T>().firstOrNull { it.name == value } }

    private fun encodeField(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun decodeField(field: String): String? =
        if (field == NONE) null else URLDecoder.decode(field, "UTF-8")

    /**
     * The fields every card of this version has; a shorter text is another version's. A card
     * written before the file's hashed query values were kept has only these.
     */
    private val FIELDS = 30
}
