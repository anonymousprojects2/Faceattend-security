package com.example.governmentapp;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.cardview.widget.CardView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.storage.FirebaseStorage;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.res.ColorStateList;
import android.view.animation.OvershootInterpolator;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;

public class AdminDashboardActivity extends AppCompatActivity {

    private TextView welcomeText;
    private TextView dateText;
    private TextView usersTab;
    private TextView officesTab;
    private TextView reportsTab;
    private TextView sectionTitleText;
    private LinearLayout userListContainer;
    private FloatingActionButton fabAddUser;
    
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    
    // Keep track of the selected tab
    private TextView selectedTab;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);
        
        // Force light mode
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        
        // Initialize Firebase components
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        
        // Initialize views
        welcomeText = findViewById(R.id.welcomeText);
        dateText = findViewById(R.id.dateText);
        usersTab = findViewById(R.id.usersTab);
        officesTab = findViewById(R.id.officesTab);
        reportsTab = findViewById(R.id.reportsTab);
        sectionTitleText = findViewById(R.id.sectionTitleText);
        userListContainer = findViewById(R.id.userListContainer);
        fabAddUser = findViewById(R.id.fabAddUser);
        
        // Initialize selectedTab to usersTab
        selectedTab = usersTab;
        
        ImageView refreshButton = findViewById(R.id.refreshButton);
        ImageView logoutButton = findViewById(R.id.logoutButton);
        
        // Set up welcome message with time-based greeting
        setupWelcomeMessage();
        
        // Set up date display
        setupDateDisplay();
        
        // Set up tab navigation
        setupTabNavigation();
        
        // Set up button click listeners
        refreshButton.setOnClickListener(v -> refreshData());
        logoutButton.setOnClickListener(v -> logout());
        
        // Set FAB click listener
        updateFabAction();
        
        // Add entrance animation for FAB
        fabAddUser.setScaleX(0);
        fabAddUser.setScaleY(0);
        fabAddUser.setAlpha(0f);
        fabAddUser.post(() -> {
            ObjectAnimator scaleX = ObjectAnimator.ofFloat(fabAddUser, "scaleX", 0f, 1f);
            ObjectAnimator scaleY = ObjectAnimator.ofFloat(fabAddUser, "scaleY", 0f, 1f);
            ObjectAnimator alpha = ObjectAnimator.ofFloat(fabAddUser, "alpha", 0f, 1f);
            
            AnimatorSet animSet = new AnimatorSet();
            animSet.playTogether(scaleX, scaleY, alpha);
            animSet.setDuration(500);
            animSet.setStartDelay(300);
            animSet.setInterpolator(new OvershootInterpolator());
            animSet.start();
        });
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        // Log the current state
        String currentTab = selectedTab == usersTab ? "Users" : 
                            selectedTab == officesTab ? "Offices" : "Reports";
        Log.d("AdminDashboard", "Activity resumed, loading data for " + currentTab + " tab");
        
        // Load data for the currently selected tab
        refreshData();
    }
    
    private void setupWelcomeMessage() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        
        // Get the current time of day for greeting
        Calendar c = Calendar.getInstance();
        int timeOfDay = c.get(Calendar.HOUR_OF_DAY);
        
        String greeting;
        if (timeOfDay < 12) {
            greeting = "Good morning";
        } else if (timeOfDay < 16) {
            greeting = "Good afternoon";
        } else {
            greeting = "Good evening";
        }
        
        String displayName = "Admin";
        if (currentUser != null) {
            if (currentUser.getDisplayName() != null && !currentUser.getDisplayName().isEmpty()) {
                displayName = currentUser.getDisplayName();
            } else if (currentUser.getEmail() != null) {
                // Extract name from email
                displayName = currentUser.getEmail().split("@")[0];
                // Capitalize first letter
                displayName = displayName.substring(0, 1).toUpperCase() + displayName.substring(1);
            }
        }
        
        welcomeText.setText(String.format("%s, %s", greeting, displayName));
    }
    
    private void setupDateDisplay() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault());
        String currentDate = dateFormat.format(new Date());
        dateText.setText(currentDate);
    }
    
    private void loadUsers() {
        // Clear existing user list
        userListContainer.removeAllViews();
        
        // Add animation to container
        userListContainer.setLayoutAnimation(AnimationUtils.loadLayoutAnimation(this, R.anim.layout_animation_scale_up));
        
        // Load users from Firestore
        db.collection("users")
            .get()
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    if (task.getResult().isEmpty()) {
                        // No users found, show empty state
                        showEmptyState();
                    } else {
                        // Hide empty state if it exists
                        hideEmptyState();
                        
                        // Create a list to sort users by name
                        List<Map.Entry<String, QueryDocumentSnapshot>> users = new ArrayList<>();
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            String name = document.getString("name");
                            if (name != null) {
                                users.add(new AbstractMap.SimpleEntry<>(name, document));
                            }
                        }
                        
                        // Sort users by name
                        Collections.sort(users, (a, b) -> a.getKey().compareTo(b.getKey()));
                        
                        // Add sorted users to the view
                        for (Map.Entry<String, QueryDocumentSnapshot> entry : users) {
                            QueryDocumentSnapshot document = entry.getValue();
                            Map<String, Object> userData = document.getData();
                            String userId = document.getId();
                            addUserCard(userId, userData);
                        }
                        
                        // Start the layout animation
                        userListContainer.scheduleLayoutAnimation();
                    }
                } else {
                    Toast.makeText(AdminDashboardActivity.this, 
                            "Error loading users: " + task.getException().getMessage(), 
                            Toast.LENGTH_SHORT).show();
                }
            });
    }
    
    private void addUserCard(String userId, Map<String, Object> userData) {
        // Inflate the user card layout
        View userCardView = LayoutInflater.from(this).inflate(
                R.layout.item_user_card, userListContainer, false);
        
        // Get views from the user card layout
        TextView nameText = userCardView.findViewById(R.id.userNameText);
        TextView sevarthIdText = userCardView.findViewById(R.id.sevarthIdText);
        TextView emailText = userCardView.findViewById(R.id.emailText);
        TextView locationText = userCardView.findViewById(R.id.locationText);
        ImageView editButton = userCardView.findViewById(R.id.editButton);
        ImageView deleteButton = userCardView.findViewById(R.id.deleteButton);
        
        // Set user data to views
        String name = (String) userData.get("name");
        String sevarthId = (String) userData.get("sevarthId");
        String email = (String) userData.get("email");
        
        // Handle both old and new location format
        String locationDisplay = "Not assigned";
        
        // Check for multiple locations format first
        Object locationsObj = userData.get("locations");
        if (locationsObj instanceof List && !((List<?>) locationsObj).isEmpty()) {
            List<?> locations = (List<?>) locationsObj;
            if (locations.size() == 1) {
                // Single location
                locationDisplay = locations.get(0).toString();
            } else if (locations.size() > 1) {
                // Multiple locations
                locationDisplay = locations.get(0).toString() + " + " + (locations.size() - 1) + " more";
            }
        } else {
            // Check for legacy single location format
            String singleLocation = (String) userData.get("location");
            if (singleLocation != null && !singleLocation.isEmpty()) {
                locationDisplay = singleLocation;
            }
        }
        
        nameText.setText(name != null ? name : "User");
        sevarthIdText.setText("Sevarth ID: " + (sevarthId != null ? sevarthId : "Not set"));
        emailText.setText("Email: " + (email != null ? email : "Not set"));
        locationText.setText("Location: " + locationDisplay);
        
        // Set click listeners for action buttons
        editButton.setOnClickListener(v -> editUser(userId, userData));
        deleteButton.setOnClickListener(v -> deleteUser(userId));
        
        // Add the user card to the container
        userListContainer.addView(userCardView);
    }
    
    private void showEmptyState() {
        // If there are no users, show an empty state message
        if (userListContainer.getChildCount() == 0) {
            View emptyStateView = LayoutInflater.from(this).inflate(
                    R.layout.item_empty_state, userListContainer, false);
            
            TextView emptyStateText = emptyStateView.findViewById(R.id.emptyStateText);
            emptyStateText.setText("No users found. Add a new user to get started.");
            
            userListContainer.addView(emptyStateView);
        }
    }
    
    private void hideEmptyState() {
        // Remove empty state if it exists
        for (int i = 0; i < userListContainer.getChildCount(); i++) {
            View child = userListContainer.getChildAt(i);
            if (child.getId() == R.id.emptyStateLayout) {
                userListContainer.removeView(child);
                break;
            }
        }
    }
    
    private void setupTabNavigation() {
        // Set users tab as selected by default
        highlightTab(usersTab);
        
        // Set click listeners for tabs
        usersTab.setOnClickListener(v -> {
            if (selectedTab != usersTab) {
                // Add exit animation for current content
                userListContainer.animate()
                    .alpha(0f)
                    .translationX(-25f)
                    .setDuration(100)
                    .withEndAction(() -> {
            highlightTab(usersTab);
            // Update section title
            sectionTitleText.setText("Users");
            // Load users content
            loadUsers();
            // Update FAB action
            updateFabAction();
                        // Add entrance animation for new content
                        userListContainer.setTranslationX(25f);
                        userListContainer.animate()
                            .alpha(1f)
                            .translationX(0f)
                            .setDuration(100)
                            .start();
                    }).start();
            }
        });
        
        officesTab.setOnClickListener(v -> {
            if (selectedTab != officesTab) {
                // Add exit animation for current content
                userListContainer.animate()
                    .alpha(0f)
                    .translationX(-25f)
                    .setDuration(100)
                    .withEndAction(() -> {
            highlightTab(officesTab);
            // Update section title
            sectionTitleText.setText("Offices");
            // Load offices content
            loadOffices();
            // Update FAB action
            updateFabAction();
                        // Add entrance animation for new content
                        userListContainer.setTranslationX(25f);
                        userListContainer.animate()
                            .alpha(1f)
                            .translationX(0f)
                            .setDuration(100)
                            .start();
                    }).start();
            }
        });
        
        reportsTab.setOnClickListener(v -> {
            if (selectedTab != reportsTab) {
                // Add exit animation for current content
                userListContainer.animate()
                    .alpha(0f)
                    .translationX(-25f)
                    .setDuration(100)
                    .withEndAction(() -> {
            highlightTab(reportsTab);
            // Update section title
            sectionTitleText.setText("Reports");
            // Load reports content
            loadReports();
            // Update FAB action
            updateFabAction();
                        // Add entrance animation for new content
                        userListContainer.setTranslationX(25f);
                        userListContainer.animate()
                            .alpha(1f)
                            .translationX(0f)
                            .setDuration(100)
                            .start();
                    }).start();
            }
        });
    }
    
    private void highlightTab(TextView selectedTab) {
        // Remove bold style from all tabs
        usersTab.setTypeface(null, android.graphics.Typeface.NORMAL);
        officesTab.setTypeface(null, android.graphics.Typeface.NORMAL);
        reportsTab.setTypeface(null, android.graphics.Typeface.NORMAL);
        
        // Set bold style to selected tab
        selectedTab.setTypeface(null, android.graphics.Typeface.BOLD);
        
        // Add bottom border to selected tab (could be replaced with an actual border view)
        usersTab.setBackgroundResource(android.R.color.transparent);
        officesTab.setBackgroundResource(android.R.color.transparent);
        reportsTab.setBackgroundResource(android.R.color.transparent);
        
        // Add pill shape background to selected tab
        selectedTab.setBackgroundResource(R.drawable.card_ripple_effect);
        
        // Update the selectedTab reference
        this.selectedTab = selectedTab;
    }
    
    private void refreshData() {
        // Reload data based on current tab
        if (selectedTab == usersTab) {
            loadUsers();
        } else if (selectedTab == officesTab) {
            // Refresh offices
            loadOffices();
        } else if (selectedTab == reportsTab) {
            // Refresh reports
            loadReports();
        }
    }
    
    private void logout() {
        mAuth.signOut();
        finish();
    }
    
    private void showAddUserDialog() {
        // Launch the AddUserActivity
        Intent intent = new Intent(AdminDashboardActivity.this, AddUserActivity.class);
        startActivity(intent);
    }
    
    private void showAddLocationDialog() {
        // Launch the AddLocationActivity
        Intent intent = new Intent(AdminDashboardActivity.this, AddLocationActivity.class);
        startActivity(intent);
    }
    
    private void updateFabAction() {
        // Set the appropriate action based on which tab is selected
        if (selectedTab == usersTab) {
            // Users tab is selected
            fabAddUser.setOnClickListener(v -> showAddUserDialog());
        } else if (selectedTab == officesTab) {
            // Offices tab is selected
            fabAddUser.setOnClickListener(v -> showAddLocationDialog());
        } else if (selectedTab == reportsTab) {
            // Reports tab is selected - hide FAB or set different action
            fabAddUser.setVisibility(View.GONE);
            return;
        }
        
        // Make sure FAB is visible for Users and Offices tabs
        fabAddUser.setVisibility(View.VISIBLE);
    }
    
    private void editUser(String userId, Map<String, Object> userData) {
        // Launch EditUserActivity with user ID
        Intent intent = new Intent(AdminDashboardActivity.this, EditUserActivity.class);
        intent.putExtra("user_id", userId);
        startActivity(intent);
    }
    
    private void deleteUser(String userId) {
        // Show confirmation dialog before deleting user
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Delete User");
        builder.setMessage("Are you sure you want to delete this user? This action cannot be undone.");
        builder.setPositiveButton("Delete", (dialog, which) -> {
            // Delete user from Authentication and Firestore
            db.collection("users").document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // Get user email
                        String userEmail = (String) documentSnapshot.get("email");
                        
                        // Delete user document from Firestore
                        db.collection("users").document(userId)
                            .delete()
                            .addOnSuccessListener(aVoid -> {
                                Log.d("AdminDashboard", "User document deleted successfully");
                                
                                // Also delete the user from Authentication if we have admin SDK capabilities
                                // Note: For a real app, you might need to use a server function for this
                                Toast.makeText(AdminDashboardActivity.this, 
                                        "User deleted successfully", Toast.LENGTH_SHORT).show();
                                
                                // Delete user photo from storage if exists
                                if (userEmail != null) {
                                    // Format email for filename: replace @ with _at_ and . with _dot_
                                    String formattedEmail = userEmail.replace("@", "_at_").replace(".", "_dot_");
                                    String imagePath = "faces/" + formattedEmail + ".jpg";
                                    
                                    // Get reference to the file and delete it
                                    FirebaseStorage.getInstance().getReference().child(imagePath)
                                        .delete()
                                        .addOnSuccessListener(aVoid2 -> 
                                            Log.d("AdminDashboard", "User photo deleted successfully"))
                                        .addOnFailureListener(e -> 
                                            Log.w("AdminDashboard", "Error deleting user photo", e));
                                }
                                
                                // Refresh the user list
                                loadUsers();
                            })
                            .addOnFailureListener(e -> {
                                Log.e("AdminDashboard", "Error deleting user document", e);
                                Toast.makeText(AdminDashboardActivity.this, 
                                        "Error deleting user: " + e.getMessage(), 
                                        Toast.LENGTH_SHORT).show();
                            });
                    } else {
                        Toast.makeText(AdminDashboardActivity.this, 
                                "User document not found", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("AdminDashboard", "Error getting user document", e);
                    Toast.makeText(AdminDashboardActivity.this, 
                            "Error getting user details: " + e.getMessage(), 
                            Toast.LENGTH_SHORT).show();
                });
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.show();
    }
    
    private void loadOffices() {
        // Clear existing user list
        userListContainer.removeAllViews();
        
        // Add animation to container
        userListContainer.setLayoutAnimation(AnimationUtils.loadLayoutAnimation(this, R.anim.layout_animation_scale_up));
        
        // Debug message
        Log.d("AdminDashboard", "Loading offices from Firestore...");
        
        // Load offices from Firestore
        db.collection("locations")
            .get()
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Log.d("AdminDashboard", "Query completed successfully");
                    if (task.getResult().isEmpty()) {
                        // No offices found, show empty state
                        Log.d("AdminDashboard", "No offices found in database");
                        showEmptyOfficesState();
                    } else {
                        // Hide empty state if it exists
                        hideEmptyState();
                        
                        // Create a list to sort offices by name
                        List<Map.Entry<String, QueryDocumentSnapshot>> offices = new ArrayList<>();
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            String officeName = document.getString("officeName");
                            if (officeName != null) {
                                offices.add(new AbstractMap.SimpleEntry<>(officeName, document));
                            }
                        }
                        
                        // Sort offices by name
                        Collections.sort(offices, (a, b) -> a.getKey().compareTo(b.getKey()));
                        
                        // Add sorted offices to the view
                        int count = 0;
                        for (Map.Entry<String, QueryDocumentSnapshot> entry : offices) {
                            count++;
                            QueryDocumentSnapshot document = entry.getValue();
                            Map<String, Object> officeData = document.getData();
                            String officeId = document.getId();
                            Log.d("AdminDashboard", "Adding office: " + entry.getKey() + " (ID: " + officeId + ")");
                            addOfficeCard(officeId, officeData);
                        }
                        Log.d("AdminDashboard", "Added " + count + " office(s) to the view");
                        
                        // Start the layout animation
                        userListContainer.scheduleLayoutAnimation();
                    }
                } else {
                    Log.e("AdminDashboard", "Error loading offices", task.getException());
                    Toast.makeText(AdminDashboardActivity.this, 
                            "Error loading offices: " + task.getException().getMessage(), 
                            Toast.LENGTH_SHORT).show();
                }
            });
    }
    
    private void addOfficeCard(String officeId, Map<String, Object> officeData) {
        // Inflate the office card layout
        View officeCardView = LayoutInflater.from(this).inflate(
                R.layout.item_office_card, userListContainer, false);
        
        // Get views from the card layout
        TextView nameText = officeCardView.findViewById(R.id.userNameText);
        TextView sevarthIdText = officeCardView.findViewById(R.id.sevarthIdText);
        TextView emailText = officeCardView.findViewById(R.id.emailText);
        TextView locationText = officeCardView.findViewById(R.id.locationText);
        ImageView editButton = officeCardView.findViewById(R.id.editButton);
        ImageView deleteButton = officeCardView.findViewById(R.id.deleteButton);
        
        // Set office data to views
        String officeName = (String) officeData.get("officeName");
        String taluka = (String) officeData.get("taluka");
        Double latitude = (Double) officeData.get("latitude");
        Double longitude = (Double) officeData.get("longitude");
        
        // Handle radius which could be Integer or Long
        Object radiusObj = officeData.get("radius");
        String radiusStr = "100m"; // Default
        if (radiusObj instanceof Long) {
            radiusStr = ((Long) radiusObj) + "m";
        } else if (radiusObj instanceof Integer) {
            radiusStr = ((Integer) radiusObj) + "m";
        } else if (radiusObj instanceof Double) {
            radiusStr = ((int) ((Double) radiusObj).doubleValue()) + "m";
        }
        
        nameText.setText(officeName != null ? officeName : "Office");
        sevarthIdText.setText("Taluka: " + (taluka != null ? taluka : "Not set"));
        emailText.setText("Location: " + (latitude != null && longitude != null ? 
                latitude + ", " + longitude : "Not set"));
        locationText.setText("Radius: " + radiusStr);
        
        // Set click listeners for action buttons
        editButton.setOnClickListener(v -> editOffice(officeId, officeData));
        deleteButton.setOnClickListener(v -> deleteOffice(officeId));
        
        // Add the office card to the container with animation
        officeCardView.setAlpha(0f);
        officeCardView.setTranslationY(25);
        officeCardView.setScaleX(0.9f);
        officeCardView.setScaleY(0.9f);
        userListContainer.addView(officeCardView);
        
        // Animate the card entry with scale and fade
        officeCardView.animate()
                .alpha(1f)
                .translationY(0)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(150)
                .setStartDelay(50)
                .setInterpolator(new OvershootInterpolator(0.5f))
                .start();
    }
    
    private void showEmptyOfficesState() {
        // If there are no offices, show an empty state message
        if (userListContainer.getChildCount() == 0) {
            View emptyStateView = LayoutInflater.from(this).inflate(
                    R.layout.item_empty_state, userListContainer, false);
            
            TextView emptyStateText = emptyStateView.findViewById(R.id.emptyStateText);
            emptyStateText.setText("No offices found. Add a new office location to get started.");
            
            userListContainer.addView(emptyStateView);
        }
    }
    
    private void editOffice(String officeId, Map<String, Object> officeData) {
        // Launch EditLocationActivity with office ID
        Intent intent = new Intent(AdminDashboardActivity.this, EditLocationActivity.class);
        intent.putExtra("office_id", officeId);
        startActivity(intent);
    }
    
    private void deleteOffice(String officeId) {
        // Show confirmation dialog before deleting office
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Delete Office");
        builder.setMessage("Are you sure you want to delete this office? This action cannot be undone.");
        builder.setPositiveButton("Delete", (dialog, which) -> {
            // Delete office from Firestore
            db.collection("locations").document(officeId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Log.d("AdminDashboard", "Office deleted successfully");
                    Toast.makeText(AdminDashboardActivity.this, "Office deleted successfully", Toast.LENGTH_SHORT).show();
                    // Refresh the office list
                    loadOffices();
                })
                .addOnFailureListener(e -> {
                    Log.e("AdminDashboard", "Error deleting office", e);
                    Toast.makeText(AdminDashboardActivity.this, 
                            "Error deleting office: " + e.getMessage(), 
                            Toast.LENGTH_SHORT).show();
                });
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    /**
     * Load report options in the UI
     */
    private void loadReports() {
        // Clear existing list
        userListContainer.removeAllViews();
        
        // Create modern UI for reports dashboard
        userListContainer.setLayoutAnimation(AnimationUtils.loadLayoutAnimation(this, R.anim.layout_animation_scale_up));
        
        // Add User Reports option
        View userReportsCard = createDashboardCard(
                R.drawable.ic_person,
                "User Reports",
                "View attendance records for individual users",
                "View Details",
                String.valueOf(getUserCount()) + " users",
                R.drawable.gradient_users,
                v -> {
                    Intent intent = new Intent(AdminDashboardActivity.this, UserReportsActivity.class);
                    startActivity(intent);
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                });
        userListContainer.addView(userReportsCard);
        
        // Add Daily Reports option
        View dailyReportsCard = createDashboardCard(
                R.drawable.ic_calendar,
                "Daily Reports",
                "View attendance statistics for specific dates",
                "View Details",
                "Today",
                R.drawable.gradient_primary,
                v -> {
                    Intent intent = new Intent(AdminDashboardActivity.this, DailyReportsActivity.class);
                    startActivity(intent);
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                });
        userListContainer.addView(dailyReportsCard);
        
        // Add Location Reports option
        View locationReportsCard = createDashboardCard(
                R.drawable.ic_location,
                "Location Reports",
                "View attendance data for different office locations",
                "View Details",
                String.valueOf(getOfficeCount()) + " offices",
                R.drawable.gradient_office,
                v -> {
                    Intent intent = new Intent(AdminDashboardActivity.this, LocationReportsActivity.class);
                    startActivity(intent);
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                });
        userListContainer.addView(locationReportsCard);
        
        // Start the layout animation
        userListContainer.scheduleLayoutAnimation();
    }
    
    /**
     * Create a dashboard card with the modern design
     */
    private View createDashboardCard(int iconRes, String title, String description, 
                                    String actionText, String statusInfo, 
                                    int gradientRes, View.OnClickListener clickListener) {
        // Inflate the dashboard card layout
        View cardView = LayoutInflater.from(this).inflate(
                R.layout.item_dashboard_card, userListContainer, false);
        
        // Get views from the card layout
        View headerBg = cardView.findViewById(R.id.cardHeaderBg);
        TextView titleTextView = cardView.findViewById(R.id.sectionTitle);
        TextView descriptionTextView = cardView.findViewById(R.id.sectionDescription);
        ImageView iconImageView = cardView.findViewById(R.id.sectionIcon);
        TextView actionTextView = cardView.findViewById(R.id.actionText);
        TextView statusInfoTextView = cardView.findViewById(R.id.statusInfo);
        
        // Set card data
        headerBg.setBackgroundResource(gradientRes);
        titleTextView.setText(title);
        descriptionTextView.setText(description);
        iconImageView.setImageResource(iconRes);
        actionTextView.setText(actionText);
        statusInfoTextView.setText(statusInfo);
        
        // Match icon tint to gradient color for better visual harmony
        if (gradientRes == R.drawable.gradient_users) {
            iconImageView.setColorFilter(Color.parseColor("#2979FF"));
            statusInfoTextView.setTextColor(Color.parseColor("#2979FF"));
        } else if (gradientRes == R.drawable.gradient_office) {
            iconImageView.setColorFilter(Color.parseColor("#1565C0"));
            statusInfoTextView.setTextColor(Color.parseColor("#1565C0"));
        } else if (gradientRes == R.drawable.gradient_reports) {
            iconImageView.setColorFilter(Color.parseColor("#7B1FA2"));
            statusInfoTextView.setTextColor(Color.parseColor("#7B1FA2"));
        } else {
            iconImageView.setColorFilter(Color.parseColor("#3D85F0"));
            statusInfoTextView.setTextColor(Color.parseColor("#3D85F0"));
        }
        
        // Set click listener for the card
        cardView.setOnClickListener(clickListener);
        
        return cardView;
    }
    
    /**
     * Get count of users for status display
     */
    private int getUserCount() {
        // Default value
        return 0;
    }
    
    /**
     * Get count of offices for status display
     */
    private int getOfficeCount() {
        // Default value
        return 0;
    }
} 