package com.habit.tracker;

import android.content.Context;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONArray;
import org.json.JSONObject;

@CapacitorPlugin(name = "PendingActions")
public class PendingActionsPlugin extends Plugin {

    // Returns (and clears) the Yes answers given from notifications while the app was closed
    @PluginMethod
    public void drain(PluginCall call) {
        JSObject r = new JSObject();
        JSONArray arr;
        try {
            arr = new JSONArray(NotifActionReceiver.drain(getContext()));
        } catch (Exception e) {
            arr = new JSONArray();
        }
        r.put("items", arr);
        call.resolve(r);
    }

    // Button titles (language aware) used for snoozed notifications shown natively
    @PluginMethod
    public void setTitles(PluginCall call) {
        try {
            JSONObject t = new JSONObject();
            t.put("yes", call.getString("yes", "Yes"));
            t.put("no", call.getString("no", "No"));
            t.put("snooze", call.getString("snooze", "Snooze"));
            getContext().getSharedPreferences(NotifActionReceiver.PREFS, Context.MODE_PRIVATE)
                    .edit().putString(NotifActionReceiver.KEY_T, t.toString()).apply();
        } catch (Exception e) {
            // ignore
        }
        call.resolve();
    }
}
