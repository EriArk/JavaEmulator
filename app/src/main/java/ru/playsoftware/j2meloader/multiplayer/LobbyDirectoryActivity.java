/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.*;
import androidx.recyclerview.widget.ListAdapter;
import java.util.*;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.settings.CompactSettingsActivity;
import ru.playsoftware.j2meloader.ui.AppPalette;

/** Advertisement browsing only; opening a row still requires host admission and explicit Ready. */
public final class LobbyDirectoryActivity extends CompactSettingsActivity {
    private LobbyDirectoryModel model;
    private LobbyDirectory.State state;
    private TextView status,empty;
    private Button account,community;
    private EditText search;
    private CheckBox free;
    private ImageButton refresh;
    private ProgressBar progress;
    private LinearLayout filters;
    private final Rooms adapter=new Rooms();
    private AlertDialog detail;
    private String selected="";
    private long selectedRevision;
    private String gamePath, gameHash = "";
    private boolean checkingGame;

    @Override protected void onCreate(Bundle saved){
        super.onCreate(saved);screenTitle("Lobbies");
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                |android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        refresh=headerButton("Refresh",android.R.drawable.ic_popup_sync,()->model.refresh());refresh.setId(R.id.lobby_refresh);
        headerButton("My room",android.R.drawable.ic_menu_share,()->startActivity(new Intent(this,LobbyRoomActivity.class).putExtra(PlayTogether.EXTRA_GAME,gamePath)));
        headerButton("Chats",android.R.drawable.ic_dialog_email,()->startActivity(new Intent(this,FluxerChatsActivity.class)));
        headerButton("Account",android.R.drawable.ic_menu_myplaces,()->startActivity(new Intent(this,FluxerAccountActivity.class)));
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);screenContent.addView(root,new ViewGroup.LayoutParams(-1,-1));
        status=label(14,R.color.text_secondary);status.setId(R.id.lobby_status);status.setPadding(dp(12),dp(8),dp(12),dp(8));
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);root.addView(status);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(true);root.addView(progress,new LinearLayout.LayoutParams(-1,dp(3)));
        account=action(root,"Open Fluxer account",R.id.lobby_account,()->startActivity(new Intent(this,FluxerAccountActivity.class)));
        community=action(root,"Retry community access",R.id.lobby_community,()->{if(state!=null)model.retryCommunity(state.account);});
        filters=new LinearLayout(this);filters.setGravity(Gravity.CENTER_VERTICAL);root.addView(filters);
        search=new EditText(this);search.setId(R.id.lobby_search);search.setSingleLine(true);search.setTextSize(16);search.setHint("Search lobbies");
        search.setTextColor(color(R.color.text_primary));search.setHintTextColor(color(R.color.text_secondary));search.setSaveEnabled(false);
        search.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);search.setPadding(dp(12),0,dp(8),0);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH|EditorInfo.IME_FLAG_NO_EXTRACT_UI|EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        filters.addView(search,new LinearLayout.LayoutParams(0,dp(48),1));
        free=new CheckBox(this);free.setId(R.id.lobby_free);free.setText("Free seats");free.setTextSize(14);free.setTextColor(color(R.color.text_primary));free.setMinHeight(dp(48));filters.addView(free);
        FrameLayout area=new FrameLayout(this);root.addView(area,new LinearLayout.LayoutParams(-1,0,1));
        RecyclerView list=new RecyclerView(this);list.setId(R.id.lobby_list);list.setLayoutManager(new LinearLayoutManager(this));list.setAdapter(adapter);area.addView(list,new FrameLayout.LayoutParams(-1,-1));
        empty=label(17,R.color.text_secondary);empty.setGravity(Gravity.CENTER);empty.setPadding(dp(20),dp(20),dp(20),dp(20));area.addView(empty,new FrameLayout.LayoutParams(-1,-1));
        free.setOnCheckedChangeListener((b,on)->filter());search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){}public void afterTextChanged(Editable e){filter();}
        });
        gamePath=getIntent().getStringExtra(PlayTogether.EXTRA_GAME);
        if(gamePath!=null){
            checkingGame=true;screenTitle("Matching lobbies");
            PlayTogether.inspect(gamePath,entry->{
                if(isFinishing()||isDestroyed())return;
                if(entry==null){checkingGame=false;filter();return;}
                PlayTogether.identity(entry,hash->{if(isFinishing()||isDestroyed())return;gameHash=hash;checkingGame=false;filter();});
            });
        }
        model=new ViewModelProvider(this).get(LobbyDirectoryModel.class);model.state.observe(this,this::render);
    }
    private void render(LobbyDirectory.State next){
        if(state!=null&&!state.account.equals(next.account)){search.setText("");dismissDetail();}
        state=next;status.setText(next.status);progress.setVisibility(next.busy?View.VISIBLE:View.INVISIBLE);
        account.setVisibility(!next.verified&&!next.busy?View.VISIBLE:View.GONE);community.setVisibility(next.needsCommunity?View.VISIBLE:View.GONE);
        refresh.setEnabled(!next.busy&&android.os.SystemClock.elapsedRealtime()>=next.retryAt);
        community.setEnabled(!next.busy&&android.os.SystemClock.elapsedRealtime()>=next.retryAt);
        filters.setVisibility(next.verified&&!next.needsCommunity?View.VISIBLE:View.GONE);
        if(!selected.isEmpty()&&next.rooms.stream().noneMatch(r->r.id.equals(selected)&&r.revision==selectedRevision))dismissDetail();
        filter();
    }
    private void filter(){
        if(state==null)return;String query=search.getText().toString().trim().toLowerCase(Locale.ROOT);List<LobbyDirectory.Room> filtered=new ArrayList<>();
        for(LobbyDirectory.Room r:state.rooms)if((gamePath==null||!checkingGame&&!gameHash.isEmpty()&&r.acceptsJavaGame(gameHash))
                &&(!free.isChecked()||r.status.equals("choosing")||r.available>0)
                &&(r.title+" "+r.name+" "+r.platform).toLowerCase(Locale.ROOT).contains(query))filtered.add(r);
        adapter.submitList(filtered);empty.setVisibility(filtered.isEmpty()?View.VISIBLE:View.GONE);
        empty.setText(!state.verified?"":state.busy?"Loading lobbies...":state.needsCommunity?"":!state.rooms.isEmpty()?"No matching lobbies":state.status.equals("No compatible lobbies right now.")?"No compatible lobbies right now.":"");
        if(gamePath!=null&&state.verified&&!state.needsCommunity&&filtered.isEmpty())
            empty.setText(checkingGame?"Checking selected game...":gameHash.isEmpty()?"Selected game is unavailable. Return to Library.":"No matching or unassigned rooms. Return to My room to create one.");
    }
    private void open(LobbyDirectory.Room expected){
        if(state==null)return;LobbyDirectory.Room room=null;for(LobbyDirectory.Room candidate:state.rooms)if(candidate.id.equals(expected.id)&&candidate.revision==expected.revision)room=candidate;
        if(room==null)return;selected=room.id;selectedRevision=room.revision;
        LobbyDirectory.Room target=room;
        detail=new AlertDialog.Builder(this).setTitle(room.title).setMessage("Host: "+room.name+"\n"+(room.access.equals("approval")?"Host approval required":"Open room")
                +(room.seats>0?"\n"+room.available+" / "+room.seats+" seats available":""))
                .setPositiveButton("Open room",(d,w)->startActivity(new Intent(this,LobbyRoomActivity.class).setData(android.net.Uri.parse(LobbyLink.create(target.owner,target.id))).putExtra(PlayTogether.EXTRA_GAME,gamePath)))
                .setNegativeButton("Close",null).show();
        detail.setOnDismissListener(d->{selected="";detail=null;});
    }
    private void dismissDetail(){if(detail!=null)detail.dismiss();selected="";}
    private TextView label(int size,int tint){TextView t=new TextView(this);t.setTextSize(size);t.setTextColor(color(tint));return t;}
    private Button action(LinearLayout root,String text,int id,Runnable run){Button b=new Button(this);b.setId(id);b.setText(text);b.setAllCaps(false);b.setTextColor(color(R.color.accent));b.setBackgroundResource(R.drawable.bg_fluxer_action);b.setMinHeight(dp(48));root.addView(b,new LinearLayout.LayoutParams(-1,-2));b.setOnClickListener(v->run.run());return b;}
    private int color(int id){return AppPalette.color(this,id);}
    @Override protected void onStart(){super.onStart();model.resume();}
    @Override protected void onStop(){dismissDetail();model.pause();super.onStop();}
    @Override public boolean dispatchKeyEvent(android.view.KeyEvent event){
        androidx.core.view.WindowInsetsCompat insets=androidx.core.view.ViewCompat.getRootWindowInsets(screenContent);
        if(event.getKeyCode()==android.view.KeyEvent.KEYCODE_BUTTON_B&&insets!=null&&insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())){
            if(event.getAction()==android.view.KeyEvent.ACTION_UP)((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(),0);
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
    private final class Row extends RecyclerView.ViewHolder {
        final LinearLayout root;final TextView title,host,info;
        Row(){super(new LinearLayout(LobbyDirectoryActivity.this));root=(LinearLayout)itemView;root.setOrientation(LinearLayout.VERTICAL);root.setFocusable(true);root.setBackgroundResource(R.drawable.bg_library_tab);root.setPadding(dp(14),dp(12),dp(14),dp(12));root.setLayoutParams(new RecyclerView.LayoutParams(-1,-2));
            title=label(18,R.color.text_primary);host=label(14,R.color.text_secondary);info=label(13,R.color.accent);root.addView(title);root.addView(host);root.addView(info);}
    }
    private final class Rooms extends ListAdapter<LobbyDirectory.Room,Row>{
        Rooms(){super(new DiffUtil.ItemCallback<LobbyDirectory.Room>(){
            public boolean areItemsTheSame(LobbyDirectory.Room a,LobbyDirectory.Room b){return a.id.equals(b.id);}
            public boolean areContentsTheSame(LobbyDirectory.Room a,LobbyDirectory.Room b){return a.revision==b.revision;}
        });}
        public Row onCreateViewHolder(ViewGroup p,int type){return new Row();}
        public void onBindViewHolder(Row row,int position){LobbyDirectory.Room r=getItem(position);row.title.setText(r.title);row.host.setText(r.name);
            row.info.setText((r.access.equals("approval")?"Approval":"Open")+(r.seats>0?"  |  "+r.available+" / "+r.seats+" free  |  "+r.platform.toUpperCase(Locale.ROOT):""));row.root.setOnClickListener(v->open(r));}
    }
}
