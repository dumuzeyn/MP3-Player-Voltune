package com.dumuzeyn.mp3player

object MetadataValidator {
    const val MAX_TEXT_LENGTH = 300
    private val controls = Regex("[\\p{Cntrl}&&[^\\n\\t]]")
    private val whitespace = Regex("\\s+")

    @JvmStatic
    fun cleanText(value: String?): String {
        val cleaned = value.orEmpty()
            .replace(controls, "")
            .replace('\n', ' ')
            .replace('\t', ' ')
            .replace(whitespace, " ")
            .trim()
        return if (cleaned.length <= MAX_TEXT_LENGTH) cleaned else cleaned
            .substring(0, MAX_TEXT_LENGTH)
            .trim()
    }

    @JvmStatic
    fun year(value: String?): Int = boundedNumber(value, 0, 3_000)

    @JvmStatic
    fun trackNumber(value: String?): Int = boundedNumber(value.orEmpty().substringBefore('/'), 0, 9_999)

    private fun boundedNumber(value: String?, minimum: Int, maximum: Int): Int =
        value?.trim()?.toIntOrNull()?.takeIf { it in minimum..maximum } ?: 0
}
