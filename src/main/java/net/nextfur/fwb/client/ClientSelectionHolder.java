package net.nextfur.fwb.client;

import net.nextfur.fwb.data.PlayerSelectionData;

public class ClientSelectionHolder {
    private static PlayerSelectionData currentSelection = PlayerSelectionData.DEFAULT;

    public static PlayerSelectionData get() {
        return currentSelection;
    }

    public static void set(PlayerSelectionData data) {
        currentSelection = data != null ? data : PlayerSelectionData.DEFAULT;
    }

    public static void clear() {
        currentSelection = PlayerSelectionData.DEFAULT;
    }
}
