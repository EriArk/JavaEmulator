/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.ui.AppPalette;
import ru.playsoftware.j2meloader.ui.ConsoleSurface;
import ru.playsoftware.j2meloader.input.GameMenuDialog;

/** Friends share the chat account/actor, rather than maintaining a second contact graph. */
final class FluxerFriendsView extends LinearLayout {
    private final FluxerChatsModel model;
    private final EditText search;
    private final Spinner filter;
    private final ImageButton add;
    private final TextView empty;
    private final LinearLayout tools;
    private boolean compactTools;
    private final People adapter=new People();
    private FluxerChats.State state;
    private GameMenuDialog dialog;

    FluxerFriendsView(Context context,FluxerChatsModel model) {
        super(context);this.model=model;setOrientation(VERTICAL);setId(R.id.fluxer_friends_panel);
        tools=new LinearLayout(context);tools.setGravity(Gravity.CENTER_VERTICAL);
        filter=new Spinner(context);filter.setId(R.id.fluxer_friends_filter);
        filter.setContentDescription("Contact filter");surface(filter,ConsoleSurface.Kind.KEY);
        filter.setAdapter(new ArrayAdapter<String>(context,android.R.layout.simple_spinner_item,new String[]{"Friends","Requests","Blocked"}){
            private View item(int p,boolean dropdown){
                TextView text=label(15,R.color.text_primary);text.setText(getItem(p));text.setGravity(Gravity.CENTER_VERTICAL);
                text.setPadding(dp(14),dp(6),dp(12),dp(6));text.setMinHeight(dp(48));
                if(dropdown)text.setBackgroundColor(color(R.color.background));
                else{android.graphics.drawable.Drawable arrow=context.getDrawable(R.drawable.ic_arrow_down).mutate();arrow.setTint(color(R.color.accent));arrow.setBounds(0,0,dp(20),dp(20));text.setCompoundDrawablesRelative(null,null,arrow,null);}
                return text;
            }
            @Override public View getView(int p,View v,ViewGroup parent){return item(p,false);}
            @Override public View getDropDownView(int p,View v,ViewGroup parent){return item(p,true);}
        });
        tools.addView(filter,new LayoutParams(0,dp(48),1));
        add=new ImageButton(context);add.setId(R.id.fluxer_friends_add);add.setImageResource(android.R.drawable.ic_input_add);
        add.setColorFilter(color(R.color.accent));add.setContentDescription("Add friend");add.setTooltipText("Add friend");surface(add,ConsoleSurface.Kind.KEY);
        tools.addView(add,new LayoutParams(dp(48),dp(48)));addView(tools);add.setOnClickListener(v->addDialog());
        search=new EditText(context);search.setId(R.id.fluxer_friends_search);search.setHint("Search people");search.setSingleLine(true);
        search.setTextSize(16);search.setTextColor(color(R.color.text_primary));search.setHintTextColor(color(R.color.text_secondary));
        search.setPadding(dp(12),0,dp(12),0);search.setSaveEnabled(false);search.setImportantForAutofill(IMPORTANT_FOR_AUTOFILL_NO);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH|EditorInfo.IME_FLAG_NO_EXTRACT_UI|EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        addView(search,new LayoutParams(-1,dp(48)));
        FrameLayout area=new FrameLayout(context);addView(area,new LayoutParams(-1,0,1));
        area.setBackground(new ConsoleSurface(context,ConsoleSurface.Kind.SCREEN));
        RecyclerView list=new RecyclerView(context);list.setId(R.id.fluxer_friends_list);list.setLayoutManager(new LinearLayoutManager(context));list.setAdapter(adapter);
        area.addView(list,new FrameLayout.LayoutParams(-1,-1));empty=label(16,R.color.text_secondary);empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(16),dp(8),dp(16),dp(8));area.addView(empty,new FrameLayout.LayoutParams(-1,-1));
        filter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> p,View v,int pos,long id){renderList();}public void onNothingSelected(AdapterView<?> p){}
        });
        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){}public void afterTextChanged(Editable e){renderList();}
        });
    }
    void arrange(int width,boolean compact){
        boolean inline=compact&&width>=dp(440);
        if(inline==compactTools)return;
        compactTools=inline;((ViewGroup)search.getParent()).removeView(search);
        filter.setLayoutParams(new LayoutParams(inline?dp(150):0,dp(48),inline?0:1));
        if(inline)tools.addView(search,1,new LayoutParams(0,dp(48),1));
        else addView(search,1,new LayoutParams(-1,dp(48)));
    }
    void render(FluxerChats.State next) {
        if(state!=null&&!state.account.equals(next.account)){
            dismissActions();search.setText("");filter.setSelection(0);
        }
        boolean changed=state==null||state.friendsReady!=next.friendsReady;
        state=next;add.setEnabled(next.friendsReady);add.setAlpha(next.friendsReady?1f:0.4f);renderList();
        if(changed)adapter.notifyItemRangeChanged(0,adapter.getItemCount(),"enabled");
    }
    void dismissActions(){GameMenuDialog old=dialog;dialog=null;if(old!=null)old.dismiss();}
    private GameMenuDialog menu(String title){dismissActions();GameMenuDialog m=new GameMenuDialog(getContext(),title);dialog=m;m.useEdgePanel();m.show();m.page(title);m.setOnDismissListener(d->{if(dialog==m)dialog=null;});return m;}
    private void renderList(){
        if(state==null)return;
        String query=search.getText().toString().trim().toLowerCase(Locale.ROOT);int section=filter.getSelectedItemPosition();
        List<FluxerChats.Friend> rows=new ArrayList<>();
        for(FluxerChats.Friend f:state.friends)if((section==0?f.type==1:section==1?f.type==3||f.type==4:f.type==2)
                &&(f.name+" "+f.tag).toLowerCase(Locale.ROOT).contains(query))rows.add(f);
        adapter.submitList(rows);
        empty.setText(state.friendsBusy?"Loading contacts...":!query.isEmpty()?"No matching people":section==0?"No friends yet":section==1?"No requests":"No blocked accounts");
        empty.setVisibility(rows.isEmpty()?VISIBLE:GONE);
    }
    private void addDialog(){
        if(state==null||!state.friendsReady)return;String account=state.account;
        GameMenuDialog m=menu("Add friend");
        EditText tag=m.textInput("username#1234","");tag.setId(R.id.fluxer_friend_tag);tag.setSingleLine(true);tag.setHint("username#1234");tag.setTextSize(16);
        tag.setTextColor(color(R.color.text_primary));tag.setHintTextColor(color(R.color.text_secondary));
        tag.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        tag.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(37)});tag.setSaveEnabled(false);
        tag.setImportantForAutofill(IMPORTANT_FOR_AUTOFILL_NO);tag.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI|EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        tag.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){}
            public void afterTextChanged(Editable e){if(tag.getError()!=null)tag.setError(null);}
        });
        m.action("Send request",android.R.drawable.ic_menu_send,()->{
            String value=tag.getText().toString().trim();if(!value.matches("[A-Za-z0-9_]{1,32}#[0-9]{4}")){tag.setError("Enter username#1234");return;}
            model.addFriend(account,value);dismissActions();
        });
        m.action("Cancel",android.R.drawable.ic_menu_close_clear_cancel,this::dismissActions);
    }
    private void actions(View anchor,FluxerChats.Friend f){
        if(state==null||!state.friendsReady)return;String account=state.account;menu(f.name).message(f.tag+" / "+f.kind());
        if(f.type==1){item("Message",account,f,FluxerChats.FriendAction.MESSAGE,false);item("Remove friend",account,f,FluxerChats.FriendAction.REMOVE,true);}
        if(f.type==3){item("Accept request",account,f,FluxerChats.FriendAction.ACCEPT,false);item("Decline request",account,f,FluxerChats.FriendAction.DECLINE,true);}
        if(f.type==4)item("Cancel request",account,f,FluxerChats.FriendAction.CANCEL,true);
        if(f.type==2)item("Unblock",account,f,FluxerChats.FriendAction.UNBLOCK,true);
        else item("Block",account,f,FluxerChats.FriendAction.BLOCK,true);
    }
    private void item(String name,String account,FluxerChats.Friend f,FluxerChats.FriendAction action,boolean confirm){
        dialog.action(name,action==FluxerChats.FriendAction.MESSAGE?R.drawable.ic_library_messages_square:android.R.drawable.ic_menu_edit,()->{
            if(confirm){GameMenuDialog m=menu(name+"?");m.message(f.name+"\n"+f.tag);
                m.action("Cancel",android.R.drawable.ic_menu_close_clear_cancel,this::dismissActions);
                m.action(name,android.R.drawable.ic_menu_delete,()->{dismissActions();model.friendAction(account,f,action);});}
            else {dismissActions();model.friendAction(account,f,action);}
        });
    }
    private void surface(View view,ConsoleSurface.Kind kind){view.setBackgroundTintList(null);view.setStateListAnimator(null);view.setDefaultFocusHighlightEnabled(false);view.setBackground(new ConsoleSurface(getContext(),kind));}
    private TextView label(int size,int tint){TextView v=new TextView(getContext());v.setTextSize(size);v.setTextColor(color(tint));return v;}
    private int color(int id){return AppPalette.color(getContext(),id);}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private final class PersonRow extends RecyclerView.ViewHolder {
        final LinearLayout root;final TextView name,tag,kind;
        PersonRow(){super(new LinearLayout(getContext()));root=(LinearLayout)itemView;root.setOrientation(VERTICAL);
            root.setPadding(dp(16),dp(12),dp(16),dp(12));root.setLayoutParams(new RecyclerView.LayoutParams(-1,-2));surface(root,ConsoleSurface.Kind.ROW);root.setFocusable(true);
            name=label(17,R.color.text_primary);tag=label(14,R.color.text_secondary);kind=label(12,R.color.accent);
            name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);tag.setSingleLine(true);tag.setEllipsize(android.text.TextUtils.TruncateAt.END);
            root.addView(name);root.addView(tag);root.addView(kind);
        }
    }
    private final class People extends ListAdapter<FluxerChats.Friend,PersonRow> {
        People(){super(new DiffUtil.ItemCallback<FluxerChats.Friend>(){
            public boolean areItemsTheSame(FluxerChats.Friend a,FluxerChats.Friend b){return a.id.equals(b.id);}
            public boolean areContentsTheSame(FluxerChats.Friend a,FluxerChats.Friend b){return a.same(b)&&a.name.equals(b.name)&&a.tag.equals(b.tag);}
        });}
        public PersonRow onCreateViewHolder(ViewGroup parent,int type){return new PersonRow();}
        public void onBindViewHolder(PersonRow row,int position){FluxerChats.Friend f=getItem(position);row.name.setText(f.name);row.tag.setText(f.tag);row.kind.setText(f.kind());
            row.root.setEnabled(state!=null&&state.friendsReady);row.root.setOnClickListener(v->actions(row.root,f));
        }
    }
}
