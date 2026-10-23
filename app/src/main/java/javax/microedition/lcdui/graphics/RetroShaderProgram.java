/* Copyright 2026. GPL-3.0-only. Upstream shader notices are retained in assets. */
package javax.microedition.lcdui.graphics;

import java.nio.FloatBuffer;
import javax.microedition.util.ContextHolder;
import static android.opengl.GLES20.*;

/** Libretro GLSL adapter. Only accepts cropped, nearest-filtered guest textures. */
final class RetroShaderProgram implements AutoCloseable {
    enum XbrzPass { ANALYZE, RESOLVE }
    private final int id, texture, inputSize, textureSize, outputSize, matrix, frameDirection, frameCount, blendInfo;
    private static final float[] IDENTITY={1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1};
    RetroShaderProgram(int mode) {
        this(mode==1?"xbrz.glsl":"crt-easymode.glsl",null,"");
    }
    RetroShaderProgram(XbrzPass pass) {
        this("xbrz.glsl",pass==XbrzPass.RESOLVE?"xbrz-resolve.glsl":null,
                pass==XbrzPass.ANALYZE?"#define ABYSS_XBRZ_ANALYZE\n":"");
    }
    private RetroShaderProgram(String path,String fragmentPath,String defines) {
        String code=ContextHolder.getAssetAsString("shaders/retro/"+path);
        int vertex=0, fragment=0, program=0;
        try {
            vertex=compile(GL_VERTEX_SHADER,"#define VERTEX\n"+code);
            fragment=compile(GL_FRAGMENT_SHADER,"#define FRAGMENT\n"+defines
                    +(fragmentPath==null?code:ContextHolder.getAssetAsString("shaders/retro/"+fragmentPath)));
            program=glCreateProgram(); glAttachShader(program,vertex); glAttachShader(program,fragment);
            glBindAttribLocation(program,0,"VertexCoord"); glBindAttribLocation(program,1,"TexCoord");
            glLinkProgram(program); int[] status=new int[1]; glGetProgramiv(program,GL_LINK_STATUS,status,0);
            if(status[0]==0) throw new IllegalStateException(path+": "+glGetProgramInfoLog(program));
            id=program;
        } catch(RuntimeException failure) { if(program!=0) glDeleteProgram(program); throw failure; }
        finally { if(vertex!=0) glDeleteShader(vertex); if(fragment!=0) glDeleteShader(fragment); }
        texture=glGetUniformLocation(id,"Texture"); inputSize=glGetUniformLocation(id,"InputSize");
        textureSize=glGetUniformLocation(id,"TextureSize"); outputSize=glGetUniformLocation(id,"OutputSize");
        matrix=glGetUniformLocation(id,"MVPMatrix"); frameDirection=glGetUniformLocation(id,"FrameDirection");
        frameCount=glGetUniformLocation(id,"FrameCount");
        blendInfo=glGetUniformLocation(id,"BlendInfo");
    }
    void draw(int source,int width,int height,int outWidth,int outHeight,FloatBuffer quad) {
        draw(source,width,height,outWidth,outHeight,quad,0);
    }
    void draw(int source,int width,int height,int outWidth,int outHeight,FloatBuffer quad,int decisions) {
        glUseProgram(id); glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D,source);
        if(blendInfo>=0) {
            glActiveTexture(GL_TEXTURE1); glBindTexture(GL_TEXTURE_2D,decisions);
            glUniform1i(blendInfo,1); glActiveTexture(GL_TEXTURE0);
        }
        glUniform1i(texture,0); glUniform2f(inputSize,width,height); glUniform2f(textureSize,width,height);
        glUniform2f(outputSize,outWidth,outHeight); glUniformMatrix4fv(matrix,1,false,IDENTITY,0);
        glUniform1i(frameDirection,1); glUniform1i(frameCount,0);
        quad.position(0); glVertexAttribPointer(0,2,GL_FLOAT,false,16,quad); glEnableVertexAttribArray(0);
        quad.position(2); glVertexAttribPointer(1,2,GL_FLOAT,false,16,quad); glEnableVertexAttribArray(1);
        glDrawArrays(GL_TRIANGLE_STRIP,0,4);
    }
    private static int compile(int type,String code) {
        int shader=glCreateShader(type); glShaderSource(shader,code); glCompileShader(shader);
        int[] status=new int[1]; glGetShaderiv(shader,GL_COMPILE_STATUS,status,0);
        if(status[0]==0) { String error=glGetShaderInfoLog(shader); glDeleteShader(shader); throw new IllegalStateException(error); }
        return shader;
    }
    public void close() { glDeleteProgram(id); }
}
