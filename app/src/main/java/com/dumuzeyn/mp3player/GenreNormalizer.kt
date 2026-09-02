package com.dumuzeyn.mp3player

import java.util.Locale

/** Preserves real genre tags while normalizing empty values and ID3 numeric genres. */
object GenreNormalizer {
    const val UNKNOWN = "Unknown genre"
    private val whitespace = Regex("\\s+")
    private val id3V1 = arrayOf(
        "Blues", "Classic Rock", "Country", "Dance", "Disco", "Funk", "Grunge", "Hip-Hop",
        "Jazz", "Metal", "New Age", "Oldies", "Other", "Pop", "R&B", "Rap", "Reggae",
        "Rock", "Techno", "Industrial", "Alternative", "Ska", "Death Metal", "Pranks",
        "Soundtrack", "Euro-Techno", "Ambient", "Trip-Hop", "Vocal", "Jazz+Funk", "Fusion",
        "Trance", "Classical", "Instrumental", "Acid", "House", "Game", "Sound Clip",
        "Gospel", "Noise", "Alternative Rock", "Bass", "Soul", "Punk", "Space", "Meditative",
        "Instrumental Pop", "Instrumental Rock", "Ethnic", "Gothic", "Darkwave",
        "Techno-Industrial", "Electronic", "Pop-Folk", "Eurodance", "Dream", "Southern Rock",
        "Comedy", "Cult", "Gangsta", "Top 40", "Christian Rap", "Pop/Funk", "Jungle",
        "Native American", "Cabaret", "New Wave", "Psychedelic", "Rave", "Showtunes",
        "Trailer", "Lo-Fi", "Tribal", "Acid Punk", "Acid Jazz", "Polka", "Retro", "Musical",
        "Rock & Roll", "Hard Rock", "Folk", "Folk-Rock", "National Folk", "Swing",
        "Fast Fusion", "Bebop", "Latin", "Revival", "Celtic", "Bluegrass", "Avantgarde",
        "Gothic Rock", "Progressive Rock", "Psychedelic Rock", "Symphonic Rock", "Slow Rock",
        "Big Band", "Chorus", "Easy Listening", "Acoustic", "Humour", "Speech", "Chanson",
        "Opera", "Chamber Music", "Sonata", "Symphony", "Booty Bass", "Primus", "Porn Groove",
        "Satire", "Slow Jam", "Club", "Tango", "Samba", "Folklore", "Ballad", "Power Ballad",
        "Rhythmic Soul", "Freestyle", "Duet", "Punk Rock", "Drum Solo", "A Cappella",
        "Euro-House", "Dance Hall", "Goa", "Drum & Bass", "Club-House", "Hardcore", "Terror",
        "Indie", "BritPop", "Negerpunk", "Polsk Punk", "Beat", "Christian Gangsta Rap",
        "Heavy Metal", "Black Metal", "Crossover", "Contemporary Christian", "Christian Rock",
        "Merengue", "Salsa", "Thrash Metal", "Anime", "JPop", "Synthpop",
    )

    @JvmStatic
    fun normalize(raw: String?): String {
        val value = clean(raw)
        if (isPlaceholder(value)) return UNKNOWN
        if (value.startsWith('(')) {
            val closing = value.indexOf(')')
            if (closing > 1) {
                val trailing = clean(value.substring(closing + 1))
                if (!isPlaceholder(trailing)) return trailing
                numericGenre(value.substring(1, closing))?.let { return it }
            }
        }
        return numericGenre(value) ?: value
    }

    @JvmStatic
    fun isUnknown(value: String?): Boolean = normalize(value) == UNKNOWN

    private fun clean(value: String?): String = value.orEmpty()
        .replace('\u0000', ' ')
        .replace('\n', ' ')
        .replace('\r', ' ')
        .trim()
        .replace(whitespace, " ")

    private fun isPlaceholder(value: String): Boolean = when (value.lowercase(Locale.ROOT)) {
        "", "unknown", "unknown genre", "неизвестный жанр", "<unknown>",
        "none", "null", "undefined", "n/a", "-", "255",
        -> true
        else -> false
    }

    private fun numericGenre(value: String): String? =
        value.trim().toIntOrNull()?.takeIf(id3V1.indices::contains)?.let(id3V1::get)
}
