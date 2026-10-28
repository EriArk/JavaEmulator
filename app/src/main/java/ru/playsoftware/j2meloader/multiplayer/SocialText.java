// SPDX-License-Identifier: GPL-3.0-only
package ru.playsoftware.j2meloader.multiplayer;

import android.content.Context;
import ru.playsoftware.j2meloader.R;

/** UI-only rendering. Actors retain stable states and never translate protocol/user text. */
final class SocialText {
    private SocialText() { }
    static String content(Context context, FluxerChats.Message message) {
        switch(message.contentKind) {
            case SESSION: return context.getString(R.string.social_session_message);
            case ATTACHMENT: return context.getString(R.string.social_attachment);
            case STICKER: return context.getString(R.string.social_sticker);
            case EVENT: return context.getString(R.string.social_conversation_event);
            default: return message.text;
        }
    }
    static String channel(Context context, FluxerChats.Channel channel) {
        return channel.fallbackName?context.getString(channel.group?R.string.social_group_conversation:R.string.social_direct_conversation):channel.name;
    }
    static String chatTitle(Context context, FluxerChats.State state) {
        for(FluxerChats.Channel channel:state.channels) if(channel.id.equals(state.channel)) return channel(context,channel);
        return context.getString(R.string.social_chats);
    }
    static String friendsStatus(Context context, FluxerChats.State state) {
        return state.friendsStatusCode()==SocialNotice.EXTERNAL ? state.friendFailureDetail
                : notice(context,state.friendsStatusCode());
    }
    static String notice(Context context, SocialNotice notice) {
        switch (notice) {
            case JOIN_COMMUNITY: return context.getString(R.string.social_state_join_community);
            case NONE: case EXTERNAL: return "";
            case CHATS_SIGNIN: return context.getString(R.string.social_state_chats_signin);
            case CHECK_ACCOUNT: return context.getString(R.string.social_state_check_account);
            case VERIFY_FAILED: return context.getString(R.string.social_state_verify_failed);
            case UNLOCK_FAILED: return context.getString(R.string.social_state_unlock_failed);
            case CONTACTS_FAILED: return context.getString(R.string.social_state_contacts_failed);
            case CONVERSATIONS_FAILED: return context.getString(R.string.social_state_conversations_failed);
            case CONVERSATIONS_LIMIT: return context.getString(R.string.social_state_conversations_limit);
            case NO_CONVERSATIONS: return context.getString(R.string.social_state_no_conversations);
            case CURRENT: return context.getString(R.string.social_state_current);
            case CONTACTS_CURRENT: return context.getString(R.string.social_state_contacts_current);
            case CONTACTS_REFRESHING: return context.getString(R.string.social_state_contacts_refreshing);
            case CONTACT_ACCEPTED: return context.getString(R.string.social_state_contact_accepted);
            case CONTACTS_REFRESHED: return context.getString(R.string.social_state_contacts_refreshed);
            case CHECK_CONTACT: return context.getString(R.string.social_state_check_contact);
            case CONTACT_VERIFY_FAILED: return context.getString(R.string.social_state_contact_verify_failed);
            case CONTACT_CHANGED: return context.getString(R.string.social_state_contact_changed);
            case CONTACT_UPDATING: return context.getString(R.string.social_state_contact_updating);
            case ENTER_TAG: return context.getString(R.string.social_state_enter_tag);
            case CHECK_CONTACTS: return context.getString(R.string.social_state_check_contacts);
            case REQUEST_CHECK_FAILED: return context.getString(R.string.social_state_request_check_failed);
            case REQUEST_EXISTS: return context.getString(R.string.social_state_request_exists);
            case CONTACT_EXISTS: return context.getString(R.string.social_state_contact_exists);
            case SENDING_REQUEST: return context.getString(R.string.social_state_sending_request);
            case CONTACT_UNCERTAIN: return context.getString(R.string.social_state_contact_uncertain);
            case CONTACT_REJECTED: return context.getString(R.string.social_state_contact_rejected);
            case CONTACT_BUSY: return context.getString(R.string.social_state_contact_busy);
            case OPEN_CONVERSATION_FAILED: return context.getString(R.string.social_state_open_conversation_failed);
            case PENDING_CONVERSATIONS: return context.getString(R.string.social_state_pending_conversations);
            case CONVERSATION_UNAVAILABLE: return context.getString(R.string.social_state_conversation_unavailable);
            case MESSAGES_FAILED: return context.getString(R.string.social_state_messages_failed);
            case EARLIER_MESSAGES: return context.getString(R.string.social_state_earlier_messages);
            case RESOLVE_PENDING: return context.getString(R.string.social_state_resolve_pending);
            case DELIVERY_CHECK: return context.getString(R.string.social_state_delivery_check);
            case SENT: return context.getString(R.string.social_state_sent);
            case SENDING: return context.getString(R.string.social_state_sending);
            case NOT_SENT: return context.getString(R.string.social_state_not_sent);
            case DELIVERY_UNKNOWN: return context.getString(R.string.social_state_delivery_unknown);
            case EDITED: return context.getString(R.string.social_state_edited);
            case SEND_FAILED: return context.getString(R.string.social_state_send_failed);
            case SEND_UNCERTAIN: return context.getString(R.string.social_state_send_uncertain);
            case SESSION_EXPIRED: return context.getString(R.string.social_state_session_expired);
            case BUSY: return context.getString(R.string.social_state_busy);
            case DIRECTORY_SIGNIN: return context.getString(R.string.social_state_directory_signin);
            case DIRECTORY_CHECK: return context.getString(R.string.social_state_directory_check);
            case DIRECTORY_VERIFY_FAILED: return context.getString(R.string.social_state_directory_verify_failed);
            case DIRECTORY_REFRESHING: return context.getString(R.string.social_state_directory_refreshing);
            case DIRECTORY_EMPTY: return context.getString(R.string.social_state_directory_empty);
            case DIRECTORY_LARGE: return context.getString(R.string.social_state_directory_large);
            case MEMBERSHIP_SAVE_FAILED: return context.getString(R.string.social_state_membership_save_failed);
            case MEMBERSHIP_UNAVAILABLE: return context.getString(R.string.social_state_membership_unavailable);
            case DIRECTORY_FAILED: return context.getString(R.string.social_state_directory_failed);
            case DIRECTORY_INVALID: return context.getString(R.string.social_state_directory_invalid);
            case DIRECTORY_PAGE_INVALID: return context.getString(R.string.social_state_directory_page_invalid);
            case DIRECTORY_LIMIT: return context.getString(R.string.social_state_directory_limit);
            case DIRECTORY_EXPIRED: return context.getString(R.string.social_state_directory_expired);
            case DIRECTORY_BUSY: return context.getString(R.string.social_state_directory_busy);
            case COMMUNITY_CONNECTING: return context.getString(R.string.social_state_community_connecting);
            case COMMUNITY_INVITE_FAILED: return context.getString(R.string.social_state_community_invite_failed);
            case COMMUNITY_UNCERTAIN: return context.getString(R.string.social_state_community_uncertain);
            case COMMUNITY_FAILED: return context.getString(R.string.social_state_community_failed);
            default: throw new IllegalArgumentException("Unknown social notice: " + notice);
        }
    }

