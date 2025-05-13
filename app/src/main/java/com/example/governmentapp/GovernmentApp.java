package com.example.governmentapp;

import android.app.Application;
import com.google.firebase.FirebaseApp;

public class GovernmentApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        FirebaseApp.initializeApp(this);
    }
} 