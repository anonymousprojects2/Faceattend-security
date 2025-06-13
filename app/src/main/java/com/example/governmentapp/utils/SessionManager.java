package com.example.governmentapp.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Date;

public class SessionManager {
    private static final String TAG = "SessionManager";
    private static final String PREF_NAME = "FaceAttendSecurePrefs";
    private static final String KEY_SESSION_TOKEN = "session_token";
    private static final String KEY_SESSION_EXPIRY = "session_expiry";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USER_ROLE = "user_role";
    private static final long SESSION_TIMEOUT = 30 * 60 * 1000; // 30 minutes

    private final SharedPreferences securePrefs;
    private final Handler sessionHandler;
    private SessionTimeoutListener timeoutListener;

    public SessionManager(Context context) {
        securePrefs = getEncryptedSharedPreferences(context);
        sessionHandler = new Handler(Looper.getMainLooper());
    }

    private SharedPreferences getEncryptedSharedPreferences(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            return EncryptedSharedPreferences.create(
                    context,
                    PREF_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "Error creating encrypted shared preferences", e);
            return null;
        }
    }

    public void startSession(String userId, String userRole) {
        String sessionToken = SecurityUtil.generateSecureToken(32);
        long expiryTime = System.currentTimeMillis() + SESSION_TIMEOUT;

        SharedPreferences.Editor editor = securePrefs.edit();
        editor.putString(KEY_SESSION_TOKEN, sessionToken);
        editor.putLong(KEY_SESSION_EXPIRY, expiryTime);
        editor.putString(KEY_USER_ID, userId);
        editor.putString(KEY_USER_ROLE, userRole);
        editor.apply();

        scheduleSessionTimeout();
    }

    public void refreshSession() {
        if (isSessionValid()) {
            long newExpiryTime = System.currentTimeMillis() + SESSION_TIMEOUT;
            securePrefs.edit().putLong(KEY_SESSION_EXPIRY, newExpiryTime).apply();
            scheduleSessionTimeout();
        }
    }

    public void endSession() {
        securePrefs.edit().clear().apply();
        sessionHandler.removeCallbacksAndMessages(null);
        if (timeoutListener != null) {
            timeoutListener.onSessionTimeout();
        }
    }

    public boolean isSessionValid() {
        String sessionToken = securePrefs.getString(KEY_SESSION_TOKEN, null);
        long expiryTime = securePrefs.getLong(KEY_SESSION_EXPIRY, 0);
        return sessionToken != null && System.currentTimeMillis() < expiryTime;
    }

    public String getUserId() {
        return securePrefs.getString(KEY_USER_ID, null);
    }

    public String getUserRole() {
        return securePrefs.getString(KEY_USER_ROLE, null);
    }

    private void scheduleSessionTimeout() {
        sessionHandler.removeCallbacksAndMessages(null);
        long expiryTime = securePrefs.getLong(KEY_SESSION_EXPIRY, 0);
        long delay = expiryTime - System.currentTimeMillis();

        if (delay > 0) {
            sessionHandler.postDelayed(() -> {
                if (timeoutListener != null) {
                    endSession();
                }
            }, delay);
        }
    }

    public void setSessionTimeoutListener(SessionTimeoutListener listener) {
        this.timeoutListener = listener;
    }

    public interface SessionTimeoutListener {
        void onSessionTimeout();
    }
} 