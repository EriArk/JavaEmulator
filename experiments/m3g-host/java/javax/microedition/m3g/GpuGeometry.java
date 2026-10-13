/* SPDX-License-Identifier: GPL-3.0-or-later */
package javax.microedition.m3g;

import java.util.ArrayList;
import java.util.Arrays;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.recompile.mobile.Mobile;
import static android.opengl.GLES30.*;

/** Opt-in hardware vertex stage. Unsupported states keep the prepared-triangle path. */
final class GpuGeometry {
    static boolean enabled = ru.playsoftware.j2meloader.BuildConfig.M3G_GPU_GEOMETRY;
    static long draws, vertices;
    static long cacheHits, cacheMisses;
    static final int CACHE_BYTES=4*1024*1024;
    private static final ArrayList<Geometry> cache=new ArrayList<>();
    private static int cachedBytes;
    private static float[] staging = new float[0];

    private static final class Geometry {
        final VertexBuffer buffer;
        final IndexBuffer indices;
        final VertexArray[] attributes;
        final long[] revisions;
        final int[] order;
        final int color;
        final ByteBuffer data;
        Geometry(VertexBuffer buffer,IndexBuffer indices,VertexArray[] attributes,int[] order,ByteBuffer data) {
            this.buffer=buffer;this.indices=indices;this.attributes=attributes;this.data=data;
            this.order=order.clone(); color=buffer.getDefaultColor(); revisions=new long[attributes.length];
            for(int i=0;i<attributes.length;i++) revisions[i]=attributes[i]==null?0:attributes[i].revision;
        }
        boolean matches(VertexArray[] arrays,int[] currentOrder,int currentColor) {
            if(color!=currentColor || !Arrays.equals(order,currentOrder)) return false;
            for(int i=0;i<arrays.length;i++) if(arrays[i]!=attributes[i] || (arrays[i]!=null && arrays[i].revision!=revisions[i])) return false;
            return true;
        }
        int bytes() { return data.capacity()+order.length*4; }
    }

    static int cachedBytes() { return cachedBytes; }
    static void clear() { cache.clear();cachedBytes=0;staging=new float[0]; }

    static final class State {
        final float[] clip = new float[16], eye = new float[16], normal = new float[16];
        final float[] texture = new float[48], viewport = new float[4];
        final float[] material = new float[16], light = new float[8*20];
        int culling, winding, lights;
        boolean lit, tracking, local, perspective;
        float near, far, shininess;
    }

