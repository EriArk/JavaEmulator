// SPDX-License-Identifier: GPL-3.0-only
package ru.playsoftware.j2meloader.multiplayer;

import java.io.IOException;

/** App-owned failure identity; raw filesystem and converter diagnostics stay in the log. */
final class NetworkGameException extends IOException {
    final SocialNotice notice;

    NetworkGameException(SocialNotice notice) {
        super(notice.english);
        this.notice = notice;
    }

    static SocialNotice notice(Throwable failure) {
        return failure instanceof NetworkGameException
                ? ((NetworkGameException) failure).notice : SocialNotice.GAME_PREPARE_FAILED;
    }
}
