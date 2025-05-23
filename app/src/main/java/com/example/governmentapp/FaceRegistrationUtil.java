package com.example.governmentapp;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;

/**
 * Utility class to register a user's face by uploading it to Firebase Storage
 * Note: In a real implementation, you'd want to add more security checks and better error handling
 */
public class FaceRegistrationUtil {
    private static final String TAG = "FaceRegistrationUtil";
    
    /**
     * Upload a face image to Firebase Storage for a given user
     * @param context Application context
     * @param imageUri URI of the face image to upload
     * @param callback Callback to handle success/failure
     */
    public static void uploadFaceImage(Context context, Uri imageUri, FaceUploadCallback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.getEmail() == null) {
            if (callback != null) {
                callback.onFailure("User not authenticated");
            }
            return;
        }
        
        // Get the user's email to look up their sevarthId
        String email = user.getEmail();
        
        // Look up the sevarthId in Firestore
        FirebaseFirestore.getInstance().collection("users")
            .whereEqualTo("email", email)
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                if (!queryDocumentSnapshots.isEmpty()) {
                    DocumentSnapshot document = queryDocumentSnapshots.getDocuments().get(0);
                    String sevarthId = document.getString("sevarthId");
                    
                    if (sevarthId == null || sevarthId.isEmpty()) {
                        if (callback != null) {
                            callback.onFailure("SevarthId not found for this user");
                        }
                        return;
                    }
                    
                    // Reference to the face image location in Firebase Storage using sevarthId
                    StorageReference faceImageRef = FirebaseStorage.getInstance()
                            .getReference()
                            .child("faces/" + sevarthId + ".jpg");
                    
                    // Upload the image
                    UploadTask uploadTask = faceImageRef.putFile(imageUri);
                    
                    // Register listeners for success and failure
                    uploadTask
                            .addOnSuccessListener(taskSnapshot -> {
                                // Get the download URL
                                faceImageRef.getDownloadUrl().addOnSuccessListener(downloadUri -> {
                                    if (callback != null) {
                                        callback.onSuccess(downloadUri.toString());
                                    }
                                });
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Face image upload failed", e);
                                if (callback != null) {
                                    callback.onFailure("Failed to upload face image: " + e.getMessage());
                                }
                            });
                } else {
                    if (callback != null) {
                        callback.onFailure("User profile not found");
                    }
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error looking up user", e);
                if (callback != null) {
                    callback.onFailure("Error accessing user data: " + e.getMessage());
                }
            });
    }
    
    /**
     * Callback interface for face image upload
     */
    public interface FaceUploadCallback {
        void onSuccess(String downloadUrl);
        void onFailure(String errorMessage);
    }
} 