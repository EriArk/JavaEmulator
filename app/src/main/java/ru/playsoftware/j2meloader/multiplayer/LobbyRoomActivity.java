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
    @Override protected void onCreate(Bundle saved){
        super.onCreate(saved);screenTitle("My room");
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        model=LobbyRoomModel.get(getApplication());
        share=headerButton("Share room link",android.R.drawable.ic_menu_share,()->{if(state!=null&&state.hosting){Intent intent=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,LobbyLink.create(state.host,state.room));startActivity(Intent.createChooser(intent,"Share room"));}});
        share.setVisibility(View.GONE);
        headerButton("Refresh",android.R.drawable.ic_popup_sync,()->model.refresh());
        headerButton("Chats",android.R.drawable.ic_dialog_email,()->startActivity(new Intent(this,FluxerChatsActivity.class)));
        headerButton("Account",android.R.drawable.ic_menu_myplaces,()->startActivity(new Intent(this,FluxerAccountActivity.class)));
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);screenContent.addView(root,new ViewGroup.LayoutParams(-1,-1));
        notice=label("",14);notice.setPadding(dp(12),dp(8),dp(12),dp(8));root.addView(notice);
        ScrollView scroll=new ScrollView(this);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);rows.setPadding(dp(12),0,dp(12),dp(12));scroll.addView(rows);
        proposed=LobbyLink.parse(getIntent().getDataString());
        if(saved!=null&&saved.getBoolean("linkConsumed"))proposed=null;
        model.state.observe(this,this::render);
    }
    @Override protected void onSaveInstanceState(Bundle out){out.putBoolean("linkConsumed",proposed==null);super.onSaveInstanceState(out);}
    private void render(LobbyRoomModel.State s){
        if(state!=null&&!state.account.equals(s.account)&&dialog!=null)dialog.dismiss();state=s;
        notice.setText(s.status);
        share.setVisibility(s.hosting?View.VISIBLE:View.GONE);
        String next=s.account+s.verified+s.room+s.snapshot+s.invitations+s.requests+s.friends+s.names+s.publication+s.available+(proposed==null?"":proposed.room);
        if(next.equals(signature))return;signature=next;
        Object focused=getCurrentFocus()==null?null:getCurrentFocus().getTag();rows.removeAllViews();
        if(!s.verified){button(rows,"Open Fluxer account","account",()->startActivity(new Intent(this,FluxerAccountActivity.class)));return;}
        if(s.room.isEmpty()){
            button(rows,"Create room","create",()->chooseAccess(true));
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
            JsonElement members=s.snapshot.get("members");if(members!=null&&members.isJsonArray()){
                heading("Players");int seat=0;for(JsonElement e:members.getAsJsonArray()){JsonObject m=e.getAsJsonObject();String id=text(m,"id");
                    String status=!bool(m,"connected")||!s.available?"Reconnecting":bool(m,"ready")?"Ready":bool(m,"prepared")?"Prepared":"Waiting for game";
                    String title=(++seat)+"  "+s.names.getOrDefault(id,id)+(id.equals(s.host)?"  (Host)":"")+"\n"+status;
                    if(s.hosting&&!id.equals(s.account))button(rows,title,"member:"+id,()->confirm("Remove player?",s.names.getOrDefault(id,id),()->model.action(s.account,r->r.remove(id))));
                    else {TextView player=label(title,16);player.setPadding(dp(6),dp(10),dp(6),dp(10));rows.addView(player);}
                }
            }
            for(Map.Entry<String,String> request:s.requests.entrySet()){heading(request.getValue()+" wants to join");LinearLayout actions=line();button(actions,"Approve","approve:"+request.getKey(),()->model.action(s.account,r->r.approve(request.getKey(),true)));button(actions,"Decline","reject:"+request.getKey(),()->model.action(s.account,r->r.approve(request.getKey(),false)));}
            if(s.joined){heading(s.snapshot.has("game")?"Game selected":"No online game selected");JsonObject mine=member(s.snapshot,s.account);
                Button ready=button(rows,bool(mine,"ready")?"Not ready":"Ready","ready",()->model.action(s.account,r->r.ready(!bool(mine,"ready"))));ready.setEnabled(s.available&&bool(mine,"prepared"));ready.setAlpha(ready.isEnabled()?1f:0.45f);}
            button(rows,s.hosting?"Close room":"Leave room","leave",()->confirm(s.hosting?"Close room?":"Leave room?",s.hosting?"All participants will be disconnected from this room.":"You can request to join again later.",()->model.action(s.account,LobbyRoom::leave)));
        }
        if(focused!=null){View restore=rows.findViewWithTag(focused);if(restore!=null&&restore.isEnabled())restore.requestFocus();}
    }
    private void chooseAccess(boolean create){
        if(state==null)return;String account=state.account;
        String[] names={"Invite only","Open","Host approval"},values={"private","public","approval"};
        GameMenuDialog menu=menu(create?"Create room":"Room access");
        for(int i=0;i<names.length;i++){String access=values[i];menu.action(names[i],0,()->{menu.dismiss();model.action(account,r->{if(create)r.create(access);else r.access(access);});});}
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
    private GameMenuDialog menu(String title){if(dialog!=null)dialog.dismiss();dialog=new GameMenuDialog(this,title);dialog.show();dialog.page(title);return dialog;}
    private void confirm(String title,String message,Runnable action){GameMenuDialog menu=menu(title);menu.message(message);menu.action("Confirm",android.R.drawable.ic_menu_send,()->{menu.dismiss();action.run();});menu.action("Cancel",android.R.drawable.ic_menu_close_clear_cancel,menu::dismiss);}
    private String accessLabel(String access){return access.equals("public")?"Open":access.equals("approval")?"Host approval":"Invite only";}
    private void heading(String title){TextView h=label(title,18);h.setPadding(0,dp(8),0,dp(6));rows.addView(h);}
    private TextView label(String text,int size){TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(AppPalette.color(this,R.color.text_primary));return v;}
    private LinearLayout line(){LinearLayout line=new LinearLayout(this);line.setOrientation(LinearLayout.HORIZONTAL);rows.addView(line,new LinearLayout.LayoutParams(-1,-2));return line;}
    private Button button(LinearLayout parent,String text,String tag,Runnable action){Button b=new Button(this);b.setText(text);b.setTag(tag);b.setAllCaps(false);b.setMinHeight(dp(48));b.setTextColor(AppPalette.color(this,R.color.text_primary));b.setBackgroundResource(R.drawable.bg_fluxer_action);b.setPadding(dp(10),dp(6),dp(10),dp(6));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(parent==rows?-1:0,-2,parent==rows?0:1);lp.bottomMargin=dp(6);lp.rightMargin=dp(4);parent.addView(b,lp);b.setOnClickListener(v->action.run());return b;}
    @Override protected void onStart(){super.onStart();model.resume();}
    @Override protected void onStop(){if(dialog!=null)dialog.dismiss();model.pause();super.onStop();}
}
