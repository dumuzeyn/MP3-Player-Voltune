package com.dumuzeyn.mp3player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Builds truthful local names from absolute, human-readable audio characteristics. */
final class SoundGroupNamer {
    private SoundGroupNamer() {
    }

    static ArrayList<SoundGroup> name(List<SoundGroup> source) {
        ArrayList<SoundGroup> ordered = new ArrayList<>(source);
        Collections.sort(ordered, (left, right) -> left.id.compareTo(right.id));
        Set<String> used = new HashSet<>();
        ArrayList<SoundGroup> result = new ArrayList<>();
        for (SoundGroup group : ordered) {
            ArrayList<Descriptor> descriptors = descriptors(group.centroid);
            ArrayList<Descriptor> selected = new ArrayList<>();
            for (Descriptor descriptor : descriptors) {
                if (selected.size() == 2) break;
                if (!containsCategory(selected, descriptor.category)) selected.add(descriptor);
            }
            if (selected.isEmpty()) {
                selected.add(new Descriptor("profile", "Сбалансированный профиль",
                        "Balanced profile", 0.0d));
            }
            String russian = join(selected, true);
            String english = join(selected, false);
            if (used.contains(russian)) {
                for (Descriptor descriptor : descriptors) {
                    if (descriptor.score >= 0.55d
                            && !containsCategory(selected, descriptor.category)) {
                        selected.add(descriptor);
                        russian = join(selected, true);
                        english = join(selected, false);
                        break;
                    }
                }
            }
            used.add(russian);
            result.add(group.named(russian, english));
        }
        return result;
    }

    private static ArrayList<Descriptor> descriptors(double[] values) {
        ArrayList<Descriptor> result = new ArrayList<>();
        double bpm = value(values, TrackAudioProfile.BPM);
        double confidence = value(values, TrackAudioProfile.TEMPO_CONFIDENCE);
        if (confidence >= 0.45d && bpm >= 50.0d) {
            if (bpm < 90.0d) {
                add(result, "tempo", "Медленный темп", "Slow tempo",
                        0.70d + (90.0d - bpm) / 70.0d);
            } else if (bpm < 118.0d) {
                add(result, "tempo", "Умеренный темп", "Moderate tempo", 0.34d);
            } else if (bpm < 145.0d) {
                add(result, "tempo", "Подвижный темп", "Driving tempo",
                        0.55d + (bpm - 118.0d) / 90.0d);
            } else {
                add(result, "tempo", "Быстрый темп", "Fast tempo",
                        0.85d + (bpm - 145.0d) / 70.0d);
            }
        }
        double energy = value(values, TrackAudioProfile.ENERGY);
        if (energy >= 0.22d) {
            add(result, "energy", "Высокая энергия", "High energy",
                    0.65d + (energy - 0.22d) * 3.0d);
        } else if (energy <= 0.075d) {
            add(result, "energy", "Спокойная энергия", "Calm energy",
                    0.58d + (0.075d - energy) * 4.0d);
        }
        double bass = value(values, TrackAudioProfile.BASS);
        if (bass >= 0.34d) {
            add(result, "bass", "Глубокий бас", "Deep bass",
                    0.68d + (bass - 0.34d) * 2.0d);
        } else if (bass <= 0.10d) {
            add(result, "bass", "Лёгкий бас", "Light bass",
                    0.48d + (0.10d - bass) * 2.0d);
        }
        double brightness = value(values, TrackAudioProfile.CENTROID);
        if (brightness >= 0.40d) {
            add(result, "spectrum", "Яркий спектр", "Bright spectrum",
                    0.66d + (brightness - 0.40d) * 2.0d);
        } else if (brightness <= 0.22d) {
            add(result, "spectrum", "Тёплый спектр", "Warm spectrum",
                    0.58d + (0.22d - brightness) * 2.0d);
        }
        double dynamics = value(values, TrackAudioProfile.DYNAMIC_RANGE);
        if (dynamics >= 14.0d) {
            add(result, "dynamics", "Живая динамика", "Expressive dynamics",
                    0.58d + (dynamics - 14.0d) / 20.0d);
        } else if (dynamics > 0.0d && dynamics <= 6.0d) {
            add(result, "dynamics", "Ровная динамика", "Even dynamics",
                    0.55d + (6.0d - dynamics) / 14.0d);
        }
        double rhythm = value(values, TrackAudioProfile.RHYTHM);
        if (rhythm >= 0.16d) {
            add(result, "rhythm", "Чёткий пульс", "Defined pulse",
                    0.56d + (rhythm - 0.16d) * 2.0d);
        } else if (rhythm > 0.0d && rhythm <= 0.055d) {
            add(result, "rhythm", "Плавный пульс", "Smooth pulse", 0.50d);
        }
        Collections.sort(result, (left, right) -> Double.compare(right.score, left.score));
        return result;
    }

    private static void add(ArrayList<Descriptor> target, String category,
            String russian, String english, double score) {
        target.add(new Descriptor(category, russian, english, score));
    }

    private static double value(double[] values, int index) {
        return values != null && index < values.length ? values[index] : 0.0d;
    }

    private static boolean containsCategory(List<Descriptor> values, String category) {
        for (Descriptor value : values) {
            if (value.category.equals(category)) return true;
        }
        return false;
    }

    private static String join(List<Descriptor> values, boolean russian) {
        StringBuilder result = new StringBuilder();
        for (Descriptor value : values) {
            if (result.length() > 0) result.append(" · ");
            result.append(russian ? value.russian : value.english);
        }
        return result.toString();
    }

    private static final class Descriptor {
        final String category;
        final String russian;
        final String english;
        final double score;

        Descriptor(String category, String russian, String english, double score) {
            this.category = category;
            this.russian = russian;
            this.english = english;
            this.score = score;
        }
    }
}
