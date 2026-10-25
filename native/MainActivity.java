package com.habit.tracker;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(FullscreenModePlugin.class);
        registerPlugin(BiometricAuthPlugin.class);
        registerPlugin(PendingActionsPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
