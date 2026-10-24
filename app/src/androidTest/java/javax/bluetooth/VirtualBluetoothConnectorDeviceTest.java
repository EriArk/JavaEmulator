/* SPDX-License-Identifier: GPL-3.0-only */
package javax.bluetooth;

import org.junit.Test;
import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;
import javax.microedition.io.StreamConnectionNotifier;
import static org.junit.Assert.*;

/** Includes the shared contracts, plus the Android game's actual Connector route. */
public class VirtualBluetoothConnectorDeviceTest extends VirtualBluetoothTest {
    @Test public void connectorUsesVirtualBackendWithoutRadioPermission() throws Exception {
        VirtualBluetooth.install(host);
        StreamConnectionNotifier server = (StreamConnectionNotifier)Connector.open("btspp://localhost:"+GAME+";name=Runtime route");
        assertNotNull(LocalDevice.getLocalDevice().getRecord(server));
        StreamConnection client = (StreamConnection)guest.open(new DiscoveryAgent(guest).selectService(GAME, 0, false), 3);
        StreamConnection accepted = server.acceptAndOpen();
        client.openDataOutputStream().writeInt(0x12345678);
        assertEquals(0x12345678, accepted.openDataInputStream().readInt());
        L2CAPConnectionNotifier packetServer = (L2CAPConnectionNotifier)Connector.open("btl2cap://localhost:987654321");
        assertNotNull(LocalDevice.getLocalDevice().getRecord(packetServer));
        L2CAPConnection packetClient = (L2CAPConnection)guest.open(new DiscoveryAgent(guest).selectService(new UUID("987654321", false), 0, false), 3);
        L2CAPConnection packetPeer = packetServer.acceptAndOpen(); packetClient.send(new byte[]{42});
        byte[] value = new byte[1]; assertEquals(1, packetPeer.receive(value)); assertEquals(42, value[0]);
    }
}
