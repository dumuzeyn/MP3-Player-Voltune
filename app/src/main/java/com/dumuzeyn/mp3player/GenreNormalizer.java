package com.dumuzeyn.mp3player;

import java.util.Locale;

/** Preserves real genre tags while normalizing empty values and ID3 numeric genres. */
final class GenreNormalizer {
    static final String UNKNOWN = "Unknown genre";

    private static final String[] ID3_V1 = {
            "Blues", "Classic Rock", "Country", "Dance", "Disco", "Funk",
            "Grunge", "Hip-Hop", "Jazz", "Metal", "New Age", "Oldies", "Other",
            "Pop", "R&B", "Rap", "Reggae", "Rock", "Techno", "Industrial",
            "Alternative", "Ska", "Death Metal", "Pranks", "Soundtrack",
            "Euro-Techno", "Ambient", "Trip-Hop", "Vocal", "Jazz+Funk", "Fusion",
            "Trance", "Classical", "Instrumental", "Acid", "House", "Game",
            "Sound Clip", "Gospel", "Noise", "Alternative Rock", "Bass", "Soul",
            "Punk", "Space", "Meditative", "Instrumental Pop", "Instrumental Rock",
            "Ethnic", "Gothic", "Darkwave", "Techno-Industrial", "Electronic",
            "Pop-Folk", "Eurodance", "Dream", "Southern Rock", "Comedy", "Cult",
            "Gangsta", "Top 40", "Christian Rap", "Pop/Funk", "Jungle",
            "Native American", "Cabaret", "New Wave", "Psychedelic", "Rave",
            "Showtunes", "Trailer", "Lo-Fi", "Tribal", "Acid Punk", "Acid Jazz",
            "Polka", "Retro", "Musical", "Rock & Roll", "Hard Rock", "Folk",
            "Folk-Rock", "National Folk", "Swing", "Fast Fusion", "Bebop", "Latin",
            "Revival", "Celtic", "Bluegrass", "Avantgarde", "Gothic Rock",
            "Progressive Rock", "Psychedelic Rock", "Symphonic Rock", "Slow Rock",
            "Big Band", "Chorus", "Easy Listening", "Acoustic", "Humour", "Speech",
            "Chanson", "Opera", "Chamber Music", "Sonata", "Symphony", "Booty Bass",
            "Primus", "Porn Groove", "Satire", "Slow Jam", "Club", "Tango", "Samba",
            "Folklore", "Ballad", "Power Ballad", "Rhythmic Soul", "Freestyle", "Duet",
            "Punk Rock", "Drum Solo", "A Cappella", "Euro-House", "Dance Hall", "Goa",
            "Drum & Bass", "Club-House", "Hardcore", "Terror", "Indie", "BritPop",
            "Negerpunk", "Polsk Punk", "Beat", "Christian Gangsta Rap", "Heavy Metal",
            "Black Metal", "Crossover", "Contemporary Christian", "Christian Rock",
            "Merengue", "Salsa", "Thrash Metal", "Anime", "JPop", "Synthpop"
    };

    private GenreNormalizer() {
    }

    static String normalize(String raw) {
        String value = clean(raw);
        if (isPlaceholder(value)) {
            return UNKNOWN;
        }
        if (value.startsWith("(")) {
            int closing = value.indexOf(')');
            if (closing > 1) {
                String trailing = clean(value.substring(closing + 1));
                if (!isPlaceholder(trailing)) {
                    return trailing;
                }
                String mapped = numericGenre(value.substring(1, closing));
                if (mapped != null) {
                    return mapped;
                }
            }
        }
        String mapped = numericGenre(value);
        return mapped == null ? value : mapped;
    }

    static boolean isUnknown(String value) {
        return UNKNOWN.equals(normalize(value));
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace('\u0000', ' ')
                .replace('\n', ' ').replace('\r', ' ').trim().replaceAll("\\s+", " ");
    }

    private static boolean isPlaceholder(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.isEmpty() || "unknown".equals(lower) || "unknown genre".equals(lower)
                || "неизвестный жанр".equals(lower) || "<unknown>".equals(lower)
                || "none".equals(lower) || "null".equals(lower)
                || "undefined".equals(lower) || "n/a".equals(lower)
                || "-".equals(lower) || "255".equals(lower);
    }

    private static String numericGenre(String value) {
        try {
            int index = Integer.parseInt(value.trim());
            return index >= 0 && index < ID3_V1.length ? ID3_V1[index] : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
