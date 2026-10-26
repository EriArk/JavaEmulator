/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.input;

/** Local UI must block controls without pausing a live network guest. */
public final class GameUiPolicy {
    private GameUiPolicy() { }
    public static boolean keepNetworkRunning(boolean network, boolean visible, boolean localPanel, boolean social) {
        return network && (social || visible && localPanel);
    }
}
