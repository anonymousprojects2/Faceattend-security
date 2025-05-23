package com.example.governmentapp;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AddUserActivity extends AppCompatActivity {

    private static final String TAG = "AddUserActivity";
    private static final int CAMERA_PERMISSION_CODE = 100;

    private EditText sevarthIdEditText;
    private EditText firstNameEditText;
    private EditText lastNameEditText;
    private RadioGroup genderRadioGroup;
    private RadioButton maleRadioButton;
    private RadioButton femaleRadioButton;
    private EditText dobEditText;
    private EditText phoneEditText;
    private EditText emailEditText;
    private EditText passwordEditText;
    private AutoCompleteTextView locationDropdown;
    private AutoCompleteTextView talukaDropdown;
    private LinearLayout selectedLocationsContainer;
    private String selectedTaluka;
    
    private Button cancelButton;
    private Button nextButton;
    private ImageButton backButton;
    private androidx.appcompat.widget.Toolbar toolbar;
    
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private StorageReference storageRef;
    
    private Calendar calendar;
    private boolean isPasswordVisible = false;
    private Uri imageUri;
    private String userEmail;
    private String userId;
    private Map<String, Object> userData;
    
    // Map to store location IDs and names
    private Map<String, String> locationMap = new HashMap<>();
    // Lists to store selected locations
    private List<String> selectedLocationIds = new ArrayList<>();
    private List<String> selectedLocations = new ArrayList<>();
    
    // Activity result launcher for camera
    private final ActivityResultLauncher<Uri> takePicture = registerForActivityResult(
            new ActivityResultContracts.TakePicture(),
            result -> {
                if (result) {
                    // Image captured successfully
                    uploadImageToFirebase();
                } else {
                    Toast.makeText(AddUserActivity.this, "Failed to capture image", Toast.LENGTH_SHORT).show();
                    // Delete the user since we couldn't get their image
                    if (userId != null) {
                        deleteUser();
                    }
                }
            });
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_user);
        
        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        storageRef = storage.getReference();
        
        // Initialize form elements
        initializeViews();
        
        // Setup toolbar
        setupToolbar();
        
        // Set up date picker
        setupDatePicker();
        
        // Set up location dropdown
        setupLocationDropdown();
        
        // Set up button listeners
        setupButtonListeners();
    }
    
    private void initializeViews() {
        sevarthIdEditText = findViewById(R.id.sevarthIdEditText);
        firstNameEditText = findViewById(R.id.firstNameEditText);
        lastNameEditText = findViewById(R.id.lastNameEditText);
        genderRadioGroup = findViewById(R.id.genderRadioGroup);
        maleRadioButton = findViewById(R.id.maleRadioButton);
        femaleRadioButton = findViewById(R.id.femaleRadioButton);
        dobEditText = findViewById(R.id.dobEditText);
        phoneEditText = findViewById(R.id.phoneEditText);
        emailEditText = findViewById(R.id.emailEditText);
        locationDropdown = findViewById(R.id.locationDropdown);
        talukaDropdown = findViewById(R.id.talukaDropdown);
        selectedLocationsContainer = findViewById(R.id.selectedLocationsContainer);
        passwordEditText = findViewById(R.id.passwordEditText);
        
        cancelButton = findViewById(R.id.cancelButton);
        nextButton = findViewById(R.id.nextButton);
        backButton = findViewById(R.id.backButton);
        toolbar = findViewById(R.id.toolbar);
        
        calendar = Calendar.getInstance();

        // Setup Taluka dropdown
        setupTalukaDropdown();
    }
    
    private void setupToolbar() {
        // Set up the toolbar as ActionBar
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
    }
    
    private void setupDatePicker() {
        dobEditText.setOnClickListener(view -> {
            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH);
            int day = calendar.get(Calendar.DAY_OF_MONTH);
            
            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    AddUserActivity.this,
                    (datePicker, selectedYear, selectedMonth, selectedDay) -> {
                        calendar.set(Calendar.YEAR, selectedYear);
                        calendar.set(Calendar.MONTH, selectedMonth);
                        calendar.set(Calendar.DAY_OF_MONTH, selectedDay);
                        
                        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                        dobEditText.setText(dateFormat.format(calendar.getTime()));
                    },
                    year, month, day);
            
            datePickerDialog.show();
        });
    }
    
    private void setupLocationDropdown() {
        // Initially load all locations
        // This will be replaced when a taluka is selected
        loadLocationsForTaluka(null);
    }
    
    private void loadLocationsForTaluka(String taluka) {
        // Load locations from Firestore
        db.collection("locations")
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                if (!queryDocumentSnapshots.isEmpty()) {
                    List<String> locationNames = new ArrayList<>();
                    locationMap.clear();
                    
                    // Populate location data, filtering by taluka if specified
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        Map<String, Object> locationData = document.getData();
                        if (locationData != null && locationData.containsKey("officeName")) {
                            String officeName = (String) locationData.get("officeName");
                            String locationId = document.getId();
                            String locationTaluka = (String) locationData.get("taluka");
                            
                            // If taluka is specified, only add matching locations
                            if (taluka == null || taluka.equals(locationTaluka)) {
                            if (officeName != null && !officeName.isEmpty()) {
                                locationNames.add(officeName);
                                locationMap.put(officeName, locationId);
                                }
                            }
                        }
                    }
                    
                    // Sort location names alphabetically
                    Collections.sort(locationNames);
                    
                    // Set up the dropdown adapter with sorted locations
                    if (locationNames.size() > 0) {
                        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                                this, android.R.layout.simple_dropdown_item_1line, locationNames);
                        locationDropdown.setAdapter(adapter);
                        
                        // Set item click listener for dropdown
                        locationDropdown.setOnItemClickListener((parent, view, position, id) -> {
                            String selectedLocation = (String) parent.getItemAtPosition(position);
                            String locationId = locationMap.get(selectedLocation);
                            
                            // Add the selected location if not already selected
                            if (!selectedLocations.contains(selectedLocation)) {
                                selectedLocations.add(selectedLocation);
                                selectedLocationIds.add(locationId);
                                updateSelectedLocationsDisplay();
                                
                                // Clear the dropdown after selection
                                locationDropdown.setText("", false);
                            } else {
                                Toast.makeText(AddUserActivity.this, 
                                    "Location already selected", Toast.LENGTH_SHORT).show();
                                locationDropdown.setText("", false);
                            }
                        });
                    } else {
                        // Handle no locations case
                        if (taluka != null) {
                            Toast.makeText(AddUserActivity.this, 
                                    "No locations available for " + taluka + ". Please add locations first.", 
                                    Toast.LENGTH_LONG).show();
                        } else {
                        Toast.makeText(AddUserActivity.this, 
                                "No locations available. Please add locations first.", 
                                Toast.LENGTH_LONG).show();
                        }
                    }
                }
            })
            .addOnFailureListener(e -> {
                // Handle error
                Toast.makeText(AddUserActivity.this, 
                        "Error loading locations: " + e.getMessage(), 
                        Toast.LENGTH_SHORT).show();
            });
    }
    
    private void updateSelectedLocationsDisplay() {
        // Clear the container
        selectedLocationsContainer.removeAllViews();
        
        // Create a chip group to hold the chips
        ChipGroup chipGroup = new ChipGroup(this);
        chipGroup.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                LinearLayout.LayoutParams.WRAP_CONTENT));
        chipGroup.setChipSpacingHorizontal(8);
        chipGroup.setChipSpacingVertical(8);
        
        // Add chips for each selected location
        for (int i = 0; i < selectedLocations.size(); i++) {
            final int index = i;
            final String locationName = selectedLocations.get(i);
            
            Chip chip = new Chip(this);
            chip.setText(locationName);
            chip.setCloseIconVisible(true);
            chip.setClickable(true);
            chip.setCheckable(false);
            
            // Set up close icon click listener to remove the location
            chip.setOnCloseIconClickListener(v -> {
                selectedLocations.remove(index);
                selectedLocationIds.remove(index);
                updateSelectedLocationsDisplay();
            });
            
            chipGroup.addView(chip);
        }
        
        selectedLocationsContainer.addView(chipGroup);
    }
    
    private void setupTalukaDropdown() {
        // Create array of talukas
        String[] talukas = new String[]{"Hingoli", "Sengaon"};
        
        // Create adapter for taluka dropdown
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                talukas);
        
        talukaDropdown.setAdapter(adapter);
        
        // Set item click listener
        talukaDropdown.setOnItemClickListener((parent, view, position, id) -> {
            selectedTaluka = (String) parent.getItemAtPosition(position);
            
            // When taluka is selected, clear any previously selected locations
            selectedLocations.clear();
            selectedLocationIds.clear();
            updateSelectedLocationsDisplay();
            
            // Reload locations based on selected taluka
            loadLocationsForTaluka(selectedTaluka);
        });
    }
    
    private void setupButtonListeners() {
        backButton.setOnClickListener(view -> finish());
        
        cancelButton.setOnClickListener(view -> finish());
        
        nextButton.setOnClickListener(view -> {
            // Validate form
            if (validateForm()) {
                // Create user
                createUser();
            }
        });
    }
    
    private boolean validateForm() {
        // Get form values
        String sevarthId = sevarthIdEditText.getText().toString().trim();
        String firstName = firstNameEditText.getText().toString().trim();
        String lastName = lastNameEditText.getText().toString().trim();
        String dob = dobEditText.getText().toString().trim();
        String phone = phoneEditText.getText().toString().trim();
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();
        
        // Validate form fields
        if (sevarthId.isEmpty()) {
            sevarthIdEditText.setError("Sevarth ID is required");
            sevarthIdEditText.requestFocus();
            return false;
        }
        
        if (firstName.isEmpty()) {
            firstNameEditText.setError("First name is required");
            firstNameEditText.requestFocus();
            return false;
        }
        
        if (lastName.isEmpty()) {
            lastNameEditText.setError("Last name is required");
            lastNameEditText.requestFocus();
            return false;
        }
        
        if (dob.isEmpty()) {
            dobEditText.setError("Date of birth is required");
            dobEditText.requestFocus();
            return false;
        }
        
        if (phone.isEmpty()) {
            phoneEditText.setError("Phone number is required");
            phoneEditText.requestFocus();
            return false;
        }
        
        if (phone.length() != 10) {
            phoneEditText.setError("Phone number must be 10 digits");
            phoneEditText.requestFocus();
            return false;
        }
        
        if (email.isEmpty()) {
            emailEditText.setError("Email is required");
            emailEditText.requestFocus();
            return false;
        }
        
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEditText.setError("Invalid email format");
            emailEditText.requestFocus();
            return false;
        }
        
        if (selectedLocationIds.isEmpty()) {
            Toast.makeText(this, "Please select at least one location", Toast.LENGTH_SHORT).show();
            return false;
        }
        
        if (selectedTaluka == null || selectedTaluka.isEmpty()) {
            ((TextInputLayout) findViewById(R.id.talukaInputLayout)).setError("Please select a Taluka");
            talukaDropdown.requestFocus();
            return false;
        }
        
        if (password.isEmpty()) {
            passwordEditText.setError("Password is required");
            passwordEditText.requestFocus();
            return false;
        }
        
        if (password.length() < 6) {
            passwordEditText.setError("Password must be at least 6 characters");
            passwordEditText.requestFocus();
            return false;
        }
        
        return true;
    }
    
    private void createUser() {
        // Get form values
        String firstName = firstNameEditText.getText().toString().trim();
        String lastName = lastNameEditText.getText().toString().trim();
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();
        String phone = phoneEditText.getText().toString().trim();
        String dob = dobEditText.getText().toString().trim();
        String gender = maleRadioButton.isChecked() ? "Male" : "Female";
        String sevarthId = sevarthIdEditText.getText().toString().trim();
        
        // Store email for later use
        userEmail = email;
        
        // Create a new user with email and password
        mAuth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this, task -> {
                if (task.isSuccessful()) {
                    // User created successfully
                    userId = mAuth.getCurrentUser().getUid();
                    
                    // Create user data for Firestore
                    userData = new HashMap<>();
                    userData.put("firstName", firstName);
                    userData.put("lastName", lastName);
                    userData.put("name", firstName + " " + lastName);
                    userData.put("gender", gender);
                    userData.put("dob", dob);
                    userData.put("phone", phone);
                    userData.put("email", email);
                    userData.put("locations", selectedLocations);
                    userData.put("locationIds", selectedLocationIds);
                    userData.put("role", "USER");
                    userData.put("createdAt", Calendar.getInstance().getTime());
                    userData.put("sevarthId", sevarthId);
                    userData.put("taluka", selectedTaluka);
                    
                    // Check camera permission and open camera
                    checkCameraPermission();
                } else {
                    // If sign in fails, display a message to the user
                    if (task.getException() != null) {
                        Toast.makeText(AddUserActivity.this, "Error: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(AddUserActivity.this, "Failed to create user", Toast.LENGTH_SHORT).show();
                    }
                }
            });
    }
    
    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        } else {
            openCamera();
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(this, "Camera permission denied. Cannot capture user image.", Toast.LENGTH_SHORT).show();
                // Complete user creation without image
                saveUserToFirestore();
            }
        }
    }
    
    private void openCamera() {
        try {
            Log.d(TAG, "Opening camera...");
            // Create file for image
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String imageFileName = "JPEG_" + timeStamp + "_";
            File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            File image = File.createTempFile(imageFileName, ".jpg", storageDir);
            
            Log.d(TAG, "Created temporary file: " + image.getAbsolutePath());
            
            // Get URI from file provider
            imageUri = FileProvider.getUriForFile(this,
                    getApplicationContext().getPackageName() + ".provider", image);
            
            Log.d(TAG, "File provider URI: " + imageUri.toString());
            
            takePicture.launch(imageUri);
        } catch (IOException e) {
            Log.e(TAG, "Error creating image file: " + e.getMessage(), e);
            Toast.makeText(this, "Error creating image file", Toast.LENGTH_SHORT).show();
            // Complete user creation without image
            saveUserToFirestore();
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error opening camera: " + e.getMessage(), e);
            Toast.makeText(this, "Error opening camera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            saveUserToFirestore();
        }
    }
    
    private void uploadImageToFirebase() {
        if (imageUri != null) {
            String sevarthId = sevarthIdEditText.getText().toString().trim();
            if (sevarthId.isEmpty()) {
                Log.e(TAG, "SevarthId is empty, cannot upload image");
                saveUserToFirestore();
                return;
            }
            
            // Use sevarthId directly as the filename
            String imagePath = "faces/" + sevarthId + ".jpg";
            
            StorageReference imageRef = storageRef.child(imagePath);
            
            imageRef.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot -> {
                    Log.d(TAG, "Image uploaded successfully");
                    // Get the download URL
                    imageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                        // Add the image URL to user data
                        userData.put("profileImageUrl", uri.toString());
                        userData.put("faceImagePath", imagePath);
                        
                        // Save user data to Firestore
                        saveUserToFirestore();
                    });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to upload image", e);
                    // Still save the user even if image upload fails
                    saveUserToFirestore();
                });
        } else {
            Log.w(TAG, "Image URI is null");
            saveUserToFirestore();
        }
    }
    
    private void saveUserToFirestore() {
        db.collection("users")
            .document(userId)
            .set(userData)
            .addOnSuccessListener(aVoid -> {
                Toast.makeText(AddUserActivity.this, "User created successfully", Toast.LENGTH_SHORT).show();
                // Return to the previous screen
                finish();
            })
            .addOnFailureListener(e -> {
                Toast.makeText(AddUserActivity.this, "Error saving user data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                // Delete the user since we couldn't save their data
                deleteUser();
            });
    }
    
    private void deleteUser() {
        if (mAuth.getCurrentUser() != null) {
            mAuth.getCurrentUser().delete()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "User account deleted");
                    } else {
                        Log.e(TAG, "Error deleting user account", task.getException());
                    }
                });
        }
    }
} 