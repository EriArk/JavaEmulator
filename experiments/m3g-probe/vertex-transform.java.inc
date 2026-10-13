        // SPDX-License-Identifier: GPL-3.0-or-later
        // Read packed vertices and transform only the defined output range.
        float[] m=this.matrix;
        float w=W ? 1.0f : 0.0f;
        boolean bytes=in.getComponentType()==1;
        byte[] b=in.vertArrayByteSize;
        short[] sh=in.vertArrayShortSize;
        for (int i=0, src=0, dst=0; i<vertexCount; i++,src+=vertexDims,dst+=4) {
            float x=bytes ? b[src] : sh[src];
            float y=bytes ? b[src+1] : sh[src+1];
            float z=vertexDims==2 ? 0.0f : bytes ? b[src+2] : sh[src+2];
            out[dst]=m[0]*x+m[1]*y+m[2]*z+m[3]*w;
            out[dst+1]=m[4]*x+m[5]*y+m[6]*z+m[7]*w;
            out[dst+2]=m[8]*x+m[9]*y+m[10]*z+m[11]*w;
            out[dst+3]=m[12]*x+m[13]*y+m[14]*z+m[15]*w;
        }
