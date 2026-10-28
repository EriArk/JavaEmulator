/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import ru.playsoftware.j2meloader.input.GameMenuDialog;
import com.google.gson.*;
import java.util.*;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.settings.CompactSettingsActivity;
import ru.playsoftware.j2meloader.ui.AppPalette;
import static ru.playsoftware.j2meloader.multiplayer.LobbyRoom.*;

public final class LobbyRoomActivity extends CompactSettingsActivity {
    private LobbyRoomModel model;
    private LobbyRoomModel.State state;
    private LinearLayout rows;
    private TextView notice;
    private String signature="";
    private LobbyLink proposed;
    private GameMenuDialog dialog;
    private ImageButton share;
    private ScrollView scroll;
    private LinearLayout footer;
    private ProgressBar progress;
    private NetworkGameCatalog.Entry selectedGame;
    private String selectionStatus = "";
    @Override protected void onCreate(Bundle saved){
        super.onCreate(saved);screenTitle(getString(R.string.social_my_room));
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        model=LobbyRoomModel.get(getApplication());
        share=headerButton(getString(R.string.social_share_link),android.R.drawable.ic_menu_share,()->{if(state!=null&&state.hosting){Intent intent=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,LobbyLink.create(state.host,state.room));startActivity(Intent.createChooser(intent,getString(R.string.social_share_room)));}});
        share.setVisibility(View.GONE);
        headerButton(getString(R.string.social_refresh),android.R.drawable.ic_popup_sync,()->model.refresh());
        headerButton(getString(R.string.social_options),R.drawable.ic_library_ellipsis_vertical,()->{
            GameMenuDialog menu=menu(getString(R.string.social_title));
            menu.action(getString(R.string.social_browse_lobbies),android.R.drawable.ic_menu_search,()->{menu.dismiss();startActivity(new Intent(this,LobbyDirectoryActivity.class).putExtra(PlayTogether.EXTRA_GAME,selectedGame==null?null:selectedGame.directory.getPath()));});
            menu.action(getString(R.string.social_chats),R.drawable.ic_library_messages_square,()->{menu.dismiss();startActivity(new Intent(this,FluxerChatsActivity.class));});
            menu.action(getString(R.string.social_account_short),android.R.drawable.ic_menu_myplaces,()->{menu.dismiss();startActivity(new Intent(this,FluxerAccountActivity.class));});
        });
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);screenContent.addView(root,new ViewGroup.LayoutParams(-1,-1));
        notice=label("",14);notice.setPadding(dp(12),dp(8),dp(12),dp(8));root.addView(notice);
        root.setBackground(new ru.playsoftware.j2meloader.ui.ConsoleSurface(this,ru.playsoftware.j2meloader.ui.ConsoleSurface.Kind.CHASSIS));
        screenHeader.setBackground(new ru.playsoftware.j2meloader.ui.ConsoleSurface(this,ru.playsoftware.j2meloader.ui.ConsoleSurface.Kind.CHASSIS));
        notice.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(true);root.addView(progress,new LinearLayout.LayoutParams(-1,dp(3)));
        scroll=new ScrollView(this);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        scroll.setBackground(new ru.playsoftware.j2meloader.ui.ConsoleSurface(this,ru.playsoftware.j2meloader.ui.ConsoleSurface.Kind.SCREEN));
        rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);rows.setPadding(dp(12),0,dp(12),dp(12));scroll.addView(rows);
        footer=new LinearLayout(this);footer.setPadding(dp(12),dp(4),dp(8),dp(4));root.addView(footer,new LinearLayout.LayoutParams(-1,-2));
        proposed=LobbyLink.parse(getIntent().getDataString());
        if(saved!=null&&saved.getBoolean("linkConsumed"))proposed=null;
        String path=getIntent().getStringExtra(PlayTogether.EXTRA_GAME);
        if(path!=null){
            selectionStatus=getString(R.string.social_check_selected_game);
            PlayTogether.inspect(path,entry->{
                if(isFinishing()||isDestroyed())return;
                selectedGame=entry;selectionStatus=entry==null?getString(R.string.social_game_unavailable):"";
                signature="";if(state!=null)render(state);
            });
        }
        model.state.observe(this,this::render);
    }
    @Override protected void onSaveInstanceState(Bundle out){out.putBoolean("linkConsumed",proposed==null);super.onSaveInstanceState(out);}
    private void render(LobbyRoomModel.State s){
        if(state!=null&&!state.account.equals(s.account)&&dialog!=null)dialog.dismiss();state=s;
        notice.setText(s.status);
        progress.setVisibility(s.checkingGame||!s.room.isEmpty()&&!s.joined||s.snapshot.has("launch")&&!bool(object(s.snapshot,"launch"),"running")?View.VISIBLE:View.INVISIBLE);
        launchIfOffered();
        share.setVisibility(s.hosting?View.VISIBLE:View.GONE);
        String next=s.account+s.verified+s.room+s.snapshot+s.invitations+s.requests+s.friends+s.names+s.publication+s.available+s.contacts+s.gameTitle+s.selectedGamePath+s.checkingGame+s.attempt+s.gameSelection+s.joined+s.hosting+s.access+(proposed==null?"":proposed.room);
        if(next.equals(signature))return;signature=next;
        int oldScroll=scroll.getScrollY();
        Object focused=getCurrentFocus()==null?null:getCurrentFocus().getTag();rows.removeAllViews();footer.removeAllViews();
        rows.addView(label(getString(R.string.social_experimental),13));
        footer.setVisibility(s.joined?View.VISIBLE:View.GONE);
        if(!selectionStatus.isEmpty())rows.addView(label(selectionStatus,14));
        if(selectedGame!=null&&!selectedGame.directory.getAbsolutePath().equals(s.selectedGamePath)){
            heading(selectedGame.title);
            rows.addView(label(getString(R.string.social_bluetooth_experimental),14));
            if(s.joined&&s.gameSelection){
                Button use=button(rows,getString(R.string.social_use_game),"use-selected",()->model.chooseGame(s.account,selectedGame));
                use.setEnabled(s.available&&!s.checkingGame&&!s.snapshot.has("launch"));
            }
        }
        if(!s.verified){button(rows,getString(R.string.social_open_account),"account",()->startActivity(new Intent(this,FluxerAccountActivity.class)));return;}
        if(s.room.isEmpty()){
            Button create=button(rows,getString(R.string.social_create_room),"create",()->chooseAccess(true));
            create.setEnabled(selectionStatus.isEmpty());
            button(rows,getString(R.string.social_browse_lobbies),"browse",()->startActivity(new Intent(this,LobbyDirectoryActivity.class)
                    .putExtra(PlayTogether.EXTRA_GAME,selectedGame==null?null:selectedGame.directory.getPath())));
            if(proposed!=null){LobbyLink target=proposed;button(rows,getString(R.string.social_join_selected),"join-link",()->confirm(getString(R.string.social_join_room_title),getString(R.string.social_host_account,target.host),()->{proposed=null;model.action(s.account,r->r.join(target.host,target.room,""));}));}
            for(JsonObject invite:s.invitations){String peer=text(invite,"host"),key=text(invite,"key");heading(getString(R.string.social_invitation_from,s.names.getOrDefault(peer,peer)));
                LinearLayout actions=line();button(actions,getString(R.string.social_accept),"accept:"+key,()->model.action(s.account,r->r.answer(key,true)));button(actions,getString(R.string.social_decline),"decline:"+key,()->model.action(s.account,r->r.answer(key,false)));
            }
        }else{
            if(!s.hosting)heading(getString(R.string.social_room_with,s.names.getOrDefault(s.host,s.host)));
            if(s.hosting){
                LinearLayout actions=line();button(actions,getString(R.string.social_invite),"invite",this::invite);button(actions,accessLabel(s.access),"access",()->chooseAccess(false));
                TextView publication=label(s.publication,14);publication.setPadding(0,dp(6),0,dp(6));rows.addView(publication);
            }
            int gamePosition=rows.getChildCount();
            JsonElement members=s.snapshot.get("members");if(members!=null&&members.isJsonArray()){
                heading(getString(R.string.social_players));LobbySeats.add(rows,s,this::player,this::invite);
            }
            for(Map.Entry<String,String> request:s.requests.entrySet()){heading(getString(R.string.social_wants_join,request.getValue()));LinearLayout actions=line();button(actions,getString(R.string.social_approve),"approve:"+request.getKey(),()->model.action(s.account,r->r.approve(request.getKey(),true)));button(actions,getString(R.string.social_decline),"reject:"+request.getKey(),()->model.action(s.account,r->r.approve(request.getKey(),false)));}
            int gameStart=rows.getChildCount();
            if(s.joined){heading(s.snapshot.has("game")?(s.gameTitle.isEmpty()?getString(R.string.social_java_game):s.gameTitle):getString(R.string.social_choose_game));JsonObject mine=member(s.snapshot,s.account);
                rows.addView(label(s.gameSelection?getString(R.string.social_bluetooth_experimental):getString(R.string.social_launch_unavailable),14));
                if(s.gameSelection&&members!=null&&members.isJsonArray()&&members.getAsJsonArray().size()>2)
                    rows.addView(label(getString(R.string.social_two_players),14));
                boolean launching=s.snapshot.has("launch");
                if(s.gameSelection){Button select=button(rows,s.checkingGame?getString(R.string.social_check_game):s.hosting?getString(R.string.social_select_game):getString(R.string.social_select_matching_game),"select-game",this::chooseGame);select.setEnabled(s.available&&!launching&&!s.checkingGame&&(s.hosting||s.snapshot.has("game")));select.setAlpha(select.isEnabled()?1f:0.45f);}
                if(!launching){
                Button ready=button(footer,bool(mine,"ready")?getString(R.string.social_not_ready):getString(R.string.social_ready),"ready",()->model.action(s.account,r->r.ready(!bool(mine,"ready"))));ready.setEnabled(s.available&&!s.checkingGame&&bool(mine,"prepared"));ready.setAlpha(ready.isEnabled()?1f:.55f);
                if(s.hosting&&s.gameSelection){boolean allReady=s.snapshot.has("game")&&members!=null&&members.isJsonArray()&&members.getAsJsonArray().size()==2;
                    if(allReady)for(JsonElement e:members.getAsJsonArray()){JsonObject m=e.getAsJsonObject();allReady&=bool(m,"ready")&&bool(m,"prepared")&&bool(m,"connected");}
                    Button start=button(footer,getString(R.string.social_start_game),"start-game",()->model.action(s.account,LobbyRoom::start));start.setEnabled(allReady&&s.available);start.setAlpha(start.isEnabled()?1f:.55f);
                }
                }else{
                    boolean running=bool(object(s.snapshot,"launch"),"running");
                    TextView phase=label(running?getString(R.string.social_playing):getString(R.string.social_starting_game),16);phase.setGravity(Gravity.CENTER);phase.setMinHeight(dp(48));footer.addView(phase,new LinearLayout.LayoutParams(-1,-2));
                    String attempt=text(object(s.snapshot,"launch"),"attempt");
                    button(rows,running?getString(R.string.social_end_session):getString(R.string.social_cancel_launch),"cancel-launch",()->{
                        if(running)confirm(getString(R.string.social_end_session_title),getString(R.string.social_end_session_body),()->model.action(s.account,r->r.finish(attempt)));
                        else model.action(s.account,r->r.finish(attempt));
                    });
                }
            }
            LinearLayout gameSection=new LinearLayout(this);gameSection.setOrientation(LinearLayout.VERTICAL);
            while(rows.getChildCount()>gameStart){View child=rows.getChildAt(gameStart);rows.removeViewAt(gameStart);gameSection.addView(child);}
            rows.addView(gameSection,gamePosition);
            if(!s.joined)button(rows,getString(R.string.social_cancel_request),"leave",()->model.action(s.account,LobbyRoom::leave));
            else button(rows,s.hosting?getString(R.string.social_close_room):getString(R.string.social_leave_room),"leave",()->confirm(s.hosting?getString(R.string.social_close_room_title):getString(R.string.social_leave_room_title),s.hosting?getString(R.string.social_close_room_body):getString(R.string.social_leave_room_body),()->model.action(s.account,LobbyRoom::leave)));
        }
        JsonObject contacts=object(s.contacts,"peers");
        String contactError=text(s.contacts,"error");
        if(contacts.size()>0||!contactError.isEmpty()){
            heading(getString(R.string.social_temporary_contacts));
            if(!contactError.isEmpty())rows.addView(label(contactError,14));
            for(Map.Entry<String,JsonElement> entry:contacts.entrySet()){
                String peer=entry.getKey();JsonObject contact=entry.getValue().getAsJsonObject();
                TextView summary=label(s.names.getOrDefault(peer,peer)+"\n"+contactStatus(contact),16);
                summary.setPadding(0,dp(8),0,dp(6));rows.addView(summary);
                if(bool(contact,"canKeep")||bool(contact,"pendingKeep")){
                    Button keep=button(rows,bool(contact,"pendingKeep")?getString(R.string.social_keeping_friend):getString(R.string.social_keep_friend),"keep:"+peer,()->model.keep(s.account,peer));
                    keep.setEnabled(bool(contact,"canKeep"));
                }
            }
            button(rows,getString(R.string.social_open_friends),"contact-friends",()->startActivity(new Intent(this,FluxerChatsActivity.class).putExtra("friends",true)));
        }
        if(focused!=null){View restore=getWindow().getDecorView().findViewWithTag(focused);if(restore!=null&&restore.isEnabled())restore.requestFocus();}
        scroll.post(()->scroll.scrollTo(0,oldScroll));
    }
    private void player(String id){
        if(state==null)return;String account=state.account;
        GameMenuDialog menu=menu(state.names.getOrDefault(id,id));
        JsonObject m=member(state.snapshot,id);
        menu.message(id.equals(state.host)?getString(R.string.social_host):getString(R.string.social_player));
        menu.message(!bool(m,"connected")||!state.available?getString(R.string.social_reconnecting):bool(m,"ready")?getString(R.string.social_ready):bool(m,"prepared")?getString(R.string.social_prepared):getString(R.string.social_waiting_game));
        if(bool(object(object(state.contacts,"peers"),id),"canKeep"))
            menu.action(getString(R.string.social_keep_friend),android.R.drawable.ic_menu_add,()->{menu.dismiss();model.keep(account,id);});
        if(state.hosting&&!id.equals(account))menu.action(getString(R.string.social_remove_player),android.R.drawable.ic_menu_delete,
                ()->confirm(getString(R.string.social_remove_player_title),state.names.getOrDefault(id,id),()->model.action(account,r->r.remove(id))));
        menu.action(getString(R.string.social_close),android.R.drawable.ic_menu_close_clear_cancel,menu::dismiss);
    }
    private void chooseGame(){
        if(state==null)return;String account=state.account;GameMenuDialog menu=menu(getString(R.string.social_select_java_game));menu.message(getString(R.string.social_loading_games));
        model.games(entries->{
            if(isFinishing()||isDestroyed()||dialog!=menu||!menu.isShowing()||state==null||!account.equals(state.account))return;
            menu.page(getString(R.string.social_select_java_game));
            menu.message(getString(R.string.social_same_edition));
            if(entries.isEmpty())menu.message(getString(R.string.social_no_java_games));
            for(NetworkGameCatalog.Entry entry:entries)menu.action(entry.title,android.R.drawable.ic_media_play,()->{menu.dismiss();model.chooseGame(account,entry);});
        },reason->{if(dialog==menu&&menu.isShowing()){menu.page(getString(R.string.social_select_java_game));menu.message(reason);}});
    }
    private void launchIfOffered(){
        if(state==null||!state.launchOffered||!getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))return;
        String account=state.account,attempt=state.attempt;
        model.launch(account,attempt,intent->{
            if(isFinishing()||isDestroyed()||!getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)||state==null||!account.equals(state.account)||!attempt.equals(state.attempt)){
                model.action(account,r->r.finish(attempt));return;
            }
            try{startActivity(intent);}catch(RuntimeException failure){model.action(account,r->r.finish(attempt));}
        });
    }
    private String contactStatus(JsonObject contact){
        if(!text(contact,"error").isEmpty())return text(contact,"error");
        switch(text(contact,"phase")){
            case "friend":case "accepted":return getString(R.string.social_contact_connected);
            case "keep":return getString(R.string.social_contact_waiting);
            case "release":case "released":case "cleanup":case "deleting":return getString(R.string.social_contact_finishing);
            case "uncertain":case "error":case "blocked":return getString(R.string.social_contact_attention);
            default:return getString(R.string.social_connecting);
        }
    }
    private void chooseAccess(boolean create){
        if(state==null)return;String account=state.account;
        String[] names={getString(R.string.social_invite_only),getString(R.string.social_open),getString(R.string.social_host_approval)},values={"private","public","approval"};
        GameMenuDialog menu=menu(create?getString(R.string.social_create_room):getString(R.string.social_room_access));
        for(int i=0;i<names.length;i++){String access=values[i];menu.action(names[i],0,()->{menu.dismiss();if(create)model.createForGame(account,access,selectedGame);else model.action(account,r->r.access(access));});}
    }
    private void invite(){
        if(state==null)return;String account=state.account;List<String> ids=new ArrayList<>(state.friends.keySet()),labels=new ArrayList<>(state.friends.values());
        GameMenuDialog menu=menu(getString(R.string.social_invite_player));
        for(int i=0;i<ids.size();i++){String id=ids.get(i);menu.action(labels.get(i),0,()->{menu.dismiss();model.action(account,r->r.invite(id));});}
        menu.action(getString(R.string.social_enter_id),android.R.drawable.ic_input_add,()->{
            menu.page(getString(R.string.social_invite_player));EditText input=menu.textInput(getString(R.string.social_user_id),"");input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            menu.action(getString(R.string.social_invite),android.R.drawable.ic_menu_send,()->{String peer=input.getText().toString().trim();if(!LobbyDirectory.id(peer)||peer.equals(account)){input.setError(getString(R.string.social_other_id));return;}model.action(account,r->r.invite(peer));menu.dismiss();});
        });
    }
    private GameMenuDialog menu(String title){if(dialog!=null)dialog.dismiss();dialog=new GameMenuDialog(this,title);dialog.useEdgePanel();dialog.show();dialog.page(title);return dialog;}
    private void confirm(String title,String message,Runnable action){GameMenuDialog menu=menu(title);menu.confirmationPage(title);menu.message(message);menu.action(getString(R.string.social_confirm),android.R.drawable.ic_menu_send,()->{menu.dismiss();action.run();});menu.action(getString(R.string.social_cancel),android.R.drawable.ic_menu_close_clear_cancel,menu::dismiss);}
    private String accessLabel(String access){return access.equals("public")?getString(R.string.social_open):access.equals("approval")?getString(R.string.social_host_approval):getString(R.string.social_invite_only);}
    private void heading(String title){TextView h=label(title,18);h.setPadding(0,dp(8),0,dp(6));rows.addView(h);}
    private TextView label(String text,int size){TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(AppPalette.color(this,R.color.text_primary));return v;}
    private LinearLayout line(){LinearLayout line=new LinearLayout(this);line.setOrientation(LinearLayout.HORIZONTAL);rows.addView(line,new LinearLayout.LayoutParams(-1,-2));return line;}
    private Button button(LinearLayout parent,String text,String tag,Runnable action){Button b=new Button(this);b.setText(text);b.setTag(tag);b.setAllCaps(false);b.setMinHeight(dp(48));b.setTextSize(15);b.setTextColor(AppPalette.color(this,R.color.text_primary));b.setStateListAnimator(null);b.setDefaultFocusHighlightEnabled(false);b.setBackgroundTintList(null);b.setBackground(new ru.playsoftware.j2meloader.ui.ConsoleSurface(this,tag.equals("ready")||tag.equals("start-game")?ru.playsoftware.j2meloader.ui.ConsoleSurface.Kind.KEY:ru.playsoftware.j2meloader.ui.ConsoleSurface.Kind.ROW));b.setPadding(dp(12),dp(8),dp(12),dp(8));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(parent==rows?-1:0,-2,parent==rows?0:1);lp.bottomMargin=dp(6);lp.rightMargin=dp(4);parent.addView(b,lp);b.setOnClickListener(v->action.run());return b;}
    @Override protected void onStart(){super.onStart();model.resume();}
    @Override protected void onResume(){super.onResume();launchIfOffered();}
    @Override protected void onStop(){if(dialog!=null)dialog.dismiss();model.pause();super.onStop();}
}
