package com.example.governmentapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class AttendanceActivity extends AppCompatActivity {
    private static final int REQUEST_CODE_FACE_DETECTION = 100;
    private static final String TAG = "AttendanceActivity";
    
    private TextView welcomeText;
    private TextView dateText;
    private TextView officeText;
    private MaterialButton checkInButton;
    private MaterialButton checkOutButton;
    private MaterialButton viewHistoryButton;
    private MaterialButton backButton;
    private MaterialButton logoutButton;
    
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    
    // Selected location details
    private String locationId;
    private String locationName;
    private double locationLatitude;
    private double locationLongitude;
    private int locationRadius;
    
    // Activity result launcher for face detection
    private final ActivityResultLauncher<Intent> faceDetectionLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    boolean success = result.getData().getBooleanExtra("success", false);
                    String type = result.getData().getStringExtra("type");
                    
                    if (success) {
                        // Record the attendance in Firestore
                        recordAttendance(type);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attendance);
        
        // Force light mode
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        // Initialize Firebase Auth and Firestore
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        
        // Get location information from intent
        Intent intent = getIntent();
        if (intent != null) {
            locationId = intent.getStringExtra("location_id");
            locationName = intent.getStringExtra("location_name");
            locationLatitude = intent.getDoubleExtra("location_latitude", 0);
            locationLongitude = intent.getDoubleExtra("location_longitude", 0);
            locationRadius = intent.getIntExtra("location_radius", 100);
        }
        
        // If no location was passed, return to location selection
        if (locationId == null || locationName == null) {
            Toast.makeText(this, "No location selected", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LocationSelectionActivity.class));
            finish();
            return;
        }
        
        // Initialize views
        welcomeText = findViewById(R.id.welcomeTextView);
        dateText = findViewById(R.id.dateTextView);
        officeText = findViewById(R.id.officeTextView);
        checkInButton = findViewById(R.id.checkInButton);
        checkOutButton = findViewById(R.id.checkOutButton);
        viewHistoryButton = findViewById(R.id.viewHistoryButton);
        backButton = findViewById(R.id.backButton);
        logoutButton = findViewById(R.id.logoutButton);

        // Set up the user details
        setupUserInfo();
        
        // Set date
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault());
        String currentDate = dateFormat.format(new Date());
        dateText.setText(currentDate);
        
        // Set office name from the selected location
        officeText.setText("Office: " + locationName);
        
        // Set click listeners
        checkInButton.setOnClickListener(v -> launchFaceDetection("check in"));
        checkOutButton.setOnClickListener(v -> launchFaceDetection("check out"));
        viewHistoryButton.setOnClickListener(v -> viewAttendanceHistory());
        if (backButton != null) {
            backButton.setOnClickListener(v -> goBack());
        }
        logoutButton.setOnClickListener(v -> logout());
    }
    
    private void setupUserInfo() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            String displayName = user.getDisplayName();
            if (displayName == null || displayName.isEmpty()) {
                // Extract name from email if display name is not set
                String email = user.getEmail();
                if (email != null && !email.isEmpty()) {
                    displayName = email.substring(0, email.indexOf('@'));
                    // Capitalize first letter
                    displayName = displayName.substring(0, 1).toUpperCase() + displayName.substring(1);
                } else {
                    displayName = "User";
                }
            }
            welcomeText.setText("Welcome, " + displayName);
        } else {
            // User is not logged in, redirect back to login
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        }
    }
    
    private void launchFaceDetection(String type) {
        Intent intent = new Intent(this, FaceDetectionActivity.class);
        intent.putExtra("attendance_type", type);
        intent.putExtra("location_id", locationId);
        intent.putExtra("location_name", locationName);
        faceDetectionLauncher.launch(intent);
    }
    
    private void recordAttendance(String type) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;
        
        // Create a record of the attendance with the location
        Map<String, Object> attendanceRecord = new HashMap<>();
        attendanceRecord.put("userId", user.getUid());
        attendanceRecord.put("userEmail", user.getEmail());
        attendanceRecord.put("type", type);
        attendanceRecord.put("timestamp", new Date());
        attendanceRecord.put("locationId", locationId);
        attendanceRecord.put("locationName", locationName);
        
        // Add to Firestore
        db.collection("attendance")
            .add(attendanceRecord)
            .addOnSuccessListener(documentReference -> {
                Toast.makeText(this, type + " successful", Toast.LENGTH_SHORT).show();
            })
            .addOnFailureListener(e -> {
                Toast.makeText(this, "Failed to record " + type + ". Please try again.", Toast.LENGTH_SHORT).show();
            });
    }
    
    private void viewAttendanceHistory() {
        // Navigate to attendance history screen
        Intent intent = new Intent(this, AttendanceHistoryActivity.class);
        startActivity(intent);
    }
    
    private void goBack() {
        // Go back to location selection screen
        startActivity(new Intent(this, LocationSelectionActivity.class));
        finish();
    }
    
    private void logout() {
        FirebaseAuth.getInstance().signOut();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }
} 