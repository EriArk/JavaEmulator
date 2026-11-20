/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.ui;
public class NativeMenuAlertBuilder extends android.app.AlertDialog.Builder {
    public NativeMenuAlertBuilder(android.content.Context c){super(c);}
    public NativeMenuAlertBuilder(android.content.Context c,int theme){super(c,theme);}
    @Override public android.app.AlertDialog create(){android.app.AlertDialog d=super.create();MenuLayout.watchDialog(d);return d;}
}
