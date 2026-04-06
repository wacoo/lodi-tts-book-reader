package com.gugesoft.lodibookreader;

import android.content.Context;
import android.content.SharedPreferences;

public class SettingsManager {

    private static final String PREF_NAME = "app_settings";

    private static final String KEY_TIMER = "timer_ms";
    private static final String KEY_FADE = "fade_ms";
    private static final String KEY_SHAKE = "shake_intensity";
    private static final String KEY_FONT_SIZE = "font_size";
    private static final String KEY_PAPER_COLOR = "paper_color";
    private static final String KEY_FONT_COLOR = "font_color"; // NEW

    private SharedPreferences prefs;

    public SettingsManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // ===== TIMER =====
    public long getTimerMs() {
        return prefs.getLong(KEY_TIMER, 60 * 1000);
    }
    public void setTimerMs(long value) {
        prefs.edit().putLong(KEY_TIMER, value).apply();
    }

    // ===== FADE =====
    public long getFadeMs() {
        return prefs.getLong(KEY_FADE, 10 * 1000);
    }
    public void setFadeMs(long value) {
        prefs.edit().putLong(KEY_FADE, value).apply();
    }

    // ===== SHAKE =====
    public float getShakeIntensity() {
        return prefs.getFloat(KEY_SHAKE, 12.0f);
    }
    public void setShakeIntensity(float value) {
        prefs.edit().putFloat(KEY_SHAKE, value).apply();
    }

    // ===== FONT SIZE =====
    public int getFontSize() {
        return prefs.getInt(KEY_FONT_SIZE, 16);
    }
    public void setFontSize(int value) {
        prefs.edit().putInt(KEY_FONT_SIZE, value).apply();
    }

    // ===== PAPER COLOR =====
    public int getPaperColor() {
        return prefs.getInt(KEY_PAPER_COLOR, 0xFFFFFFFF); // default white
    }
    public void setPaperColor(int value) {
        prefs.edit().putInt(KEY_PAPER_COLOR, value).apply();
    }

    // ===== FONT COLOR =====
    public int getFontColor() {
        return prefs.getInt(KEY_FONT_COLOR, 0xFF000000); // default black
    }
    public void setFontColor(int value) {
        prefs.edit().putInt(KEY_FONT_COLOR, value).apply();
    }
}
