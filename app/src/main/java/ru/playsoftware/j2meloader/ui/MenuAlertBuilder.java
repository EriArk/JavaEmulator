/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.ui;
public class MenuAlertBuilder extends androidx.appcompat.app.AlertDialog.Builder {
    public MenuAlertBuilder(android.content.Context c){super(c);}
    public MenuAlertBuilder(android.content.Context c,int theme){super(c,theme);}
    @Override public androidx.appcompat.app.AlertDialog create(){androidx.appcompat.app.AlertDialog d=super.create();MenuLayout.watchDialog(d);return d;}
}