    static boolean draw(VertexBuffer vb, IndexBuffer ib, Appearance appearance, Transform model,
            Transform camera, Transform projection, ArrayList<Light> lights, ArrayList<Transform> lightTransforms,
            int scope, boolean perspective, Fog fog, boolean parallel, int[] pixels, short[] depth,
            int w, int h, int ox, int oy, int vw, int vh, int left, int top, int right, int bottom,
            float near, float far, boolean depthTest, int alpha, boolean resident) {
        PolygonMode polygon=appearance.getPolygonMode();
        // Flat provoking vertices and two-sided colors need separate interpolation contracts.
        if(polygon!=null && (polygon.getShading()==PolygonMode.SHADE_FLAT || polygon.isTwoSidedLightingEnabled())) return false;
        CompositingMode comp=appearance.getCompositingMode();
        if(comp==null) comp=new CompositingMode();
        if(depthTest && (comp.getDepthOffsetFactor()!=0 || comp.getDepthOffsetUnits()!=0)) return false;
        if(vw<=0 || vh<=0 || w<=0 || h<=0 || w>2048 || h>2048 || near<0 || far>1 || !Float.isFinite(near+far)) return false;
        float[] sb=new float[4]; VertexArray position=vb.getPositions(sb), normals=vb.getNormals(), colors=vb.getColors();
        if(position==null || position.getComponentCount()!=3) return false;
        int[] indices=ib.getIndexArray();
        if(indices.length==0 || indices.length%3!=0 || indices.length>65536*3) return false;
        if((long)indices.length*80+4096>GpuRasterizer.frame.byteLimit) return false;
        for(int index:indices) if(index<0 || index>=position.getVertexCount()) return false;
        Material material=appearance.getMaterial();
        State state=new State();
        state.lit=normals!=null && material!=null && lights!=null;
        state.culling=polygon==null?PolygonMode.CULL_BACK:polygon.getCulling();
        state.winding=polygon==null?PolygonMode.WINDING_CCW:polygon.getWinding();
        state.local=polygon!=null && polygon.isLocalCameraLightingEnabled();
        state.perspective=perspective; state.near=near; state.far=far;
        state.viewport[0]=ox; state.viewport[1]=oy; state.viewport[2]=vw; state.viewport[3]=vh;
        Transform eye=new Transform();
        if(camera!=null) eye.postMultiply(camera);
        if(model!=null) eye.postMultiply(model);
        Transform normal=new Transform(eye);
        try { normal.invert(); normal.transpose(); } catch(ArithmeticException ignored) { normal.setIdentity(); }
        matrix(normal,state.normal,0);
        eye.postTranslate(sb[1],sb[2],sb[3]); eye.postScale(sb[0],sb[0],sb[0]);
        matrix(eye,state.eye,0);
        Transform clip=new Transform(projection); clip.postMultiply(eye); matrix(clip,state.clip,0);
        if(!finite(state.clip) || !finite(state.eye) || !finite(state.normal)) return false;
        if(state.lit) {
            state.tracking=material.isVertexColorTrackingEnabled(); state.shininess=material.getShininess();
            int[] properties={Material.AMBIENT,Material.DIFFUSE,Material.SPECULAR,Material.EMISSIVE};
            for(int i=0;i<4;i++) color(material.getColor(properties[i]),state.material,i*4,1f/255);
            for(int i=0;i<lights.size();i++) {
                Light light=lights.get(i);
                if(light==null || !light.isRenderingEnabled() || (light.getScope()&scope)==0) continue;
                if(state.lights==8) return false;
                int offset=state.lights++*20;
                color(light.getColor(),state.light,offset,light.getIntensity()/255);
                state.light[offset+3]=light.getMode();
                Transform lt=new Transform(); if(camera!=null) lt.postMultiply(camera);
                Transform world=lightTransforms.get(i); if(world!=null) lt.postMultiply(world);
                float[] p={0,0,0,1,0,0,-1,0}; lt.transform(p);
                System.arraycopy(p,0,state.light,offset+4,3);
                float len=(float)Math.sqrt(p[4]*p[4]+p[5]*p[5]+p[6]*p[6]);
                for(int j=0;j<3;j++) state.light[offset+8+j]=len>0?p[4+j]/len:0;
                state.light[offset+11]=(float)Math.cos(Math.toRadians(light.getSpotAngle()));
                state.light[offset+12]=light.getConstantAttenuation();
                state.light[offset+13]=light.getLinearAttenuation();
                state.light[offset+14]=light.getQuadraticAttenuation();
                state.light[offset+15]=light.getSpotExponent();
            }
            if(!finite(state.light) || !finite(state.material) || !Float.isFinite(state.shininess)) return false;
        }
        Texture2D[] textures=new Texture2D[3]; VertexArray[] uv=new VertexArray[3];
        Graphics3D.TextureFilter[] filters=new Graphics3D.TextureFilter[3];
        Graphics3DPipelines.MipmapMode[] mip=new Graphics3DPipelines.MipmapMode[3];
        int units=0;
        if(!Mobile.M3GRenderUntexturedPolygons) for(int i=0;i<4;i++) {
            Texture2D texture=appearance.getTexture(i);
            VertexArray coords=texture==null?null:vb.getTexCoords(i,sb);
            if(coords==null) continue;
            // Attribute 5 carries the normal; the four-texture case uses the old path.
            if(units==3 || coords.getComponentCount()>3 || texture.getImage()==null) return false;
            uv[units]=coords; textures[units]=texture;
            Transform t=new Transform(); texture.getCompositeTransform(t);
            Transform scale=new Transform(); scale.postScale(texture.getImage().getWidth(),texture.getImage().getHeight(),1);
            scale.postMultiply(t); scale.postTranslate(sb[1],sb[2],sb[3]); scale.postScale(sb[0],sb[0],sb[0]);
            matrix(scale,state.texture,units*16);
            boolean linear=Mobile.m3gBilinearFilterMode==Graphics3D.MODE_FORCE_ENABLE || (Mobile.m3gBilinearFilterMode==Graphics3D.MODE_APP_CONTROLLED && texture.getImageFilter()==Texture2D.FILTER_LINEAR);
            filters[units]=linear?Graphics3D.TextureFilters.BILINEAR:Graphics3D.TextureFilters.NEAREST;
            int level=Mobile.m3gMipmapMode==Graphics3D.MODE_FORCE_DISABLE?Texture2D.FILTER_BASE_LEVEL:Mobile.m3gMipmapMode==Graphics3D.MODE_FORCE_ENABLE?Texture2D.FILTER_NEAREST
                    :Mobile.m3gMipmapMode==3?Texture2D.FILTER_LINEAR:texture.getLevelFilter();
            mip[units]=level==Texture2D.FILTER_BASE_LEVEL?null:level==Texture2D.FILTER_LINEAR
                    ?Graphics3DPipelines.MipmapModes.LINEAR:Graphics3DPipelines.MipmapModes.NEAREST;
            units++;
        }
        if(!finite(state.texture)) return false;
        VertexArray[] attributes={position,colors,state.lit?normals:null,uv[0],uv[1],uv[2]};
        ByteBuffer data=null;
        for(int i=cache.size()-1;i>=0;i--) {
            Geometry entry=cache.get(i);
            if(entry.buffer!=vb || entry.indices!=ib) continue;
            cache.remove(i); cachedBytes-=entry.bytes();
            if(entry.matches(attributes,indices,vb.getDefaultColor())) {
                cache.add(entry); cachedBytes+=entry.bytes(); data=entry.data; cacheHits++;
            }
            break;
        }
        if(data==null) {
            cacheMisses++;
            int floats=indices.length*20;
            if(staging.length<floats) staging=new float[floats];
            for(int i=0;i<indices.length;i++) {
                int vertex=indices[i], at=i*20;
                for(int c=0;c<3;c++) staging[at+c]=component(position,vertex,c);
                int color=vb.getDefaultColor();
                if(colors!=null) {
                    int count=colors.getComponentCount(), base=vertex*count;
                    byte[] values=colors.vertArrayByteSize;
                    color=((count==4?values[base+3]&255:255)<<24) | ((values[base]&255)<<16) | ((values[base+1]&255)<<8) | (values[base+2]&255);
                }
                color(color,staging,at+3,1);
                for(int u=0;u<3;u++) for(int c=0;c<3;c++) staging[at+7+u*3+c]=u<units?component(uv[u],vertex,c):0;
                for(int c=0;c<3;c++) staging[at+16+c]=state.lit?component(normals,vertex,c):0;
                staging[at+19]=0;
            }
            data=ByteBuffer.allocateDirect(floats*4).order(ByteOrder.nativeOrder());
            data.asFloatBuffer().put(staging,0,floats);
            // Immutable command snapshots may outlive an eviction or a mutable mesh revision.
            data=data.asReadOnlyBuffer().order(ByteOrder.nativeOrder());
            Geometry entry=new Geometry(vb,ib,attributes,indices,data);
            if(entry.bytes()<=CACHE_BYTES) {
                while(!cache.isEmpty() && (cache.size()>=32 || cachedBytes+entry.bytes()>CACHE_BYTES)) cachedBytes-=cache.remove(0).bytes();
                cache.add(entry); cachedBytes+=entry.bytes();
            }
        }
        boolean result=GpuRasterizer.raw(pixels,depth,w,h,indices.length/3,left,top,right,bottom,
                depthTest,depthTest&&comp.isDepthWriteEnabled(),(comp.isColorWriteEnabled()?0xffffff:0)|(comp.isAlphaWriteEnabled()?0xff000000:0),
                alpha,(int)(comp.getAlphaThreshold()*255),textures,units,filters,mip,comp.getBlending(),fog,parallel,resident,state,data);
        if(result) { draws++; vertices+=indices.length; }
        return result;
    }

