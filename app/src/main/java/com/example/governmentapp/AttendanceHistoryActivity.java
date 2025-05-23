package com.example.governmentapp;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AttendanceHistoryActivity extends AppCompatActivity {
    private static final String TAG = "AttendanceHistory";
    
    private TextView emptyStateText;
    private LinearLayout emptyStateContainer;
    private LinearProgressIndicator progressIndicator;
    private RecyclerView attendanceRecyclerView;
    private TabLayout tabLayout;
    private Toolbar toolbar;
    private TextView thisMonthCount;
    private TextView last7DaysCount;
    private TextView todayCount;
    
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private AttendanceAdapter adapter;
    private List<AttendanceRecord> allRecords = new ArrayList<>();
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attendance_history);
        
        // Initialize Firebase
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        
        // Initialize UI components
        initViews();
        
        // Set up Toolbar
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        
        // Set up RecyclerView
        attendanceRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AttendanceAdapter();
        attendanceRecyclerView.setAdapter(adapter);
        
        // Set up back button
        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        
        // Set up tab selection
        setupTabs();
        
        // Load attendance data
        loadAttendanceHistory();
    }
    
    private void initViews() {
        emptyStateText = findViewById(R.id.emptyStateText);
        emptyStateContainer = findViewById(R.id.emptyStateContainer);
        progressIndicator = findViewById(R.id.progressIndicator);
        attendanceRecyclerView = findViewById(R.id.attendanceRecyclerView);
        tabLayout = findViewById(R.id.tabLayout);
        toolbar = findViewById(R.id.toolbar);
        thisMonthCount = findViewById(R.id.thisMonthCount);
        last7DaysCount = findViewById(R.id.last7DaysCount);
        todayCount = findViewById(R.id.todayCount);
    }
    
    private void setupTabs() {
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                filterRecordsByTab(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                // Not needed
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                // Not needed
            }
        });
    }
    
    private void filterRecordsByTab(int tabPosition) {
        if (allRecords.isEmpty()) {
            showEmptyState();
            return;
        }
        
        List<AttendanceRecord> filteredRecords = new ArrayList<>();
        
        switch (tabPosition) {
            case 0: // All
                filteredRecords.addAll(allRecords);
                break;
            case 1: // Check In
                for (AttendanceRecord record : allRecords) {
                    if (record.type.toLowerCase().contains("check in") || 
                            record.type.toLowerCase().contains("checkin")) {
                        filteredRecords.add(record);
                    }
                }
                break;
            case 2: // Check Out
                for (AttendanceRecord record : allRecords) {
                    if (record.type.toLowerCase().contains("check out") || 
                            record.type.toLowerCase().contains("checkout")) {
                        filteredRecords.add(record);
                    }
                }
                break;
        }
        
        if (filteredRecords.isEmpty()) {
            showEmptyStateWithMessage("No " + tabLayout.getTabAt(tabPosition).getText() + " records found");
        } else {
            hideEmptyState();
            adapter.setAttendanceRecords(filteredRecords);
        }
    }
    
    private void loadAttendanceHistory() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        showLoading();
        
        String userEmail = currentUser.getEmail();
        String userUid = currentUser.getUid();
        
        Log.d(TAG, "Looking for attendance records for user with email: " + userEmail + " and UID: " + userUid);
        
        // First, check exactly as shown in the screenshot
        checkAttendanceCollection(userEmail, userUid);
    }
    
    private void checkAttendanceCollection(String userEmail, String userUid) {
        // Check the "attendance" collection first, as it appears in the screenshot
        db.collection("attendance")
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                Log.d(TAG, "Retrieved " + queryDocumentSnapshots.size() + " documents from attendance collection");
                
                List<AttendanceRecord> records = new ArrayList<>();
                
                // Look through all documents as we need to match different field combinations
                for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                    Log.d(TAG, "Examining document: " + document.getId());
                    
                    // Log all fields in the document to help debugging
                    Log.d(TAG, "Document data: " + document.getData().toString());
                    
                    // Check if this document belongs to the current user by checking multiple field combinations
                    boolean matchesUser = false;
                    
                    // Check exact match as in screenshot
                    if (userEmail != null && userEmail.equals(document.getString("userEmail"))) {
                        Log.d(TAG, "Matched by userEmail field");
                        matchesUser = true;
                    }
                    
                    // Also check userId field with UID
                    if (!matchesUser && userUid != null && userUid.equals(document.getString("userId"))) {
                        Log.d(TAG, "Matched by userId field (UID)");
                        matchesUser = true;
                    }
                    
                    if (matchesUser) {
                        AttendanceRecord record = getAttendanceRecordFromDocument(document);
                        if (record != null) {
                            records.add(record);
                            Log.d(TAG, "Added record: date=" + record.date + ", time=" + record.time + ", type=" + record.type);
                        }
                    }
                }
                
                if (records.isEmpty()) {
                    Log.d(TAG, "No records found in attendance collection, checking other collections");
                    checkOtherCollections(userEmail, userUid);
                } else {
                    Log.d(TAG, "Found " + records.size() + " records in attendance collection");
                    allRecords.addAll(records);
                    processRecordsAndUpdateUI();
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error loading from attendance collection", e);
                checkOtherCollections(userEmail, userUid);
            });
    }
    
    private void checkOtherCollections(String userEmail, String userUid) {
        // Then try the user_attendance collection
        db.collection("user_attendance")
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                List<AttendanceRecord> records = new ArrayList<>();
                
                // Look through all documents
                for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                    Log.d(TAG, "Examining document in user_attendance: " + document.getId());
                    Log.d(TAG, "Document data: " + document.getData().toString());
                    
                    // Check if this document belongs to the current user by checking multiple field combinations
                    boolean matchesUser = false;
                    
                    if (userEmail != null) {
                        if (userEmail.equals(document.getString("userEmail")) || 
                            userEmail.equals(document.getString("userId")) || 
                            userEmail.equals(document.getString("email"))) {
                            matchesUser = true;
                        }
                    }
                    
                    if (!matchesUser && userUid != null) {
                        if (userUid.equals(document.getString("userId")) || 
                            userUid.equals(document.getString("uid")) || 
                            userUid.equals(document.getString("userUid"))) {
                            matchesUser = true;
                        }
                    }
                    
                    if (matchesUser) {
                        AttendanceRecord record = getAttendanceRecordFromDocument(document);
                        if (record != null) {
                            records.add(record);
                        }
                    }
                }
                
                if (records.isEmpty()) {
                    checkAppDataCollection(userEmail, userUid);
                } else {
                    allRecords.addAll(records);
                    processRecordsAndUpdateUI();
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error loading from user_attendance collection", e);
                checkAppDataCollection(userEmail, userUid);
            });
    }
    
    private void checkAppDataCollection(String userEmail, String userUid) {
        // Finally, try the app_data collection
        db.collection("app_data")
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                List<AttendanceRecord> records = new ArrayList<>();
                
                // Look through all documents
                for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                    // Check if this document belongs to the current user
                    boolean matchesUser = false;
                    
                    if (userEmail != null) {
                        if (userEmail.equals(document.getString("userEmail")) || 
                            userEmail.equals(document.getString("userId")) || 
                            userEmail.equals(document.getString("email"))) {
                            matchesUser = true;
                        }
                    }
                    
                    if (!matchesUser && userUid != null) {
                        if (userUid.equals(document.getString("userId")) || 
                            userUid.equals(document.getString("uid")) || 
                            userUid.equals(document.getString("userUid"))) {
                            matchesUser = true;
                        }
                    }
                    
                    if (matchesUser) {
                        AttendanceRecord record = getAttendanceRecordFromDocument(document);
                        if (record != null) {
                            records.add(record);
                        }
                    }
                }
                
                allRecords.addAll(records);
                processRecordsAndUpdateUI();
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error loading from app_data collection", e);
                hideLoading();
                showEmptyState();
                Toast.makeText(AttendanceHistoryActivity.this, "Failed to load attendance records", Toast.LENGTH_SHORT).show();
            });
    }
    
    private AttendanceRecord getAttendanceRecordFromDocument(QueryDocumentSnapshot document) {
        String date = document.getString("date");
        String time = document.getString("time");
        String type = document.getString("type");
        String locationName = document.getString("locationName");
        
        // Handle Firebase timestamp format (as seen in screenshot)
        if (date == null && document.contains("timestamp")) {
            // Extract date and time from timestamp field
            try {
                // Try to get timestamp as a Date object first
                Date timestamp = document.getDate("timestamp");
                if (timestamp != null) {
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                    SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                    date = dateFormat.format(timestamp);
                    time = timeFormat.format(timestamp);
                    Log.d(TAG, "Converted timestamp to date: " + date + " and time: " + time);
                } else {
                    // If not a Date, try as string (as seen in the screenshot)
                    String timestampStr = document.getString("timestamp");
                    if (timestampStr != null) {
                        Log.d(TAG, "Found timestamp string: " + timestampStr);
                        // Parse timestamp like "May 13, 2025 at 10:18:10PM UTC+5:30"
                        // This is complex, so we'll extract approximately
                        try {
                            // Parse a string like "May 13, 2025 at 10:18:10PM"
                            SimpleDateFormat inputFormat = new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm:ssa", Locale.US);
                            // Remove timezone part for parsing
                            String trimmedTimestamp = timestampStr;
                            if (timestampStr.contains("UTC")) {
                                trimmedTimestamp = timestampStr.substring(0, timestampStr.indexOf("UTC")).trim();
                            }
                            Date parsedDate = inputFormat.parse(trimmedTimestamp);
                            if (parsedDate != null) {
                                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                                SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                                date = dateFormat.format(parsedDate);
                                time = timeFormat.format(parsedDate);
                                Log.d(TAG, "Parsed timestamp string to date: " + date + " and time: " + time);
                            }
                        } catch (ParseException e) {
                            Log.e(TAG, "Error parsing timestamp string", e);
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing timestamp field", e);
            }
        }
        
        if (date != null && time != null) {
            // Handle case where type is missing
            if (type == null) {
                // Try to infer from other fields or use default
                if (document.contains("checkInTime")) {
                    type = "check in";
                } else if (document.contains("checkOutTime")) {
                    type = "check out";
                } else {
                    // Check the format in the screenshot
                    type = document.getString("type");
                    if (type == null) {
                        type = "attendance";
                    }
                }
            }
            
            // Handle missing location name
            if (locationName == null) {
                locationName = document.getString("locationName");
                if (locationName == null) {
                    // Try other possible field names seen in the screenshot
                    locationName = "Unknown Location";
                }
            }
            
            return new AttendanceRecord(
                    document.getId(),
                    date,
                    time,
                    type,
                    locationName
            );
        }
        return null;
    }
    
    private void processRecordsAndUpdateUI() {
        // Sort records by date (newest first)
        Collections.sort(allRecords, new Comparator<AttendanceRecord>() {
            private final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            
            @Override
            public int compare(AttendanceRecord o1, AttendanceRecord o2) {
                try {
                    Date date1 = dateFormat.parse(o1.date + " " + o1.time);
                    Date date2 = dateFormat.parse(o2.date + " " + o2.time);
                    
                    if (date1 != null && date2 != null) {
                        return date2.compareTo(date1); // Descending order
                    }
                } catch (ParseException e) {
                    Log.e(TAG, "Error parsing date", e);
                }
                return 0;
            }
        });
        
        // Update UI
        hideLoading();
        
        if (allRecords.isEmpty()) {
            showEmptyState();
        } else {
            // Update statistics
            updateStatistics();
            
            // Filter based on current tab
            filterRecordsByTab(tabLayout.getSelectedTabPosition());
        }
    }
    
    private void updateStatistics() {
        int monthCount = 0;
        int weekCount = 0;
        int dayCount = 0;
        
        // Get current date
        Calendar calendar = Calendar.getInstance();
        Date today = calendar.getTime();
        
        // Get 7 days ago
        calendar.add(Calendar.DAY_OF_YEAR, -7);
        Date sevenDaysAgo = calendar.getTime();
        
        // Get first day of month
        calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        Date firstDayOfMonth = calendar.getTime();
        
        // Date format for comparing
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String todayStr = dateFormat.format(today);
        
        try {
            for (AttendanceRecord record : allRecords) {
                Date recordDate = dateFormat.parse(record.date);
                
                if (recordDate != null) {
                    // Check if record is from today
                    if (dateFormat.format(recordDate).equals(todayStr)) {
                        dayCount++;
                    }
                    
                    // Check if record is from this week (last 7 days)
                    if (recordDate.after(sevenDaysAgo) || recordDate.equals(sevenDaysAgo)) {
                        weekCount++;
                    }
                    
                    // Check if record is from this month
                    if (recordDate.after(firstDayOfMonth) || recordDate.equals(firstDayOfMonth)) {
                        monthCount++;
                    }
                }
            }
        } catch (ParseException e) {
            Log.e(TAG, "Error parsing date for statistics", e);
        }
        
        // Update UI
        thisMonthCount.setText(String.valueOf(monthCount));
        last7DaysCount.setText(String.valueOf(weekCount));
        todayCount.setText(String.valueOf(dayCount));
    }
    
    private void showLoading() {
        progressIndicator.setVisibility(View.VISIBLE);
        attendanceRecyclerView.setVisibility(View.GONE);
        emptyStateContainer.setVisibility(View.GONE);
    }
    
    private void hideLoading() {
        progressIndicator.setVisibility(View.GONE);
    }
    
    private void showEmptyState() {
        emptyStateContainer.setVisibility(View.VISIBLE);
        attendanceRecyclerView.setVisibility(View.GONE);
    }
    
    private void showEmptyStateWithMessage(String message) {
        emptyStateText.setText(message);
        showEmptyState();
    }
    
    private void hideEmptyState() {
        emptyStateContainer.setVisibility(View.GONE);
        attendanceRecyclerView.setVisibility(View.VISIBLE);
    }
    
    // Attendance record model class
    private static class AttendanceRecord {
        private final String id;
        private final String date;
        private final String time;
        private final String type;
        private final String locationName;
        
        public AttendanceRecord(String id, String date, String time, String type, String locationName) {
            this.id = id;
            this.date = date;
            this.time = time;
            this.type = type;
            this.locationName = locationName;
        }
    }
    
    // Adapter for attendance records
    private class AttendanceAdapter extends RecyclerView.Adapter<AttendanceAdapter.ViewHolder> {
        private List<AttendanceRecord> attendanceRecords = new ArrayList<>();
        
        public void setAttendanceRecords(List<AttendanceRecord> attendanceRecords) {
            this.attendanceRecords = attendanceRecords;
            notifyDataSetChanged();
        }
        
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_attendance_record, parent, false);
            return new ViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AttendanceRecord record = attendanceRecords.get(position);
            
            // Format date to more readable format
            try {
                SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                Date date = inputFormat.parse(record.date);
                
                if (date != null) {
                    SimpleDateFormat outputFormat = new SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault());
                    holder.dateText.setText(outputFormat.format(date));
                } else {
                    holder.dateText.setText(record.date);
                }
            } catch (ParseException e) {
                holder.dateText.setText(record.date);
            }
            
            // Format time
            try {
                SimpleDateFormat inputFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                Date time = inputFormat.parse(record.time);
                
                if (time != null) {
                    SimpleDateFormat outputFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
                    holder.timeText.setText(outputFormat.format(time));
                } else {
                    holder.timeText.setText(record.time);
                }
            } catch (ParseException e) {
                holder.timeText.setText(record.time);
            }
            
            // Set type with appropriate styling
            String formattedType = record.type.substring(0, 1).toUpperCase() + record.type.substring(1);
            holder.typeText.setText(formattedType);
            
            // Set type color and indicator based on check in or check out
            View typeIndicator = holder.itemView.findViewById(R.id.typeIndicator);
            int greenColor = getResources().getColor(android.R.color.holo_green_dark);
            int blueColor = getResources().getColor(android.R.color.holo_blue_dark);
            
            if (record.type.toLowerCase().contains("check in")) {
                holder.typeText.setBackgroundColor(greenColor);
                typeIndicator.setBackgroundColor(greenColor);
            } else if (record.type.toLowerCase().contains("check out")) {
                holder.typeText.setBackgroundColor(blueColor);
                typeIndicator.setBackgroundColor(blueColor);
            }
            
            // Set location
            holder.locationText.setText(record.locationName);
        }
        
        @Override
        public int getItemCount() {
            return attendanceRecords.size();
        }
        
        class ViewHolder extends RecyclerView.ViewHolder {
            TextView dateText;
            TextView timeText;
            TextView typeText;
            TextView locationText;
            
            ViewHolder(View itemView) {
                super(itemView);
                dateText = itemView.findViewById(R.id.dateText);
                timeText = itemView.findViewById(R.id.timeText);
                typeText = itemView.findViewById(R.id.typeText);
                locationText = itemView.findViewById(R.id.locationText);
            }
        }
    }
} 