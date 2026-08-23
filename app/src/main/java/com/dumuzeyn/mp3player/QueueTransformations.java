package com.dumuzeyn.mp3player;

import java.util.ArrayList;
import java.util.List;

final class QueueTransformations {
    private QueueTransformations() {
    }

    static <T> ArrayList<T> move(List<T> source, int from, int to) {
        ArrayList<T> result = new ArrayList<>(source);
        if (from < 0 || from >= result.size() || to < 0 || to >= result.size()
                || from == to) {
            return result;
        }
        T item = result.remove(from);
        result.add(to, item);
        return result;
    }

    static <T> ArrayList<T> remove(List<T> source, int index) {
        ArrayList<T> result = new ArrayList<>(source);
        if (index >= 0 && index < result.size()) {
            result.remove(index);
        }
        return result;
    }

    static <T> ArrayList<T> playNext(List<T> source, T item, int currentIndex) {
        ArrayList<T> result = new ArrayList<>(source);
        result.remove(item);
        int insertion = Math.max(0, Math.min(currentIndex + 1, result.size()));
        result.add(insertion, item);
        return result;
    }
}
