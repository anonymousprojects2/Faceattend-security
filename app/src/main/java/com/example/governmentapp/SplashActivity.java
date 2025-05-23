package com.example.governmentapp;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DELAY = 2000; // 2 seconds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        
        // Hide system bars
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        
        setContentView(R.layout.activity_splash);
        
        // Animate the logo and text
        setupAnimations();

        // Delayed navigation to the next screen
        new Handler(Looper.getMainLooper()).postDelayed(this::navigateToNextScreen, SPLASH_DELAY);
    }
    
    private void setupAnimations() {
        ImageView logo = findViewById(R.id.ivLogo);
        TextView appName = findViewById(R.id.tvAppName);
        TextView appSubtitle = findViewById(R.id.tvAppSubtitle);
        TextView developerCredit = findViewById(R.id.tvDeveloperCredit);
        
        // Fade-in animation
        AlphaAnimation fadeIn = new AlphaAnimation(0.0f, 1.0f);
        fadeIn.setDuration(1000);
        
        // Apply animation to views with slight delay between each
        logo.startAnimation(fadeIn);
        
        fadeIn.setStartOffset(300);
        appName.startAnimation(fadeIn);
        
        fadeIn.setStartOffset(500);
        appSubtitle.startAnimation(fadeIn);
        
        fadeIn.setStartOffset(700);
        developerCredit.startAnimation(fadeIn);
    }

    private void navigateToNextScreen() {
        // Check if user is already logged in
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        Intent intent;
        
        if (currentUser != null) {
            // User is already logged in, go to location selection
            intent = new Intent(this, LocationSelectionActivity.class);
        } else {
            // User is not logged in, go to login screen
            intent = new Intent(this, LoginActivity.class);
        }
        
        startActivity(intent);
        finish(); // Close splash activity
    }
} 