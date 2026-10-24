/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import java.net.URI;
import java.util.*;

/** A locator, never an invitation capability or authorization to join. */
public final class LobbyLink {
    public final String host,room;
    private LobbyLink(String host,String room){this.host=host;this.room=room;}
    public static LobbyLink parse(String value){
        try{
            if(value==null||value.length()>300)return null;URI uri=new URI(value);
            if(!"abyssme".equals(uri.getScheme())||!"lobby".equals(uri.getRawAuthority())||!uri.getRawPath().isEmpty()||uri.getRawFragment()!=null)return null;
            Map<String,String> fields=new HashMap<>();for(String part:uri.getRawQuery().split("&",-1)){String[] pair=part.split("=",-1);if(pair.length!=2||fields.put(pair[0],pair[1])!=null)return null;}
            if(fields.size()!=3||!"1".equals(fields.get("v"))||!LobbyDirectory.id(fields.getOrDefault("host",""))||!LobbyRoom.uuid(fields.getOrDefault("room","")))return null;
            return new LobbyLink(fields.get("host"),fields.get("room"));
        }catch(Exception e){return null;}
    }
    public static String create(String host,String room){if(!LobbyDirectory.id(host)||!LobbyRoom.uuid(room))throw new IllegalArgumentException();return "abyssme://lobby?v=1&host="+host+"&room="+room;}
}
