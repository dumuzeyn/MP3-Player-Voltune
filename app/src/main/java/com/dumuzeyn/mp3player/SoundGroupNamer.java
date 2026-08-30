package com.dumuzeyn.mp3player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class SoundGroupNamer {
    private static final Trait[] TRAITS = new Trait[]{
            new Trait(TrackAudioProfile.ENERGY, "Энергичный поток", "Energetic flow",
                    "Спокойный поток", "Calm flow"),
            new Trait(TrackAudioProfile.BPM, "Быстрый ритм", "Fast rhythm",
                    "Медленный ритм", "Slow rhythm"),
            new Trait(TrackAudioProfile.CENTROID, "Яркий тембр", "Bright timbre",
                    "Тёмный тембр", "Dark timbre"),
            new Trait(TrackAudioProfile.BASS, "Глубокий бас", "Deep bass",
                    "Лёгкий бас", "Light bass"),
            new Trait(TrackAudioProfile.DYNAMIC_RANGE, "Живой контраст", "Vivid contrast",
                    "Ровный контраст", "Even contrast"),
            new Trait(TrackAudioProfile.RHYTHM, "Чёткий пульс", "Crisp pulse",
                    "Плавный пульс", "Smooth pulse"),
            new Trait(TrackAudioProfile.TREBLE, "Воздушный верх", "Airy treble",
                    "Тёплый верх", "Warm treble"),
            new Trait(TrackAudioProfile.ZERO_CROSSING, "Острый рисунок", "Sharp texture",
                    "Мягкий рисунок", "Soft texture")
    };
    private static final String[] FALLBACK_RU_ADJECTIVES = {
            "Лунный", "Чистый", "Глубокий", "Тёплый", "Свежий", "Звонкий",
            "Мягкий", "Светлый"
    };
    private static final String[] FALLBACK_EN_ADJECTIVES = {
            "Lunar", "Clear", "Deep", "Warm", "Fresh", "Resonant", "Soft", "Light"
    };
    private static final String[] FALLBACK_RU_NOUNS = {
            "пульс", "ритм", "поток", "тембр", "контур", "бас", "отклик", "узор"
    };
    private static final String[] FALLBACK_EN_NOUNS = {
            "pulse", "rhythm", "flow", "timbre", "contour", "bass", "response", "pattern"
    };

    private SoundGroupNamer() {
    }

    static ArrayList<SoundGroup> name(List<SoundGroup> source) {
        ArrayList<SoundGroup> ordered = new ArrayList<>(source);
        ordered.sort(Comparator.comparing(group -> group.id));
        Set<String> used = new HashSet<>();
        ArrayList<SoundGroup> result = new ArrayList<>();
        for (SoundGroup group : ordered) {
            ArrayList<Candidate> candidates = candidates(group.centroid);
            Candidate selected = null;
            for (Candidate candidate : candidates) {
                if (used.add(candidate.russian)) {
                    selected = candidate;
                    break;
                }
            }
            if (selected == null) {
                selected = fallback(group.id, used);
                used.add(selected.russian);
            }
            result.add(group.named(selected.russian, selected.english));
        }
        return result;
    }

    private static Candidate fallback(String id, Set<String> used) {
        int start = Math.floorMod(id.hashCode(), FALLBACK_RU_ADJECTIVES.length
                * FALLBACK_RU_NOUNS.length);
        for (int offset = 0; offset < FALLBACK_RU_ADJECTIVES.length
                * FALLBACK_RU_NOUNS.length; offset++) {
            int value = (start + offset) % (FALLBACK_RU_ADJECTIVES.length
                    * FALLBACK_RU_NOUNS.length);
            int adjective = value / FALLBACK_RU_NOUNS.length;
            int noun = value % FALLBACK_RU_NOUNS.length;
            String russian = FALLBACK_RU_ADJECTIVES[adjective] + " "
                    + FALLBACK_RU_NOUNS[noun];
            if (!used.contains(russian)) {
                return new Candidate(russian, FALLBACK_EN_ADJECTIVES[adjective] + " "
                        + FALLBACK_EN_NOUNS[noun], 0.0d);
            }
        }
        return new Candidate("Звуковой поток", "Sound flow", 0.0d);
    }

    private static ArrayList<Candidate> candidates(double[] centroid) {
        ArrayList<Candidate> result = new ArrayList<>();
        for (Trait trait : TRAITS) {
            double value = trait.feature < centroid.length ? centroid[trait.feature] : 0.0d;
            result.add(value >= 0.0d
                    ? new Candidate(trait.highRussian, trait.highEnglish, Math.abs(value))
                    : new Candidate(trait.lowRussian, trait.lowEnglish, Math.abs(value)));
        }
        result.sort((left, right) -> Double.compare(right.score, left.score));
        return result;
    }

    private static final class Trait {
        final int feature;
        final String highRussian;
        final String highEnglish;
        final String lowRussian;
        final String lowEnglish;

        Trait(int feature, String highRussian, String highEnglish, String lowRussian,
                String lowEnglish) {
            this.feature = feature;
            this.highRussian = highRussian;
            this.highEnglish = highEnglish;
            this.lowRussian = lowRussian;
            this.lowEnglish = lowEnglish;
        }
    }

    private static final class Candidate {
        final String russian;
        final String english;
        final double score;

        Candidate(String russian, String english, double score) {
            this.russian = russian;
            this.english = english;
            this.score = score;
        }
    }
}
