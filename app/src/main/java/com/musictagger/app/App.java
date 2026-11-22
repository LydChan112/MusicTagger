package com.musictagger.app;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

public class App extends Application {

    public static final String PREFS = "musictagger";
    public static final String KEY_NIGHT = "night_mode";

    @Override
    public void onCreate() {
        super.onCreate();
        int mode = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getInt(KEY_NIGHT, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(mode);
    }
}
