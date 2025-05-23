package com.example.governmentapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LocationSelectionActivity extends AppCompatActivity {

    private static final String TAG = "LocationSelection";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    // UI Elements
    private TextView userNameText;
    private RecyclerView locationRecyclerView;
    private LinearLayout loadingLayout;
    private LinearLayout emptyStateLayout;
    private Button logoutButton;

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    private String userId;

    // Location
    private FusedLocationProviderClient fusedLocationClient;
    private Location currentLocation;

    // Data
    private List<LocationModel> userLocations = new ArrayList<>();
    private LocationAdapter locationAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location_selection);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        currentUser = mAuth.getCurrentUser();

        // Check if user is logged in
        if (currentUser == null) {
            // Redirect to login
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        userId = currentUser.getUid();

        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Initialize UI
        initializeUI();

        // Set up location adapter
        setupRecyclerView();

        // Load user data
        loadUserData();

        // Check location permission
        checkLocationPermission();
    }

    private void initializeUI() {
        userNameText = findViewById(R.id.userNameText);
        locationRecyclerView = findViewById(R.id.locationRecyclerView);
        loadingLayout = findViewById(R.id.loadingLayout);
        emptyStateLayout = findViewById(R.id.emptyStateLayout);
        logoutButton = findViewById(R.id.logoutButton);

        // Set welcome text with user email
        String email = currentUser.getEmail();
        if (email != null) {
            userNameText.setText("Welcome, " + email);
        }

        // Set up logout button
        logoutButton.setOnClickListener(v -> logout());

        // Initially show loading state
        showLoadingState();
    }

    private void setupRecyclerView() {
        locationAdapter = new LocationAdapter();
        locationRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        locationRecyclerView.setAdapter(locationAdapter);
    }

    private void loadUserData() {
        db.collection("users").document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // Get user name for welcome message
                        String name = documentSnapshot.getString("name");
                        if (name != null && !name.isEmpty()) {
                            userNameText.setText("Welcome, " + name);
                        }

                        // Get user's assigned locations
                        loadUserLocations(documentSnapshot);
                    } else {
                        showEmptyState();
                        Log.w(TAG, "User document not found");
                    }
                })
                .addOnFailureListener(e -> {
                    showEmptyState();
                    Log.e(TAG, "Error loading user data", e);
                    Toast.makeText(this, "Error loading user data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void loadUserLocations(DocumentSnapshot userDocument) {
        // Get location IDs from user document
        List<String> locationIds = new ArrayList<>();
        Object locationIdsObj = userDocument.get("locationIds");
        
        Log.d(TAG, "Loading user locations. User ID: " + userId);
        
        if (locationIdsObj instanceof List) {
            List<?> tempList = (List<?>) locationIdsObj;
            for (Object obj : tempList) {
                if (obj != null && !obj.toString().isEmpty()) {
                locationIds.add(obj.toString());
                    Log.d(TAG, "Found location ID: " + obj.toString());
                }
            }
        } else {
            Log.w(TAG, "locationIds is not a List or is null. Type: " + 
                (locationIdsObj != null ? locationIdsObj.getClass().getName() : "null"));
        }

        if (locationIds.isEmpty()) {
            Log.w(TAG, "No location IDs found for user. Checking legacy 'locations' field");
            
            // Check for legacy 'location' field which might store location names instead of IDs
            Object locationsObj = userDocument.get("locations");
            if (locationsObj instanceof List && !((List<?>) locationsObj).isEmpty()) {
                List<?> locationNames = (List<?>) locationsObj;
                Log.d(TAG, "Found " + locationNames.size() + " location names, will attempt to resolve IDs");
                
                // Query all locations
                db.collection("locations")
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        Map<String, LocationModel> locationsMap = new HashMap<>();
                        
                        for (DocumentSnapshot locationDoc : queryDocumentSnapshots.getDocuments()) {
                            String officeName = locationDoc.getString("officeName");
                            
                            // If this location name matches any in our list
                            if (officeName != null && locationNames.contains(officeName)) {
                                String locationId = locationDoc.getId();
                                String taluka = locationDoc.getString("taluka");
                                Double latitude = locationDoc.getDouble("latitude");
                                Double longitude = locationDoc.getDouble("longitude");
                                
                                // Get radius, handling different types
                                int radius = 100; // Default
                                Object radiusObj = locationDoc.get("radius");
                                if (radiusObj instanceof Long) {
                                    radius = ((Long) radiusObj).intValue();
                                } else if (radiusObj instanceof Integer) {
                                    radius = (Integer) radiusObj;
                                } else if (radiusObj instanceof Double) {
                                    radius = ((Double) radiusObj).intValue();
                                }
                                
                                if (latitude != null && longitude != null) {
                                    // Create location model
                                    LocationModel location = new LocationModel(
                                            locationId,
                                            officeName,
                                            taluka != null ? taluka : "",
                                            latitude,
                                            longitude,
                                            radius
                                    );
                                    
                                    locationsMap.put(locationId, location);
                                    Log.d(TAG, "Matched location by name: " + officeName);
                                }
                            }
                        }
                        
                        if (!locationsMap.isEmpty()) {
                            userLocations = new ArrayList<>(locationsMap.values());
                            locationAdapter.updateLocations(userLocations);
                            
                            if (currentLocation != null) {
                                updateLocationsWithDistance();
                            }
                            
                            showLocationList();
                        } else {
                            Log.e(TAG, "No matching locations found by name");
                            showEmptyState();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error loading locations by name", e);
                        showEmptyState();
                    });
                
                return;
            }
            
            Log.e(TAG, "No locations found for user in any format");
            showEmptyState();
            return;
        }

        // Create a map to store location details
        Map<String, LocationModel> locationsMap = new HashMap<>();
        final int[] processingCount = {0}; // Counter for processed location queries
        final int totalToProcess = locationIds.size();

        // For each location ID, fetch location details
        for (String locationId : locationIds) {
            if (locationId == null || locationId.isEmpty()) {
                processingCount[0]++;
                if (processingCount[0] >= totalToProcess) {
                    checkAndShowResults(locationsMap, totalToProcess);
                }
                continue;
            }
            
            db.collection("locations").document(locationId)
                    .get()
                    .addOnSuccessListener(locationDoc -> {
                        processingCount[0]++;
                        
                        if (locationDoc.exists()) {
                            // Extract location data
                            String officeName = locationDoc.getString("officeName");
                            String taluka = locationDoc.getString("taluka");
                            Double latitude = locationDoc.getDouble("latitude");
                            Double longitude = locationDoc.getDouble("longitude");
                            
                            // Get radius, handling different types
                            int radius = 100; // Default
                            Object radiusObj = locationDoc.get("radius");
                            if (radiusObj instanceof Long) {
                                radius = ((Long) radiusObj).intValue();
                            } else if (radiusObj instanceof Integer) {
                                radius = (Integer) radiusObj;
                            } else if (radiusObj instanceof Double) {
                                radius = ((Double) radiusObj).intValue();
                            }

                            if (officeName != null && latitude != null && longitude != null) {
                                // Create location model
                                LocationModel location = new LocationModel(
                                        locationId,
                                        officeName,
                                        taluka != null ? taluka : "",
                                        latitude,
                                        longitude,
                                        radius
                                );
                                
                                // Add to map
                                locationsMap.put(locationId, location);
                                Log.d(TAG, "Added location: " + officeName);
                            }
                        } else {
                            Log.w(TAG, "Location document not found: " + locationId);
                        }
                            
                            // Check if we've processed all locations
                        if (processingCount[0] >= totalToProcess) {
                            checkAndShowResults(locationsMap, totalToProcess);
                        }
                    })
                    .addOnFailureListener(e -> {
                        processingCount[0]++;
                        Log.e(TAG, "Error loading location: " + locationId, e);
                        
                        // Check if we've processed all locations
                        if (processingCount[0] >= totalToProcess) {
                            checkAndShowResults(locationsMap, totalToProcess);
                        }
                    });
        }
    }
    
    private void checkAndShowResults(Map<String, LocationModel> locationsMap, int totalProcessed) {
        Log.d(TAG, "Finished processing " + totalProcessed + " locations, found " + locationsMap.size() + " valid ones");
        
        if (locationsMap.isEmpty()) {
            showEmptyState();
        } else {
            userLocations = new ArrayList<>(locationsMap.values());
            locationAdapter.updateLocations(userLocations);
            
            // Update location status for all locations
            if (currentLocation != null) {
                updateLocationsWithDistance();
            }
            
            // Show list
            showLocationList();
        }
    }

    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
                != PackageManager.PERMISSION_GRANTED) {
            // Request location permission
            ActivityCompat.requestPermissions(this, 
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            // Permission already granted
            getCurrentLocation();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted
                getCurrentLocation();
            } else {
                // Permission denied
                Toast.makeText(this, "Location permission is required to check if you're in range of your office", 
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        currentLocation = location;
                        updateLocationsWithDistance();
                    } else {
                        Log.w(TAG, "getLastLocation returned null");
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error getting location", e));
    }

    private void updateLocationsWithDistance() {
        if (currentLocation == null || userLocations.isEmpty()) {
            return;
        }

        for (LocationModel location : userLocations) {
            Location officeLoc = new Location("office");
            officeLoc.setLatitude(location.getLatitude());
            officeLoc.setLongitude(location.getLongitude());
            
            // Calculate distance
            float distanceInMeters = currentLocation.distanceTo(officeLoc);
            location.setDistanceFromUser(distanceInMeters);
            
            // Update in-range status
            location.setInRange(distanceInMeters <= location.getRadius());
        }
        
        // Update the adapter
        locationAdapter.notifyDataSetChanged();
    }

    private void showLocationList() {
        loadingLayout.setVisibility(View.GONE);
        emptyStateLayout.setVisibility(View.GONE);
        locationRecyclerView.setVisibility(View.VISIBLE);
    }

    private void showEmptyState() {
        loadingLayout.setVisibility(View.GONE);
        locationRecyclerView.setVisibility(View.GONE);
        emptyStateLayout.setVisibility(View.VISIBLE);
    }

    private void showLoadingState() {
        emptyStateLayout.setVisibility(View.GONE);
        locationRecyclerView.setVisibility(View.GONE);
        loadingLayout.setVisibility(View.VISIBLE);
    }

    private void logout() {
        mAuth.signOut();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    // Location Model class
    private static class LocationModel {
        private String id;
        private String name;
        private String address;
        private double latitude;
        private double longitude;
        private int radius;
        private float distanceFromUser;
        private boolean inRange;

        public LocationModel(String id, String name, String address, double latitude, double longitude, int radius) {
            this.id = id;
            this.name = name;
            this.address = address;
            this.latitude = latitude;
            this.longitude = longitude;
            this.radius = radius;
            this.distanceFromUser = -1; // Unknown initially
            this.inRange = false;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getAddress() { return address; }
        public double getLatitude() { return latitude; }
        public double getLongitude() { return longitude; }
        public int getRadius() { return radius; }
        public float getDistanceFromUser() { return distanceFromUser; }
        public boolean isInRange() { return inRange; }

        public void setDistanceFromUser(float distanceFromUser) {
            this.distanceFromUser = distanceFromUser;
        }

        public void setInRange(boolean inRange) {
            this.inRange = inRange;
        }
    }

    // Adapter for the RecyclerView
    private class LocationAdapter extends RecyclerView.Adapter<LocationAdapter.LocationViewHolder> {
        
        private List<LocationModel> locations = new ArrayList<>();

        @NonNull
        @Override
        public LocationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_location, parent, false);
            return new LocationViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull LocationViewHolder holder, int position) {
            LocationModel location = locations.get(position);
            holder.bind(location);
        }

        @Override
        public int getItemCount() {
            return locations.size();
        }

        public void updateLocations(List<LocationModel> newLocations) {
            this.locations = newLocations;
            notifyDataSetChanged();
        }

        class LocationViewHolder extends RecyclerView.ViewHolder {
            private TextView locationNameText;
            private TextView locationAddressText;
            private TextView locationStatusText;
            private Button selectLocationButton;

            public LocationViewHolder(@NonNull View itemView) {
                super(itemView);
                locationNameText = itemView.findViewById(R.id.locationNameText);
                locationAddressText = itemView.findViewById(R.id.locationAddressText);
                locationStatusText = itemView.findViewById(R.id.locationStatusText);
                selectLocationButton = itemView.findViewById(R.id.selectLocationButton);
            }

            public void bind(LocationModel location) {
                // Set basic location info
                locationNameText.setText(location.getName());
                
                if (location.getAddress().isEmpty()) {
                    locationAddressText.setVisibility(View.GONE);
                } else {
                    locationAddressText.setText(location.getAddress());
                    locationAddressText.setVisibility(View.VISIBLE);
                }

                // Set status based on distance
                if (location.getDistanceFromUser() < 0) {
                    // Distance not calculated yet
                    locationStatusText.setText("Checking location...");
                    locationStatusText.setTextColor(getResources().getColor(android.R.color.darker_gray));
                    locationStatusText.setCompoundDrawablesWithIntrinsicBounds(
                            android.R.drawable.ic_menu_mylocation, 0, 0, 0);
                    locationStatusText.getCompoundDrawables()[0].setTint(getResources().getColor(android.R.color.darker_gray));
                } else if (location.isInRange()) {
                    // In range
                    locationStatusText.setText(String.format("In range (%.0fm away)", location.getDistanceFromUser()));
                    locationStatusText.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                    locationStatusText.setCompoundDrawablesWithIntrinsicBounds(
                            android.R.drawable.ic_menu_mylocation, 0, 0, 0);
                    locationStatusText.getCompoundDrawables()[0].setTint(getResources().getColor(android.R.color.holo_green_dark));
                    
                    // Enable select button
                    selectLocationButton.setEnabled(true);
                    selectLocationButton.setAlpha(1.0f);
                } else {
                    // Out of range
                    locationStatusText.setText(String.format("Out of range (%.0fm away)", location.getDistanceFromUser()));
                    locationStatusText.setTextColor(getResources().getColor(android.R.color.holo_red_light));
                    locationStatusText.setCompoundDrawablesWithIntrinsicBounds(
                            android.R.drawable.ic_menu_mylocation, 0, 0, 0);
                    locationStatusText.getCompoundDrawables()[0].setTint(getResources().getColor(android.R.color.holo_red_light));
                    
                    // Disable select button
                    selectLocationButton.setEnabled(false);
                    selectLocationButton.setAlpha(0.5f);
                }

                // Set button click listener
                selectLocationButton.setOnClickListener(v -> {
                    if (location.isInRange()) {
                        // Start attendance activity with the selected location
                        Intent intent = new Intent(LocationSelectionActivity.this, AttendanceActivity.class);
                        intent.putExtra("location_id", location.getId());
                        intent.putExtra("location_name", location.getName());
                        intent.putExtra("location_latitude", location.getLatitude());
                        intent.putExtra("location_longitude", location.getLongitude());
                        intent.putExtra("location_radius", location.getRadius());
                        startActivity(intent);
                    } else {
                        Toast.makeText(LocationSelectionActivity.this, 
                                "You must be within range of this location to check in", 
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }
    }
} 