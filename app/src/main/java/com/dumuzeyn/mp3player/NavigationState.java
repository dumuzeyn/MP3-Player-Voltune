package com.dumuzeyn.mp3player;

/** Current library destination and transient tab transition state. */
final class NavigationState implements TabTransitionCoordinator.Navigation {
    int tabIndex;
    int preferredTabDirection;
    boolean tabAnimating;
    String search = "";
    boolean fullPlayerOpening;
    int songRenderGeneration;
    boolean renderingTabPreview;

    @Override
    public int selectedTab() {
        return tabIndex;
    }

    @Override
    public void setSelectedTab(int value) {
        tabIndex = Math.max(0, value);
    }

    @Override
    public String searchQuery() {
        return search;
    }

    @Override
    public void setSearchQuery(String value) {
        search = value == null ? "" : value;
    }

    @Override
    public void setPreferredDirection(int value) {
        preferredTabDirection = value;
    }

    @Override
    public void setTransitionRunning(boolean value) {
        tabAnimating = value;
    }
}
