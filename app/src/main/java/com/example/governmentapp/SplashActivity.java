package com.example.governmentapp;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SplashActivity extends AppCompatActivity {

    private static final String TAG = "SplashActivity";
    private static final long SPLASH_DELAY = 2000; // 2 seconds
    private boolean isNavigating = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
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
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate: " + e.getMessage(), e);
            // If there's an error, try to navigate to login screen
            navigateToLoginScreen();
        }
    }
    
    private void setupAnimations() {
        try {
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
        } catch (Exception e) {
            Log.e(TAG, "Error in setupAnimations: " + e.getMessage(), e);
        }
    }

    private void navigateToNextScreen() {
        if (isNavigating) return;
        isNavigating = true;
        
        try {
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
        } catch (Exception e) {
            Log.e(TAG, "Error in navigateToNextScreen: " + e.getMessage(), e);
            navigateToLoginScreen();
        }
    }
    
    private void navigateToLoginScreen() {
        try {
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
            finish();
        } catch (Exception e) {
            Log.e(TAG, "Error navigating to login screen: " + e.getMessage(), e);
            // If all else fails, show error and close app
            Toast.makeText(this, "Error initializing app. Please try again.", Toast.LENGTH_LONG).show();
            finish();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up any pending handlers
        new Handler(Looper.getMainLooper()).removeCallbacksAndMessages(null);
    }
} 