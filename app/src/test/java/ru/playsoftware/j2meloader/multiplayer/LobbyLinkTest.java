/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;
import org.junit.Test;
import static org.junit.Assert.*;
public class LobbyLinkTest {
    @Test public void exactLocatorRoundTrips(){String id=LobbyRoom.uuid();LobbyLink link=LobbyLink.parse(LobbyLink.create("11",id));assertNotNull(link);assertEquals("11",link.host);assertEquals(id,link.room);}
    @Test public void rejectsInjectedAndAmbiguousLinks(){String good=LobbyLink.create("11",LobbyRoom.uuid());for(String v:new String[]{good+"&host=22",good+"#x",good+"&token=secret",good.replace("abyssme:","https:"),good.replace("host=11","host=%31%31"),good.replace("v=1","v=2"),good.replace("//lobby","//evil@lobby"),"abyssme://lobby",null})assertNull(v,LobbyLink.parse(v));}
    @Test public void nullRoomIsRejected(){assertNull(LobbyLink.parse("abyssme://lobby?v=1&host=11&room=00000000-0000-0000-0000-000000000000"));}
}
