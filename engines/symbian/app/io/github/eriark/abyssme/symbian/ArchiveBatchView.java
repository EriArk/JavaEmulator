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
        setOrientation(VERTICAL); setPadding(dp(10),dp(8),dp(10),dp(8)); setBackgroundColor(ru.playsoftware.j2meloader.ui.AppPalette.color(getContext(), ru.playsoftware.j2meloader.R.color.background));
        LinearLayout header=new LinearLayout(context); header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(icon("Back",android.R.drawable.ic_media_previous,host::close),new LayoutParams(dp(44),dp(44)));
        TextView title=text("Archive import",20); header.addView(title,new LayoutParams(0,-2,1));
        header.addView(icon("Remove queue",android.R.drawable.ic_menu_delete,host::remove),new LayoutParams(dp(44),dp(44))); addView(header);
        TextView name=text(batch.name,13); name.setMaxLines(1); name.setEllipsize(android.text.TextUtils.TruncateAt.END); addView(name);
        status=text("",13); status.setMaxLines(2); addView(status);
        progress=new ProgressBar(context,null,android.R.attr.progressBarStyleHorizontal); progress.setIndeterminate(true);
        progress.setIndeterminateTintList(ColorStateList.valueOf(ru.playsoftware.j2meloader.ui.AppPalette.color(getContext(), ru.playsoftware.j2meloader.R.color.accent))); progress.setVisibility(GONE); addView(progress,new LayoutParams(-1,dp(4)));
        RecyclerView list=new RecyclerView(context); list.setLayoutManager(new LinearLayoutManager(context));
        list.setContentDescription("Archive files"); list.setAdapter(rows); addView(list,new LayoutParams(-1,0,1));
        LinearLayout footer=new LinearLayout(context); footer.setGravity(Gravity.CENTER_VERTICAL);
        all=new CheckBox(context); all.setText("Select all"); all.setTextColor(ru.playsoftware.j2meloader.ui.AppPalette.color(getContext(), ru.playsoftware.j2meloader.R.color.text_primary)); all.setButtonTintList(ColorStateList.valueOf(ru.playsoftware.j2meloader.ui.AppPalette.color(getContext(), ru.playsoftware.j2meloader.R.color.accent)));
        all.setVisibility(batch.started ? GONE : VISIBLE);
        all.setOnCheckedChangeListener((v,value)-> { if(binding) return; for(ArchiveBatch.Item item:batch.items) if(item.problem.isEmpty()) item.selected=value; list.getAdapter().notifyDataSetChanged(); update(); host.changed(); });
        footer.addView(all,new LayoutParams(0,dp(48),1));
        if(batch.started) { others=button("Continue others",host::others); footer.addView(others,new LayoutParams(0,dp(48),1)); }
        start=button("Import",()-> { if(finished())host.done();else host.start(); }); footer.addView(start,new LayoutParams(0,dp(48),1)); addView(footer); update();
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
    private TextView text(String s,int size) {
        TextView v=new TextView(getContext()); v.setText(s); v.setTextSize(size); v.setTextColor(ru.playsoftware.j2meloader.ui.AppPalette.color(getContext(), ru.playsoftware.j2meloader.R.color.text_primary)); v.setPadding(dp(6),dp(3),dp(6),dp(3)); return v;
    }
    private Button button(String label,Runnable run) {
        Button b=new Button(getContext()); b.setText(label); b.setAllCaps(false); b.setTextSize(14); b.setTextColor(ru.playsoftware.j2meloader.ui.AppPalette.color(getContext(), ru.playsoftware.j2meloader.R.color.text_primary));
        b.setBackgroundResource(R.drawable.bg_quick_setting_button); b.setOnClickListener(v->run.run()); return b;
    }
    private ImageButton icon(String label,int resource,Runnable run) {
        ImageButton b=new ImageButton(getContext()); b.setImageResource(resource); b.setImageTintList(ColorStateList.valueOf(ru.playsoftware.j2meloader.ui.AppPalette.color(getContext(), ru.playsoftware.j2meloader.R.color.accent)));
        b.setBackgroundResource(R.drawable.bg_quick_setting_button); b.setContentDescription(label); b.setTooltipText(label); b.setOnClickListener(v->run.run()); return b;
    }
    private void update() {
        int selected=0,valid=0,done=0,failed=0,waiting=0;
        for(ArchiveBatch.Item item:batch.items) {
            if(item.problem.isEmpty()) {valid++;if(item.selected) selected++;}
            if(item.state==ArchiveBatch.State.DONE) done++;
            if(item.state==ArchiveBatch.State.FAILED || !item.problem.isEmpty()) failed++;
            if(item.state==ArchiveBatch.State.WAITING) waiting++;
        }
        status.setText(batch.started ? done+" completed  /  "+waiting+" waiting  /  "+failed+" errors"
                : selected+" selected  /  "+batch.items.size()+" files"+(failed==0 ? "" : "  /  "+failed+" unavailable"));
        start.setText(finished() ? "Done" : batch.started ? "Continue queue" : "Import "+selected); start.setEnabled(batch.started || selected>0);
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
            role=new Spinner(getContext()); ArrayAdapter<String> choices=new ArrayAdapter<String>(getContext(),android.R.layout.simple_spinner_item,new String[]{"Game","Component"}) {
                @Override public View getView(int pos,View old,ViewGroup parent) { TextView v=(TextView)super.getView(pos,old,parent); v.setTextColor(ru.playsoftware.j2meloader.ui.AppPalette.color(getContext(), ru.playsoftware.j2meloader.R.color.accent)); v.setTextSize(13); return v; }
            }; choices.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); role.setAdapter(choices);
            row.addView(role,new LayoutParams(dp(118),dp(48)));
            more=icon("File details",android.R.drawable.ic_menu_more,()->host.row(getBindingAdapterPosition())); row.addView(more,new LayoutParams(dp(42),dp(48)));
        }
    }
    private final class Rows extends RecyclerView.Adapter<Row> {
        @Override public Row onCreateViewHolder(ViewGroup parent,int type) { return new Row(new LinearLayout(getContext())); }
        @Override public int getItemCount() { return batch.items.size(); }
        @Override public void onBindViewHolder(Row row,int position) {
            ArchiveBatch.Item item=batch.items.get(position); row.name.setText(item.label());
            String type=item.role==ArchiveBatch.Role.PLATFORM ? "N-Gage 2.0 installer" : item.kind==null ? "Unsupported" : item.kind==GameImport.Kind.SIS_LEGACY ? "SIS / older phones" : item.kind==GameImport.Kind.SIS_MODERN ? "SIS/SISX" : item.kind==GameImport.Kind.CARD ? "N-Gage card" : "N-Gage 2.0 game";
            String state=item.state.name().toLowerCase(java.util.Locale.ROOT); state=Character.toUpperCase(state.charAt(0))+state.substring(1);
            row.detail.setText(!item.problem.isEmpty() ? item.problem : batch.started ? state+" - "+item.detail : type+"\n"+(item.detail.isEmpty() ? item.name : item.detail));
            row.check.setOnCheckedChangeListener(null); row.check.setChecked(item.selected); row.check.setEnabled(!working && item.problem.isEmpty());
            row.check.setContentDescription("Select "+item.label()); row.check.setVisibility(batch.started ? GONE : VISIBLE);
            row.check.setOnCheckedChangeListener((v,value)->{ item.selected=value; update(); host.changed(); });
            row.role.setVisibility(!batch.started && item.canSave && item.problem.isEmpty() && item.role!=ArchiveBatch.Role.PLATFORM ? VISIBLE : GONE);
            row.role.setEnabled(!working); row.more.setEnabled(!working);
            row.role.setContentDescription("Import as: "+item.label()); row.role.setOnItemSelectedListener(null); row.role.setSelection(item.role==ArchiveBatch.Role.COMPONENT ? 1 : 0,false);
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
