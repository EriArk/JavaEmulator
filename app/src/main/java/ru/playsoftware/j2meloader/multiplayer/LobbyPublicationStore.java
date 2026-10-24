/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import android.content.Context;
import android.util.AtomicFile;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

final class LobbyPublicationStore implements LobbyPublisher.Store {
    private final AtomicFile file;
    LobbyPublicationStore(Context context,String account)throws IOException{
        if(!LobbyDirectory.id(account))throw new IOException("Invalid account");
        File root=new File(context.getNoBackupFilesDir(),"lobby-publications");if(!root.isDirectory()&&!root.mkdirs())throw new IOException("Storage unavailable");
        file=new AtomicFile(new File(root,account+".json"));
    }
    public JsonObject read()throws IOException{
        try(InputStream input=file.openRead();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] chunk=new byte[1024];int n;while((n=input.read(chunk))!=-1){if(out.size()+n>4096)throw new IOException("Publication record too large");out.write(chunk,0,n);}
            return JsonParser.parseString(out.toString("UTF-8")).getAsJsonObject();
        }catch(FileNotFoundException e){return new JsonObject();}catch(RuntimeException e){throw new IOException("Invalid publication record");}
    }
    public void write(JsonObject state)throws IOException{
        byte[] data=state.toString().getBytes(StandardCharsets.UTF_8);if(data.length>4096)throw new IOException("Publication record too large");
        FileOutputStream output=null;try{output=file.startWrite();output.write(data);file.finishWrite(output);}catch(IOException e){file.failWrite(output);throw e;}
    }
}
