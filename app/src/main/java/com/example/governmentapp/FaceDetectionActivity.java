package com.example.governmentapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.media.ExifInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.face.FaceLandmark;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FaceDetectionActivity extends AppCompatActivity {
    private static final String TAG = "FaceDetectionActivity";
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = new String[]{Manifest.permission.CAMERA};
    
    // Face detection thresholds
    private static final float FACE_MATCH_THRESHOLD_STRICT = 0.92f;
    private static final float FACE_MATCH_THRESHOLD_NORMAL = 0.82f;
    private static final float FACE_MATCH_THRESHOLD_RELAXED = 0.78f;
    private static final float FACE_MATCH_THRESHOLD = FACE_MATCH_THRESHOLD_NORMAL;

    // Face detection parameters
    private static final float MAX_HEAD_ANGLE = 35.0f;
    private static final float MIN_EYE_OPEN_PROBABILITY = 0.35f;
    
    // Face size validation
    private static final int MIN_FACE_SIZE_PIXELS = 120;  // Minimum face size in pixels
    private static final float MIN_FACE_PROPORTION = 0.15f; // Minimum face size relative to image
    private static final float MAX_FACE_PROPORTION = 0.85f; // Maximum face size relative to image

    // Lighting condition thresholds
    private static final float GOOD_LIGHTING_THRESHOLD = 180.0f;  // Average pixel value for good lighting
    private static final float POOR_LIGHTING_THRESHOLD = 100.0f;  // Average pixel value for poor lighting

    private PreviewView previewView;
    private Button captureButton;
    private LinearProgressIndicator progressIndicator;

    private FaceDetector faceDetector;
    private ImageCapture imageCapture;
    private ProcessCameraProvider cameraProvider;
    private FirebaseStorage storage;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private String attendanceType;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private float lastSimilarityScore = 0.0f;
    private File lastCapturedPhotoFile = null;
    private String lastUserId = null;
    private volatile boolean isProcessingImage = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_detection);
        
        // Initialize views and Firebase instances
        initializeViews();
        initializeFirebase();
        initializeFaceDetector();
        
        // Request camera permissions if not already granted
        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        // Set up capture button click listener with debounce
        captureButton.setOnClickListener(v -> {
            if (!isProcessingImage) {
                captureImage();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Shutdown executor service
        executor.shutdownNow();
        
        // Release camera resources
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        
        // Clean up temporary files
        if (lastCapturedPhotoFile != null && lastCapturedPhotoFile.exists()) {
            lastCapturedPhotoFile.delete();
        }
    }

    private void initializeViews() {
        previewView = findViewById(R.id.viewFinder);
        captureButton = findViewById(R.id.captureButton);
        progressIndicator = findViewById(R.id.progressIndicator);
        
        // Get attendance type from intent
        attendanceType = getIntent().getStringExtra("type");
        if (attendanceType == null) {
            attendanceType = "CHECK_IN"; // Default value
        }
    }

    private void initializeFirebase() {
        storage = FirebaseStorage.getInstance();
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
    }

    private void initializeFaceDetector() {
        try {
            FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                    .setMinFaceSize(MIN_FACE_PROPORTION)
                    .build();
            
            faceDetector = FaceDetection.getClient(options);
            Log.d(TAG, "Face detector initialized with min face size: " + MIN_FACE_PROPORTION);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing face detector: " + e.getMessage(), e);
            Toast.makeText(this, "Error initializing face detection", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private synchronized void setProcessingState(boolean isProcessing) {
        isProcessingImage = isProcessing;
        runOnUiThread(() -> {
            captureButton.setEnabled(!isProcessing);
            progressIndicator.setVisibility(isProcessing ? View.VISIBLE : View.GONE);
        });
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();

                // Set up the preview use case
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                // Set up the image capture use case
                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                // Set up image analysis for face detection
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(executor, new FaceAnalyzer());

                // Select front camera
                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                        .build();

                // Unbind any bound use cases before rebinding
                cameraProvider.unbindAll();

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, imageAnalysis);

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Error starting camera: ", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void captureImage() {
        if (imageCapture == null) return;

        setProcessingState(true);

        // Create temporary file for the image
        File photoFile = new File(getFilesDir(), "face_verification_" + System.currentTimeMillis() + ".jpg");
        // Clean up previous temp file if it exists
        if (lastCapturedPhotoFile != null && lastCapturedPhotoFile.exists()) {
            lastCapturedPhotoFile.delete();
        }
        lastCapturedPhotoFile = photoFile;

        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(
                outputOptions,
                executor,
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        verifyFace(photoFile);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e(TAG, "Image capture failed: ", exception);
                        setProcessingState(false);
                        runOnUiThread(() -> {
                            Toast.makeText(FaceDetectionActivity.this, "Failed to capture image", Toast.LENGTH_SHORT).show();
                        });
                    }
                }
        );
    }

    private void verifyFace(File photoFile) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            showResult(false, "User not logged in");
            return;
        }

        // Get email from current user and then fetch sevarthId from Firestore
        String email = user.getEmail();
        if (email == null) {
            showResult(false, "Invalid user ID");
            return;
        }
        
        Log.d(TAG, "Getting sevarthId for user with email: " + email);
        
        // Query Firestore to get the user's sevarthId
        FirebaseFirestore.getInstance().collection("users")
            .whereEqualTo("email", email)
            .get()
            .addOnSuccessListener(querySnapshot -> {
                if (!querySnapshot.isEmpty()) {
                    try {
                        DocumentSnapshot document = querySnapshot.getDocuments().get(0);
                        String sevarthId = document.getString("sevarthId");
                        
                        if (sevarthId == null || sevarthId.isEmpty()) {
                            Log.e(TAG, "SevarthId not found for user: " + email);
                            showResult(false, "User ID error. Please contact administrator.");
                            return;
                        }
                        
                        // Store sevarthId for retry
                        lastUserId = sevarthId; 
                        
                        Log.d(TAG, "Verifying face for sevarthId: " + sevarthId);
                        
                        // Use sevarthId as storage identifier
                        String storageId = sevarthId;
                        
                        // Reference to the stored face image in Firebase Storage
                        StorageReference faceRef = storage.getReference().child("faces/" + storageId + ".jpg");
                        
                        Log.d(TAG, "Looking for reference image at: faces/" + storageId + ".jpg");

                        // Download the stored face image to compare with timeout handling
                        try {
                            // Show progress while downloading
                            progressIndicator.setProgress(25, true);
                            
                            faceRef.getBytes(Long.MAX_VALUE)
                                .addOnSuccessListener(bytes -> {
                                    Log.d(TAG, "Successfully downloaded reference image of size: " + bytes.length + " bytes");
                                    try {
                                        progressIndicator.setProgress(50, true);
                                        
                                        // Create bitmap of the reference face from Firebase Storage
                                        Bitmap referenceImageBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                                        // Create bitmap of the captured face
                                        Bitmap capturedImageBitmap = BitmapFactory.decodeFile(photoFile.getPath());
                                        
                                        if (referenceImageBitmap == null || capturedImageBitmap == null) {
                                            Log.e(TAG, "Failed to decode bitmaps");
                                            showResult(false, "Failed to process face images");
                                            return;
                                        }
                                        
                                        Log.d(TAG, "Reference image dimensions: " + referenceImageBitmap.getWidth() + "x" + referenceImageBitmap.getHeight());
                                        Log.d(TAG, "Captured image dimensions: " + capturedImageBitmap.getWidth() + "x" + capturedImageBitmap.getHeight());
                                        
                                        // Compare the two face images
                                        compareFaces(referenceImageBitmap, capturedImageBitmap, sevarthId);
                                        
                                    } catch (Exception e) {
                                        Log.e(TAG, "Error processing images for comparison: " + e.getMessage(), e);
                                        showResult(false, "Error processing face images: " + e.getMessage());
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Error downloading stored face image: " + e.getMessage(), e);
                                    showResult(false, "Failed to retrieve stored face image. Would you like to save your current image as reference?", true);
                                });
                        } catch (Exception e) {
                            Log.e(TAG, "Exception in verifyFace: " + e.getMessage(), e);
                            showResult(false, "Error during face verification: " + e.getMessage());
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing user document: " + e.getMessage(), e);
                        showResult(false, "Error processing user data. Please try again.");
                    }
                } else {
                    Log.e(TAG, "User document not found for email: " + email);
                    showResult(false, "User profile not found. Please contact administrator.");
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error finding user document: " + e.getMessage(), e);
                showResult(false, "Error accessing user data: " + e.getMessage());
            });
    }
    
    private void compareFaces(Bitmap referenceFace, Bitmap capturedFace, String userId) {
        try {
            // Calculate adaptive threshold based on lighting conditions
            float adaptiveThreshold = getAdaptiveThreshold(capturedFace);
            Log.d(TAG, "Using adaptive threshold: " + adaptiveThreshold + " for face comparison");

            InputImage referenceImage = InputImage.fromBitmap(referenceFace, 0);
            InputImage capturedImage = InputImage.fromBitmap(capturedFace, 0);
            
            faceDetector.process(referenceImage)
                .addOnSuccessListener(referencefaces -> {
                    if (referencefaces.isEmpty()) {
                        showResult(false, "No face detected in reference image");
                        return;
                    }
                    
                    if (referencefaces.size() > 1) {
                        Log.w(TAG, "Multiple faces found in reference image");
                    }
                    
                    Face refFace = referencefaces.get(0);
                    
                    // Validate reference face size
                    if (!isFaceSizeValid(refFace, referenceFace)) {
                        showResult(false, "Reference face image quality is not sufficient");
                        return;
                    }
                    
                    faceDetector.process(capturedImage)
                        .addOnSuccessListener(capturedFaces -> {
                            if (capturedFaces.isEmpty()) {
                                showResult(false, "No face detected in captured image");
                                return;
                            }
                            
                            if (capturedFaces.size() > 1) {
                                showResult(false, "Multiple faces detected. Please ensure only your face is visible");
                                return;
                            }
                            
                            Face capturedFace1 = capturedFaces.get(0);
                            
                            // Validate captured face size
                            if (!isFaceSizeValid(capturedFace1, capturedFace)) {
                                showResult(false, "Please adjust your distance from the camera");
                                return;
                            }
                            
                            // Continue with existing face comparison logic
                            if (checkHeadPose(capturedFace1)) {
                                boolean faceVerified = simulateFaceComparison(refFace, capturedFace1);
                                if (faceVerified) {
                                    recordAttendance(userId);
                                } else {
                                    showResult(false, "Face verification failed. Please try again");
                                }
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Face detection failed on captured image: ", e);
                            showResult(false, "Face detection failed");
                        });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Face detection failed on reference image: ", e);
                    showResult(false, "Face detection failed");
                });
                
        } catch (Exception e) {
            Log.e(TAG, "Error during face comparison: ", e);
            showResult(false, "Error comparing faces");
        }
    }
    
    private boolean isFaceSizeValid(Face face, Bitmap image) {
        if (face == null || image == null) return false;

        // Get face and image dimensions
        Rect faceBounds = face.getBoundingBox();
        int faceWidth = faceBounds.width();
        int faceHeight = faceBounds.height();
        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();

        // Check absolute size
        if (faceWidth < MIN_FACE_SIZE_PIXELS || faceHeight < MIN_FACE_SIZE_PIXELS) {
            Log.d(TAG, "Face too small: " + faceWidth + "x" + faceHeight + 
                  " (minimum: " + MIN_FACE_SIZE_PIXELS + "x" + MIN_FACE_SIZE_PIXELS + ")");
            return false;
        }

        // Check relative size
        float widthProportion = (float) faceWidth / imageWidth;
        float heightProportion = (float) faceHeight / imageHeight;

        if (widthProportion < MIN_FACE_PROPORTION || heightProportion < MIN_FACE_PROPORTION) {
            Log.d(TAG, "Face too small relative to image: " + 
                  String.format("%.2f", widthProportion * 100) + "% x " + 
                  String.format("%.2f", heightProportion * 100) + "%");
            return false;
        }

        if (widthProportion > MAX_FACE_PROPORTION || heightProportion > MAX_FACE_PROPORTION) {
            Log.d(TAG, "Face too large relative to image: " + 
                  String.format("%.2f", widthProportion * 100) + "% x " + 
                  String.format("%.2f", heightProportion * 100) + "%");
            return false;
        }

        Log.d(TAG, "Face size valid: " + faceWidth + "x" + faceHeight + 
              " (" + String.format("%.2f", widthProportion * 100) + "% x " + 
              String.format("%.2f", heightProportion * 100) + "%)");
        return true;
    }

    private boolean checkHeadPose(Face face) {
        float headAngleX = Math.abs(face.getHeadEulerAngleX());
        float headAngleY = Math.abs(face.getHeadEulerAngleY());
        float headAngleZ = Math.abs(face.getHeadEulerAngleZ());
        
        if (headAngleX > MAX_HEAD_ANGLE || headAngleY > MAX_HEAD_ANGLE) {
            showResult(false, "Please look directly at the camera with your head straight");
            Log.d(TAG, "Head angle too extreme - X: " + headAngleX + ", Y: " + headAngleY + ", Z: " + headAngleZ);
            return false;
        }
        return true;
    }

    // Update the basic implementation of face comparison for better recognition
    private boolean simulateFaceComparison(Face referenceFace, Face capturedFace) {
        Log.d(TAG, "Comparing face characteristics with improved algorithm...");
        
        // Get the adaptive threshold based on current lighting conditions
        float currentThreshold = FACE_MATCH_THRESHOLD_NORMAL; // Default threshold
        android.graphics.drawable.Drawable background = previewView.getBackground();
        if (background instanceof BitmapDrawable) {
            Bitmap backgroundBitmap = ((BitmapDrawable) background).getBitmap();
            if (backgroundBitmap != null) {
                currentThreshold = getAdaptiveThreshold(backgroundBitmap);
            }
        }
        Log.d(TAG, "Using adaptive threshold: " + currentThreshold + " for face comparison");
        
        // Advanced face comparison with weighted features and multiple checks
        float similarityScore = 0.0f;
        float totalWeight = 0.0f;
        
        // Store individual scores for debugging
        float ratioSimilarity = 0.0f;
        float leftEyeSimilarity = 0.0f;
        float rightEyeSimilarity = 0.0f;
        float smileSimilarity = 0.0f;
        float angleSimX = 0.0f;
        float angleSimY = 0.0f;
        float angleSimZ = 0.0f;
        
        // ------ INITIAL QUALITY CHECKS ------
        // 1. Check if captured face quality is sufficient
        float refWidth = referenceFace.getBoundingBox().width();
        float refHeight = referenceFace.getBoundingBox().height();
        float captWidth = capturedFace.getBoundingBox().width();
        float captHeight = capturedFace.getBoundingBox().height();
        
        // DEBUG: Log actual face measurements
        Log.d(TAG, "DEBUG: Reference face dimensions: " + refWidth + "x" + refHeight);
        Log.d(TAG, "DEBUG: Captured face dimensions: " + captWidth + "x" + captHeight);
        
        // Reject if face is too small
        /*
        if (captWidth < MIN_FACE_SIZE || captHeight < MIN_FACE_SIZE) {
            Log.d(TAG, "Face too small for accurate verification: " + captWidth + "x" + captHeight);
            return false;
        }
        */
        
        // 2. Check if eyes are sufficiently open for reliable recognition
        if (capturedFace.getRightEyeOpenProbability() != null && 
            capturedFace.getLeftEyeOpenProbability() != null) {
            float leftEyeOpen = capturedFace.getLeftEyeOpenProbability();
            float rightEyeOpen = capturedFace.getRightEyeOpenProbability();
            
            if (leftEyeOpen < MIN_EYE_OPEN_PROBABILITY && rightEyeOpen < MIN_EYE_OPEN_PROBABILITY) {
                Log.d(TAG, "Eyes not open enough for reliable verification. Left: " + 
                      leftEyeOpen + ", Right: " + rightEyeOpen);
                return false;
            }
        }
        
        // 3. Check if head angle is within acceptable range
        float headAngleX = Math.abs(capturedFace.getHeadEulerAngleX()); // Up/down
        float headAngleY = Math.abs(capturedFace.getHeadEulerAngleY()); // Left/right
        float headAngleZ = Math.abs(capturedFace.getHeadEulerAngleZ()); // Tilt
        
        if (headAngleX > MAX_HEAD_ANGLE || headAngleY > MAX_HEAD_ANGLE) {
            Log.d(TAG, "Head angle too extreme - X: " + headAngleX + ", Y: " + headAngleY + ", Z: " + headAngleZ);
            return false;
        }
        
        // ------ FACE PROPORTIONS CHECK ------
        // 4. Check fundamental face proportions match
        boolean proportionsMatch = checkFacialProportionsMatch(referenceFace, capturedFace);
        if (!proportionsMatch) {
            Log.d(TAG, "Face proportions do not match - rejecting");
            return false;
        }
        
        // ------ FACIAL STRUCTURE COMPARISON (heavy weight) ------
        // 5. Compare ratio of width/height (face shape is a strong identifier)
        float refRatio = refWidth / refHeight;
        float captRatio = captWidth / captHeight;
        float ratioDiff = Math.abs(refRatio - captRatio);
        ratioSimilarity = Math.max(0, 1.0f - (ratioDiff * 4.5f)); // Moderately strict penalty
        
        float ratioWeight = 6.0f; // High weight as face shape is important for identity
        similarityScore += ratioSimilarity * ratioWeight;
        totalWeight += ratioWeight;
        Log.d(TAG, "Face ratio similarity: " + ratioSimilarity + " (weight: " + ratioWeight + ")");
        
        // ------ FACIAL FEATURES COMPARISON (medium weight) ------
        // 6. Compare eye openness - somewhat stable feature
        float eyeWeight = 2.5f; // Moderate weight 
        if (referenceFace.getLeftEyeOpenProbability() != null && 
            capturedFace.getLeftEyeOpenProbability() != null) {
            float eyeDiff = Math.abs(referenceFace.getLeftEyeOpenProbability() - 
                                     capturedFace.getLeftEyeOpenProbability());
            leftEyeSimilarity = (1.0f - eyeDiff);
            similarityScore += leftEyeSimilarity * eyeWeight;
            totalWeight += eyeWeight;
            Log.d(TAG, "Left eye similarity: " + leftEyeSimilarity + " (weight: " + eyeWeight + ")");
        }
        
        if (referenceFace.getRightEyeOpenProbability() != null && 
            capturedFace.getRightEyeOpenProbability() != null) {
            float eyeDiff = Math.abs(referenceFace.getRightEyeOpenProbability() - 
                                     capturedFace.getRightEyeOpenProbability());
            rightEyeSimilarity = (1.0f - eyeDiff);
            similarityScore += rightEyeSimilarity * eyeWeight;
            totalWeight += eyeWeight;
            Log.d(TAG, "Right eye similarity: " + rightEyeSimilarity + " (weight: " + eyeWeight + ")");
        }
        
        // 7. Compare smile state - less important as it changes easily
        float smileWeight = 0.5f; 
        if (referenceFace.getSmilingProbability() != null && 
            capturedFace.getSmilingProbability() != null) {
            float smileDiff = Math.abs(referenceFace.getSmilingProbability() - 
                                        capturedFace.getSmilingProbability());
            smileSimilarity = (1.0f - smileDiff);
            similarityScore += smileSimilarity * smileWeight;
            totalWeight += smileWeight;
            Log.d(TAG, "Smile similarity: " + smileSimilarity + " (weight: " + smileWeight + ")");
        }
        
        // ------ HEAD POSITION COMPARISON (very high weight) ------
        // 8. Compare head rotation angles - stable structural feature
        float headAngleWeight = 5.5f; // Reduced weight (from 6.5f) to be more permissive with angles
        
        float angleDiffX = Math.abs(referenceFace.getHeadEulerAngleX() - capturedFace.getHeadEulerAngleX());
        float angleDiffY = Math.abs(referenceFace.getHeadEulerAngleY() - capturedFace.getHeadEulerAngleY());
        float angleDiffZ = Math.abs(referenceFace.getHeadEulerAngleZ() - capturedFace.getHeadEulerAngleZ());
        
        // Log head angles for debugging
        Log.d(TAG, "DEBUG: Reference head angles - X: " + referenceFace.getHeadEulerAngleX() + 
              ", Y: " + referenceFace.getHeadEulerAngleY() + 
              ", Z: " + referenceFace.getHeadEulerAngleZ());
        Log.d(TAG, "DEBUG: Captured head angles - X: " + capturedFace.getHeadEulerAngleX() + 
              ", Y: " + capturedFace.getHeadEulerAngleY() + 
              ", Z: " + capturedFace.getHeadEulerAngleZ());
        
        // Balanced penalty for angle differences
        angleSimX = Math.max(0, 1.0f - (angleDiffX / 20.0f)); // More permissive (increased from 16.0f)
        angleSimY = Math.max(0, 1.0f - (angleDiffY / 20.0f)); // More permissive (increased from 16.0f)
        angleSimZ = Math.max(0, 1.0f - (angleDiffZ / 16.0f)); // More permissive (increased from 13.0f)
        
        // Add individual angle similarities with slightly reduced weight
        similarityScore += angleSimX * headAngleWeight;
        similarityScore += angleSimY * headAngleWeight;
        similarityScore += angleSimZ * headAngleWeight;
        totalWeight += headAngleWeight * 3;
        
        Log.d(TAG, "Head angle similarity - X: " + angleSimX + ", Y: " + angleSimY + 
              ", Z: " + angleSimZ + " (weight each: " + headAngleWeight + ")");
        
        // ------ FINAL SCORE CALCULATION ------
        // Calculate final similarity score with weighted features
        float finalScore = (totalWeight > 0) ? similarityScore / totalWeight : 0;
        Log.d(TAG, "Final weighted similarity score: " + finalScore + 
              " (threshold: " + currentThreshold + ")");
        
        // Generate detailed summary for debugging
        Log.d(TAG, "VERIFICATION SUMMARY:");
        Log.d(TAG, "------------ Feature Scores ------------");
        Log.d(TAG, "Face ratio similarity: " + ratioSimilarity + " (weight: " + ratioWeight + ")");
        Log.d(TAG, "Left eye similarity: " + leftEyeSimilarity + " (weight: " + eyeWeight + ")");
        Log.d(TAG, "Right eye similarity: " + rightEyeSimilarity + " (weight: " + eyeWeight + ")");
        Log.d(TAG, "Smile similarity: " + smileSimilarity + " (weight: " + smileWeight + ")");
        Log.d(TAG, "Head angle X similarity: " + angleSimX + " (weight: " + headAngleWeight + ")");
        Log.d(TAG, "Head angle Y similarity: " + angleSimY + " (weight: " + headAngleWeight + ")");
        Log.d(TAG, "Head angle Z similarity: " + angleSimZ + " (weight: " + headAngleWeight + ")");
        Log.d(TAG, "Final similarity score: " + finalScore);
        Log.d(TAG, "Required threshold: " + currentThreshold);
        Log.d(TAG, "------------------------------------");
        
        // Apply threshold check with detailed failure reason
        if (finalScore < currentThreshold) {
            // Identify which feature is causing the biggest problem
            float[] scores = {ratioSimilarity, leftEyeSimilarity, rightEyeSimilarity, 
                             smileSimilarity, angleSimX, angleSimY, angleSimZ};
            String[] featureNames = {"Face proportions", "Left eye", "Right eye", 
                                    "Smile", "Head tilt up/down", "Head rotation left/right", "Head tilt sideways"};
            
            float minScore = 1.0f;
            String worstFeature = "Unknown";
            
            for (int i = 0; i < scores.length; i++) {
                if (scores[i] > 0 && scores[i] < minScore) {
                    minScore = scores[i];
                    worstFeature = featureNames[i];
                }
            }
            
            Log.d(TAG, "Score below threshold - verification failed with score: " + finalScore);
            Log.d(TAG, "Weakest feature: " + worstFeature + " with score: " + minScore);
            return false;
        }
        
        // Additional check for score near threshold - balanced approach
        if (finalScore < currentThreshold + 0.05f) {
            // Focus on the most stable features with balanced threshold
            float criticalFeatureAvg = (angleSimX + angleSimY + angleSimZ + ratioSimilarity) / 4.0f;
            if (criticalFeatureAvg < 0.76f) { // Slightly more permissive (was 0.78)
                Log.d(TAG, "Critical features don't match well enough. Rejecting with critical avg: " + 
                      criticalFeatureAvg);
                return false;
            }
        }
        
        // Store the last calculated score for logging purposes
        lastSimilarityScore = finalScore;
        
        Log.d(TAG, "Face verification PASSED with final score: " + finalScore + 
              " (threshold: " + currentThreshold + ")");
        return true;
    }

    // Update the facial proportions matching method to be balanced
    private boolean checkFacialProportionsMatch(Face referenceFace, Face capturedFace) {
        // This method checks fundamental structural aspects of the face
        // that should be consistent regardless of expression
        
        float refWidth = referenceFace.getBoundingBox().width();
        float refHeight = referenceFace.getBoundingBox().height();
        float captWidth = capturedFace.getBoundingBox().width();
        float captHeight = capturedFace.getBoundingBox().height();
        
        // 1. Calculate and compare face aspect ratio (width/height)
        float refRatio = refWidth / refHeight;
        float captRatio = captWidth / captHeight;
        float ratioDifference = Math.abs(refRatio - captRatio);
        
        // 2. Compare head rotation angles - should be similar for structural comparison
        float angleXDiff = Math.abs(referenceFace.getHeadEulerAngleX() - capturedFace.getHeadEulerAngleX());
        float angleYDiff = Math.abs(referenceFace.getHeadEulerAngleY() - capturedFace.getHeadEulerAngleY());
        float angleZDiff = Math.abs(referenceFace.getHeadEulerAngleZ() - capturedFace.getHeadEulerAngleZ());
        
        // 3. Check eye spacing - distance between eyes relative to face width
        // This is one of the most stable facial proportions
        Float refLeftEyeX = null;
        Float refRightEyeX = null;
        Float captLeftEyeX = null;
        Float captRightEyeX = null;
        
        FaceLandmark refLeftEye = referenceFace.getLandmark(FaceLandmark.LEFT_EYE);
        FaceLandmark refRightEye = referenceFace.getLandmark(FaceLandmark.RIGHT_EYE);
        FaceLandmark captLeftEye = capturedFace.getLandmark(FaceLandmark.LEFT_EYE);
        FaceLandmark captRightEye = capturedFace.getLandmark(FaceLandmark.RIGHT_EYE);
        
        boolean eyeSpacingMatch = true;
        if (refLeftEye != null && refRightEye != null && captLeftEye != null && captRightEye != null) {
            refLeftEyeX = refLeftEye.getPosition().x;
            refRightEyeX = refRightEye.getPosition().x;
            captLeftEyeX = captLeftEye.getPosition().x;
            captRightEyeX = captRightEye.getPosition().x;
            
            // Calculate eye spacing relative to face width
            float refEyeSpacing = Math.abs(refLeftEyeX - refRightEyeX) / refWidth;
            float captEyeSpacing = Math.abs(captLeftEyeX - captRightEyeX) / captWidth;
            
            // Compare relative eye spacing with more tolerance
            float eyeSpacingDiff = Math.abs(refEyeSpacing - captEyeSpacing);
            eyeSpacingMatch = eyeSpacingDiff < 0.12; // Increased tolerance from 0.09 to 0.12
            
            Log.d(TAG, "Eye spacing check: " + eyeSpacingMatch + 
                  " (ref: " + refEyeSpacing + ", capt: " + captEyeSpacing + 
                  ", diff: " + eyeSpacingDiff + ")");
        }
        
        // 4. Check facial expressions with more tolerance
        boolean expressionMatch = true;
        if (referenceFace.getSmilingProbability() != null && 
            capturedFace.getSmilingProbability() != null) {
            float smileDiff = Math.abs(referenceFace.getSmilingProbability() - 
                                      capturedFace.getSmilingProbability());
            if (smileDiff > 0.85) { // More permissive threshold (increased from 0.75)
                Log.d(TAG, "Expression differs drastically: " + smileDiff);
                expressionMatch = false;
            }
        }
        
        // More permissive thresholds for proportions check
        boolean proportionsMatch = (ratioDifference < 0.15) && // More permissive (increased from 0.12)
                                   (angleXDiff < 32) && (angleYDiff < 32) && // More permissive (increased from 25)
                                   eyeSpacingMatch && expressionMatch;
        
        Log.d(TAG, "Proportions match check: " + proportionsMatch + 
              " (ratio diff: " + ratioDifference + 
              ", angle diffs - X: " + angleXDiff + 
              ", Y: " + angleYDiff + 
              ", Z: " + angleZDiff + 
              ", eye spacing match: " + eyeSpacingMatch +
              ", expression match: " + expressionMatch + ")");
        
        return proportionsMatch;
    }

    private void recordAttendance(String userId) {
        Log.d(TAG, "Attempting to record attendance for user: " + userId + " with type: " + attendanceType);
        Log.d(TAG, "Face verification passed with similarity score: " + lastSimilarityScore);
        
        // Get current date and time
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        String date = dateFormat.format(new Date());
        String time = timeFormat.format(new Date());

        // Get location name from intent if available
        String locationName = getIntent().getStringExtra("location_name");
        if (locationName == null) {
            locationName = "Unknown Location";
        }

        // Create attendance record
        Map<String, Object> attendance = new HashMap<>();
        attendance.put("userId", userId); // This must match what AttendanceHistoryActivity looks for
        attendance.put("date", date);
        attendance.put("time", time);
        attendance.put("type", attendanceType);
        attendance.put("verified", true);
        attendance.put("similarityScore", lastSimilarityScore);
        attendance.put("locationName", locationName); // Add location name to match history query
        
        Log.d(TAG, "Attendance record created: " + attendance.toString());

        // Check if Firestore is initialized
        if (db == null) {
            Log.e(TAG, "Firestore database is null. Reinitializing...");
            db = FirebaseFirestore.getInstance();
            if (db == null) {
                showResult(false, "Failed to initialize Firestore database");
                return;
            }
        }

        // Try to use a different collection name to avoid potential permission issues
        String collectionName = "user_attendance";

        // Add attendance record to Firestore
        db.collection(collectionName)
                .add(attendance)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "Attendance recorded with ID: " + documentReference.getId());
                    showResult(true, "Attendance recorded successfully");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error recording attendance: " + e.getMessage(), e);
                    
                    // Try with a different collection as fallback
                    if (collectionName.equals("user_attendance")) {
                        Log.d(TAG, "Trying fallback collection 'app_data'");
                        db.collection("app_data")
                            .add(attendance)
                            .addOnSuccessListener(documentReference -> {
                                Log.d(TAG, "Attendance recorded with fallback collection ID: " + documentReference.getId());
                                showResult(true, "Attendance recorded successfully with fallback");
                            })
                            .addOnFailureListener(e2 -> {
                                Log.e(TAG, "Error with fallback collection: " + e2.getMessage(), e2);
                                showResult(false, "Failed to record attendance: " + e.getMessage() + 
                                    ". Please check Firestore rules and network connection.");
                            });
                    } else {
                        showResult(false, "Failed to record attendance: " + e.getMessage() + 
                            ". Please check Firestore rules and network connection.");
                    }
                });
    }

    // Method to save the current image as reference
    private void saveCurrentImageAsReference() {
        if (lastCapturedPhotoFile == null || lastUserId == null) {
            Toast.makeText(this, "No image available to save", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Show progress
        progressIndicator.setVisibility(View.VISIBLE);
        
        // Use sevarthId directly (already stored in lastUserId from verifyFace)
        String storageId = lastUserId;
        
        // Reference to the stored face image in Firebase Storage
        StorageReference faceRef = storage.getReference().child("faces/" + storageId + ".jpg");
        
        try {
            // Upload the file
            faceRef.putFile(android.net.Uri.fromFile(lastCapturedPhotoFile))
                .addOnSuccessListener(taskSnapshot -> {
                    progressIndicator.setVisibility(View.GONE);
                    Toast.makeText(FaceDetectionActivity.this, "Reference image saved successfully", Toast.LENGTH_LONG).show();
                    
                    // Since we just saved this as the reference, verification succeeds automatically
                    recordAttendance(lastUserId);
                })
                .addOnFailureListener(e -> {
                    progressIndicator.setVisibility(View.GONE);
                    Toast.makeText(FaceDetectionActivity.this, "Failed to save reference image: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
        } catch (Exception e) {
            progressIndicator.setVisibility(View.GONE);
            Toast.makeText(this, "Error saving reference image: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // Enhanced showResult method with option to save reference
    private void showResult(boolean success, String message) {
        showResult(success, message, false);
    }
    
    private void showResult(boolean success, String message, boolean offerSaveReference) {
        runOnUiThread(() -> {
            progressIndicator.setVisibility(View.GONE);
            captureButton.setEnabled(true);

            if (!success && offerSaveReference) {
                // Show dialog offering to save this as a reference image
                androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
                builder.setTitle("Face Not Recognized");
                builder.setMessage(message + " Would you like to save your current image as a new reference?");
                builder.setPositiveButton("Save as Reference", (dialog, which) -> {
                    saveCurrentImageAsReference();
                    dialog.dismiss();
                });
                builder.setNegativeButton("Cancel", (dialog, which) -> {
                    dialog.dismiss();
                });
                builder.show();
            } else if (!success) {
                // Show a more detailed dialog with guidance instead of just a toast
                androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
                builder.setTitle("Verification Failed");
                
                // Enhanced message with tips
                StringBuilder enhancedMessage = new StringBuilder(message);
                enhancedMessage.append("\n\nTips for successful verification:");
                enhancedMessage.append("\n• Ensure good lighting on your face");
                enhancedMessage.append("\n• Look directly at the camera");
                enhancedMessage.append("\n• Keep a neutral expression");
                enhancedMessage.append("\n• Remove glasses or obstacles");
                enhancedMessage.append("\n• Ensure only your face is in the frame");
                
                builder.setMessage(enhancedMessage.toString());
                builder.setPositiveButton("Try Again", (dialog, which) -> dialog.dismiss());
                builder.show();
                
                // Log for debugging purposes
                Log.d(TAG, "Face verification failed with reason: " + message);
            } else {
                // Just show toast for success messages
                Toast.makeText(FaceDetectionActivity.this, message, Toast.LENGTH_SHORT).show();
            }

            if (success) {
                // Return to the attendance screen with result
                Intent resultIntent = new Intent();
                resultIntent.putExtra("success", true);
                resultIntent.putExtra("type", attendanceType);
                setResult(RESULT_OK, resultIntent);
                finish();
            }
        });
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "Camera permissions are required for face verification", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private class FaceAnalyzer implements ImageAnalysis.Analyzer {
        @Override
        public void analyze(@NonNull ImageProxy imageProxy) {
            InputImage image = InputImage.fromMediaImage(
                    imageProxy.getImage(),
                    imageProxy.getImageInfo().getRotationDegrees()
            );

            faceDetector.process(image)
                    .addOnSuccessListener(faces -> {
                        // Highlight detected faces or provide UI feedback
                        // Enable capture button only when a face is detected
                        captureButton.setEnabled(!faces.isEmpty());
                    })
                    .addOnFailureListener(e -> Log.e(TAG, "Face detection failed: ", e))
                    .addOnCompleteListener(task -> imageProxy.close());
        }
    }

    private float calculateLightingCondition(Bitmap image) {
        if (image == null) return 0;

        // Convert bitmap to grayscale and calculate average brightness
        int width = image.getWidth();
        int height = image.getHeight();
        int pixelCount = width * height;
        float totalBrightness = 0;

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int pixel = image.getPixel(x, y);
                // Convert RGB to grayscale using standard coefficients
                float brightness = (Color.red(pixel) * 0.299f + 
                                  Color.green(pixel) * 0.587f + 
                                  Color.blue(pixel) * 0.114f);
                totalBrightness += brightness;
            }
        }

        float averageBrightness = totalBrightness / pixelCount;
        Log.d(TAG, "Average image brightness: " + averageBrightness);
        return averageBrightness;
    }

    private float getAdaptiveThreshold(Bitmap image) {
        float lightingCondition = calculateLightingCondition(image);
        
        if (lightingCondition >= GOOD_LIGHTING_THRESHOLD) {
            Log.d(TAG, "Good lighting detected, using strict threshold");
            return FACE_MATCH_THRESHOLD_STRICT;
        } else if (lightingCondition <= POOR_LIGHTING_THRESHOLD) {
            Log.d(TAG, "Poor lighting detected, using relaxed threshold");
            return FACE_MATCH_THRESHOLD_RELAXED;
        } else {
            // Linear interpolation between thresholds based on lighting
            float lightingRange = GOOD_LIGHTING_THRESHOLD - POOR_LIGHTING_THRESHOLD;
            float thresholdRange = FACE_MATCH_THRESHOLD_STRICT - FACE_MATCH_THRESHOLD_RELAXED;
            float lightingFactor = (lightingCondition - POOR_LIGHTING_THRESHOLD) / lightingRange;
            float adaptiveThreshold = FACE_MATCH_THRESHOLD_RELAXED + (thresholdRange * lightingFactor);
            
            Log.d(TAG, "Moderate lighting detected, using adaptive threshold: " + adaptiveThreshold);
            return adaptiveThreshold;
        }
    }
} 