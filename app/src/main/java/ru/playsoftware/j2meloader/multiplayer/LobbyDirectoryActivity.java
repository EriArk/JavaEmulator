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
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.*;
import androidx.recyclerview.widget.ListAdapter;
import java.util.*;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.settings.CompactSettingsActivity;
import ru.playsoftware.j2meloader.ui.AppPalette;
import ru.playsoftware.j2meloader.ui.ConsoleSurface;
import ru.playsoftware.j2meloader.input.GameMenuDialog;

/** Advertisement browsing only; opening a row still requires host admission and explicit Ready. */
public final class LobbyDirectoryActivity extends CompactSettingsActivity {
    private LobbyDirectoryModel model;
    private LobbyDirectory.State state;
    private TextView status,empty;
    private Button account,community,retry;
    private EditText search;
    private CheckBox free;
    private ImageButton refresh;
    private ProgressBar progress;
    private LinearLayout filters;
    private final Rooms adapter=new Rooms();
    private GameMenuDialog detail;
    private String selected="";
    private long selectedRevision;
    private String gamePath, gameHash = "";
    private boolean checkingGame;

    @Override protected void onCreate(Bundle saved){
        super.onCreate(saved);screenTitle(getString(R.string.social_lobbies));
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                |android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        refresh=headerButton(getString(R.string.social_refresh),android.R.drawable.ic_popup_sync,()->model.refresh());refresh.setId(R.id.lobby_refresh);
        headerButton(getString(R.string.social_options),R.drawable.ic_library_ellipsis_vertical,this::socialOptions);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);screenContent.addView(root,new ViewGroup.LayoutParams(-1,-1));
        root.setBackground(new ConsoleSurface(this,ConsoleSurface.Kind.CHASSIS));
        screenHeader.setBackground(new ConsoleSurface(this,ConsoleSurface.Kind.CHASSIS));
        TextView experimental=label(12,R.color.text_secondary);experimental.setText(getString(R.string.social_experimental));
        experimental.setPadding(dp(12),dp(4),dp(12),0);root.addView(experimental);
        status=label(14,R.color.text_secondary);status.setId(R.id.lobby_status);status.setPadding(dp(12),dp(8),dp(12),dp(8));
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);root.addView(status);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(true);root.addView(progress,new LinearLayout.LayoutParams(-1,dp(3)));
        account=action(root,getString(R.string.social_open_account),R.id.lobby_account,()->startActivity(new Intent(this,FluxerAccountActivity.class)));
        community=action(root,getString(R.string.social_retry_community),R.id.lobby_community,()->{if(state!=null)model.retryCommunity(state.account);});
        retry=action(root,getString(R.string.social_retry_loading),View.NO_ID,()->model.refresh());retry.setTag("retry-lobbies");
        filters=new LinearLayout(this);filters.setGravity(Gravity.CENTER_VERTICAL);root.addView(filters);
        search=new EditText(this);search.setId(R.id.lobby_search);search.setSingleLine(true);search.setTextSize(16);search.setHint(getString(R.string.social_search_lobbies));
        search.setTextColor(color(R.color.text_primary));search.setHintTextColor(color(R.color.text_secondary));search.setSaveEnabled(false);
        search.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);search.setPadding(dp(12),0,dp(8),0);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH|EditorInfo.IME_FLAG_NO_EXTRACT_UI|EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        filters.addView(search,new LinearLayout.LayoutParams(0,dp(48),1));
        free=new CheckBox(this);free.setId(R.id.lobby_free);free.setText(getString(R.string.social_free_seats));free.setTextSize(14);free.setTextColor(color(R.color.text_primary));free.setMinHeight(dp(48));filters.addView(free);
        FrameLayout area=new FrameLayout(this);root.addView(area,new LinearLayout.LayoutParams(-1,0,1));
        area.setBackground(new ConsoleSurface(this,ConsoleSurface.Kind.SCREEN));
        RecyclerView list=new RecyclerView(this);list.setId(R.id.lobby_list);list.setLayoutManager(new LinearLayoutManager(this));list.setAdapter(adapter);area.addView(list,new FrameLayout.LayoutParams(-1,-1));
        empty=label(17,R.color.text_secondary);empty.setTag("directory-empty");empty.setGravity(Gravity.CENTER);empty.setPadding(dp(20),dp(20),dp(20),dp(20));area.addView(empty,new FrameLayout.LayoutParams(-1,-1));
        free.setOnCheckedChangeListener((b,on)->filter());search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){}public void afterTextChanged(Editable e){filter();}
        });
        gamePath=getIntent().getStringExtra(PlayTogether.EXTRA_GAME);
        if(gamePath!=null){
            checkingGame=true;screenTitle(getString(R.string.social_matching_lobbies));
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
        state=next;status.setText(SocialText.notice(this,next.notice));progress.setVisibility(next.busy?View.VISIBLE:View.INVISIBLE);
        account.setVisibility(!next.verified&&!next.busy?View.VISIBLE:View.GONE);community.setVisibility(next.needsCommunity?View.VISIBLE:View.GONE);
        refresh.setEnabled(!next.busy&&android.os.SystemClock.elapsedRealtime()>=next.retryAt);
        community.setEnabled(!next.busy&&android.os.SystemClock.elapsedRealtime()>=next.retryAt);
        retry.setVisibility(next.error&&next.verified&&!next.needsCommunity&&!next.busy?View.VISIBLE:View.GONE);
        retry.setEnabled(android.os.SystemClock.elapsedRealtime()>=next.retryAt);
        filters.setVisibility(next.verified&&!next.needsCommunity?View.VISIBLE:View.GONE);
        if(!selected.isEmpty()&&next.rooms.stream().noneMatch(r->r.id.equals(selected)&&r.revision==selectedRevision))dismissDetail();
        filter();
    }
    private void filter(){
        if(state==null)return;String query=search.getText().toString().trim().toLowerCase(Locale.ROOT);List<LobbyDirectory.Room> filtered=new ArrayList<>();
        for(LobbyDirectory.Room r:state.rooms)if((gamePath==null||!checkingGame&&!gameHash.isEmpty()&&r.acceptsJavaGame(gameHash))
                &&(!free.isChecked()||r.hasSpace())
                &&(r.title+" "+r.name+" "+r.platform).toLowerCase(Locale.ROOT).contains(query))filtered.add(r);
        adapter.submitList(filtered);empty.setVisibility(filtered.isEmpty()?View.VISIBLE:View.GONE);
        empty.setText(!state.verified||state.needsCommunity?"":state.busy?getString(R.string.social_loading_lobbies):state.error?getString(R.string.social_load_rooms_failed):!query.isEmpty()||free.isChecked()?getString(R.string.social_no_rooms_match):getString(R.string.social_no_compatible_lobbies));
        if(gamePath!=null&&state.verified&&!state.needsCommunity&&!state.busy&&!state.error&&filtered.isEmpty())
            empty.setText(checkingGame?getString(R.string.social_check_selected_game):gameHash.isEmpty()?getString(R.string.social_selected_unavailable):!query.isEmpty()||free.isChecked()?getString(R.string.social_no_rooms_match):getString(R.string.social_no_game_rooms));
    }
    private void open(LobbyDirectory.Room expected){
        if(state==null)return;LobbyDirectory.Room room=null;for(LobbyDirectory.Room candidate:state.rooms)if(candidate.id.equals(expected.id)&&candidate.revision==expected.revision)room=candidate;
        if(room==null)return;dismissDetail();selected=room.id;selectedRevision=room.revision;
        LobbyDirectory.Room target=room;
        detail=new GameMenuDialog(this,SocialText.roomTitle(this,room));detail.useEdgePanel();detail.show();detail.page(SocialText.roomTitle(this,room));
        detail.message(getString(R.string.social_host_value,room.name));detail.message(SocialText.roomPhase(this,room));
        detail.message(room.access.equals("approval")?getString(R.string.social_approval_required):getString(R.string.social_open_access));
        if(room.seats>0)detail.message(getString(R.string.social_seats_available,room.available,room.seats));
        boolean own=room.owner.equals(state.account);
        if(own||room.hasSpace())detail.action(own?getString(R.string.social_my_room):getString(R.string.social_open_room),android.R.drawable.ic_media_play,()->{
            if(state==null||state.rooms.stream().noneMatch(r->r.id.equals(target.id)&&r.revision==target.revision&&r.live(System.currentTimeMillis()/1000,android.os.SystemClock.elapsedRealtime())))return;
            dismissDetail();startActivity(new Intent(this,LobbyRoomActivity.class).setData(own?null:android.net.Uri.parse(LobbyLink.create(target.owner,target.id))).putExtra(PlayTogether.EXTRA_GAME,gamePath));
        });
        detail.action(getString(R.string.social_close),android.R.drawable.ic_menu_close_clear_cancel,this::dismissDetail);
        GameMenuDialog shown=detail;detail.setOnDismissListener(d->{if(detail==shown){selected="";detail=null;}});
    }
    private void socialOptions(){
        dismissDetail();detail=new GameMenuDialog(this,getString(R.string.social_title));detail.useEdgePanel();detail.show();detail.page(getString(R.string.social_title));
        detail.action(getString(R.string.social_my_room),android.R.drawable.ic_menu_myplaces,()->{dismissDetail();startActivity(new Intent(this,LobbyRoomActivity.class).putExtra(PlayTogether.EXTRA_GAME,gamePath));});
        detail.action(getString(R.string.social_chats),R.drawable.ic_library_messages_square,()->{dismissDetail();startActivity(new Intent(this,FluxerChatsActivity.class));});
        detail.action(getString(R.string.social_account_short),android.R.drawable.ic_menu_myplaces,()->{dismissDetail();startActivity(new Intent(this,FluxerAccountActivity.class));});
        GameMenuDialog shown=detail;detail.setOnDismissListener(d->{if(detail==shown)detail=null;});
    }
    private void dismissDetail(){GameMenuDialog old=detail;detail=null;if(old!=null)old.dismiss();selected="";}
    private TextView label(int size,int tint){TextView t=new TextView(this);t.setTextSize(size);t.setTextColor(color(tint));return t;}
    private Button action(LinearLayout root,String text,int id,Runnable run){Button b=new Button(this);b.setId(id);b.setText(text);b.setAllCaps(false);b.setTextColor(color(R.color.text_primary));b.setBackgroundTintList(null);b.setStateListAnimator(null);b.setBackground(new ConsoleSurface(this,ConsoleSurface.Kind.KEY));b.setMinHeight(dp(48));root.addView(b,new LinearLayout.LayoutParams(-1,-2));b.setOnClickListener(v->run.run());return b;}
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
        Row(){super(new LinearLayout(LobbyDirectoryActivity.this));root=(LinearLayout)itemView;root.setOrientation(LinearLayout.VERTICAL);root.setFocusable(true);root.setBackground(new ConsoleSurface(LobbyDirectoryActivity.this,ConsoleSurface.Kind.ROW));root.setPadding(dp(14),dp(12),dp(14),dp(12));RecyclerView.LayoutParams lp=new RecyclerView.LayoutParams(-1,-2);lp.setMargins(dp(10),dp(4),dp(10),dp(4));root.setLayoutParams(lp);
            title=label(18,R.color.text_primary);host=label(14,R.color.text_secondary);info=label(13,R.color.accent);root.addView(title);root.addView(host);root.addView(info);}
    }
    private final class Rooms extends ListAdapter<LobbyDirectory.Room,Row>{
        Rooms(){super(new DiffUtil.ItemCallback<LobbyDirectory.Room>(){
            public boolean areItemsTheSame(LobbyDirectory.Room a,LobbyDirectory.Room b){return a.id.equals(b.id);}
            public boolean areContentsTheSame(LobbyDirectory.Room a,LobbyDirectory.Room b){return a.revision==b.revision;}
        });}
        public Row onCreateViewHolder(ViewGroup p,int type){return new Row();}
        public void onBindViewHolder(Row row,int position){LobbyDirectory.Room r=getItem(position);row.title.setText(SocialText.roomTitle(LobbyDirectoryActivity.this,r));row.host.setText(r.name);
            String access=getString(r.access.equals("approval")?R.string.social_approval:R.string.social_open);
            row.info.setText(r.seats>0?getString(R.string.social_room_summary_seats,SocialText.roomPhase(LobbyDirectoryActivity.this,r),access,r.available,r.seats):getString(R.string.social_room_summary,SocialText.roomPhase(LobbyDirectoryActivity.this,r),access));row.root.setOnClickListener(v->open(r));}
    }
}
