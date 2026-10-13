    // SPDX-License-Identifier: GPL-3.0-or-later
    private Object3D[] loadM3G() throws IOException {
        objs = new Vector<Object3D>(); roots = new Vector<String>();
        objs.addElement(null); objs.addElement(null);
        M3gSectionReader header = new M3gSectionReader(dis, Integer.MAX_VALUE);
        if (header.compression != 0 || header.data.length < 17 || header.data[0] != 0
                || M3gSectionReader.integer(header.data, 1) != header.data.length - 5) {
            throw new IOException("Invalid M3G header section");
        }
        DataInputStream sections = dis;
        dis = new DataInputStream(new ByteArrayInputStream(header.data, 5, header.data.length - 5));
        int totalFileSize;
        try {
            int major = readByte(), minor = readByte();
            readBoolean();
            totalFileSize = readInt();
            readInt(); // approximate content size is only a hint
            readString();
            if (major != 1 || minor != 0 || dis.available() != 0) throw new IOException("Invalid M3G header");
        } finally { dis = sections; }
        long read = M3G_FILE_IDENTIFIER.length + (long) header.length;
        if (totalFileSize < read) throw new IOException("Invalid M3G file length");
        while (read < totalFileSize) {
            M3gSectionReader section = new M3gSectionReader(dis, totalFileSize - read);
            new Loader(section.data, objs, roots, resDir, activeRefs).loadM3GSectionData();
            read += section.length;
        }
        Object3D[] result = new Object3D[roots.size()];
        for (int i = 0; i < result.length; i++) result[i] = objs.get(Integer.parseInt(roots.get(i)));
        return result;
    }
