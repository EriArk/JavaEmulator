/* Copyright 2026. Licensed under the Apache License, Version 2.0. */
package ru.playsoftware.j2meloader.input;

import android.app.Dialog;
import android.content.Context;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Window;
import android.view.WindowManager;
import java.util.ArrayList;
import ru.playsoftware.j2meloader.config.ProfileModel;

/** Edits a detached draft; Save here does not commit the game profile. */
public final class MapperDialog extends Dialog {
    private final ControllerMapperView mapper;
    public MapperDialog(Context context, String title, ProfileModel draft, Runnable closed) {
        super(context);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        draft.ensureKeyMappingProfiles();
        mapper = new ControllerMapperView(context, title, draft.keyMappingProfiles, draft.activeKeyMappingProfile,
                new ControllerMapperView.Listener() {
                    public void save(ArrayList<ProfileModel.KeyMappingProfile> profiles, int active) {
                        draft.keyMappingProfiles = profiles; draft.setActiveKeyMappingProfile(active); dismiss();
                    }
                    public void cancel() { dismiss(); }
                });
        mapper.setSaveLabel("Use mapping");
        setContentView(mapper);
        setOnDismissListener(d -> closed.run());
    }
    @Override public void show() {
        super.show();
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xff0e1418));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(5894);
        getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        return mapper.handleKey(event) || super.dispatchKeyEvent(event);
    }
    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if ((event.getSource() & android.view.InputDevice.SOURCE_JOYSTICK) != 0) {
            mapper.captureAxes(event); return true;
        }
        return super.dispatchGenericMotionEvent(event);
    }
}
