/* SPDX-License-Identifier: GPL-3.0-only */
package javax.bluetooth;

import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded SDP value snapshots. Never share mutable game-owned DataElement trees with IO. */
final class VirtualServiceAttributes {
    private VirtualServiceAttributes() { }

    static Map<Integer, DataElement> snapshot(ServiceRecord record) {
        Map<Integer, DataElement> result = new LinkedHashMap<>();
        int[] ids = record.getAttributeIDs();
        if (ids == null || ids.length > 256) throw new IllegalArgumentException("Too many service attributes");
        int[] budget = {16384};
        for (int id : ids) {
            if (id < 0 || id > 65535 || result.containsKey(id)) throw new IllegalArgumentException("Invalid attribute id");
            result.put(id, copy(record.getAttributeValue(id), 0, budget));
        }
        return result;
    }

    static DataElement copy(DataElement value) { return copy(value, 0, new int[]{16384}); }

    private static DataElement copy(DataElement value, int depth, int[] budget) {
        if (value == null || depth > 8 || (budget[0] -= 8) < 0) throw new IllegalArgumentException("Invalid or excessive SDP value");
        int type = value.getDataType();
        switch (type) {
            case DataElement.NULL: return new DataElement(type);
            case DataElement.BOOL: return new DataElement(value.getBoolean());
            case DataElement.DATSEQ:
            case DataElement.DATALT:
                DataElement copy = new DataElement(type);
                Enumeration<?> elements = (Enumeration<?>)value.getValue();
                while (elements.hasMoreElements()) copy.addElement(copy((DataElement)elements.nextElement(), depth+1, budget));
                return copy;
            case DataElement.STRING:
            case DataElement.URL:
                String string = (String)value.getValue();
                if (string.length() > 4096 || (budget[0] -= string.length()*2) < 0) throw new IllegalArgumentException("Excessive SDP string");
                return new DataElement(type, string);
            case DataElement.UUID:
            case DataElement.U_INT_8:
            case DataElement.U_INT_16:
            case DataElement.INT_16:
                budget[0] -= 16;
                if (budget[0] < 0) throw new IllegalArgumentException("Excessive SDP data");
                return new DataElement(type, value.getValue());
            default: return new DataElement(type, value.getLong());
        }
    }

    static boolean contains(DataElement value, UUID uuid) {
        if (value.getDataType() == DataElement.UUID) return uuid.equals(value.getValue());
        if (value.getDataType() != DataElement.DATSEQ && value.getDataType() != DataElement.DATALT) return false;
        Enumeration<?> elements = (Enumeration<?>)value.getValue();
        while (elements.hasMoreElements()) if (contains((DataElement)elements.nextElement(), uuid)) return true;
        return false;
    }
}
