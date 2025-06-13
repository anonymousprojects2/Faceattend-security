package com.example.governmentapp;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.governmentapp.utils.BiometricUtil;
import com.example.governmentapp.utils.SecurityUtil;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

public class GovernmentApp extends Application {
    private static final String TAG = "GovernmentApp";
    
    // Singleton instance
    private static GovernmentApp instance;
    
    // ML Kit Face Detector
    private FaceDetector faceDetector;
    
    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        
        try {
            // Initialize security components
            SecurityUtil.initializeKeyStore();
            BiometricUtil.initializeBiometricKey(this);
            
            // Initialize Firebase
            initializeFirebase();
            
            // Initialize ML Kit Face Detector
            initializeFaceDetector();
            
        } catch (Exception e) {
            Log.e(TAG, "Error during app initialization: " + e.getMessage(), e);
        }
    }
    
    private void initializeFirebase() {
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this);
                Log.d(TAG, "Firebase initialized successfully");
                
                // Initialize Firestore and Storage after Firebase is initialized
                FirebaseFirestore.getInstance();
                FirebaseStorage.getInstance();
                FirebaseAuth.getInstance();
                
                Log.d(TAG, "Firebase Auth, Firestore and Storage initialized");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Firebase: " + e.getMessage(), e);
            // Attempt to recover by reinitializing
            try {
                FirebaseApp.getInstance().delete();
                FirebaseApp.initializeApp(this);
            } catch (Exception ex) {
                Log.e(TAG, "Failed to recover Firebase initialization: " + ex.getMessage(), ex);
            }
        }
    }
    
    private void initializeFaceDetector() {
        try {
            FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                    .build();
            
            faceDetector = FaceDetection.getClient(options);
            Log.d(TAG, "ML Kit Face Detector initialized");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing ML Kit Face Detector: " + e.getMessage(), e);
        }
    }
    
    public static GovernmentApp getInstance() {
        return instance;
    }
    
    public FaceDetector getFaceDetector() {
        return faceDetector;
    }
} 