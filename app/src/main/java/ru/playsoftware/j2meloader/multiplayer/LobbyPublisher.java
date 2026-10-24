/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import com.google.gson.*;
import java.util.function.*;
import static ru.playsoftware.j2meloader.multiplayer.LobbyRoom.*;

/** Advertisements are separate from host admission. Durable intent precedes each bot command. */
final class LobbyPublisher {
    static final String RESULT_PREFIX="TrainerOS lobby directory result\n```json\n";
    interface Store {JsonObject read() throws Exception;void write(JsonObject state) throws Exception;}
    private final String self;
    private final Store store;
    private final LongSupplier clock;
    private final Consumer<String> send;
    private final Runnable changed;
    private JsonObject saved=new JsonObject(),pending;
    private String desired="",access="private",status="Invite-only room",acknowledged="";
    private long next,deadline;
    private boolean broken,recovery;
    LobbyPublisher(String self,Store store,LongSupplier seconds,Consumer<String> send,Runnable changed){
        this.self=self;this.store=store;clock=seconds;this.send=send;this.changed=changed;
        try{saved=store.read();if(saved==null)saved=new JsonObject();if(!saved.entrySet().isEmpty()&&(!uuid(text(saved,"lobby"))||number(saved,"revision")<1))throw new IllegalStateException();recovery=!saved.entrySet().isEmpty();}
        catch(Exception e){broken=true;status="Publication history could not be read. Public listing is disabled.";}
    }
    String status(){return status;}
    void desired(String room,String access){if(!desired.equals(room)||!this.access.equals(access)){desired=room;this.access=access;next=0;acknowledged="";}tick();}
    void retry(){if(pending!=null)return;next=0;tick();}
    void tick(){
        long now=clock.getAsLong();if(broken)return;
        if(pending!=null){if(now>=deadline){pending=null;next=Long.MAX_VALUE;status="Directory confirmation missing. Refresh or retry publication.";changed.run();}return;}
        if(now<next)return;
        String old=text(saved,"lobby"),operation;
        if(!old.isEmpty()&&(recovery||!old.equals(desired)))operation="close";
        else if(!desired.isEmpty()&&!access.equals("private"))operation="upsert";
        else if(!old.isEmpty()&&!acknowledged.equals("withdraw"))operation="withdraw";
        else{status="Invite-only room";next=Long.MAX_VALUE;changed.run();return;}
        String target=operation.equals("upsert")?desired:old;
        long revision=target.equals(old)?number(saved,"revision")+1:1;
        JsonObject command=new JsonObject();command.addProperty("v",2);command.addProperty("product","abyssme");command.addProperty("protocol",1);command.addProperty("lobby",target);command.addProperty("revision",revision);command.addProperty("op",operation);command.addProperty("request",uuid());
        if(operation.equals("upsert")){command.addProperty("topic","choosing");command.addProperty("status","choosing");command.addProperty("access",access);command.addProperty("seats",0);command.addProperty("available",0);command.addProperty("streams",0);}
        try{store.write(command);saved=command.deepCopy();}catch(Exception e){broken=true;status="Could not save publication intent. Nothing was published.";changed.run();return;}
        pending=command;deadline=now+45;status=operation.equals("upsert")?"Waiting for directory confirmation...":"Waiting for listing removal confirmation...";
        send.accept(LobbyDirectory.PREFIX+command+"\n```");changed.run();
    }
    void receive(String peer,String content){
        if(!peer.equals(LobbyDirectory.BOT)||pending==null||content.length()>2000||!content.startsWith(RESULT_PREFIX)||!content.endsWith("\n```"))return;
        try{
            JsonObject result=JsonParser.parseString(content.substring(RESULT_PREFIX.length(),content.length()-4)).getAsJsonObject();
            if(number(result,"v")!=2||!text(result,"product").equals("abyssme")||number(result,"protocol")!=1||!text(result,"owner").equals(self)
                    ||!text(result,"request").equals(text(pending,"request"))||!text(result,"lobby").equals(text(pending,"lobby"))||number(result,"revision")!=number(pending,"revision")
                    ||!text(result,"op").equals(text(pending,"op"))||!LobbyExpiry.live(number(result,"expires"),clock.getAsLong(),90))return;
            String code=text(result,"result"),op=text(pending,"op");pending=null;next=Long.MAX_VALUE;
            if(code.equals("accepted")&&op.equals("upsert")){acknowledged=op;next=clock.getAsLong()+60;status="Public listing accepted by the directory";}
            else if(code.equals("withdrawn")&&op.equals("withdraw")){acknowledged=op;status="Listing withdrawal accepted";}
            else if(code.equals("closed")&&op.equals("close")){
                store.write(new JsonObject());saved=new JsonObject();recovery=false;acknowledged="";next=0;status="Previous listing closed";
            }else status=code.equals("already-listed")?"Another public room is active. Close it in TrainerOS or AbyssME first.":"Directory rejected this update: "+(code.matches("[a-z-]{1,24}")?code:"invalid response");
            if(!recovery&&(!text(saved,"lobby").equals(desired)||code.equals("accepted")&&!access.equals(text(saved,"access"))||code.equals("withdrawn")&&!access.equals("private")))next=0;
            changed.run();
        }catch(Exception e){broken=true;status="Directory reply or saved publication state could not be confirmed.";changed.run();}
    }
}
