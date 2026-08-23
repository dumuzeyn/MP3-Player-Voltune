package com.dumuzeyn.mp3player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Deterministic current-item selection after removing library tracks from a queue. */
public final class QueueRemovalPlan {
    public final ArrayList<String> remaining;
    public final int currentIndex;
    public final boolean currentRemoved;

    private QueueRemovalPlan(ArrayList<String> remaining, int currentIndex,
            boolean currentRemoved) {
        this.remaining = remaining;
        this.currentIndex = currentIndex;
        this.currentRemoved = currentRemoved;
    }

    public static QueueRemovalPlan create(List<String> queue, int currentIndex,
            Set<String> removedIds) {
        ArrayList<String> remaining = new ArrayList<>();
        int retainedBeforeCurrent = 0;
        String current = currentIndex >= 0 && currentIndex < queue.size()
                ? queue.get(currentIndex) : "";
        for (int index = 0; index < queue.size(); index++) {
            String id = queue.get(index);
            if (!removedIds.contains(id)) {
                remaining.add(id);
                if (index < currentIndex) {
                    retainedBeforeCurrent++;
                }
            }
        }
        boolean currentRemoved = removedIds.contains(current);
        int nextIndex = remaining.isEmpty() ? 0
                : Math.min(retainedBeforeCurrent, remaining.size() - 1);
        return new QueueRemovalPlan(remaining, nextIndex, currentRemoved);
    }
}
