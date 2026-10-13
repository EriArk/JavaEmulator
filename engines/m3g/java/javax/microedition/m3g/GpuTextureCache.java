/* SPDX-License-Identifier: GPL-3.0-or-later */
package javax.microedition.m3g;

import static android.opengl.GLES30.*;

/** Per-unit LRU ownership prevents eviction or sampler changes in another bound unit. */
final class GpuTextureCache {
    static final int UNIT_BYTES=2*1024*1024, UNIT_ENTRIES=32;
    static long hits, evictions;
    private final Entry[][] entries=new Entry[4][UNIT_ENTRIES];
    private final Entry[] scratch=new Entry[4];
    private final int[] bytes=new int[4];
    private final int byteLimit;
    private long clock;

    static final class Entry {
        final int id;
        final Image2D base;
        final Image2D[] images=new Image2D[12];
        final long[] revisions=new long[12];
        int samplerKey=-1, bytes;
        long used;
        Entry(Image2D base) {
            this.base=base;
            int[] id=new int[1]; glGenTextures(1,id,0); this.id=id[0];
        }
    }

    GpuTextureCache(int byteLimit) { this.byteLimit=byteLimit; }

    Entry select(int unit,GpuFrame.Texture texture,boolean enabled) {
        Image2D base=texture.levels[0].source;
        int needed=0;
        for(GpuFrame.Image level:texture.levels) needed+=level.width*level.height*4;
        if(!enabled || needed>byteLimit) {
            // Oversized entries are transient; the off switch retains the old single-slot path.
            if(scratch[unit]==null || enabled && scratch[unit].base!=base) {
                destroy(scratch[unit]); scratch[unit]=new Entry(base);
            }
            return scratch[unit];
        }
        Entry selected=null;
        for(Entry entry:entries[unit]) if(entry!=null && entry.base==base) { selected=entry; break; }
        if(selected!=null) {
            hits++;
            // Smaller mip chains do not free driver storage. Account for the high-water mark.
            needed=Math.max(needed,selected.bytes)-selected.bytes;
        }
        while(bytes[unit]+needed>byteLimit || selected==null && count(unit)==UNIT_ENTRIES) {
            int oldest=-1;
            for(int i=0;i<UNIT_ENTRIES;i++) {
                Entry entry=entries[unit][i];
                if(entry!=null && entry!=selected && (oldest<0 || entry.used<entries[unit][oldest].used)) oldest=i;
            }
            if(oldest<0) throw new IllegalStateException("Texture cache accounting");
            Entry victim=entries[unit][oldest]; bytes[unit]-=victim.bytes;
            destroy(victim); entries[unit][oldest]=null; evictions++;
        }
        if(selected==null) {
            selected=new Entry(base);
            for(int i=0;i<UNIT_ENTRIES;i++) if(entries[unit][i]==null) { entries[unit][i]=selected; break; }
        }
        selected.bytes+=needed; bytes[unit]+=needed; selected.used=++clock;
        return selected;
    }

    int bytes() { int total=0; for(int value:bytes) total+=value; return total; }
    private int count(int unit) { int total=0; for(Entry entry:entries[unit]) if(entry!=null) total++; return total; }
    private static void destroy(Entry entry) { if(entry!=null) glDeleteTextures(1,new int[]{entry.id},0); }
}
