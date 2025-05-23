package com.example.governmentapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {
    private TextInputEditText emailEditText;
    private TextInputEditText passwordEditText;
    private TextInputLayout emailLayout;
    private TextInputLayout passwordLayout;
    private MaterialButton loginButton;
    private MaterialButton registerButton;
    private MaterialButton userButton;
    private MaterialButton adminButton;
    private TextView forgotPasswordButton;
    private String selectedRole = "USER"; // Default role
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Force light mode
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Initialize views
        emailEditText = findViewById(R.id.sevarthIdEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        emailLayout = findViewById(R.id.sevarthIdLayout);
        passwordLayout = findViewById(R.id.passwordLayout);
        loginButton = findViewById(R.id.loginButton);
        userButton = findViewById(R.id.userButton);
        adminButton = findViewById(R.id.adminButton);
        forgotPasswordButton = findViewById(R.id.forgotPasswordButton);

        // Set click listeners
        userButton.setOnClickListener(v -> selectRole("USER"));
        adminButton.setOnClickListener(v -> selectRole("ADMIN"));
        loginButton.setOnClickListener(v -> attemptLogin());
        forgotPasswordButton.setOnClickListener(v -> showForgotPasswordDialog());
    }

    @Override
    public void onStart() {
        super.onStart();
        // Check if user is signed in (non-null) and update UI accordingly.
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if(currentUser != null) {
            // Navigate to appropriate screen based on selected role
            navigateToAttendanceActivity();
        }
    }

    private void selectRole(String role) {
        selectedRole = role;
        if ("USER".equals(role)) {
            userButton.setBackgroundTintList(getColorStateList(R.color.light_blue));
            userButton.setTextColor(getColor(R.color.primary));
            adminButton.setBackgroundTintList(getColorStateList(R.color.white));
            adminButton.setTextColor(getColor(R.color.gray));
        } else {
            adminButton.setBackgroundTintList(getColorStateList(R.color.light_blue));
            adminButton.setTextColor(getColor(R.color.primary));
            userButton.setBackgroundTintList(getColorStateList(R.color.white));
            userButton.setTextColor(getColor(R.color.gray));
        }
    }

    private void setLoading(boolean isLoading) {
        loginButton.setEnabled(!isLoading);
        loginButton.setText(isLoading ? "Logging in..." : "Login");
        userButton.setEnabled(!isLoading);
        adminButton.setEnabled(!isLoading);
        emailLayout.setEnabled(!isLoading);
        passwordLayout.setEnabled(!isLoading);
        forgotPasswordButton.setEnabled(!isLoading);
    }

    private void clearErrors() {
        emailLayout.setError(null);
        passwordLayout.setError(null);
    }

    private void attemptLogin() {
        String sevarthId = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        clearErrors();

        if (sevarthId.isEmpty()) {
            emailLayout.setError("Please enter your Sevarth ID");
            return;
        }

        if (password.isEmpty()) {
            passwordLayout.setError("Please enter your password");
            return;
        }

        setLoading(true);

        // Log the exact sevarthId being queried
        Log.d("LoginActivity", "Attempting to find user with sevarthId: '" + sevarthId + "'");

        // First, look up the email using sevarthId in Firestore
        db.collection("users")
            .whereEqualTo("sevarthId", sevarthId)
            .get()
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Log.d("LoginActivity", "Query successful, found " + 
                          (task.getResult().isEmpty() ? "0" : task.getResult().size()) + " documents");
                    
                    if (!task.getResult().isEmpty()) {
                        proceedWithLogin(task.getResult().getDocuments().get(0), password);
                    } else {
                        // Try a case-insensitive search by loading all users and filtering
                        Log.d("LoginActivity", "No exact match found, trying case-insensitive search...");
                        performCaseInsensitiveSearch(sevarthId, password);
                    }
                } else {
                    // Query failed
                    setLoading(false);
                    Log.e("LoginActivity", "Query failed", task.getException());
                    Toast.makeText(LoginActivity.this, "Database error: " + task.getException().getMessage(), 
                        Toast.LENGTH_SHORT).show();
                }
            });
    }
    
    private void performCaseInsensitiveSearch(String sevarthId, String password) {
        // Get all users and filter manually for case-insensitive match
        db.collection("users")
            .get()
            .addOnCompleteListener(task -> {
                boolean found = false;
                
                if (task.isSuccessful() && !task.getResult().isEmpty()) {
                    Log.d("LoginActivity", "Checking " + task.getResult().size() + " users for case-insensitive match");
                    
                    for (DocumentSnapshot document : task.getResult().getDocuments()) {
                        String dbSevarthId = document.getString("sevarthId");
                        
                        if (dbSevarthId != null && dbSevarthId.equalsIgnoreCase(sevarthId)) {
                            Log.d("LoginActivity", "Found case-insensitive match: " + dbSevarthId);
                            found = true;
                            proceedWithLogin(document, password);
                            break;
                        }
                    }
                }
                
                if (!found) {
                    // Still no user found even with case-insensitive search
                    setLoading(false);
                    Log.e("LoginActivity", "No user found with sevarthId (case-insensitive): '" + sevarthId + "'");
                    
                    // Debug: List all sevarthIds in database
                    Log.d("LoginActivity", "All users in database:");
                    for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                        Log.d("LoginActivity", "User: " + 
                             doc.getString("name") + 
                             ", SevarthID: '" + doc.getString("sevarthId") + "'");
                    }
                    
                    Toast.makeText(LoginActivity.this, "No user found with this Sevarth ID", 
                        Toast.LENGTH_SHORT).show();
                }
            })
            .addOnFailureListener(e -> {
                setLoading(false);
                Log.e("LoginActivity", "Case-insensitive query failed", e);
                Toast.makeText(LoginActivity.this, "Database error: " + e.getMessage(), 
                    Toast.LENGTH_SHORT).show();
            });
    }
    
    private void proceedWithLogin(DocumentSnapshot document, String password) {
        String email = document.getString("email");
        Log.d("LoginActivity", "Found user with email: " + email);

        // Check if the known admin is trying to login with sevarthId
        boolean isKnownAdmin = "ADMIN".equals(document.getString("role"));

        // Sign in with email and password
        mAuth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this, authTask -> {
                setLoading(false);
                if (authTask.isSuccessful()) {
                    // Sign in success
                    FirebaseUser user = mAuth.getCurrentUser();
                    
                    // If user selected admin role and is a known admin, allow direct access
                    if ("ADMIN".equals(selectedRole) && isKnownAdmin) {
                        Toast.makeText(LoginActivity.this, "Admin login successful", Toast.LENGTH_SHORT).show();
                        navigateToAttendanceActivity();
                        return;
                    }
                    
                    // Otherwise check user role in Firestore
                    String userRole = document.getString("role");
                    
                    // Only allow admin access if user has ADMIN role
                    if ("ADMIN".equals(selectedRole) && !"ADMIN".equals(userRole)) {
                        Toast.makeText(LoginActivity.this, 
                            "You don't have admin privileges", Toast.LENGTH_SHORT).show();
                        FirebaseAuth.getInstance().signOut();
                        return;
                    }
                    
                    Toast.makeText(LoginActivity.this, "Login successful", Toast.LENGTH_SHORT).show();
                    navigateToAttendanceActivity();
                } else {
                    // If sign in fails, display a message to the user
                    Log.e("LoginActivity", "Authentication failed", authTask.getException());
                    Toast.makeText(LoginActivity.this, "Authentication failed. Invalid password.", 
                        Toast.LENGTH_SHORT).show();
                }
            });
    }

    private void navigateToAttendanceActivity() {
        // Navigate based on role
        if ("ADMIN".equals(selectedRole)) {
            Intent intent = new Intent(LoginActivity.this, AdminDashboardActivity.class);
            startActivity(intent);
        } else {
            // Navigate to User Location Selection screen first
            Intent intent = new Intent(LoginActivity.this, LocationSelectionActivity.class);
            startActivity(intent);
        }
        
        // Close login activity to prevent going back
        finish();
    }
    
    private void showForgotPasswordDialog() {
        // Create a dialog with an input field
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Reset Password");
        builder.setMessage("Enter your Sevarth ID to receive a password reset link on your email");
        
        // Set up the input field
        final TextInputLayout inputLayout = new TextInputLayout(this);
        inputLayout.setHint("Sevarth ID");
        
        final TextInputEditText input = new TextInputEditText(this);
        // Pre-fill with the current input if available
        if (!TextUtils.isEmpty(emailEditText.getText())) {
            input.setText(emailEditText.getText());
        }
        
        inputLayout.addView(input);
        inputLayout.setPadding(48, 24, 48, 0);
        builder.setView(inputLayout);
        
        // Set up the buttons
        builder.setPositiveButton("Send Reset Link", (dialog, which) -> {
            String sevarthId = input.getText().toString().trim();
            if (TextUtils.isEmpty(sevarthId)) {
                Toast.makeText(LoginActivity.this, "Please enter your Sevarth ID", Toast.LENGTH_SHORT).show();
                return;
            }
            
            resetPasswordWithSevarthId(sevarthId);
        });
        
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        
        builder.show();
    }
    
    private void resetPasswordWithSevarthId(String sevarthId) {
        // Show loading state
        Toast.makeText(this, "Processing your request...", Toast.LENGTH_SHORT).show();
        
        // Find user by sevarthId to get their email
        db.collection("users")
            .whereEqualTo("sevarthId", sevarthId)
            .get()
            .addOnCompleteListener(task -> {
                if (task.isSuccessful() && !task.getResult().isEmpty()) {
                    DocumentSnapshot document = task.getResult().getDocuments().get(0);
                    String email = document.getString("email");
                    
                    // Send password reset email using Firebase Auth
                    sendPasswordResetEmail(email);
                } else {
                    Toast.makeText(LoginActivity.this, 
                        "No account found with this Sevarth ID", 
                        Toast.LENGTH_LONG).show();
                }
            });
    }
    
    private void sendPasswordResetEmail(String email) {
        // Send password reset email using Firebase Auth
        mAuth.sendPasswordResetEmail(email)
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Toast.makeText(LoginActivity.this, 
                            "Password reset email sent to your registered email", 
                            Toast.LENGTH_LONG).show();
                } else {
                    String errorMessage = task.getException() != null ? 
                            task.getException().getMessage() : 
                            "Failed to send password reset email";
                    Toast.makeText(LoginActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                }
            });
    }
}