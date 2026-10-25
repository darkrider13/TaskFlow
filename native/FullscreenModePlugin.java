package com.habit.tracker;

import android.content.pm.ActivityInfo;
import android.view.Window;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "FullscreenMode")
public class FullscreenModePlugin extends Plugin {

    private WindowInsetsControllerCompat controller() {
        Window w = getActivity().getWindow();
        return WindowCompat.getInsetsController(w, w.getDecorView());
    }

    // Focus full screen opened: hide bars, allow auto rotate (portrait <-> landscape)
    @PluginMethod
    public void enter(final PluginCall call) {
        getActivity().runOnUiThread(() -> {
            WindowInsetsControllerCompat c = controller();
            c.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            c.hide(WindowInsetsCompat.Type.systemBars());
            getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
            call.resolve();
        });
    }

    // Focus full screen closed: show bars, lock back to portrait
    @PluginMethod
    public void exit(final PluginCall call) {
        getActivity().runOnUiThread(() -> {
            WindowInsetsControllerCompat c = controller();
            c.show(WindowInsetsCompat.Type.systemBars());
            getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            call.resolve();
        });
    }

    // Tap on full screen: show / hide only the status bar
    @PluginMethod
    public void statusBar(final PluginCall call) {
        final boolean show = Boolean.TRUE.equals(call.getBoolean("show", false));
        getActivity().runOnUiThread(() -> {
            WindowInsetsControllerCompat c = controller();
            if (show) c.show(WindowInsetsCompat.Type.statusBars());
            else c.hide(WindowInsetsCompat.Type.statusBars());
            call.resolve();
        });
    }
}
