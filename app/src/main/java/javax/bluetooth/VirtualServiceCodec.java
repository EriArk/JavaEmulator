/* SPDX-License-Identifier: GPL-3.0-only */
package javax.bluetooth;

import java.io.*;
import java.util.*;

/** Explicit bounded SDP wire values, never Java object deserialization. */
final class VirtualServiceCodec {
    static void write(DataOutputStream out, VirtualBluetooth.Service service) throws IOException {
        out.writeUTF(service.uuid.toString()); out.writeUTF(service.name);
        out.writeBoolean(service.packets); out.writeShort(service.port);
        out.writeShort(service.receiveMtu); out.writeShort(service.minimumTransmitMtu);
        Map<Integer, DataElement> attributes = service.attributeSnapshot();
        out.writeShort(attributes.size());
        for (Map.Entry<Integer, DataElement> e : attributes.entrySet()) {
            out.writeShort(e.getKey()); writeValue(out, e.getValue());
        }
    }
    static VirtualBluetooth.Service read(DataInputStream in) throws IOException {
        UUID uuid = uuid(in.readUTF()); String name = in.readUTF();
        boolean packets = bool(in); int port = in.readUnsignedShort();
        int rx = in.readUnsignedShort(), tx = in.readUnsignedShort(), count = in.readUnsignedShort();
        if (count > 256 || port < 1 || !packets && port > 30) throw new IOException("Invalid service metadata");
        Map<Integer, DataElement> values = new LinkedHashMap<>(); int[] budget = {2048};
        for (int i = 0; i < count; i++) {
            int id = in.readUnsignedShort();
            if (values.put(id, readValue(in, 0, budget)) != null) throw new IOException("Duplicate SDP attribute");
        }
        return new VirtualBluetooth.Service(uuid, name, packets, port, rx, tx, record(values));
    }
    static ServiceRecord record(Map<Integer, DataElement> values) {
        return new ServiceRecord() {
            public int[] getAttributeIDs() { return values.keySet().stream().mapToInt(Integer::intValue).toArray(); }
            public DataElement getAttributeValue(int id) { return values.get(id); }
            public RemoteDevice getHostDevice() { return null; }
            public String getConnectionURL(int security, boolean master) { return null; }
            public boolean populateRecord(int[] ids) { return false; }
            public boolean setAttributeValue(int id, DataElement value) { throw new UnsupportedOperationException(); }
            public void setDeviceServiceClasses(int classes) { throw new UnsupportedOperationException(); }
        };
    }
    private static void writeValue(DataOutputStream out, DataElement value) throws IOException {
        int type = value.getDataType(); out.writeByte(type);
        switch (type) {
            case DataElement.NULL: break;
            case DataElement.BOOL: out.writeBoolean(value.getBoolean()); break;
            case DataElement.STRING: case DataElement.URL: out.writeUTF((String)value.getValue()); break;
            case DataElement.UUID: out.writeUTF(value.getValue().toString()); break;
            case DataElement.U_INT_8: case DataElement.U_INT_16: case DataElement.INT_16:
                out.write((byte[])value.getValue()); break;
            case DataElement.DATSEQ: case DataElement.DATALT:
                out.writeShort(value.getSize()); Enumeration<?> it = (Enumeration<?>)value.getValue();
                while (it.hasMoreElements()) writeValue(out, (DataElement)it.nextElement()); break;
            default: out.writeLong(value.getLong());
        }
    }
    private static DataElement readValue(DataInputStream in, int depth, int[] budget) throws IOException {
        if (depth > 8 || --budget[0] < 0) throw new IOException("Excessive SDP values");
        int type = in.readUnsignedByte();
        switch (type) {
            case DataElement.NULL: return new DataElement(type);
            case DataElement.BOOL: return new DataElement(bool(in));
            case DataElement.STRING: case DataElement.URL: return new DataElement(type, in.readUTF());
            case DataElement.UUID: return new DataElement(type, uuid(in.readUTF()));
            case DataElement.U_INT_8: case DataElement.U_INT_16: case DataElement.INT_16:
                byte[] bytes = new byte[type == DataElement.U_INT_8 ? 8 : 16]; in.readFully(bytes);
                return new DataElement(type, bytes);
            case DataElement.DATSEQ: case DataElement.DATALT:
                int n = in.readUnsignedShort(); if (n > budget[0]) throw new IOException("Excessive SDP sequence");
                DataElement list = new DataElement(type);
                for (int i = 0; i < n; i++) list.addElement(readValue(in, depth + 1, budget));
                return list;
            case DataElement.U_INT_1: case DataElement.U_INT_2: case DataElement.U_INT_4:
            case DataElement.INT_1: case DataElement.INT_2: case DataElement.INT_4: case DataElement.INT_8:
                return new DataElement(type, in.readLong());
            default: throw new IOException("Unknown SDP type");
        }
    }
    static boolean bool(DataInputStream in) throws IOException {
        int v = in.readUnsignedByte(); if (v > 1) throw new IOException("Invalid boolean"); return v == 1;
    }
    private static UUID uuid(String value) throws IOException {
        if (!value.matches("[0-9a-fA-F]{1,32}")) throw new IOException("Invalid service UUID");
        return new UUID(value, false);
    }
}
