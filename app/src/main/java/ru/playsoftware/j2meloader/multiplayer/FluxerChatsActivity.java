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
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.settings.CompactSettingsActivity;
import ru.playsoftware.j2meloader.ui.AppPalette;
import ru.playsoftware.j2meloader.ui.ConsoleSurface;
import ru.playsoftware.j2meloader.input.GameMenuDialog;

/** Compact native chat surface. Narrow viewports use list -> conversation navigation. */
public final class FluxerChatsActivity extends CompactSettingsActivity {
    private FluxerChatsModel model;
    private FluxerChats.State state;
    private LinearLayout listPane,chatPane,composer,workspace,historyActions;
    private TextView status,empty,title,counter,listEmpty;
    private EditText search,input;
    private Button older,latest,signIn;
    private ImageButton send;
    private RecyclerView channels,messages;
    private ChannelAdapter channelAdapter;
    private MessageAdapter messageAdapter;
    private boolean wide,binding,showEarlier,lastIme,keepTailOnResize;
    private String shown="";
    private String headerTitle="";
    private long editRevision;
    private RadioGroup tabs;
    private RadioButton chatsTab,friendsTab;
    private FluxerFriendsView friendsView;
    private boolean showFriends,returnToFriends;
    private long handledOpen;
    private GameMenuDialog dialog;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);screenTitle(getString(R.string.social_chats));
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                |android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        headerButton(getString(R.string.social_refresh),android.R.drawable.ic_popup_sync,()->model.refresh());
        headerButton(getString(R.string.social_options),R.drawable.ic_library_ellipsis_vertical,this::socialOptions);
        LinearLayout root=column();screenContent.addView(root,new ViewGroup.LayoutParams(-1,-1));
        root.setBackground(new ConsoleSurface(this,ConsoleSurface.Kind.CHASSIS));
        screenHeader.setBackground(new ConsoleSurface(this,ConsoleSurface.Kind.CHASSIS));
        tabs=new RadioGroup(this);tabs.setOrientation(LinearLayout.HORIZONTAL);
        chatsTab=tab(getString(R.string.social_chats),R.id.fluxer_tab_chats);friendsTab=tab(getString(R.string.social_friends),R.id.fluxer_tab_friends);
        tabs.addView(chatsTab,new RadioGroup.LayoutParams(0,dp(48),1));tabs.addView(friendsTab,new RadioGroup.LayoutParams(0,dp(48),1));root.addView(tabs);
        status=text(13,R.color.text_secondary);status.setId(R.id.fluxer_chat_status);status.setPadding(dp(12),dp(6),dp(12),dp(6));
        status.setMaxLines(2);status.setEllipsize(android.text.TextUtils.TruncateAt.END);root.addView(status);
        signIn=new Button(this);signIn.setText(getString(R.string.social_open_account));signIn.setAllCaps(false);signIn.setId(R.id.fluxer_chat_sign_in);
        surface(signIn,ConsoleSurface.Kind.KEY);signIn.setTextColor(color(R.color.text_primary));
        signIn.setOnClickListener(v->startActivity(new Intent(this,FluxerAccountActivity.class)));root.addView(signIn);
        workspace=new LinearLayout(this);root.addView(workspace,new LinearLayout.LayoutParams(-1,0,1));
        listPane=column();listPane.setBackground(new ConsoleSurface(this,ConsoleSurface.Kind.SCREEN));
        search=editor(getString(R.string.social_search_chats));search.setId(R.id.fluxer_chat_search);search.setSingleLine(true);listPane.addView(search,new LinearLayout.LayoutParams(-1,dp(48)));
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH|EditorInfo.IME_FLAG_NO_EXTRACT_UI|EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        channels=new RecyclerView(this);channels.setId(R.id.fluxer_chat_channels);channels.setLayoutManager(new LinearLayoutManager(this));
        channelAdapter=new ChannelAdapter();channels.setAdapter(channelAdapter);
        FrameLayout channelArea=new FrameLayout(this);listPane.addView(channelArea,new LinearLayout.LayoutParams(-1,0,1));
        channelArea.addView(channels,new FrameLayout.LayoutParams(-1,-1));listEmpty=text(15,R.color.text_secondary);listEmpty.setGravity(Gravity.CENTER);
        listEmpty.setPadding(dp(12),dp(12),dp(12),dp(12));channelArea.addView(listEmpty,new FrameLayout.LayoutParams(-1,-1));
        chatPane=column();chatPane.setBackground(new ConsoleSurface(this,ConsoleSurface.Kind.SCREEN));title=text(16,R.color.text_primary);title.setTypeface(null,android.graphics.Typeface.BOLD);
        title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);title.setPadding(dp(12),dp(8),dp(12),dp(8));chatPane.addView(title);
        historyActions=new LinearLayout(this);older=command(getString(R.string.social_earlier),()->{showEarlier=true;model.older();});older.setId(R.id.fluxer_chat_older);
        latest=command(getString(R.string.social_latest),()->model.latest());latest.setId(R.id.fluxer_chat_latest);
        historyActions.addView(older,new LinearLayout.LayoutParams(0,dp(48),1));historyActions.addView(latest,new LinearLayout.LayoutParams(0,dp(48),1));chatPane.addView(historyActions);
        FrameLayout history=new FrameLayout(this);chatPane.addView(history,new LinearLayout.LayoutParams(-1,0,1));
        messages=new RecyclerView(this);messages.setId(R.id.fluxer_chat_messages);LinearLayoutManager layout=new LinearLayoutManager(this);layout.setStackFromEnd(true);
        messages.setLayoutManager(layout);messages.setItemAnimator(null);messageAdapter=new MessageAdapter();messages.setAdapter(messageAdapter);
        history.addView(messages,new FrameLayout.LayoutParams(-1,-1));
        empty=text(16,R.color.text_secondary);empty.setGravity(Gravity.CENTER);empty.setPadding(dp(16),dp(8),dp(16),dp(8));
        history.addView(empty,new FrameLayout.LayoutParams(-1,-1));
        composer=new LinearLayout(this);composer.setGravity(Gravity.CENTER_VERTICAL);composer.setPadding(dp(8),dp(4),dp(8),dp(4));composer.setBackground(new ConsoleSurface(this,ConsoleSurface.Kind.CHASSIS));
        input=editor(getString(R.string.social_message));input.setId(R.id.fluxer_chat_input);input.setMinLines(1);input.setMaxLines(3);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(FluxerChats.TEXT_LIMIT)});
        input.setImeOptions(EditorInfo.IME_ACTION_SEND|EditorInfo.IME_FLAG_NO_EXTRACT_UI|EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        input.setSaveEnabled(false);input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        composer.addView(input,new LinearLayout.LayoutParams(0,-2,1));
        send=new ImageButton(this);send.setId(R.id.fluxer_chat_send);send.setImageResource(android.R.drawable.ic_menu_send);
        send.setColorFilter(color(R.color.accent));send.setContentDescription(getString(R.string.social_send));send.setTooltipText(getString(R.string.social_send));surface(send,ConsoleSurface.Kind.KEY);
        send.setOnClickListener(v->send());composer.addView(send,new LinearLayout.LayoutParams(dp(48),dp(48)));chatPane.addView(composer);
        counter=text(11,R.color.text_secondary);counter.setGravity(Gravity.END);counter.setPadding(0,0,dp(12),dp(2));chatPane.addView(counter);
        model=new ViewModelProvider(this).get(FluxerChatsModel.class);
        friendsView=new FluxerFriendsView(this,model);root.addView(friendsView,new LinearLayout.LayoutParams(-1,0,1));
        showFriends=saved==null?getIntent().getBooleanExtra("friends",false):saved.getBoolean("friends");
        handledOpen=saved==null?0:saved.getLong("opened");returnToFriends=saved!=null&&saved.getBoolean("returnFriends");
        tabs.setOnCheckedChangeListener((group,id)->{
            showFriends=id==R.id.fluxer_tab_friends;chatsTab.setSelected(!showFriends);friendsTab.setSelected(showFriends);
            ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0);
            if(state!=null){friendsView.render(state);arrange(screenContent.getWidth(),screenContent.getHeight());}
            focusContentWhenReady();
        });tabs.check(showFriends?R.id.fluxer_tab_friends:R.id.fluxer_tab_chats);
        messages.addOnScrollListener(new RecyclerView.OnScrollListener(){
            @Override public void onScrolled(RecyclerView view,int dx,int dy){
                if(dy!=0){
                    LinearLayoutManager manager=(LinearLayoutManager)view.getLayoutManager();
                    keepTailOnResize=manager.findLastVisibleItemPosition()>=messageAdapter.getItemCount()-1;
                }
            }
        });
        messages.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{
            if(b-t!=ob-ot&&keepTailOnResize&&state!=null&&!state.past){
                messages.post(()->{
                    if(keepTailOnResize&&state!=null&&!state.past&&messageAdapter.getItemCount()>0)
                        messages.scrollToPosition(messageAdapter.getItemCount()-1);
                });
            }
        });
        search.addTextChangedListener(watcher(()->filterChannels()));
        input.addTextChangedListener(watcher(()->{
            if(!binding&&state!=null&&!shown.isEmpty())editRevision=model.draft(shown,input.getText().toString());
            updateSend();
        }));
        input.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_SEND){send();return true;}return false;});
        model.state.observe(this,this::render);
        screenContent.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->arrange(r-l,b-t));
        // IME visibility can settle after the resize callback. Reflow chrome
        // from the inset event as well, without consuming the host's insets.
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(screenContent,(view,insets)->{
            view.post(()->arrange(view.getWidth(),view.getHeight()));return insets;
        });
    }
    private void arrange(int width,int height) {
        boolean split=width>=dp(Math.round(600*Math.max(1f,getResources().getConfiguration().fontScale)))&&getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        if(workspace.getChildCount()==0||wide!=split){
            wide=split;workspace.removeAllViews();workspace.setOrientation(LinearLayout.HORIZONTAL);
            workspace.addView(listPane,new LinearLayout.LayoutParams(wide?dp(220):-1,-1));
            workspace.addView(chatPane,new LinearLayout.LayoutParams(wide?0:-1,-1,wide?1:0));
        }
        listPane.setVisibility(wide||shown.isEmpty()?View.VISIBLE:View.GONE);
        chatPane.setVisibility(wide||!shown.isEmpty()?View.VISIBLE:View.GONE);
        boolean ime=keyboardVisible();
        int available=height+(screenHeader.getVisibility()==View.VISIBLE?screenHeader.getHeight():0);
        screenHeader.setVisibility(ime&&available<dp(240)?View.GONE:View.VISIBLE);
        tabs.setVisibility(ime?View.GONE:View.VISIBLE);
        title.setVisibility(ime||!wide?View.GONE:View.VISIBLE);
        boolean compact=height<dp(400);
        friendsView.arrange(width,compact);
        int lines=compact?(ime?1:2):3;if(input.getMaxLines()!=lines)input.setMaxLines(lines);
        historyActions.setVisibility(ime||compact||shown.isEmpty()?View.GONE:View.VISIBLE);
        counter.setVisibility(ime||compact||shown.isEmpty()?View.GONE:View.VISIBLE);
        if(status.getMaxLines()!=(ime?1:2))status.setMaxLines(ime?1:2);
        if(state!=null){
            screenTitle(showFriends?getString(R.string.social_friends):(!wide||ime)&&!shown.isEmpty()?SocialText.chatTitle(this,state):getString(R.string.social_chats));
            workspace.setVisibility(state.verified&&!showFriends?View.VISIBLE:View.GONE);
            friendsView.setVisibility(state.verified&&showFriends?View.VISIBLE:View.GONE);
            String notice=showFriends?SocialText.friendsStatus(this,state):SocialText.notice(this,state.statusCode);
            if(!android.text.TextUtils.equals(status.getText(),notice))status.setText(notice);
            boolean quiet=state.quiet(showFriends);
            status.setVisibility(compact&&quiet?View.GONE:View.VISIBLE);
        }
        if(ime!=lastIme){
            lastIme=ime;
            if(ime&&keepTailOnResize&&state!=null&&!state.past)messages.post(()->{
                if(!messageAdapter.getCurrentList().isEmpty())messages.scrollToPosition(messageAdapter.getItemCount()-1);
            });
        }
    }
    private void render(FluxerChats.State next) {
        boolean switched=!shown.equals(next.channel),accountChanged=state!=null&&!state.account.equals(next.account);
        boolean returnedToLatest=state!=null&&state.past&&!next.past;
        state=next;
        if(accountChanged||switched)dismissActions();
        if(accountChanged){handledOpen=0;returnToFriends=false;}
        if(next.openedConversation>handledOpen){handledOpen=next.openedConversation;returnToFriends=true;tabs.check(R.id.fluxer_tab_chats);}
        friendsView.render(next);
        long incoming=next.friends.stream().filter(f->f.type==3).count();
        friendsTab.setText(incoming==0?getString(R.string.social_friends):getString(R.string.social_friends_count,incoming));
        if(accountChanged){binding=true;input.setText("");binding=false;search.setText("");messageAdapter.submitList(Collections.emptyList());}
        status.setText(SocialText.notice(this,next.statusCode));signIn.setVisibility(!next.verified&&!next.loading?View.VISIBLE:View.GONE);
        workspace.setVisibility(next.verified?View.VISIBLE:View.GONE);
        title.setText(SocialText.chatTitle(this,next));screenTitle(!wide&&!next.channel.isEmpty()?SocialText.chatTitle(this,next):getString(R.string.social_chats));
        shown=next.channel;
        if(switched||next.draftRevision>=editRevision) {
            binding=true;
            if(!input.getText().toString().equals(next.draft)){input.setText(next.draft);input.setSelection(input.length());}
            editRevision=next.draftRevision;binding=false;
        }
        filterChannels();if(switched)channelAdapter.notifyItemRangeChanged(0,channelAdapter.getItemCount(),"selection");
        LinearLayoutManager layout=(LinearLayoutManager)messages.getLayoutManager();
        boolean tail=switched||returnedToLatest||messageAdapter.getItemCount()==0||layout.findLastVisibleItemPosition()>=messageAdapter.getItemCount()-2;
        boolean earlierReady=showEarlier&&!next.historyBusy&&next.past;
        if(earlierReady||switched)showEarlier=false;
        messageAdapter.submitList(next.messages,()->{
            if(earlierReady){keepTailOnResize=false;messages.scrollToPosition(0);}
            else if(tail&&!next.past&&!next.messages.isEmpty()){
                keepTailOnResize=true;messages.scrollToPosition(next.messages.size()-1);
            }
        });
        empty.setText(shown.isEmpty()?getString(R.string.social_select_conversation):next.historyBusy?getString(R.string.social_loading_messages):getString(R.string.social_no_messages));
        empty.setVisibility(next.messages.isEmpty()?View.VISIBLE:View.GONE);
        historyActions.setVisibility(shown.isEmpty()?View.GONE:View.VISIBLE);
        older.setEnabled(next.more&&!next.historyBusy);latest.setEnabled(!next.historyBusy);
        composer.setVisibility(shown.isEmpty()?View.GONE:View.VISIBLE);counter.setVisibility(shown.isEmpty()?View.GONE:View.VISIBLE);
        arrange(screenContent.getWidth(),screenContent.getHeight());updateSend();
        if(switched){input.clearFocus();((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0);focusContentWhenReady();}
    }
    private void filterChannels() {
        if(state==null)return;String query=search.getText().toString().toLowerCase(Locale.ROOT).trim();List<FluxerChats.Channel> list=new ArrayList<>();
        for(FluxerChats.Channel c:state.channels)if(SocialText.channel(this,c).toLowerCase(Locale.ROOT).contains(query))list.add(c);
        channelAdapter.submitList(list);
        listEmpty.setText(state.loading?getString(R.string.social_loading_conversations):query.isEmpty()?getString(R.string.social_no_conversations):getString(R.string.social_no_matching_conversations));
        listEmpty.setVisibility(list.isEmpty()?View.VISIBLE:View.GONE);
    }
    private void updateSend() {boolean ok=state!=null&&state.canSend&&!input.getText().toString().trim().isEmpty();send.setEnabled(ok);send.setAlpha(ok?1f:0.4f);counter.setText(input.length()+" / "+FluxerChats.TEXT_LIMIT);}
    private void send(){if(send.isEnabled()&&!shown.isEmpty())model.send(shown,editRevision);}
    @Override protected void onStart(){super.onStart();model.resume();}
    @Override protected void onStop(){dismissActions();friendsView.dismissActions();model.pause();super.onStop();}
    @Override protected void onSaveInstanceState(Bundle out){out.putBoolean("friends",showFriends);out.putLong("opened",handledOpen);out.putBoolean("returnFriends",returnToFriends);super.onSaveInstanceState(out);}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(intent.getBooleanExtra("friends",false))tabs.check(R.id.fluxer_tab_friends);}
    @Override public void onBackPressed(){
        if(!showFriends&&returnToFriends){returnToFriends=false;tabs.check(R.id.fluxer_tab_friends);}
        else if(!showFriends&&!wide&&!shown.isEmpty()){model.list();}else super.onBackPressed();
    }
    private boolean keyboardVisible(){androidx.core.view.WindowInsetsCompat insets=androidx.core.view.ViewCompat.getRootWindowInsets(screenContent);return insets!=null&&insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime());}
    @Override public boolean dispatchKeyEvent(android.view.KeyEvent event){
        if(event.getKeyCode()==android.view.KeyEvent.KEYCODE_BUTTON_B&&keyboardVisible()){
            if(event.getAction()==android.view.KeyEvent.ACTION_UP)((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0);
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
    private int color(int id){return AppPalette.color(this,id);}
    private RadioButton tab(String label,int id){RadioButton b=new RadioButton(this);b.setId(id);b.setText(label);b.setTextSize(15);b.setTextColor(color(R.color.text_primary));b.setGravity(Gravity.CENTER);b.setButtonDrawable(null);surface(b,ConsoleSurface.Kind.TAB);return b;}
    private void surface(View view,ConsoleSurface.Kind kind){view.setBackgroundTintList(null);view.setStateListAnimator(null);view.setDefaultFocusHighlightEnabled(false);view.setBackground(new ConsoleSurface(this,kind));}
    private void dismissActions(){GameMenuDialog old=dialog;dialog=null;if(old!=null)old.dismiss();}
    private GameMenuDialog menu(String title){dismissActions();GameMenuDialog m=new GameMenuDialog(this,title);dialog=m;m.useEdgePanel();m.show();m.page(title);m.setOnDismissListener(d->{if(dialog==m)dialog=null;});return m;}
    private void socialOptions(){
        GameMenuDialog m=menu(getString(R.string.social_title));
        if(!showFriends&&!shown.isEmpty()){
            m.action(getString(R.string.social_conversations),R.drawable.ic_library_messages_square,()->{dismissActions();returnToFriends=false;model.list();});
            m.action(getString(R.string.social_earlier),android.R.drawable.ic_media_previous,()->{dismissActions();showEarlier=true;model.older();}).setEnabled(state!=null&&state.more&&!state.historyBusy);
            m.action(getString(R.string.social_latest),android.R.drawable.ic_media_next,()->{dismissActions();model.latest();}).setEnabled(state!=null&&!state.historyBusy);
        }
        m.action(getString(R.string.social_browse_lobbies),android.R.drawable.ic_menu_search,()->{dismissActions();startActivity(new Intent(this,LobbyDirectoryActivity.class));});
        m.action(getString(R.string.social_my_room),android.R.drawable.ic_menu_myplaces,()->{dismissActions();startActivity(new Intent(this,LobbyRoomActivity.class));});
        m.action(getString(R.string.social_account_short),android.R.drawable.ic_menu_myplaces,()->{dismissActions();startActivity(new Intent(this,FluxerAccountActivity.class));});
    }
    private void deliveryActions(String channel,FluxerChats.Message message){
        if(state==null||!channel.equals(shown))return;
        GameMenuDialog m=menu(getString(R.string.social_delivery));m.message(SocialText.notice(this,message.deliveryCode));
        if(message.retryable)m.action(getString(R.string.social_retry_sending),android.R.drawable.ic_popup_sync,()->{dismissActions();model.retry(channel,message.id);});
        m.action(getString(R.string.social_check_delivery),android.R.drawable.ic_menu_search,()->{dismissActions();model.latest();});
        if(message.deliveryCode!=SocialNotice.SENDING)m.action(getString(R.string.social_dismiss_notice),android.R.drawable.ic_menu_close_clear_cancel,()->{
            GameMenuDialog confirm=menu(getString(R.string.social_dismiss_notice_title));confirm.confirmationPage(getString(R.string.social_dismiss_notice_title));confirm.message(getString(R.string.social_dismiss_notice_body));
            confirm.action(getString(R.string.social_cancel),android.R.drawable.ic_menu_close_clear_cancel,this::dismissActions);
            confirm.action(getString(R.string.social_dismiss),android.R.drawable.ic_menu_delete,()->{dismissActions();model.discard(channel,message.id);});
        });
    }
    @Override protected void screenTitle(CharSequence title){
        if(!title.toString().equals(headerTitle)){headerTitle=title.toString();super.screenTitle(title);}
    }
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private TextView text(int size,int color){TextView t=new TextView(this);t.setTextSize(size);t.setTextColor(color(color));return t;}
    private EditText editor(String hint){EditText e=new EditText(this);e.setTextSize(16);e.setHint(hint);e.setTextColor(color(R.color.text_primary));e.setHintTextColor(color(R.color.text_secondary));e.setPadding(dp(12),dp(8),dp(12),dp(8));return e;}
    private Button command(String label,Runnable action){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(13);b.setTextColor(color(R.color.text_primary));surface(b,ConsoleSurface.Kind.KEY);b.setPadding(dp(8),0,dp(8),0);b.setOnClickListener(v->action.run());return b;}
    private TextWatcher watcher(Runnable changed){return new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){}public void afterTextChanged(Editable s){changed.run();}};}
    private String time(String iso){try{return OffsetDateTime.parse(iso).atZoneSameInstant(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.SHORT).withLocale(getResources().getConfiguration().getLocales().get(0)));}catch(RuntimeException e){return "";}}
    private final class Row extends RecyclerView.ViewHolder {
        final LinearLayout root;final TextView top,body,foot;
        Row(boolean conversation){super(column());root=(LinearLayout)itemView;root.setPadding(dp(12),dp(10),dp(12),dp(10));root.setLayoutParams(new RecyclerView.LayoutParams(-1,-2));
            top=text(13,R.color.accent);body=text(16,R.color.text_primary);foot=text(12,R.color.text_secondary);root.addView(top);root.addView(body);root.addView(foot);
            root.setFocusable(true);surface(root,ConsoleSurface.Kind.ROW);
            if(conversation){root.setMinimumHeight(dp(66));body.setMaxLines(2);body.setEllipsize(android.text.TextUtils.TruncateAt.END);}
        }
    }
    private final class ChannelAdapter extends ListAdapter<FluxerChats.Channel,Row> {
        ChannelAdapter(){super(new DiffUtil.ItemCallback<FluxerChats.Channel>(){
            public boolean areItemsTheSame(FluxerChats.Channel a,FluxerChats.Channel b){return a.id.equals(b.id);}
            public boolean areContentsTheSame(FluxerChats.Channel a,FluxerChats.Channel b){return a.name.equals(b.name)&&a.last.equals(b.last)&&a.group==b.group&&a.fallbackName==b.fallbackName;}
        });}
        public Row onCreateViewHolder(ViewGroup parent,int type){return new Row(true);}
        public void onBindViewHolder(Row r,int position){FluxerChats.Channel c=getItem(position);r.top.setText(c.group?getString(R.string.social_group):getString(R.string.social_direct_message));r.body.setText(SocialText.channel(FluxerChatsActivity.this,c));r.foot.setVisibility(View.GONE);
            r.root.setSelected(c.id.equals(shown));r.root.setBackground(new ConsoleSurface(FluxerChatsActivity.this,c.id.equals(shown)?ConsoleSurface.Kind.KEY:ConsoleSurface.Kind.ROW));r.root.setOnClickListener(v->{returnToFriends=false;model.select(c.id);});
        }
    }
    private final class MessageAdapter extends ListAdapter<FluxerChats.Message,Row> {
        MessageAdapter(){super(new DiffUtil.ItemCallback<FluxerChats.Message>(){
            public boolean areItemsTheSame(FluxerChats.Message a,FluxerChats.Message b){return a.id.equals(b.id);}
            public boolean areContentsTheSame(FluxerChats.Message a,FluxerChats.Message b){return a.author.equals(b.author)&&a.text.equals(b.text)&&a.time.equals(b.time)&&java.util.Objects.equals(a.deliveryCode,b.deliveryCode)&&java.util.Objects.equals(a.contentKind,b.contentKind)&&a.retryable==b.retryable&&a.mine==b.mine;}
        });}
        public Row onCreateViewHolder(ViewGroup parent,int type){return new Row(false);}
        public void onBindViewHolder(Row r,int position){FluxerChats.Message m=getItem(position);r.top.setText(m.mine?getString(R.string.social_you):m.author);r.body.setText(SocialText.content(FluxerChatsActivity.this,m));
            r.top.setTextColor(color(m.mine?R.color.phone_cyan:R.color.accent));String stamp=time(m.time);r.foot.setText(stamp+(stamp.isEmpty()||m.deliveryCode==SocialNotice.NONE?"":"  ")+SocialText.notice(FluxerChatsActivity.this,m.deliveryCode));
            r.foot.setVisibility(stamp.isEmpty()&&m.delivery.isEmpty()?View.GONE:View.VISIBLE);
            boolean pending=m.deliveryCode!=SocialNotice.NONE&&m.deliveryCode!=SocialNotice.EDITED;String channel=shown;
            r.root.setOnClickListener(pending?v->deliveryActions(channel,m):null);
            r.root.setClickable(pending);
        }
    }
}