    private static float component(VertexArray array,int vertex,int component) {
        int count=array.getComponentCount(); if(component>=count) return 0;
        int index=vertex*count+component;
        return array.getComponentType()==1?array.vertArrayByteSize[index]:array.vertArrayShortSize[index];
    }
    private static void color(int color,float[] out,int at,float scale) {
        out[at]=((color>>16)&255)*scale; out[at+1]=((color>>8)&255)*scale;
        out[at+2]=(color&255)*scale; out[at+3]=(color>>>24)*scale;
    }
    private static boolean finite(float[] values) { for(float value:values) if(!Float.isFinite(value)) return false; return true; }
    private static void matrix(Transform transform,float[] out,int at) {
        float[] m=new float[16]; transform.get(m);
        for(int row=0;row<4;row++) for(int col=0;col<4;col++) out[at+col*4+row]=m[row*4+col];
    }

    static final class Uniforms {
        final int raw,clip,eye,normal,texture,viewport,material,light,flags,depth,shine;
        Uniforms(int program) {
            raw=glGetUniformLocation(program,"rawGeometry"); clip=glGetUniformLocation(program,"rawClip");
            eye=glGetUniformLocation(program,"rawEye"); normal=glGetUniformLocation(program,"rawNormal");
            texture=glGetUniformLocation(program,"rawTexture"); viewport=glGetUniformLocation(program,"rawViewport");
            material=glGetUniformLocation(program,"rawMaterial"); light=glGetUniformLocation(program,"rawLight");
            flags=glGetUniformLocation(program,"rawFlags"); depth=glGetUniformLocation(program,"rawDepth");
            shine=glGetUniformLocation(program,"rawShininess");
        }
        void apply(State state) {
            glUniform1i(raw,state==null?0:1);
            if(state==null) { glDisable(GL_CULL_FACE); return; }
            glUniformMatrix4fv(clip,1,false,state.clip,0); glUniformMatrix4fv(eye,1,false,state.eye,0);
            glUniformMatrix4fv(normal,1,false,state.normal,0); glUniformMatrix4fv(texture,3,false,state.texture,0);
            glUniform4fv(viewport,1,state.viewport,0); glUniform4fv(material,4,state.material,0);
            glUniform4fv(light,40,state.light,0);
            glUniform4i(flags,state.lit?state.lights:-1,state.tracking?1:0,state.local?1:0,state.perspective?1:0);
            glUniform2f(depth,state.near,state.far); glUniform1f(shine,state.shininess);
            if(state.culling==PolygonMode.CULL_NONE) glDisable(GL_CULL_FACE);
            else {
                glEnable(GL_CULL_FACE); glCullFace(state.culling==PolygonMode.CULL_BACK?GL_BACK:GL_FRONT);
                // The CPU target has downward Y, opposite the camera's front-face convention.
                glFrontFace(state.winding==PolygonMode.WINDING_CCW?GL_CW:GL_CCW);
            }
        }
    }