    static String auth(Context context, FluxerAuth.Notice notice) {
        switch (notice) {
            case SIGNED_OUT: return context.getString(R.string.social_auth_signed_out);
            case UNLOCK_FAILED: return context.getString(R.string.social_auth_unlock_failed);
            case CONNECTING: return context.getString(R.string.social_auth_connecting);
            case SERVICE_FAILED: return context.getString(R.string.social_auth_service_failed);
            case START_FAILED: return context.getString(R.string.social_auth_start_failed);
            case INVALID_EXPIRY: return context.getString(R.string.social_auth_invalid_expiry);
            case EXPIRED: return context.getString(R.string.social_auth_expired);
            case APPROVAL: return context.getString(R.string.social_auth_approval);
            case BUSY: return context.getString(R.string.social_auth_busy);
            case INVALID_SESSION: return context.getString(R.string.social_auth_invalid_session);
            case DECLINED: return context.getString(R.string.social_auth_declined);
            case WAITING: return context.getString(R.string.social_auth_waiting);
            case COMPLETE_FAILED: return context.getString(R.string.social_auth_complete_failed);
            case CHECKING: return context.getString(R.string.social_auth_checking);
            case SESSION_EXPIRED: return context.getString(R.string.social_auth_session_expired);
            case VERIFY_FAILED: return context.getString(R.string.social_auth_verify_failed);
            case SAVED: return context.getString(R.string.social_auth_saved);
            case SAVE_FAILED: return context.getString(R.string.social_auth_save_failed);
            case CANCELLED: return context.getString(R.string.social_auth_cancelled);
            case SIGNING_OUT: return context.getString(R.string.social_auth_signing_out);
            case SIGNOUT_FAILED: return context.getString(R.string.social_auth_signout_failed);
            case REMOVE_FAILED: return context.getString(R.string.social_auth_remove_failed);
            default: throw new IllegalArgumentException("Unknown account notice: " + notice);
        }
    }
    static String friend(Context context, int type) {
        return context.getString(type == 1 ? R.string.social_friend : type == 2 ? R.string.social_blocked
                : type == 3 ? R.string.social_incoming_request : R.string.social_request_sent);
    }
    static String roomTitle(Context context, LobbyDirectory.Room room) {
        return room.status.equals("choosing") ? context.getString(R.string.social_choosing_game) : room.title;
    }
    static String roomPhase(Context context, LobbyDirectory.Room room) {
        return context.getString(room.status.equals("playing") ? R.string.social_playing
                : room.status.equals("choosing") ? R.string.social_choosing
                : room.available == 0 ? R.string.social_room_full : R.string.social_waiting_players);
    }
}
