package com.example.governmentapp;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AddLocationActivity extends AppCompatActivity {

    private TextInputEditText officeNameEditText;
    private MaterialAutoCompleteTextView talukaSpinner;
    private TextInputEditText latitudeEditText;
    private TextInputEditText longitudeEditText;
    private TextInputEditText radiusEditText;
    private Button cancelButton;
    private Button addButton;
    private ImageButton backButton;
    private Toolbar toolbar;

    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_location);

        // Initialize Firestore
        db = FirebaseFirestore.getInstance();

        // Initialize views
        initializeViews();

        // Setup toolbar
        setupToolbar();

        // Setup taluka dropdown
        setupTalukaDropdown();

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
        addButton = findViewById(R.id.addButton);
        backButton = findViewById(R.id.backButton);
        toolbar = findViewById(R.id.toolbar);
    }
    
    private void setupToolbar() {
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
    }

    private void setupTalukaDropdown() {
        // Only include Hingoli and Sengaon talukas
        List<String> talukas = new ArrayList<>();
        talukas.add("Hingoli");
        talukas.add("Sengaon");

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, talukas);
        talukaSpinner.setAdapter(adapter);
        
        // Set default selection
        if (!talukas.isEmpty()) {
            talukaSpinner.setText(talukas.get(0), false);
        }
    }

    private void setupButtonListeners() {
        backButton.setOnClickListener(v -> finish());
        
        cancelButton.setOnClickListener(v -> finish());

        addButton.setOnClickListener(v -> {
            if (validateForm()) {
                saveLocation();
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
        
        // Validate taluka selection
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

    private void saveLocation() {
        // Show loading state (optional)
        addButton.setEnabled(false);
        addButton.setText("Adding...");
        
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
        locationData.put("createdAt", System.currentTimeMillis());

        Log.d("AddLocation", "Saving location: " + officeName + " with radius: " + radius);
        
        // Save to Firestore
        db.collection("locations")
            .add(locationData)
            .addOnSuccessListener(documentReference -> {
                Log.d("AddLocation", "Location saved successfully with ID: " + documentReference.getId());
                Toast.makeText(this, "Location added successfully", Toast.LENGTH_SHORT).show();
                finish();
            })
            .addOnFailureListener(e -> {
                // Reset button state
                addButton.setEnabled(true);
                addButton.setText("ADD LOCATION");
                
                Log.e("AddLocation", "Error adding location", e);
                Toast.makeText(this, "Error adding location: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
    }
} 