package io.github.eriark.abyssme.symbian;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.*;
import ru.playsoftware.j2meloader.R;

/** Compact scrolling review; row count never changes the footer or viewport dimensions. */
final class ArchiveBatchView extends LinearLayout {
    interface Host {
        void changed(); void row(int id); void start(); void others(); void close(); void remove(); void done();
    }
    final TextView status;
    final ProgressBar progress;
    private final ArchiveBatch.Batch batch;
    private final Host host;
    private final Button start;
    private final CheckBox all;
    private final Rows rows=new Rows();
    private Button others;
    private boolean working;
    private boolean binding;
    ArchiveBatchView(Context context,ArchiveBatch.Batch batch,Host host) {
        super(context); this.batch=batch; this.host=host;
        setOrientation(VERTICAL); setPadding(dp(10),dp(8),dp(10),dp(8));
        ru.playsoftware.j2meloader.ui.ConsoleSurface.apply(this, ru.playsoftware.j2meloader.ui.ConsoleSurface.Kind.CHASSIS);
        LinearLayout header=new LinearLayout(context); header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(icon(context.getString(R.string.system_back),android.R.drawable.ic_media_previous,host::close),new LayoutParams(dp(48),dp(48)));
        TextView title=text(context.getString(R.string.native_queue_title),20); header.addView(title,new LayoutParams(0,-2,1));
        header.addView(icon(context.getString(R.string.native_queue_remove),android.R.drawable.ic_menu_delete,host::remove),new LayoutParams(dp(48),dp(48))); addView(header);
        TextView name=text(batch.selectedFiles?getContext().getString(R.string.native_selected_files):batch.name,13); name.setMaxLines(1); name.setEllipsize(android.text.TextUtils.TruncateAt.END); addView(name);
        status=text("",13); addView(status);
        progress=new ProgressBar(context,null,android.R.attr.progressBarStyleHorizontal); progress.setIndeterminate(true);
        progress.setIndeterminateTintList(ColorStateList.valueOf(ru.playsoftware.j2meloader.ui.AppPalette.color(getContext(), ru.playsoftware.j2meloader.R.color.accent))); progress.setVisibility(GONE); addView(progress,new LayoutParams(-1,dp(4)));
        RecyclerView list=new RecyclerView(context); list.setLayoutManager(new LinearLayoutManager(context));
        ru.playsoftware.j2meloader.ui.ConsoleSurface.apply(list, ru.playsoftware.j2meloader.ui.ConsoleSurface.Kind.SCREEN);
        list.setContentDescription(context.getString(R.string.native_queue_files)); list.setAdapter(rows); addView(list,new LayoutParams(-1,0,1));
        LinearLayout footer=new LinearLayout(context); footer.setGravity(Gravity.CENTER_VERTICAL);
        boolean stacked=compact(); footer.setOrientation(stacked?VERTICAL:HORIZONTAL);
        all=new CheckBox(context); all.setText(context.getString(R.string.native_queue_select_all)); all.setMinHeight(dp(48)); all.setTextColor(ru.playsoftware.j2meloader.ui.AppPalette.color(getContext(), ru.playsoftware.j2meloader.R.color.text_primary)); all.setButtonTintList(ColorStateList.valueOf(ru.playsoftware.j2meloader.ui.AppPalette.color(getContext(), ru.playsoftware.j2meloader.R.color.accent)));
        all.setVisibility(batch.started ? GONE : VISIBLE);
        all.setOnCheckedChangeListener((v,value)-> { if(binding) return; for(ArchiveBatch.Item item:batch.items) if(item.problem.isEmpty()) item.selected=value; list.getAdapter().notifyDataSetChanged(); update(); host.changed(); });
        footer.addView(all,footerParams(stacked));
        if(batch.started) { others=button(context.getString(R.string.native_queue_others),host::others); footer.addView(others,footerParams(stacked)); }
        start=button(context.getString(R.string.native_queue_import),()-> { if(finished())host.done();else host.start(); }); footer.addView(start,footerParams(stacked)); addView(footer); update();
    }
    void working(boolean value) {
        working=value; all.setEnabled(!value); if(others!=null)others.setEnabled(!value);
        start.setEnabled(!value && (batch.started || batch.items.stream().anyMatch(i->i.selected && i.problem.isEmpty())));
        rows.notifyDataSetChanged();
    }
    private boolean finished() {
        return batch.started && batch.items.stream().allMatch(i->i.state==ArchiveBatch.State.DONE || i.state==ArchiveBatch.State.SKIPPED);
    }
    private int dp(int n) { return Math.round(n*getResources().getDisplayMetrics().density); }
    private boolean compact() { return getResources().getConfiguration().screenWidthDp<400 || getResources().getConfiguration().fontScale>=1.3f; }
    private LayoutParams footerParams(boolean stacked) { return new LayoutParams(stacked?-1:0,-2,stacked?0:1); }
    private TextView text(String s,int size) {
        TextView v=new TextView(getContext()); v.setText(s); v.setTextSize(size); v.setTextColor(ru.playsoftware.j2meloader.ui.AppPalette.color(getContext(), ru.playsoftware.j2meloader.R.color.text_primary)); v.setPadding(dp(6),dp(3),dp(6),dp(3)); return v;
    }
    private Button button(String label,Runnable run) {
        Button b=new Button(getContext()); b.setText(label); b.setAllCaps(false); b.setTextSize(14); b.setMinHeight(dp(48)); b.setTextColor(ru.playsoftware.j2meloader.ui.AppPalette.color(getContext(), ru.playsoftware.j2meloader.R.color.text_primary));
        ru.playsoftware.j2meloader.ui.ConsoleSurface.apply(b, ru.playsoftware.j2meloader.ui.ConsoleSurface.Kind.KEY); b.setOnClickListener(v->run.run()); return b;
    }
    private ImageButton icon(String label,int resource,Runnable run) {
        ImageButton b=new ImageButton(getContext()); b.setImageResource(resource); b.setImageTintList(ColorStateList.valueOf(ru.playsoftware.j2meloader.ui.AppPalette.color(getContext(), ru.playsoftware.j2meloader.R.color.accent)));
        ru.playsoftware.j2meloader.ui.ConsoleSurface.apply(b, ru.playsoftware.j2meloader.ui.ConsoleSurface.Kind.KEY); b.setContentDescription(label); b.setTooltipText(label); b.setOnClickListener(v->run.run()); return b;
    }
    private void update() {
        int selected=0,valid=0,done=0,failed=0,waiting=0;
        for(ArchiveBatch.Item item:batch.items) {
            if(item.problem.isEmpty()) {valid++;if(item.selected) selected++;}
            if(item.state==ArchiveBatch.State.DONE) done++;
            if(item.state==ArchiveBatch.State.FAILED || !item.problem.isEmpty()) failed++;
            if(item.state==ArchiveBatch.State.WAITING) waiting++;
        }
        status.setText(batch.started ? getContext().getString(R.string.native_queue_completed_counts,done,waiting,failed)
                : failed==0 ? getContext().getString(R.string.native_queue_selection,selected,batch.items.size())
                : getContext().getString(R.string.native_queue_selection_errors,selected,batch.items.size(),failed));
        start.setText(finished() ? getContext().getString(R.string.native_queue_finish) : batch.started ? getContext().getString(R.string.native_queue_continue) : getContext().getString(R.string.native_queue_import_count,selected)); start.setEnabled(!working && (batch.started || selected>0));
        if(others!=null)others.setVisibility(waiting>0 ? VISIBLE : GONE);
        binding=true; all.setChecked(valid>0 && selected==valid); binding=false;
    }
    private final class Row extends RecyclerView.ViewHolder {
        final CheckBox check; final TextView name,detail; final Spinner role; final ImageButton more;
        Row(LinearLayout row) {
            super(row); row.setLayoutParams(new RecyclerView.LayoutParams(-1,-2)); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(0,dp(5),0,dp(5)); row.setMinimumHeight(dp(72));
            check=new CheckBox(getContext()); check.setButtonTintList(ColorStateList.valueOf(ru.playsoftware.j2meloader.ui.AppPalette.color(getContext(), ru.playsoftware.j2meloader.R.color.accent))); row.addView(check,new LayoutParams(dp(42),dp(48)));
            LinearLayout labels=new LinearLayout(getContext()); labels.setOrientation(VERTICAL); name=text("",15); name.setMaxLines(2);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END); detail=text("",12); detail.setTextColor(ru.playsoftware.j2meloader.ui.AppPalette.color(getContext(), ru.playsoftware.j2meloader.R.color.text_secondary)); detail.setMaxLines(2); detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
            labels.addView(name); labels.addView(detail); row.addView(labels,new LayoutParams(0,-2,1));
            role=new Spinner(getContext()); ArrayAdapter<String> choices=new ArrayAdapter<String>(getContext(),android.R.layout.simple_spinner_item,getResources().getStringArray(R.array.native_queue_roles)) {
                @Override public View getView(int pos,View old,ViewGroup parent) { TextView v=(TextView)super.getView(pos,old,parent); v.setTextColor(ru.playsoftware.j2meloader.ui.AppPalette.color(getContext(), ru.playsoftware.j2meloader.R.color.accent)); v.setTextSize(13); v.setSingleLine(false); v.setEllipsize(null); return v; }
            }; choices.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); role.setAdapter(choices);
            if(compact()) labels.addView(role,new LayoutParams(-1,-2));
            else row.addView(role,new LayoutParams(dp(140),-2));
            role.setMinimumHeight(dp(48));
            more=icon(getContext().getString(R.string.native_queue_details),android.R.drawable.ic_menu_more,()->host.row(getBindingAdapterPosition())); row.addView(more,new LayoutParams(dp(48),dp(48)));
        }
    }
    private final class Rows extends RecyclerView.Adapter<Row> {
        @Override public Row onCreateViewHolder(ViewGroup parent,int type) { return new Row(new LinearLayout(getContext())); }
        @Override public int getItemCount() { return batch.items.size(); }
        @Override public void onBindViewHolder(Row row,int position) {
            ArchiveBatch.Item item=batch.items.get(position); row.name.setText(item.label());
            String type=SystemTransferText.archiveType(getContext(),item);
            String state=SystemTransferText.archiveState(getContext(),item.state);
            String detail=SystemTransferText.detail(getContext(),item);
            row.detail.setText(!item.problem.isEmpty() ? SystemTransferText.problem(getContext(),item) : batch.started ? detail.isEmpty()?state:getContext().getString(R.string.native_queue_detail,state,detail) : getContext().getString(R.string.native_queue_type_detail,type,detail.isEmpty() ? item.name : detail));
            row.check.setOnCheckedChangeListener(null); row.check.setChecked(item.selected); row.check.setEnabled(!working && item.problem.isEmpty());
            row.check.setContentDescription(getContext().getString(R.string.native_queue_select_item,item.label())); row.check.setVisibility(batch.started ? GONE : VISIBLE);
            row.check.setOnCheckedChangeListener((v,value)->{ item.selected=value; update(); host.changed(); });
            row.role.setVisibility(!batch.started && item.canSave && item.problem.isEmpty() && item.role!=ArchiveBatch.Role.PLATFORM ? VISIBLE : GONE);
            row.role.setEnabled(!working); row.more.setEnabled(!working);
            row.role.setContentDescription(getContext().getString(R.string.native_queue_import_as,item.label())); row.role.setOnItemSelectedListener(null); row.role.setSelection(item.role==ArchiveBatch.Role.COMPONENT ? 1 : 0,false);
            if(row.role.getVisibility()==VISIBLE) row.role.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                public void onNothingSelected(AdapterView<?> p) { }
                public void onItemSelected(AdapterView<?> p,View v,int selected,long id) {
                    ArchiveBatch.Role next=selected==1 ? ArchiveBatch.Role.COMPONENT : ArchiveBatch.Role.GAME;
                    if(item.role!=next) {item.role=next; host.changed();}
                }
            });
        }
    }
}
