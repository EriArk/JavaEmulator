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
    private JsonObject saved=new JsonObject(),pending,listing=projection(new JsonObject(),"");
    private String desired="",access="private",status="Invite-only room",acknowledged="";
    private long next,deadline;
    private boolean broken,recovery;
    LobbyPublisher(String self,Store store,LongSupplier seconds,Consumer<String> send,Runnable changed){
        this.self=self;this.store=store;clock=seconds;this.send=send;this.changed=changed;
        try{saved=store.read();if(saved==null)saved=new JsonObject();if(!saved.entrySet().isEmpty()&&(!uuid(text(saved,"lobby"))||number(saved,"revision")<1))throw new IllegalStateException();recovery=!saved.entrySet().isEmpty();}
        catch(Exception e){broken=true;status="Publication history could not be read. Public listing is disabled.";}
    }
    String status(){return status;}
    void desired(String room,String access){desired(room,access,new JsonObject(),"");}
    void desired(String room,String access,JsonObject snapshot,String title){
        JsonObject updated=projection(snapshot,title);
        if(!desired.equals(room)||!this.access.equals(access)||!access.equals("private")&&!listing.equals(updated)){
            desired=room;this.access=access;next=0;acknowledged="";
        }
        listing=updated;
        tick();
    }
    /** Public metadata only. Runtime paths, member IDs and transport attempts never leave the room. */
    static JsonObject projection(JsonObject snapshot,String title){
        JsonObject out=new JsonObject();out.addProperty("topic","choosing");out.addProperty("status","choosing");
        out.addProperty("seats",0);out.addProperty("available",0);out.addProperty("streams",0);
        LobbySession.Game game=game(object(snapshot,"game"));
        if(game==null||!capability(game).equals("java/bluetooth/1")||game.players!=2)return out;
        StringBuilder name=new StringBuilder();
        title.codePoints().forEach(c->{if(name.length()+Character.charCount(c)<=160)name.appendCodePoint(Character.isISOControl(c)?' ':c);});
        String label=name.toString().trim();if(label.isEmpty())label="Java Bluetooth game";
        JsonObject publicGame=new JsonObject();publicGame.addProperty("id",game.contentHash);publicGame.addProperty("title",label);
        publicGame.addProperty("platform","j2me");publicGame.addProperty("mode",game.mode);publicGame.addProperty("adapter",game.adapterVersion);
        JsonElement members=snapshot.get("members");int occupied=members!=null&&members.isJsonArray()?members.getAsJsonArray().size():game.players;
        out.addProperty("topic","other");out.addProperty("status",bool(object(snapshot,"launch"),"running")?"playing":"gathering");
        out.addProperty("seats",game.players);out.addProperty("available",snapshot.has("launch")?0:Math.max(0,game.players-Math.max(1,occupied)));
        out.add("game",publicGame);return out;
    }
    private boolean currentListing(JsonObject command){
        if(!desired.equals(text(command,"lobby"))||!access.equals(text(command,"access")))return false;
        for(String key:listing.keySet())if(!listing.get(key).equals(command.get(key)))return false;
        return listing.has("game")==command.has("game");
    }
    void retry(){if(pending!=null)return;next=0;tick();}
    void tick(){
        long now=clock.getAsLong();if(broken)return;
        if(pending!=null){
            if(now>=deadline){
                // A missing upsert reply must not swallow an explicit privacy/close
                // request. Follow it with removal, never replay the uncertain upsert.
                boolean removal=text(pending,"op").equals("upsert")&&(!desired.equals(text(pending,"lobby"))||access.equals("private"));
                pending=null;next=removal?0:Long.MAX_VALUE;
                status=removal?"Removing public listing...":"Directory confirmation missing. Refresh or retry publication.";changed.run();
            }
            return;
        }
        if(now<next)return;
        String old=text(saved,"lobby"),operation;
        if(!old.isEmpty()&&(recovery||!old.equals(desired)))operation="close";
        else if(!desired.isEmpty()&&!access.equals("private"))operation="upsert";
        else if(!old.isEmpty()&&!acknowledged.equals("withdraw"))operation="withdraw";
        else{status="Invite-only room";next=Long.MAX_VALUE;changed.run();return;}
        String target=operation.equals("upsert")?desired:old;
        long revision=target.equals(old)?number(saved,"revision")+1:1;
        JsonObject command=new JsonObject();command.addProperty("v",2);command.addProperty("product","abyssme");command.addProperty("protocol",1);command.addProperty("lobby",target);command.addProperty("revision",revision);command.addProperty("op",operation);command.addProperty("request",uuid());
        if(operation.equals("upsert")){for(String key:listing.keySet())command.add(key,listing.get(key).deepCopy());command.addProperty("access",access);}
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
            }else status=code.equals("already-listed")?"Another AbyssME public room is active. Close it first.":"Directory rejected this update: "+(code.matches("[a-z-]{1,24}")?code:"invalid response");
            boolean success=code.equals("accepted")||code.equals("withdrawn")||code.equals("closed");
            boolean removal=op.equals("upsert")&&(!text(saved,"lobby").equals(desired)||access.equals("private"));
            if(!recovery&&(removal||success&&(!text(saved,"lobby").equals(desired)||code.equals("accepted")&&!currentListing(saved)||code.equals("withdrawn")&&!access.equals("private")))){
                next=0;
                status=removal?"Removing public listing...":"Updating public listing...";
            }
            changed.run();
        }catch(Exception e){broken=true;status="Directory reply or saved publication state could not be confirmed.";changed.run();}
    }
}
