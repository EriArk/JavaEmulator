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

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);screenTitle("Chats");
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                |android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        headerButton("Refresh",android.R.drawable.ic_popup_sync,()->model.refresh());
        headerButton("Account",android.R.drawable.ic_menu_myplaces,()->startActivity(new Intent(this,FluxerAccountActivity.class)));
        LinearLayout root=column();screenContent.addView(root,new ViewGroup.LayoutParams(-1,-1));
        tabs=new RadioGroup(this);tabs.setOrientation(LinearLayout.HORIZONTAL);
        chatsTab=tab("Chats",R.id.fluxer_tab_chats);friendsTab=tab("Friends",R.id.fluxer_tab_friends);
        tabs.addView(chatsTab,new RadioGroup.LayoutParams(0,dp(44),1));tabs.addView(friendsTab,new RadioGroup.LayoutParams(0,dp(44),1));root.addView(tabs);
        status=text(13,R.color.text_secondary);status.setId(R.id.fluxer_chat_status);status.setPadding(dp(12),dp(6),dp(12),dp(6));
        status.setMaxLines(2);status.setEllipsize(android.text.TextUtils.TruncateAt.END);root.addView(status);
        signIn=new Button(this);signIn.setText("Open Fluxer account");signIn.setAllCaps(false);signIn.setId(R.id.fluxer_chat_sign_in);
        signIn.setOnClickListener(v->startActivity(new Intent(this,FluxerAccountActivity.class)));root.addView(signIn);
        workspace=new LinearLayout(this);root.addView(workspace,new LinearLayout.LayoutParams(-1,0,1));
        listPane=column();listPane.setBackgroundColor(color(R.color.phone_panel));
        search=editor("Search chats");search.setId(R.id.fluxer_chat_search);search.setSingleLine(true);listPane.addView(search,new LinearLayout.LayoutParams(-1,dp(48)));
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH|EditorInfo.IME_FLAG_NO_EXTRACT_UI|EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        channels=new RecyclerView(this);channels.setId(R.id.fluxer_chat_channels);channels.setLayoutManager(new LinearLayoutManager(this));
        channelAdapter=new ChannelAdapter();channels.setAdapter(channelAdapter);
        FrameLayout channelArea=new FrameLayout(this);listPane.addView(channelArea,new LinearLayout.LayoutParams(-1,0,1));
        channelArea.addView(channels,new FrameLayout.LayoutParams(-1,-1));listEmpty=text(15,R.color.text_secondary);listEmpty.setGravity(Gravity.CENTER);
        listEmpty.setPadding(dp(12),dp(12),dp(12),dp(12));channelArea.addView(listEmpty,new FrameLayout.LayoutParams(-1,-1));
        chatPane=column();title=text(16,R.color.text_primary);title.setTypeface(null,android.graphics.Typeface.BOLD);
        title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);title.setPadding(dp(12),dp(8),dp(12),dp(8));chatPane.addView(title);
        historyActions=new LinearLayout(this);older=command("Earlier",()->{showEarlier=true;model.older();});older.setId(R.id.fluxer_chat_older);
        latest=command("Latest",()->model.latest());latest.setId(R.id.fluxer_chat_latest);
        historyActions.addView(older,new LinearLayout.LayoutParams(0,dp(40),1));historyActions.addView(latest,new LinearLayout.LayoutParams(0,dp(40),1));chatPane.addView(historyActions);
        FrameLayout history=new FrameLayout(this);chatPane.addView(history,new LinearLayout.LayoutParams(-1,0,1));
        messages=new RecyclerView(this);messages.setId(R.id.fluxer_chat_messages);LinearLayoutManager layout=new LinearLayoutManager(this);layout.setStackFromEnd(true);
        messages.setLayoutManager(layout);messages.setItemAnimator(null);messageAdapter=new MessageAdapter();messages.setAdapter(messageAdapter);
        history.addView(messages,new FrameLayout.LayoutParams(-1,-1));
        empty=text(16,R.color.text_secondary);empty.setGravity(Gravity.CENTER);empty.setPadding(dp(16),dp(8),dp(16),dp(8));
        history.addView(empty,new FrameLayout.LayoutParams(-1,-1));
        composer=new LinearLayout(this);composer.setGravity(Gravity.CENTER_VERTICAL);composer.setPadding(dp(8),dp(4),dp(8),dp(4));composer.setBackgroundColor(color(R.color.phone_panel));
        input=editor("Message");input.setId(R.id.fluxer_chat_input);input.setMinLines(1);input.setMaxLines(3);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(FluxerChats.TEXT_LIMIT)});
        input.setImeOptions(EditorInfo.IME_ACTION_SEND|EditorInfo.IME_FLAG_NO_EXTRACT_UI|EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        input.setSaveEnabled(false);input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        composer.addView(input,new LinearLayout.LayoutParams(0,-2,1));
        send=new ImageButton(this);send.setId(R.id.fluxer_chat_send);send.setImageResource(android.R.drawable.ic_menu_send);
        send.setColorFilter(color(R.color.accent));send.setContentDescription("Send message");send.setTooltipText("Send message");send.setBackgroundResource(R.drawable.bg_library_tab);
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
        input.setOnFocusChangeListener((v,focused)->{
            LinearLayoutManager manager=(LinearLayoutManager)messages.getLayoutManager();
            keepTailOnResize=focused&&manager.findLastVisibleItemPosition()>=messageAdapter.getItemCount()-1;
        });
        search.addTextChangedListener(watcher(()->filterChannels()));
        input.addTextChangedListener(watcher(()->{
            if(!binding&&state!=null&&!shown.isEmpty())editRevision=model.draft(shown,input.getText().toString());
            updateSend();
        }));
        input.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_SEND){send();return true;}return false;});
        model.state.observe(this,this::render);
        screenContent.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->arrange(r-l,b-t));
    }
    private void arrange(int width,int height) {
        boolean split=width>=dp(600)&&getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        if(workspace.getChildCount()==0||wide!=split){
            wide=split;workspace.removeAllViews();workspace.setOrientation(LinearLayout.HORIZONTAL);
            workspace.addView(listPane,new LinearLayout.LayoutParams(wide?dp(220):-1,-1));
            workspace.addView(chatPane,new LinearLayout.LayoutParams(wide?0:-1,-1,wide?1:0));
        }
        listPane.setVisibility(wide||shown.isEmpty()?View.VISIBLE:View.GONE);
        chatPane.setVisibility(wide||!shown.isEmpty()?View.VISIBLE:View.GONE);
        boolean ime=keyboardVisible();
        tabs.setVisibility(ime?View.GONE:View.VISIBLE);
        title.setVisibility(ime||!wide?View.GONE:View.VISIBLE);
        historyActions.setVisibility(ime||shown.isEmpty()?View.GONE:View.VISIBLE);
        counter.setVisibility(ime||shown.isEmpty()?View.GONE:View.VISIBLE);
        if(status.getMaxLines()!=(ime?1:2))status.setMaxLines(ime?1:2);
        if(state!=null){
            screenTitle(showFriends?"Friends":(!wide||ime)&&!shown.isEmpty()?state.title:"Chats");
            workspace.setVisibility(state.verified&&!showFriends?View.VISIBLE:View.GONE);
            friendsView.setVisibility(state.verified&&showFriends?View.VISIBLE:View.GONE);
            String notice=showFriends?state.friendsStatus():state.status;
            if(!android.text.TextUtils.equals(status.getText(),notice))status.setText(notice);
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
        if(accountChanged){handledOpen=0;returnToFriends=false;}
        if(next.openedConversation>handledOpen){handledOpen=next.openedConversation;returnToFriends=true;tabs.check(R.id.fluxer_tab_chats);}
        friendsView.render(next);
        if(accountChanged){binding=true;input.setText("");binding=false;search.setText("");messageAdapter.submitList(Collections.emptyList());}
        status.setText(next.status);signIn.setVisibility(!next.verified&&!next.loading?View.VISIBLE:View.GONE);
        workspace.setVisibility(next.verified?View.VISIBLE:View.GONE);
        title.setText(next.title);screenTitle(!wide&&!next.channel.isEmpty()?next.title:"Chats");
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
            if(earlierReady)messages.scrollToPosition(0);
            else if(tail&&!next.past&&!next.messages.isEmpty())messages.scrollToPosition(next.messages.size()-1);
        });
        empty.setText(shown.isEmpty()?"Select a conversation":next.historyBusy?"Loading messages...":"No messages in this window");
        empty.setVisibility(next.messages.isEmpty()?View.VISIBLE:View.GONE);
        historyActions.setVisibility(shown.isEmpty()?View.GONE:View.VISIBLE);
        older.setEnabled(next.more&&!next.historyBusy);latest.setEnabled(!next.historyBusy);
        composer.setVisibility(shown.isEmpty()?View.GONE:View.VISIBLE);counter.setVisibility(shown.isEmpty()?View.GONE:View.VISIBLE);
        arrange(screenContent.getWidth(),screenContent.getHeight());updateSend();
        if(switched){input.clearFocus();((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0);focusContentWhenReady();}
    }
    private void filterChannels() {
        if(state==null)return;String query=search.getText().toString().toLowerCase(Locale.ROOT).trim();List<FluxerChats.Channel> list=new ArrayList<>();
        for(FluxerChats.Channel c:state.channels)if(c.name.toLowerCase(Locale.ROOT).contains(query))list.add(c);
        channelAdapter.submitList(list);
        listEmpty.setText(state.loading?"Loading conversations...":query.isEmpty()?"No conversations":"No matching conversations");
        listEmpty.setVisibility(list.isEmpty()?View.VISIBLE:View.GONE);
    }
    private void updateSend() {boolean ok=state!=null&&state.canSend&&!input.getText().toString().trim().isEmpty();send.setEnabled(ok);send.setAlpha(ok?1f:0.4f);counter.setText(input.length()+" / "+FluxerChats.TEXT_LIMIT);}
    private void send(){if(send.isEnabled()&&!shown.isEmpty())model.send(shown,editRevision);}
    @Override protected void onStart(){super.onStart();model.resume();}
    @Override protected void onStop(){friendsView.dismissActions();model.pause();super.onStop();}
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
    private RadioButton tab(String label,int id){RadioButton b=new RadioButton(this);b.setId(id);b.setText(label);b.setTextSize(15);b.setTextColor(color(R.color.accent));b.setGravity(Gravity.CENTER);b.setButtonDrawable(null);b.setBackgroundResource(R.drawable.bg_library_tab);return b;}
    @Override protected void screenTitle(CharSequence title){
        if(!title.toString().equals(headerTitle)){headerTitle=title.toString();super.screenTitle(title);}
    }
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private TextView text(int size,int color){TextView t=new TextView(this);t.setTextSize(size);t.setTextColor(color(color));return t;}
    private EditText editor(String hint){EditText e=new EditText(this);e.setTextSize(16);e.setHint(hint);e.setTextColor(color(R.color.text_primary));e.setHintTextColor(color(R.color.text_secondary));e.setPadding(dp(12),dp(8),dp(12),dp(8));return e;}
    private Button command(String label,Runnable action){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(13);b.setTextColor(color(R.color.accent));b.setBackgroundResource(R.drawable.bg_library_tab);b.setPadding(dp(8),0,dp(8),0);b.setOnClickListener(v->action.run());return b;}
    private TextWatcher watcher(Runnable changed){return new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){}public void afterTextChanged(Editable s){changed.run();}};}
    private static String time(String iso){try{return OffsetDateTime.parse(iso).atZoneSameInstant(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd MMM HH:mm",Locale.ENGLISH));}catch(RuntimeException e){return "";}}
    private final class Row extends RecyclerView.ViewHolder {
        final LinearLayout root;final TextView top,body,foot;
        Row(boolean conversation){super(column());root=(LinearLayout)itemView;root.setPadding(dp(12),dp(10),dp(12),dp(10));root.setLayoutParams(new RecyclerView.LayoutParams(-1,-2));
            top=text(13,R.color.accent);body=text(16,R.color.text_primary);foot=text(12,R.color.text_secondary);root.addView(top);root.addView(body);root.addView(foot);
            root.setFocusable(true);root.setBackgroundResource(R.drawable.bg_library_tab);
            if(conversation){root.setMinimumHeight(dp(66));body.setMaxLines(2);body.setEllipsize(android.text.TextUtils.TruncateAt.END);}
        }
    }
    private final class ChannelAdapter extends ListAdapter<FluxerChats.Channel,Row> {
        ChannelAdapter(){super(new DiffUtil.ItemCallback<FluxerChats.Channel>(){
            public boolean areItemsTheSame(FluxerChats.Channel a,FluxerChats.Channel b){return a.id.equals(b.id);}
            public boolean areContentsTheSame(FluxerChats.Channel a,FluxerChats.Channel b){return a.name.equals(b.name)&&a.last.equals(b.last)&&a.group==b.group;}
        });}
        public Row onCreateViewHolder(ViewGroup parent,int type){return new Row(true);}
        public void onBindViewHolder(Row r,int position){FluxerChats.Channel c=getItem(position);r.top.setText(c.group?"GROUP":"DIRECT MESSAGE");r.body.setText(c.name);r.foot.setVisibility(View.GONE);
            r.root.setSelected(c.id.equals(shown));r.root.setOnClickListener(v->{returnToFriends=false;model.select(c.id);});
        }
    }
    private final class MessageAdapter extends ListAdapter<FluxerChats.Message,Row> {
        MessageAdapter(){super(new DiffUtil.ItemCallback<FluxerChats.Message>(){
            public boolean areItemsTheSame(FluxerChats.Message a,FluxerChats.Message b){return a.id.equals(b.id);}
            public boolean areContentsTheSame(FluxerChats.Message a,FluxerChats.Message b){return a.author.equals(b.author)&&a.text.equals(b.text)&&a.time.equals(b.time)&&a.delivery.equals(b.delivery)&&a.retryable==b.retryable&&a.mine==b.mine;}
        });}
        public Row onCreateViewHolder(ViewGroup parent,int type){return new Row(false);}
        public void onBindViewHolder(Row r,int position){FluxerChats.Message m=getItem(position);r.top.setText(m.mine?"You":m.author);r.body.setText(m.text);
            r.top.setTextColor(color(m.mine?R.color.phone_cyan:R.color.accent));String stamp=time(m.time);r.foot.setText(stamp+(stamp.isEmpty()||m.delivery.isEmpty()?"":"  ")+m.delivery);
            r.foot.setVisibility(stamp.isEmpty()&&m.delivery.isEmpty()?View.GONE:View.VISIBLE);
            boolean pending=!m.delivery.isEmpty()&&!m.delivery.equals("Edited");String channel=shown;
            r.root.setOnClickListener(pending?v->{
                PopupMenu menu=new PopupMenu(FluxerChatsActivity.this,r.root);
                if(m.retryable)menu.getMenu().add("Retry sending").setOnMenuItemClickListener(i->{model.retry(channel,m.id);return true;});
                menu.getMenu().add("Check delivery").setOnMenuItemClickListener(i->{model.latest();return true;});
                if(!m.delivery.equals("Sending..."))menu.getMenu().add("Dismiss local notice").setOnMenuItemClickListener(i->{new androidx.appcompat.app.AlertDialog.Builder(FluxerChatsActivity.this)
                        .setTitle("Dismiss local notice?").setMessage("This does not delete a message already delivered to Fluxer.")
                        .setNegativeButton("Cancel",null).setPositiveButton("Dismiss",(d,w)->model.discard(channel,m.id)).show();return true;});
                menu.show();
            }:null);
            r.root.setClickable(pending);
        }
    }
}
