package com.example.governmentapp;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class LoginActivity extends AppCompatActivity {
    private TextInputEditText emailEditText;
    private TextInputEditText passwordEditText;
    private TextInputLayout emailLayout;
    private TextInputLayout passwordLayout;
    private MaterialButton loginButton;
    private MaterialButton registerButton;
    private MaterialButton userButton;
    private MaterialButton adminButton;
    private String selectedRole = "USER"; // Default role
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Force light mode
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        // Initialize views
        emailEditText = findViewById(R.id.sevarthIdEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        emailLayout = findViewById(R.id.sevarthIdLayout);
        passwordLayout = findViewById(R.id.passwordLayout);
        loginButton = findViewById(R.id.loginButton);
        userButton = findViewById(R.id.userButton);
        adminButton = findViewById(R.id.adminButton);

        // Set click listeners
        userButton.setOnClickListener(v -> selectRole("USER"));
        adminButton.setOnClickListener(v -> selectRole("ADMIN"));
        loginButton.setOnClickListener(v -> attemptLogin());
    }

    @Override
    public void onStart() {
        super.onStart();
        // Check if user is signed in (non-null) and update UI accordingly.
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if(currentUser != null) {
            // Navigate to main activity
            Toast.makeText(LoginActivity.this, "Already logged in", Toast.LENGTH_SHORT).show();
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
    }

    private void clearErrors() {
        emailLayout.setError(null);
        passwordLayout.setError(null);
    }

    private void attemptLogin() {
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        clearErrors();

        if (email.isEmpty()) {
            emailLayout.setError("Please enter your email");
            return;
        }

        if (password.isEmpty()) {
            passwordLayout.setError("Please enter password");
            return;
        }

        setLoading(true);

        // Sign in with email and password
        mAuth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this, task -> {
                setLoading(false);
                if (task.isSuccessful()) {
                    // Sign in success
                    FirebaseUser user = mAuth.getCurrentUser();
                    Toast.makeText(LoginActivity.this, "Login successful", Toast.LENGTH_SHORT).show();
                    // TODO: Navigate to main activity based on role
                    // For now, just show a toast with the role
                    Toast.makeText(LoginActivity.this, "Logged in as " + selectedRole, Toast.LENGTH_SHORT).show();
                } else {
                    // If sign in fails, display a message to the user
                    String errorMessage = task.getException() != null ? 
                        task.getException().getMessage() : 
                        "Authentication failed";
                    Toast.makeText(LoginActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                }
            });
    }
} 