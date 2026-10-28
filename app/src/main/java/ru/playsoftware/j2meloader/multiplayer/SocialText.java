// SPDX-License-Identifier: GPL-3.0-only
package ru.playsoftware.j2meloader.multiplayer;

import android.content.Context;
import ru.playsoftware.j2meloader.R;

/** UI-only rendering. Actors retain stable states and never translate protocol/user text. */
final class SocialText {
    private SocialText() { }
    static String roomStatus(Context context,LobbyRoomModel.State state) {
        return state.statusCode==SocialNotice.EXTERNAL?state.status:notice(context,state.statusCode);
    }
    static String player(Context context,LobbyRoomModel.State state,String id) {
        return id.equals(state.account)?context.getString(R.string.social_you):state.names.getOrDefault(id,id);
    }
    static String publication(Context context,LobbyRoomModel.State state) {
        return state.publicationCode==SocialNotice.ROOM_DIRECTORY_REJECTED
                ?context.getString(R.string.social_room_rejected_code,state.publicationDetail)
                :state.publicationCode==SocialNotice.EXTERNAL?state.publication:notice(context,state.publicationCode);
    }
    static String contactError(Context context,com.google.gson.JsonObject record) {
        String raw=LobbyRoom.text(record,"error");
        if(raw.isEmpty())return raw;
        SocialNotice code;
        try {code=SocialNotice.valueOf(LobbyRoom.text(record,"errorCode"));}
        catch(IllegalArgumentException invalid){return raw;}
        return code==SocialNotice.NONE||code==SocialNotice.EXTERNAL?raw:notice(context,code);
    }
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
            case ROOM_PLAYER_UNREACHABLE: return context.getString(R.string.social_room_player_unreachable);
            case ROOM_CREATE_A_ROOM_OR_ACCEPT_AN_INVITATION: return context.getString(R.string.social_room_create_a_room_or_accept_an_invitation);
            case ROOM_ROOM_CREATED_INVITE_SOMEONE_TO_JOIN: return context.getString(R.string.social_room_room_created_invite_someone_to_join);
            case ROOM_INVITATION_PENDING_WAITING_FOR_AN_ANSWER: return context.getString(R.string.social_room_invitation_pending_waiting_for_an_answer);
            case ROOM_WAITING_FOR_THE_HOST: return context.getString(R.string.social_room_waiting_for_the_host);
            case ROOM_PLAYER_JOINED_THE_ROOM: return context.getString(R.string.social_room_player_joined_the_room);
            case ROOM_YOU_LEFT_THE_ROOM: return context.getString(R.string.social_room_you_left_the_room);
            case ROOM_CONNECTING_GAME_PROCESSES: return context.getString(R.string.social_room_connecting_game_processes);
            case ROOM_GAME_ENDED_PREPARE_AND_PRESS_READY_TO: return context.getString(R.string.social_room_game_ended_prepare_and_press_ready_to);
            case ROOM_RETURNING_TO_ROOM_WAITING_FOR_THE_HOST: return context.getString(R.string.social_room_returning_to_room_waiting_for_the_host);
            case ROOM_THIS_ROOM_IS_NO_LONGER_AVAILABLE_REFRESH: return context.getString(R.string.social_room_this_room_is_no_longer_available_refresh);
            case ROOM_WAITING_FOR_HOST_APPROVAL: return context.getString(R.string.social_room_waiting_for_host_approval);
            case ROOM_THE_ROOM_IS_CLOSED_OR_YOUR_REQUEST: return context.getString(R.string.social_room_the_room_is_closed_or_your_request);
            case ROOM_CONNECTED_TO_ROOM: return context.getString(R.string.social_room_connected_to_room);
            case ROOM_GAME_IN_PROGRESS: return context.getString(R.string.social_room_game_in_progress);
            case ROOM_GAME_LAUNCH_TIMED_OUT_PREPARE_AND_PRESS: return context.getString(R.string.social_room_game_launch_timed_out_prepare_and_press);
            case ROOM_THE_HOST_DID_NOT_ANSWER_TRY_JOINING: return context.getString(R.string.social_room_the_host_did_not_answer_try_joining);
            case ROOM_HOST_UNAVAILABLE_WAITING_TO_RECONNECT: return context.getString(R.string.social_room_host_unavailable_waiting_to_reconnect);
            case ROOM_INVITE_ONLY_ROOM: return context.getString(R.string.social_room_invite_only_room);
            case ROOM_PUBLICATION_HISTORY_COULD_NOT_BE_READ_PUBLIC: return context.getString(R.string.social_room_publication_history_could_not_be_read_public);
            case ROOM_REMOVING_PUBLIC_LISTING: return context.getString(R.string.social_room_removing_public_listing);
            case ROOM_DIRECTORY_CONFIRMATION_MISSING_REFRESH_OR_RETRY_PUBLICATION: return context.getString(R.string.social_room_directory_confirmation_missing_refresh_or_retry_publication);
            case ROOM_COULD_NOT_SAVE_PUBLICATION_INTENT_NOTHING_WAS: return context.getString(R.string.social_room_could_not_save_publication_intent_nothing_was);
            case ROOM_WAITING_FOR_DIRECTORY_CONFIRMATION: return context.getString(R.string.social_room_waiting_for_directory_confirmation);
            case ROOM_WAITING_FOR_LISTING_REMOVAL_CONFIRMATION: return context.getString(R.string.social_room_waiting_for_listing_removal_confirmation);
            case ROOM_PUBLIC_LISTING_ACCEPTED_BY_THE_DIRECTORY: return context.getString(R.string.social_room_public_listing_accepted_by_the_directory);
            case ROOM_LISTING_WITHDRAWAL_ACCEPTED: return context.getString(R.string.social_room_listing_withdrawal_accepted);
            case ROOM_PREVIOUS_LISTING_CLOSED: return context.getString(R.string.social_room_previous_listing_closed);
            case ROOM_ANOTHER_ABYSSME_PUBLIC_ROOM_IS_ACTIVE_CLOSE: return context.getString(R.string.social_room_another_abyssme_public_room_is_active_close);
            case ROOM_UPDATING_PUBLIC_LISTING: return context.getString(R.string.social_room_updating_public_listing);
            case ROOM_DIRECTORY_REPLY_OR_SAVED_PUBLICATION_STATE_COULD: return context.getString(R.string.social_room_directory_reply_or_saved_publication_state_could);
            case ROOM_DIRECTORY_REJECTED: return context.getString(R.string.social_room_directory_rejected);
            case ROOM_DIRECTORY_INVALID_REPLY: return context.getString(R.string.social_room_directory_invalid_reply);
            case ROOM_LOBBY_MESSAGE_COULD_NOT_BE_QUEUED: return context.getString(R.string.social_room_lobby_message_could_not_be_queued);
            case ROOM_COULD_NOT_CHECK_CONTACTS_REFRESH_TO_RECONNECT: return context.getString(R.string.social_room_could_not_check_contacts_refresh_to_reconnect);
            case ROOM_INVALID_CONTACTS_RESPONSE: return context.getString(R.string.social_room_invalid_contacts_response);
            case ROOM_COULD_NOT_REFRESH_INVITATIONS: return context.getString(R.string.social_room_could_not_refresh_invitations);
            case ROOM_COULD_NOT_REFRESH_LOBBY_MESSAGES: return context.getString(R.string.social_room_could_not_refresh_lobby_messages);
            case ROOM_PLAYER_BLOCKED_OR_LOBBY_MESSAGE_EXPIRED: return context.getString(R.string.social_room_player_blocked_or_lobby_message_expired);
            case ROOM_VERIFY_YOUR_EMAIL_IN_FLUXER_BEFORE_MESSAGING: return context.getString(R.string.social_room_verify_your_email_in_fluxer_before_messaging);
            case ROOM_FLUXER_DOES_NOT_ALLOW_MESSAGES_TO_THIS: return context.getString(R.string.social_room_fluxer_does_not_allow_messages_to_this);
            case ROOM_FLUXER_DENIED_THIS_MESSAGE_CHECK_ACCOUNT_PERMISSIONS: return context.getString(R.string.social_room_fluxer_denied_this_message_check_account_permissions);
            case ROOM_FLUXER_IS_LIMITING_REQUESTS_WAIT_BEFORE_TRYING: return context.getString(R.string.social_room_fluxer_is_limiting_requests_wait_before_trying);
            case ROOM_MESSAGE_DELIVERY_WAS_NOT_CONFIRMED_REFRESH_BEFORE: return context.getString(R.string.social_room_message_delivery_was_not_confirmed_refresh_before);
            case ROOM_SIGN_IN_TO_FLUXER_TO_USE_ROOMS: return context.getString(R.string.social_room_sign_in_to_fluxer_to_use_rooms);
            case ROOM_SIGN_IN_EXPIRED_OPEN_ACCOUNT_TO_RECONNECT: return context.getString(R.string.social_room_sign_in_expired_open_account_to_reconnect);
            case ROOM_COULD_NOT_UNLOCK_SIGN_IN_OPEN_ACCOUNT: return context.getString(R.string.social_room_could_not_unlock_sign_in_open_account);
            case ROOM_COULD_NOT_VERIFY_SIGN_IN_REFRESH_OR: return context.getString(R.string.social_room_could_not_verify_sign_in_refresh_or);
            case ROOM_THIS_GAME_NEEDS_TWO_PLAYERS_REMOVE_EXTRA: return context.getString(R.string.social_room_this_game_needs_two_players_remove_extra);
            case ROOM_CHECKING_INSTALLED_GAME: return context.getString(R.string.social_room_checking_installed_game);
            case ROOM_ROOM_CHANGED_SELECT_THE_GAME_AGAIN: return context.getString(R.string.social_room_room_changed_select_the_game_again);
            case ROOM_THIS_IS_A_DIFFERENT_GAME_EDITION_SELECT: return context.getString(R.string.social_room_this_is_a_different_game_edition_select);
            case ROOM_COULD_NOT_START_THIS_GAME_PREPARE_IT: return context.getString(R.string.social_room_could_not_start_this_game_prepare_it);
            case ROOM_GAME_CONNECTION_ENDED_PREPARE_AND_PRESS_READY: return context.getString(R.string.social_room_game_connection_ended_prepare_and_press_ready);
            case ROOM_TEMPORARY_CONTACT_HISTORY_NEEDS_RECOVERY_AUTOMATIC_CHANGES: return context.getString(R.string.social_room_temporary_contact_history_needs_recovery_automatic_changes);
            case ROOM_COULDN_T_SAVE_TEMPORARY_CONTACT_HISTORY_AUTOMATIC: return context.getString(R.string.social_room_couldn_t_save_temporary_contact_history_automatic);
            case ROOM_CHECK_THIS_FRIENDSHIP_AFTER_THE_INTERRUPTED_REQUEST: return context.getString(R.string.social_room_check_this_friendship_after_the_interrupted_request);
            case ROOM_FRIENDSHIP_PERMISSIONS_NEED_ATTENTION: return context.getString(R.string.social_room_friendship_permissions_need_attention);
            case ROOM_AN_EXISTING_REQUEST_OR_BLOCK_NEEDS_YOUR: return context.getString(R.string.social_room_an_existing_request_or_block_needs_your);
            case ROOM_FRIENDSHIP_CHANGED_IT_WAS_KEPT_REVIEW_IT: return context.getString(R.string.social_room_friendship_changed_it_was_kept_review_it);
            case ROOM_COULDN_T_CONFIRM_TEMPORARY_FRIENDSHIP_REVIEW_PERMISSIONS: return context.getString(R.string.social_room_couldn_t_confirm_temporary_friendship_review_permissions);
            case ROOM_COULDN_T_READ_FRIENDSHIP_STATE_AUTOMATIC_CHANGES: return context.getString(R.string.social_room_couldn_t_read_friendship_state_automatic_changes);
            case ROOM_AMBIGUOUS_FRIENDSHIP_STATE_AUTOMATIC_CHANGES_STOPPED: return context.getString(R.string.social_room_ambiguous_friendship_state_automatic_changes_stopped);
            case ROOM_FRIENDSHIP_CHANGED_AUTOMATIC_REQUEST_STOPPED: return context.getString(R.string.social_room_friendship_changed_automatic_request_stopped);
            case ROOM_FRIENDSHIP_CHANGED_AUTOMATIC_ACCEPTANCE_STOPPED: return context.getString(R.string.social_room_friendship_changed_automatic_acceptance_stopped);
            case ROOM_COULDN_T_VERIFY_THE_TEMPORARY_FRIENDSHIP: return context.getString(R.string.social_room_couldn_t_verify_the_temporary_friendship);
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
