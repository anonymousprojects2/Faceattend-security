package com.example.governmentapp;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EditLocationActivity extends AppCompatActivity {

    private TextInputEditText officeNameEditText;
    private MaterialAutoCompleteTextView talukaSpinner;
    private TextInputEditText latitudeEditText;
    private TextInputEditText longitudeEditText;
    private TextInputEditText radiusEditText;
    private Button cancelButton;
    private Button saveButton;

    private FirebaseFirestore db;
    private String officeId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_location);

        // Get office ID from intent
        officeId = getIntent().getStringExtra("office_id");
        if (officeId == null) {
            Toast.makeText(this, "Error: Office ID not provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize Firestore
        db = FirebaseFirestore.getInstance();

        // Initialize views
        initializeViews();

        // Setup taluka dropdown
        setupTalukaDropdown();

        // Load office data
        loadOfficeData();

        // Setup button listeners
        setupButtonListeners();
    }

    private void initializeViews() {
        officeNameEditText = findViewById(R.id.officeNameEditText);
        talukaSpinner = findViewById(R.id.talukaSpinner);
        latitudeEditText = findViewById(R.id.latitudeEditText);
        longitudeEditText = findViewById(R.id.longitudeEditText);
        radiusEditText = findViewById(R.id.radiusEditText);
        cancelButton = findViewById(R.id.cancelButton);
        saveButton = findViewById(R.id.addButton);
        
        // Change button text from "Add" to "Save"
        saveButton.setText("Save");
    }

    private void setupTalukaDropdown() {
        // Sample taluka data - in a real app, this would come from your database
        List<String> talukas = new ArrayList<>();
        talukas.add("Hingoli");
        talukas.add("Sengaon");

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, talukas);
        talukaSpinner.setAdapter(adapter);
    }

    private void loadOfficeData() {
        db.collection("locations").document(officeId)
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    Map<String, Object> officeData = documentSnapshot.getData();
                    if (officeData != null) {
                        // Populate the form with office data
                        String officeName = (String) officeData.get("officeName");
                        String taluka = (String) officeData.get("taluka");
                        Double latitude = (Double) officeData.get("latitude");
                        Double longitude = (Double) officeData.get("longitude");
                        
                        // Handle radius which could be Integer, Long or Double
                        Object radiusObj = officeData.get("radius");
                        int radius = 100; // Default
                        if (radiusObj instanceof Long) {
                            radius = ((Long) radiusObj).intValue();
                        } else if (radiusObj instanceof Integer) {
                            radius = (Integer) radiusObj;
                        } else if (radiusObj instanceof Double) {
                            radius = ((Double) radiusObj).intValue();
                        }
                        
                        // Set values to form fields
                        officeNameEditText.setText(officeName);
                        
                        // Set taluka spinner selection
                        if (taluka != null && !taluka.isEmpty()) {
                            talukaSpinner.setText(taluka, false);
                        }
                        
                        latitudeEditText.setText(String.valueOf(latitude));
                        longitudeEditText.setText(String.valueOf(longitude));
                        radiusEditText.setText(String.valueOf(radius));
                    }
                } else {
                    Toast.makeText(this, "Office not found", Toast.LENGTH_SHORT).show();
                    finish();
                }
            })
            .addOnFailureListener(e -> {
                Log.e("EditLocation", "Error loading office data", e);
                Toast.makeText(this, "Error loading office data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                finish();
            });
    }

    private void setupButtonListeners() {
        cancelButton.setOnClickListener(v -> finish());

        saveButton.setOnClickListener(v -> {
            if (validateForm()) {
                updateLocation();
            }
        });
    }

    private boolean validateForm() {
        boolean valid = true;

        // Validate office name
        String officeName = officeNameEditText.getText().toString().trim();
        if (TextUtils.isEmpty(officeName)) {
            officeNameEditText.setError("Office name is required");
            valid = false;
        } else {
            officeNameEditText.setError(null);
        }

        // Validate taluka
        String taluka = talukaSpinner.getText().toString().trim();
        if (TextUtils.isEmpty(taluka)) {
            talukaSpinner.setError("Please select a taluka");
            valid = false;
        } else {
            talukaSpinner.setError(null);
        }

        // Validate latitude
        String latitude = latitudeEditText.getText().toString().trim();
        if (TextUtils.isEmpty(latitude)) {
            latitudeEditText.setError("Latitude is required");
            valid = false;
        } else {
            try {
                double lat = Double.parseDouble(latitude);
                if (lat < -90 || lat > 90) {
                    latitudeEditText.setError("Latitude must be between -90 and 90");
                    valid = false;
                } else {
                    latitudeEditText.setError(null);
                }
            } catch (NumberFormatException e) {
                latitudeEditText.setError("Invalid latitude format");
                valid = false;
            }
        }

        // Validate longitude
        String longitude = longitudeEditText.getText().toString().trim();
        if (TextUtils.isEmpty(longitude)) {
            longitudeEditText.setError("Longitude is required");
            valid = false;
        } else {
            try {
                double lng = Double.parseDouble(longitude);
                if (lng < -180 || lng > 180) {
                    longitudeEditText.setError("Longitude must be between -180 and 180");
                    valid = false;
                } else {
                    longitudeEditText.setError(null);
                }
            } catch (NumberFormatException e) {
                longitudeEditText.setError("Invalid longitude format");
                valid = false;
            }
        }

        // Validate radius (optional, has default)
        String radius = radiusEditText.getText().toString().trim();
        if (!TextUtils.isEmpty(radius)) {
            try {
                int rad = Integer.parseInt(radius);
                if (rad <= 0) {
                    radiusEditText.setError("Radius must be greater than 0");
                    valid = false;
                } else {
                    radiusEditText.setError(null);
                }
            } catch (NumberFormatException e) {
                radiusEditText.setError("Invalid radius format");
                valid = false;
            }
        }

        return valid;
    }

    private void updateLocation() {
        // Get form values
        String officeName = officeNameEditText.getText().toString().trim();
        String taluka = talukaSpinner.getText().toString().trim();
        double latitude = Double.parseDouble(latitudeEditText.getText().toString().trim());
        double longitude = Double.parseDouble(longitudeEditText.getText().toString().trim());
        
        // Get radius with default value of 100
        String radiusStr = radiusEditText.getText().toString().trim();
        int radius = TextUtils.isEmpty(radiusStr) ? 100 : Integer.parseInt(radiusStr);

        // Create location data
        Map<String, Object> locationData = new HashMap<>();
        locationData.put("officeName", officeName);
        locationData.put("taluka", taluka);
        locationData.put("latitude", latitude);
        locationData.put("longitude", longitude);
        locationData.put("radius", radius);
        locationData.put("updatedAt", System.currentTimeMillis());

        Log.d("EditLocation", "Updating location: " + officeName + " with ID: " + officeId);
        
        // Update Firestore document
        db.collection("locations").document(officeId)
            .update(locationData)
            .addOnSuccessListener(aVoid -> {
                Log.d("EditLocation", "Location updated successfully");
                Toast.makeText(this, "Location updated successfully", Toast.LENGTH_SHORT).show();
                finish();
            })
            .addOnFailureListener(e -> {
                Log.e("EditLocation", "Error updating location", e);
                Toast.makeText(this, "Error updating location: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
    }
} 