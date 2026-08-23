package com.dumuzeyn.mp3player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LrcParser {
    private static final int MAX_INPUT_CHARS = 1_000_000;
    private static final int MAX_LINES = 10_000;
    private static final Pattern TIMESTAMP = Pattern.compile(
            "\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");
    private static final Pattern OFFSET = Pattern.compile("\\[offset:([+-]?\\d+)]",
            Pattern.CASE_INSENSITIVE);

    LrcDocument parse(String input) {
        String safe = input == null ? "" : input.substring(0,
                Math.min(input.length(), MAX_INPUT_CHARS));
        long offset = parseOffset(safe);
        ArrayList<LrcLine> timed = new ArrayList<>();
        StringBuilder plain = new StringBuilder();
        String[] rows = safe.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        for (int index = 0; index < rows.length && index < MAX_LINES; index++) {
            parseRow(rows[index], offset, timed, plain);
        }
        java.util.Collections.sort(timed,
                (left, right) -> Long.compare(left.timeMs, right.timeMs));
        return new LrcDocument(timed, plain.toString().trim(), !timed.isEmpty());
    }

    private static void parseRow(String row, long offset, ArrayList<LrcLine> timed,
            StringBuilder plain) {
        Matcher matcher = TIMESTAMP.matcher(row);
        ArrayList<Long> times = new ArrayList<>();
        int textStart = 0;
        while (matcher.find()) {
            times.add(Math.max(0L, timestamp(matcher) + offset));
            textStart = matcher.end();
        }
        String text = row.substring(Math.min(textStart, row.length())).trim();
        if (times.isEmpty()) {
            if (!row.startsWith("[") && !row.trim().isEmpty()) {
                appendPlain(plain, row.trim());
            }
            return;
        }
        for (Long time : times) {
            timed.add(new LrcLine(time, text));
        }
        appendPlain(plain, text);
    }

    private static long timestamp(Matcher matcher) {
        long minutes = number(matcher.group(1));
        long seconds = Math.min(59L, number(matcher.group(2)));
        String fraction = matcher.group(3);
        long millis = 0L;
        if (fraction != null) {
            millis = number(fraction);
            if (fraction.length() == 1) {
                millis *= 100L;
            } else if (fraction.length() == 2) {
                millis *= 10L;
            }
        }
        return minutes * 60_000L + seconds * 1_000L + Math.min(999L, millis);
    }

    private static long parseOffset(String source) {
        Matcher matcher = OFFSET.matcher(source);
        return matcher.find() ? signedNumber(matcher.group(1)) : 0L;
    }

    private static long number(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException error) {
            return 0L;
        }
    }

    private static long signedNumber(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException error) {
            return 0L;
        }
    }

    private static void appendPlain(StringBuilder target, String value) {
        if (value.isEmpty()) {
            return;
        }
        if (target.length() > 0) {
            target.append('\n');
        }
        target.append(value);
    }
}
