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
        super.onCreate(saved);screenTitle("My room");
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        model=LobbyRoomModel.get(getApplication());
        share=headerButton("Share room link",android.R.drawable.ic_menu_share,()->{if(state!=null&&state.hosting){Intent intent=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,LobbyLink.create(state.host,state.room));startActivity(Intent.createChooser(intent,"Share room"));}});
        share.setVisibility(View.GONE);
        headerButton("Refresh",android.R.drawable.ic_popup_sync,()->model.refresh());
        headerButton("Social options",R.drawable.ic_library_ellipsis_vertical,()->{
            GameMenuDialog menu=menu("Social");
            menu.action("Browse lobbies",android.R.drawable.ic_menu_search,()->{menu.dismiss();startActivity(new Intent(this,LobbyDirectoryActivity.class).putExtra(PlayTogether.EXTRA_GAME,selectedGame==null?null:selectedGame.directory.getPath()));});
            menu.action("Chats",R.drawable.ic_library_messages_square,()->{menu.dismiss();startActivity(new Intent(this,FluxerChatsActivity.class));});
            menu.action("Account",android.R.drawable.ic_menu_myplaces,()->{menu.dismiss();startActivity(new Intent(this,FluxerAccountActivity.class));});
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
            selectionStatus="Checking selected game...";
            PlayTogether.inspect(path,entry->{
                if(isFinishing()||isDestroyed())return;
                selectedGame=entry;selectionStatus=entry==null?"This game is not available for experimental Bluetooth play.":"";
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
        rows.addView(label("Early experimental - low compatibility",13));
        footer.setVisibility(s.joined?View.VISIBLE:View.GONE);
        if(!selectionStatus.isEmpty())rows.addView(label(selectionStatus,14));
        if(selectedGame!=null&&!selectedGame.directory.getAbsolutePath().equals(s.selectedGamePath)){
            heading(selectedGame.title);
            rows.addView(label("Java Bluetooth / 2 players / Experimental",14));
            if(s.joined&&s.gameSelection){
                Button use=button(rows,"Use selected game","use-selected",()->model.chooseGame(s.account,selectedGame));
                use.setEnabled(s.available&&!s.checkingGame&&!s.snapshot.has("launch"));
            }
        }
        if(!s.verified){button(rows,"Open Fluxer account","account",()->startActivity(new Intent(this,FluxerAccountActivity.class)));return;}
        if(s.room.isEmpty()){
            Button create=button(rows,"Create room","create",()->chooseAccess(true));
            create.setEnabled(selectionStatus.isEmpty());
            button(rows,"Browse lobbies","browse",()->startActivity(new Intent(this,LobbyDirectoryActivity.class)
                    .putExtra(PlayTogether.EXTRA_GAME,selectedGame==null?null:selectedGame.directory.getPath())));
            if(proposed!=null){LobbyLink target=proposed;button(rows,"Join selected room","join-link",()->confirm("Join room?","Host account: "+target.host,()->{proposed=null;model.action(s.account,r->r.join(target.host,target.room,""));}));}
            for(JsonObject invite:s.invitations){String peer=text(invite,"host"),key=text(invite,"key");heading("Invitation from "+s.names.getOrDefault(peer,peer));
                LinearLayout actions=line();button(actions,"Accept","accept:"+key,()->model.action(s.account,r->r.answer(key,true)));button(actions,"Decline","decline:"+key,()->model.action(s.account,r->r.answer(key,false)));
            }
        }else{
            if(!s.hosting)heading("Room with "+s.names.getOrDefault(s.host,s.host));
            if(s.hosting){
                LinearLayout actions=line();button(actions,"Invite","invite",this::invite);button(actions,accessLabel(s.access),"access",()->chooseAccess(false));
                TextView publication=label(s.publication,14);publication.setPadding(0,dp(6),0,dp(6));rows.addView(publication);
            }
            int gamePosition=rows.getChildCount();
            JsonElement members=s.snapshot.get("members");if(members!=null&&members.isJsonArray()){
                heading("Players");LobbySeats.add(rows,s,this::player,this::invite);
            }
            for(Map.Entry<String,String> request:s.requests.entrySet()){heading(request.getValue()+" wants to join");LinearLayout actions=line();button(actions,"Approve","approve:"+request.getKey(),()->model.action(s.account,r->r.approve(request.getKey(),true)));button(actions,"Decline","reject:"+request.getKey(),()->model.action(s.account,r->r.approve(request.getKey(),false)));}
            int gameStart=rows.getChildCount();
            if(s.joined){heading(s.snapshot.has("game")?(s.gameTitle.isEmpty()?"Java Bluetooth game":s.gameTitle):"Choose a game");JsonObject mine=member(s.snapshot,s.account);
                rows.addView(label(s.gameSelection?"Java Bluetooth / 2 players / Experimental":"Online game launch is unavailable in this build.",14));
                if(s.gameSelection&&members!=null&&members.isJsonArray()&&members.getAsJsonArray().size()>2)
                    rows.addView(label("Bluetooth play needs exactly two players in the room.",14));
                boolean launching=s.snapshot.has("launch");
                if(s.gameSelection){Button select=button(rows,s.checkingGame?"Checking game...":s.hosting?"Select game":"Select matching game","select-game",this::chooseGame);select.setEnabled(s.available&&!launching&&!s.checkingGame&&(s.hosting||s.snapshot.has("game")));select.setAlpha(select.isEnabled()?1f:0.45f);}
                if(!launching){
                Button ready=button(footer,bool(mine,"ready")?"Not ready":"Ready","ready",()->model.action(s.account,r->r.ready(!bool(mine,"ready"))));ready.setEnabled(s.available&&!s.checkingGame&&bool(mine,"prepared"));ready.setAlpha(ready.isEnabled()?1f:.55f);
                if(s.hosting&&s.gameSelection){boolean allReady=s.snapshot.has("game")&&members!=null&&members.isJsonArray()&&members.getAsJsonArray().size()==2;
                    if(allReady)for(JsonElement e:members.getAsJsonArray()){JsonObject m=e.getAsJsonObject();allReady&=bool(m,"ready")&&bool(m,"prepared")&&bool(m,"connected");}
                    Button start=button(footer,"Start game","start-game",()->model.action(s.account,LobbyRoom::start));start.setEnabled(allReady&&s.available);start.setAlpha(start.isEnabled()?1f:.55f);
                }
                }else{
                    boolean running=bool(object(s.snapshot,"launch"),"running");
                    TextView phase=label(running?"Game in progress":"Starting game...",16);phase.setGravity(Gravity.CENTER);phase.setMinHeight(dp(48));footer.addView(phase,new LinearLayout.LayoutParams(-1,-2));
                    String attempt=text(object(s.snapshot,"launch"),"attempt");
                    button(rows,running?"End game session":"Cancel game launch","cancel-launch",()->{
                        if(running)confirm("End game session?","The network session will end for both players.",()->model.action(s.account,r->r.finish(attempt)));
                        else model.action(s.account,r->r.finish(attempt));
                    });
                }
            }
            LinearLayout gameSection=new LinearLayout(this);gameSection.setOrientation(LinearLayout.VERTICAL);
            while(rows.getChildCount()>gameStart){View child=rows.getChildAt(gameStart);rows.removeViewAt(gameStart);gameSection.addView(child);}
            rows.addView(gameSection,gamePosition);
            if(!s.joined)button(rows,"Cancel request","leave",()->model.action(s.account,LobbyRoom::leave));
            else button(rows,s.hosting?"Close room":"Leave room","leave",()->confirm(s.hosting?"Close room?":"Leave room?",s.hosting?"All participants will be disconnected from this room.":"You can request to join again later.",()->model.action(s.account,LobbyRoom::leave)));
        }
        JsonObject contacts=object(s.contacts,"peers");
        String contactError=text(s.contacts,"error");
        if(contacts.size()>0||!contactError.isEmpty()){
            heading("Temporary contacts");
            if(!contactError.isEmpty())rows.addView(label(contactError,14));
            for(Map.Entry<String,JsonElement> entry:contacts.entrySet()){
                String peer=entry.getKey();JsonObject contact=entry.getValue().getAsJsonObject();
                TextView summary=label(s.names.getOrDefault(peer,peer)+"\n"+contactStatus(contact),16);
                summary.setPadding(0,dp(8),0,dp(6));rows.addView(summary);
                if(bool(contact,"canKeep")||bool(contact,"pendingKeep")){
                    Button keep=button(rows,bool(contact,"pendingKeep")?"Keeping friendship...":"Keep as friend","keep:"+peer,()->model.keep(s.account,peer));
                    keep.setEnabled(bool(contact,"canKeep"));
                }
            }
            button(rows,"Open Friends","contact-friends",()->startActivity(new Intent(this,FluxerChatsActivity.class).putExtra("friends",true)));
        }
        if(focused!=null){View restore=getWindow().getDecorView().findViewWithTag(focused);if(restore!=null&&restore.isEnabled())restore.requestFocus();}
        scroll.post(()->scroll.scrollTo(0,oldScroll));
    }
    private void player(String id){
        if(state==null)return;String account=state.account;
        GameMenuDialog menu=menu(state.names.getOrDefault(id,id));
        JsonObject m=member(state.snapshot,id);
        menu.message(id.equals(state.host)?"Host":"Player");
        menu.message(!bool(m,"connected")||!state.available?"Reconnecting":bool(m,"ready")?"Ready":bool(m,"prepared")?"Prepared":"Waiting for game");
        if(bool(object(object(state.contacts,"peers"),id),"canKeep"))
            menu.action("Keep as friend",android.R.drawable.ic_menu_add,()->{menu.dismiss();model.keep(account,id);});
        if(state.hosting&&!id.equals(account))menu.action("Remove player",android.R.drawable.ic_menu_delete,
                ()->confirm("Remove player?",state.names.getOrDefault(id,id),()->model.action(account,r->r.remove(id))));
        menu.action("Close",android.R.drawable.ic_menu_close_clear_cancel,menu::dismiss);
    }
    private void chooseGame(){
        if(state==null)return;String account=state.account;GameMenuDialog menu=menu("Select Java game");menu.message("Loading installed games...");
        model.games(entries->{
            if(isFinishing()||isDestroyed()||dialog!=menu||!menu.isShowing()||state==null||!account.equals(state.account))return;
            menu.page("Select Java game");
            menu.message("Experimental Bluetooth mode. Both players need the same game edition and its built-in multiplayer mode.");
            if(entries.isEmpty())menu.message("No single-entry Java games found. Install and open a game from Library first.");
            for(NetworkGameCatalog.Entry entry:entries)menu.action(entry.title,android.R.drawable.ic_media_play,()->{menu.dismiss();model.chooseGame(account,entry);});
        },reason->{if(dialog==menu&&menu.isShowing()){menu.page("Select Java game");menu.message(reason);}});
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
            case "friend":case "accepted":return "Connected for this room";
            case "keep":return "Waiting for confirmation";
            case "release":case "released":case "cleanup":case "deleting":return "Finishing temporary connection";
            case "uncertain":case "error":case "blocked":return "Needs attention in Friends";
            default:return "Connecting";
        }
    }
    private void chooseAccess(boolean create){
        if(state==null)return;String account=state.account;
        String[] names={"Invite only","Open","Host approval"},values={"private","public","approval"};
        GameMenuDialog menu=menu(create?"Create room":"Room access");
        for(int i=0;i<names.length;i++){String access=values[i];menu.action(names[i],0,()->{menu.dismiss();if(create)model.createForGame(account,access,selectedGame);else model.action(account,r->r.access(access));});}
    }
    private void invite(){
        if(state==null)return;String account=state.account;List<String> ids=new ArrayList<>(state.friends.keySet()),labels=new ArrayList<>(state.friends.values());
        GameMenuDialog menu=menu("Invite player");
        for(int i=0;i<ids.size();i++){String id=ids.get(i);menu.action(labels.get(i),0,()->{menu.dismiss();model.action(account,r->r.invite(id));});}
        menu.action("Enter Fluxer user ID",android.R.drawable.ic_input_add,()->{
            menu.page("Invite player");EditText input=menu.textInput("Fluxer user ID","");input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            menu.action("Invite",android.R.drawable.ic_menu_send,()->{String peer=input.getText().toString().trim();if(!LobbyDirectory.id(peer)||peer.equals(account)){input.setError("Enter another player's user ID");return;}model.action(account,r->r.invite(peer));menu.dismiss();});
        });
    }
    private GameMenuDialog menu(String title){if(dialog!=null)dialog.dismiss();dialog=new GameMenuDialog(this,title);dialog.useEdgePanel();dialog.show();dialog.page(title);return dialog;}
    private void confirm(String title,String message,Runnable action){GameMenuDialog menu=menu(title);menu.message(message);menu.action("Confirm",android.R.drawable.ic_menu_send,()->{menu.dismiss();action.run();});menu.action("Cancel",android.R.drawable.ic_menu_close_clear_cancel,menu::dismiss);}
    private String accessLabel(String access){return access.equals("public")?"Open":access.equals("approval")?"Host approval":"Invite only";}
    private void heading(String title){TextView h=label(title,18);h.setPadding(0,dp(8),0,dp(6));rows.addView(h);}
    private TextView label(String text,int size){TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(AppPalette.color(this,R.color.text_primary));return v;}
    private LinearLayout line(){LinearLayout line=new LinearLayout(this);line.setOrientation(LinearLayout.HORIZONTAL);rows.addView(line,new LinearLayout.LayoutParams(-1,-2));return line;}
    private Button button(LinearLayout parent,String text,String tag,Runnable action){Button b=new Button(this);b.setText(text);b.setTag(tag);b.setAllCaps(false);b.setMinHeight(dp(48));b.setTextSize(15);b.setTextColor(AppPalette.color(this,R.color.text_primary));b.setStateListAnimator(null);b.setDefaultFocusHighlightEnabled(false);b.setBackgroundTintList(null);b.setBackground(new ru.playsoftware.j2meloader.ui.ConsoleSurface(this,tag.equals("ready")||tag.equals("start-game")?ru.playsoftware.j2meloader.ui.ConsoleSurface.Kind.KEY:ru.playsoftware.j2meloader.ui.ConsoleSurface.Kind.ROW));b.setPadding(dp(12),dp(8),dp(12),dp(8));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(parent==rows?-1:0,-2,parent==rows?0:1);lp.bottomMargin=dp(6);lp.rightMargin=dp(4);parent.addView(b,lp);b.setOnClickListener(v->action.run());return b;}
    @Override protected void onStart(){super.onStart();model.resume();}
    @Override protected void onResume(){super.onResume();launchIfOffered();}
    @Override protected void onStop(){if(dialog!=null)dialog.dismiss();model.pause();super.onStop();}
}
