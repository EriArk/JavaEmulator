/*
 * Copyright 2018 cerg2010cerg2010
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package javax.bluetooth;

import android.bluetooth.BluetoothDevice;

import java.io.IOException;

import javax.microedition.io.Connection;

public class RemoteDevice {
	BluetoothDevice dev;
	VirtualBluetooth virtual;
	private String virtualAddress;

	RemoteDevice(VirtualBluetooth session, String address) {
		virtual = session;
		virtualAddress = address;
	}

	RemoteDevice(BluetoothDevice dev) {
		this.dev = dev;
	}

	static String javaToAndroidAddress(String addr) {
		StringBuilder sb = new StringBuilder(addr);
		for (int i = 2; i < sb.length(); i += 3)
			sb.insert(i, ':');
		return sb.toString();
	}

	protected RemoteDevice(String address) {
		if (address == null) {
			throw new NullPointerException("address is null");
		}
		VirtualBluetooth session = VirtualBluetooth.current();
		if (session != null) {
			RemoteDevice remote = session.remote(address);
			virtual = session;
			virtualAddress = remote.virtualAddress;
			return;
		}

		dev = DiscoveryAgent.adapter.getRemoteDevice(javaToAndroidAddress(address));
	}

	public String getFriendlyName(boolean alwaysAsk) throws IOException {
		if (virtual != null) return virtual.peerName(virtualAddress);
		String name = dev.getName();
		if (name == null) {
			name =  "";
		}
		return name;
	}

	public final String getBluetoothAddress() {
		if (virtual != null) return virtualAddress;
		return dev.getAddress().replace(":", "");
	}

	public boolean equals(Object obj) {
		if (obj == null || !(obj instanceof RemoteDevice))
			return false;
		RemoteDevice other = (RemoteDevice) obj;
		if (virtual != null || other.virtual != null)
			return virtual == other.virtual && getBluetoothAddress().equals(other.getBluetoothAddress());
		return dev.equals(((RemoteDevice) obj).dev);
	}

	public int hashCode() {
		if (virtual != null) return 31 * System.identityHashCode(virtual) + virtualAddress.hashCode();
		return dev.hashCode();
	}

	public static RemoteDevice getRemoteDevice(Connection conn) throws IOException {
		if (conn == null)
			throw new NullPointerException("conn is null");
		if (conn instanceof VirtualBluetooth.Connected) {
			VirtualBluetooth.Connected c = (VirtualBluetooth.Connected) conn;
			c.session().check();
			if (c.ended) throw new IOException("Bluetooth channel closed");
			return c.session().remote(c.channel.peer);
		}
		if (!(conn instanceof org.microemu.cldc.btspp.SPPConnectionImpl
				|| conn instanceof org.microemu.cldc.btl2cap.L2CAPConnectionImpl))
			throw new java.lang.IllegalArgumentException("not a RFCOMM connection");

		if (conn instanceof org.microemu.cldc.btspp.SPPConnectionImpl) {
			org.microemu.cldc.btspp.SPPConnectionImpl connection =
					(org.microemu.cldc.btspp.SPPConnectionImpl) conn;
			if (connection.socket == null)
				throw new IOException("socket is null");
			return new RemoteDevice(connection.socket.getRemoteDevice());
		} else {
			org.microemu.cldc.btl2cap.L2CAPConnectionImpl connection =
					(org.microemu.cldc.btl2cap.L2CAPConnectionImpl) conn;
			if (connection.socket == null)
				throw new IOException("socket is null");
			return new RemoteDevice(connection.socket.getRemoteDevice());
		}
	}

	public boolean authenticate() throws IOException {
		if (virtual != null) { virtual.check(); return virtual.connected(virtualAddress); }
		return false;
	}

	public boolean authorize(javax.microedition.io.Connection conn) throws IOException {
		if (virtual != null) return virtual.owns(conn, virtualAddress);
		return false;
	}

	public boolean encrypt(javax.microedition.io.Connection conn, boolean on) throws IOException {
		if (virtual != null) return virtual.owns(conn, virtualAddress) && on;
		return false;
	}

	public boolean isAuthenticated() {
		if (virtual != null) return virtual.connected(virtualAddress);
		return false;
	}

	public boolean isAuthorized(javax.microedition.io.Connection conn) throws IOException {
		if (virtual != null) return virtual.owns(conn, virtualAddress);
		return false;
	}

	public boolean isEncrypted() {
		if (virtual != null) return virtual.connected(virtualAddress);
		return false;
	}

	public boolean isTrustedDevice() {
		return false;
	}
}
