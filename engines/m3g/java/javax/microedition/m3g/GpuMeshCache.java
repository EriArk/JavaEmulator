/* SPDX-License-Identifier: GPL-3.0-or-later */
package javax.microedition.m3g;

import java.util.LinkedHashMap;
import java.util.Map;
import static android.opengl.GLES30.*;

/** GL objects belong exclusively to the owning rasterizer/context. Replay owns CPU snapshots. */
final class GpuMeshCache {
    static boolean enabled=true;
    static int byteLimit=4*1024*1024;
    static long uploads, hits, uploadedBytes, indexedDraws;
    private final LinkedHashMap<GpuGeometry.Mesh,Entry> entries=new LinkedHashMap<>(16,.75f,true);
    private int bytes;

    private static final class Entry {
        final int[] buffers=new int[2], array=new int[1];
        final int bytes;
        Entry(GpuGeometry.Mesh mesh) {
            bytes=mesh.bytes();
            glGenVertexArrays(1,array,0); glBindVertexArray(array[0]);
            glGenBuffers(2,buffers,0); glBindBuffer(GL_ARRAY_BUFFER,buffers[0]);
            glBufferData(GL_ARRAY_BUFFER,mesh.vertices.remaining(),mesh.vertices,GL_STATIC_DRAW);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER,buffers[1]);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER,mesh.indices.remaining(),mesh.indices,GL_STATIC_DRAW);
            for(int i=0;i<8;i++) glEnableVertexAttribArray(i);
            glVertexAttribPointer(0,3,GL_FLOAT,false,GpuGeometry.Mesh.STRIDE,0);
            glVertexAttribPointer(1,4,GL_FLOAT,false,GpuGeometry.Mesh.STRIDE,12);
            for(int i=0;i<4;i++) glVertexAttribPointer(2+i,3,GL_FLOAT,false,GpuGeometry.Mesh.STRIDE,28+i*12);
            glVertexAttribPointer(6,1,GL_FLOAT,false,GpuGeometry.Mesh.STRIDE,88);
            glVertexAttribPointer(7,3,GL_FLOAT,false,GpuGeometry.Mesh.STRIDE,76);
            uploads++; uploadedBytes+=bytes;
        }
        void delete() { glDeleteVertexArrays(1,array,0); glDeleteBuffers(2,buffers,0); }
    }

    int bytes() { return bytes; }
    void draw(GpuGeometry.Mesh mesh,int count) {
        int limit=enabled?Math.max(0,byteLimit):0;
        boolean retain=mesh.bytes()<=limit;
        while(!entries.isEmpty() && (bytes>limit || !entries.containsKey(mesh)
                && retain && (entries.size()>=GpuGeometry.CACHE_ENTRIES || bytes+mesh.bytes()>limit))) {
            Map.Entry<GpuGeometry.Mesh,Entry> oldest=entries.entrySet().iterator().next();
            Entry entry=oldest.getValue(); entries.remove(oldest.getKey()); bytes-=entry.bytes; entry.delete();
        }
        Entry entry=entries.get(mesh);
        if(entry==null) {
            entry=new Entry(mesh);
            if(entry.bytes<=limit) { entries.put(mesh,entry); bytes+=entry.bytes; }
        } else { hits++; glBindVertexArray(entry.array[0]); }
        glDrawElements(GL_TRIANGLES,count,GL_UNSIGNED_SHORT,0); indexedDraws++;
        if(entry.bytes>limit) entry.delete();
    }
}
