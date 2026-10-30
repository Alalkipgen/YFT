package com.alal.yft.core.model.media

import com.alal.yft.core.model.download.FileNames

/**
 * v1.0.2 item 51: one video item of a post that has several (an Instagram carousel, an X post
 * with more than one video): [number] of the post's [count] video items, in the post's order;
 * photos are not counted. The rows of the item the link names stay the lookup's answer, as
 * before, and carry the rows of the post's other video items in [others], each item under its
 * own [MediaCandidate.videoId]: the download sheet offers every item ([MediaGroups.postItems])
 * while Master's ladders, the trial and the census still compare the one item. The others' rows
 * carry no [others] of their own.
 */
data class PostVideoItem(
    val number: Int,
    val count: Int,
    val others: List<MediaCandidate> = emptyList(),
) {
    init {
        require(count >= 2) { "A post item is one of several" }
        require(number in 1..count) { "An item's number is one of the post's" }
    }

    /** "Video 2 of 4": the item's name in the sheet and in its file's name. */
    val label: String get() = "Video $number of $count"

    /**
     * [base], the post's title, with the item's [label]: "Caption (Video 2 of 4)"; the [label]
     * alone without a title. A long title is cut first ([MAX_TITLE_CHARS], [MAX_TITLE_BYTES]) so
     * that the file's name, which adds the quality, keeps the item's number. A title that has the
     * label already is kept as it is.
     */
    fun title(base: String?): String {
        val text = base?.trim()?.takeIf(String::isNotEmpty) ?: return label
        val suffix = " ($label)"
        if (text.endsWith(suffix)) return text
        val cut = FileNames.cut(text, MAX_TITLE_CHARS, MAX_TITLE_BYTES).trimEnd()
        val shown = if (cut.length < text.length) cut.trimEnd('…').trimEnd() + "…" else text
        return shown + suffix
    }

    /** Numbers only: the other items' rows hold signed links. */
    override fun toString(): String =
        "PostVideoItem(number=$number, count=$count, others=${others.size})"

    companion object {
        /** The longest post title kept in front of the label, in UTF-16 units. */
        const val MAX_TITLE_CHARS = 50

        /** The same in UTF-8 bytes: 50 letters of Myanmar's script. */
        const val MAX_TITLE_BYTES = 150
    }
}