    static final String DECLARATIONS = "uniform int rawGeometry; uniform mat4 rawClip,rawEye,rawNormal,rawTexture[3];\n"
            +"uniform vec4 rawViewport,rawMaterial[4],rawLight[40]; uniform ivec4 rawFlags; uniform float rawShininess;\n"
            +"vec3 safeNormal(vec3 v){ float d=dot(v,v); return d>1e-30?v*inversesqrt(d):vec3(0,0,1); }\n"
            +"vec4 illuminate(vec3 p,vec3 n,vec4 base){\n"
            +"vec4 diffuse=rawFlags.y!=0?base/255.0:rawMaterial[1]; vec3 ambient=rawFlags.y!=0?base.rgb/255.0:rawMaterial[0].rgb;\n"
            +"vec3 sum=rawMaterial[3].rgb; vec3 view=rawFlags.z!=0?safeNormal(-p):vec3(0,0,1);\n"
            +"for(int i=0;i<8;i++){ if(i>=rawFlags.x)break; int j=i*5; vec4 light=rawLight[j];\n"
            +"if(light.w==128.0){sum+=ambient*light.rgb;continue;} vec3 l=-rawLight[j+2].xyz; float attenuation=1.0;\n"
            +"if(light.w!=129.0){vec3 delta=rawLight[j+1].xyz-p; float d2=dot(delta,delta); float d=sqrt(d2); l=safeNormal(delta);\n"
            +"attenuation=1.0/dot(rawLight[j+3].xyz,vec3(1,d,d2));\n"
            +"if(light.w==131.0){float cone=dot(-l,rawLight[j+2].xyz); attenuation*=cone>=rawLight[j+2].w?pow(max(cone,0.0),rawLight[j+3].w):0.0;}}\n"
            +"float lambert=dot(n,l); if(lambert>0.0 && attenuation>0.0){sum+=diffuse.rgb*light.rgb*lambert*attenuation;\n"
            +"vec3 halfVector=l+view; float h2=dot(halfVector,halfVector); if(h2>1e-30){float spec=dot(n,halfVector*inversesqrt(h2));\n"
            +"if(spec>0.0)sum+=rawMaterial[2].rgb*light.rgb*pow(spec,rawShininess)*attenuation;}}}\n"
            +"return vec4(floor(clamp(sum*255.0,0.0,255.0)),diffuse.a*255.0);}\n";

    static final String BODY = "if(rawGeometry!=0){ vec4 p=vec4(position,1); vec4 clip=rawClip*p; vec3 eye=(rawEye*p).xyz;\n"
            +"vec2 screen=(vec2(clip.x,-clip.y)+clip.ww)*0.5*rawViewport.zw+rawViewport.xy*clip.w;\n"
            +"gl_Position=vec4(screen/targetSize*2.0-clip.ww,clip.z,clip.w); affineWeight=clip.w;\n"
            +"vertexColor=(rawFlags.x>=0?illuminate(eye,safeNormal((rawNormal*vec4(uv3,0)).xyz),color):color)*clip.w;\n"
            +"vec3 coords[3]; coords[0]=uv0;coords[1]=uv1;coords[2]=uv2;\n"
            +"for(int u=0;u<3;u++){vec2 t=(rawTexture[u]*vec4(coords[u],1)).xy; texCoord[u]=rawFlags.w!=0?vec3(t,1):vec3(t,1)*clip.w;}\n"
            +"texCoord[3]=vec3(0,0,1); fogValue=parallelFog!=0?-eye.z*clip.w:1.0; }\n";
}
