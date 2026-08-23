package com.dumuzeyn.mp3player;

final class MetadataValidator {
    static final int MAX_TEXT_LENGTH = 300;

    private MetadataValidator() {
    }

    static String cleanText(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "")
                .replace('\n', ' ').replace('\t', ' ').replaceAll("\\s+", " ").trim();
        return cleaned.length() <= MAX_TEXT_LENGTH
                ? cleaned : cleaned.substring(0, MAX_TEXT_LENGTH).trim();
    }

    static int year(String value) {
        return boundedNumber(value, 0, 3000);
    }

    static int trackNumber(String value) {
        return boundedNumber(firstPart(value), 0, 9999);
    }

    private static String firstPart(String value) {
        if (value == null) {
            return "";
        }
        int separator = value.indexOf('/');
        return separator < 0 ? value : value.substring(0, separator);
    }

    private static int boundedNumber(String value, int minimum, int maximum) {
        try {
            int parsed = Integer.parseInt(value == null ? "" : value.trim());
            return parsed >= minimum && parsed <= maximum ? parsed : 0;
        } catch (NumberFormatException error) {
            return 0;
        }
    }
}
