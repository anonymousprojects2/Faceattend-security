package com.example.governmentapp;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class EditUserActivity extends AppCompatActivity {

    private static final String TAG = "EditUserActivity";

    private TextInputEditText sevarthIdEditText;
    private TextInputEditText firstNameEditText;
    private TextInputEditText lastNameEditText;
    private RadioGroup genderRadioGroup;
    private RadioButton maleRadioButton;
    private RadioButton femaleRadioButton;
    private TextInputEditText dobEditText;
    private TextInputEditText phoneEditText;
    private TextInputEditText emailEditText;
    private AutoCompleteTextView locationDropdown;
    private AutoCompleteTextView talukaDropdown;
    private LinearLayout selectedLocationsContainer;
    private Button saveButton;
    private Button cancelButton;
    private ImageButton backButton;
    private Toolbar toolbar;

    private FirebaseFirestore db;
    private String userId;
    private Calendar calendar;
    private Map<String, String> locationMap = new HashMap<>();
    private List<String> selectedLocationIds = new ArrayList<>();
    private List<String> selectedLocations = new ArrayList<>();
    private String selectedTaluka;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_user);

        // Initialize Firestore
        db = FirebaseFirestore.getInstance();

        // Get user ID from intent
        userId = getIntent().getStringExtra("user_id");
        if (userId == null) {
            Toast.makeText(this, "Error: User ID not provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize views
        initializeViews();

        // Set up date picker
        setupDatePicker();

        // Set up button listeners
        setupButtonListeners();
        
        // Setup taluka dropdown
        setupTalukaDropdown();

        // Load user data
        loadUserData();
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
        saveButton = findViewById(R.id.saveButton);
        cancelButton = findViewById(R.id.cancelButton);
        backButton = findViewById(R.id.backButton);
        toolbar = findViewById(R.id.toolbar);
        
        // Set up toolbar
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        calendar = Calendar.getInstance();
    }

    private void setupDatePicker() {
        dobEditText.setOnClickListener(view -> {
            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH);
            int day = calendar.get(Calendar.DAY_OF_MONTH);

            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    EditUserActivity.this,
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

    private void setupButtonListeners() {
        backButton.setOnClickListener(view -> finish());

        cancelButton.setOnClickListener(view -> finish());

        saveButton.setOnClickListener(view -> {
            if (validateForm()) {
                saveUserData();
            }
        });
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
            String newTaluka = (String) parent.getItemAtPosition(position);
            
            // Only reload locations if the taluka actually changed
            if (!newTaluka.equals(selectedTaluka)) {
                selectedTaluka = newTaluka;
                
                // When taluka is selected, clear any previously selected locations
                selectedLocations.clear();
                selectedLocationIds.clear();
                updateSelectedLocationsDisplay();
                
                // Reload locations based on selected taluka
                loadLocationsForTaluka(selectedTaluka);
            }
        });
    }

    private void loadUserData() {
        db.collection("users").document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // Fill form with user data
                        fillUserData(documentSnapshot);
                    } else {
                        Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading user data", e);
                    Toast.makeText(this, "Error loading user data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void fillUserData(DocumentSnapshot documentSnapshot) {
        // Get user data
        String sevarthId = documentSnapshot.getString("sevarthId");
        String firstName = documentSnapshot.getString("firstName");
        String lastName = documentSnapshot.getString("lastName");
        String gender = documentSnapshot.getString("gender");
        String dob = documentSnapshot.getString("dob");
        String phone = documentSnapshot.getString("phone");
        String email = documentSnapshot.getString("email");
        
        // Get taluka
        selectedTaluka = documentSnapshot.getString("taluka");

        // Get locations
        Object locationsObj = documentSnapshot.get("locations");
        if (locationsObj instanceof List) {
            selectedLocations = (List<String>) locationsObj;
        }

        // Get location IDs
        Object locationIdsObj = documentSnapshot.get("locationIds");
        if (locationIdsObj instanceof List) {
            selectedLocationIds = (List<String>) locationIdsObj;
        }

        // Fill form fields
        sevarthIdEditText.setText(sevarthId);
        firstNameEditText.setText(firstName);
        lastNameEditText.setText(lastName);

        if ("Female".equals(gender)) {
            femaleRadioButton.setChecked(true);
        } else {
            maleRadioButton.setChecked(true);
        }

        dobEditText.setText(dob);
        phoneEditText.setText(phone);
        emailEditText.setText(email);
        
        // Set taluka dropdown value
        if (selectedTaluka != null && !selectedTaluka.isEmpty()) {
            talukaDropdown.setText(selectedTaluka, false);
            
            // Load locations for this taluka
            loadLocationsForTaluka(selectedTaluka);
        } else {
            // If no taluka is set, load all locations
            loadLocationsForTaluka(null);
        }
    }

    private void loadLocations() {
        // Use the new method with taluka filter
        loadLocationsForTaluka(selectedTaluka);
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
                            String locationId = document.getId();
                            String officeName = document.getString("officeName");
                            String locationTaluka = document.getString("taluka");

                            // If taluka is specified, only add matching locations
                            if (taluka == null || taluka.equals(locationTaluka)) {
                            if (officeName != null && !officeName.isEmpty()) {
                                locationNames.add(officeName);
                                locationMap.put(officeName, locationId);
                                }
                            }
                        }
                        
                        // Sort location names alphabetically
                        Collections.sort(locationNames);
                        
                        // Set up the dropdown adapter
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
                                    Toast.makeText(EditUserActivity.this, 
                                        "Location already selected", Toast.LENGTH_SHORT).show();
                                    locationDropdown.setText("", false);
                                }
                            });
                            
                            // Update the UI with pre-selected locations
                            updateSelectedLocationsDisplay();
                    } else {
                        showNoLocationsMessage();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading locations", e);
                    Toast.makeText(this, "Error loading locations: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    showNoLocationsMessage();
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

    private void showNoLocationsMessage() {
        selectedLocationsContainer.removeAllViews();
        TextView noLocationsText = new TextView(this);
        noLocationsText.setText("No offices available");
        noLocationsText.setPadding(8, 8, 8, 8);
        noLocationsText.setTextColor(getResources().getColor(android.R.color.darker_gray));
        selectedLocationsContainer.addView(noLocationsText);
    }

    private boolean validateForm() {
        boolean valid = true;

        // Validate Taluka
        if (selectedTaluka == null || selectedTaluka.isEmpty()) {
            ((TextInputLayout) findViewById(R.id.talukaInputLayout)).setError("Please select a taluka");
            valid = false;
        } else {
            ((TextInputLayout) findViewById(R.id.talukaInputLayout)).setError(null);
        }

        // Validate Sevarth ID
        String sevarthId = sevarthIdEditText.getText().toString().trim();
        if (TextUtils.isEmpty(sevarthId)) {
            sevarthIdEditText.setError("Sevarth ID is required");
            valid = false;
        } else {
            sevarthIdEditText.setError(null);
        }

        // Validate first name
        String firstName = firstNameEditText.getText().toString().trim();
        if (TextUtils.isEmpty(firstName)) {
            firstNameEditText.setError("First name is required");
            valid = false;
        } else {
            firstNameEditText.setError(null);
        }

        // Validate last name
        String lastName = lastNameEditText.getText().toString().trim();
        if (TextUtils.isEmpty(lastName)) {
            lastNameEditText.setError("Last name is required");
            valid = false;
        } else {
            lastNameEditText.setError(null);
        }

        // Validate date of birth
        String dob = dobEditText.getText().toString().trim();
        if (TextUtils.isEmpty(dob)) {
            dobEditText.setError("Date of birth is required");
            valid = false;
        } else {
            dobEditText.setError(null);
        }

        // Validate phone number
        String phone = phoneEditText.getText().toString().trim();
        if (TextUtils.isEmpty(phone)) {
            phoneEditText.setError("Phone number is required");
            valid = false;
        } else if (phone.length() < 10) {
            phoneEditText.setError("Please enter a valid phone number");
            valid = false;
        } else {
            phoneEditText.setError(null);
        }

        // Validate location selection
        if (selectedLocationIds.isEmpty()) {
            Toast.makeText(this, "Please select at least one office location", Toast.LENGTH_SHORT).show();
            valid = false;
        }

        return valid;
    }

    private void saveUserData() {
        // Get form data
        String sevarthId = sevarthIdEditText.getText().toString().trim();
        String firstName = firstNameEditText.getText().toString().trim();
        String lastName = lastNameEditText.getText().toString().trim();
        String gender = maleRadioButton.isChecked() ? "Male" : "Female";
        String dob = dobEditText.getText().toString().trim();
        String phone = phoneEditText.getText().toString().trim();

        // Create update data
        Map<String, Object> userData = new HashMap<>();
        userData.put("sevarthId", sevarthId);
        userData.put("firstName", firstName);
        userData.put("lastName", lastName);
        userData.put("name", firstName + " " + lastName);
        userData.put("gender", gender);
        userData.put("dob", dob);
        userData.put("phone", phone);
        userData.put("locations", selectedLocations);
        userData.put("locationIds", selectedLocationIds);
        userData.put("taluka", selectedTaluka);

        // Update user data in Firestore
        db.collection("users").document(userId)
                .update(userData)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "User updated successfully", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating user", e);
                    Toast.makeText(this, "Error updating user: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
} 