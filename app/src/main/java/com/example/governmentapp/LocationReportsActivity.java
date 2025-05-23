package com.example.governmentapp;

import android.app.DatePickerDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.example.governmentapp.utils.EmailReportUtil;

public class LocationReportsActivity extends AppCompatActivity {
    
    private static final String TAG = "LocationReportsActivity";
    private FirebaseFirestore db;
    private RecyclerView locationReportsList;
    private RecyclerView detailedReportsList;
    private TextView titleText;
    private ImageView backButton;
    private Spinner locationSpinner;
    private Button selectDateButton;
    private TextView dateRangeText;
    private Button generateReportButton;
    private TextView noDataText;
    private RadioGroup viewTypeRadioGroup;
    private RadioButton summaryRadioButton;
    private RadioButton detailRadioButton;
    private CardView detailTableCard;
    
    private List<String> locationNames;
    private Map<String, String> locationIdsMap;
    private Calendar selectedDate;
    private String selectedLocation = "All Locations";
    
    private LocationReportsAdapter summaryAdapter;
    private LocationDetailAdapter detailAdapter;
    private List<Map<String, Object>> currentReports = new ArrayList<>();
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location_reports);
        
        // Initialize Firestore
        db = FirebaseFirestore.getInstance();
        
        // Initialize UI components
        titleText = findViewById(R.id.titleText);
        backButton = findViewById(R.id.backButton);
        locationReportsList = findViewById(R.id.locationReportsList);
        detailedReportsList = findViewById(R.id.detailedReportsList);
        locationSpinner = findViewById(R.id.locationSpinner);
        selectDateButton = findViewById(R.id.selectDateButton);
        dateRangeText = findViewById(R.id.dateRangeText);
        generateReportButton = findViewById(R.id.generateReportButton);
        noDataText = findViewById(R.id.noDataText);
        viewTypeRadioGroup = findViewById(R.id.viewTypeRadioGroup);
        summaryRadioButton = findViewById(R.id.summaryRadioButton);
        detailRadioButton = findViewById(R.id.detailRadioButton);
        detailTableCard = findViewById(R.id.detailTableCard);
        
        // Initialize location maps
        locationNames = new ArrayList<>();
        locationIdsMap = new HashMap<>();
        
        // Set up RecyclerView for summary view
        locationReportsList.setLayoutManager(new LinearLayoutManager(this));
        locationReportsList.setHasFixedSize(true);
        summaryAdapter = new LocationReportsAdapter(new ArrayList<>());
        locationReportsList.setAdapter(summaryAdapter);
        
        // Set up RecyclerView for detailed view
        detailedReportsList.setLayoutManager(new LinearLayoutManager(this));
        detailedReportsList.setHasFixedSize(true);
        detailAdapter = new LocationDetailAdapter();
        detailedReportsList.setAdapter(detailAdapter);
        
        // Set up back button
        backButton.setOnClickListener(v -> finish());
        
        // Initialize date to today
        selectedDate = Calendar.getInstance();
        updateDateDisplay();
        
        // Set up date picker
        selectDateButton.setOnClickListener(v -> showDatePickerDialog());
        
        // Set up view type toggle
        viewTypeRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.summaryRadioButton) {
                // Show summary view
                locationReportsList.setVisibility(View.VISIBLE);
                detailTableCard.setVisibility(View.GONE);
            } else {
                // Show detailed view
                locationReportsList.setVisibility(View.GONE);
                detailTableCard.setVisibility(View.VISIBLE);
            }
        });
        
        // Set up generate report button
        generateReportButton.setOnClickListener(v -> generateReport());
        
        // Load locations for spinner
        loadLocations();
        
        // Set up spinner listener
        locationSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedLocation = locationNames.get(position);
                // We won't load the report here automatically anymore, user will click generate
            }
            
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });
    }
    
    private void showDatePickerDialog() {
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    selectedDate.set(Calendar.YEAR, year);
                    selectedDate.set(Calendar.MONTH, month);
                    selectedDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    updateDateDisplay();
                },
                selectedDate.get(Calendar.YEAR),
                selectedDate.get(Calendar.MONTH),
                selectedDate.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.show();
    }
    
    private void updateDateDisplay() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault());
        String formattedDate = dateFormat.format(selectedDate.getTime());
        dateRangeText.setText(formattedDate);
    }
    
    private void generateReport() {
        // Hide all views initially
        locationReportsList.setVisibility(View.GONE);
        detailTableCard.setVisibility(View.GONE);
        noDataText.setVisibility(View.GONE);
        
        if ("All Locations".equals(selectedLocation)) {
            loadAllLocationsReport();
        } else {
            String locationId = locationIdsMap.get(selectedLocation);
            loadLocationAttendanceReports(locationId, selectedLocation);
        }
    }
    
    private void loadLocations() {
        // Add "All Locations" as first option
        locationNames.add("All Locations");
        
        // Load locations from Firestore
        db.collection("locations")
            .get()
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    for (QueryDocumentSnapshot document : task.getResult()) {
                        String locationName = document.getString("officeName");
                        if (locationName != null && !locationName.isEmpty()) {
                            locationNames.add(locationName);
                            locationIdsMap.put(locationName, document.getId());
                        }
                    }
                    
                    // Sort all locations except "All Locations" which should stay first
                    if (locationNames.size() > 1) {
                        List<String> sortedLocations = locationNames.subList(1, locationNames.size());
                        Collections.sort(sortedLocations);
                    }
                    
                    // Setup spinner adapter
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(
                            this, android.R.layout.simple_spinner_item, locationNames);
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    locationSpinner.setAdapter(adapter);
                } else {
                    Toast.makeText(this, "Error loading locations", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Error getting locations", task.getException());
                }
            });
    }
    
    private void loadAllLocationsReport() {
        // Show loading message
        Toast.makeText(this, "Loading reports...", Toast.LENGTH_SHORT).show();
        
        // Create start and end of day timestamps for filtering by date
        Calendar startOfDay = (Calendar) selectedDate.clone();
        startOfDay.set(Calendar.HOUR_OF_DAY, 0);
        startOfDay.set(Calendar.MINUTE, 0);
        startOfDay.set(Calendar.SECOND, 0);
        
        Calendar endOfDay = (Calendar) selectedDate.clone();
        endOfDay.set(Calendar.HOUR_OF_DAY, 23);
        endOfDay.set(Calendar.MINUTE, 59);
        endOfDay.set(Calendar.SECOND, 59);
        
        Date startDate = startOfDay.getTime();
        Date endDate = endOfDay.getTime();
        
        // First fetch users to get names
        fetchUsers(users -> {
            // Query attendance records for selected location and date range
            db.collection("attendance")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        if (task.getResult().isEmpty()) {
                            Toast.makeText(this, "No attendance records found", 
                                    Toast.LENGTH_SHORT).show();
                            noDataText.setVisibility(View.VISIBLE);
                            return;
                        }
                        
                        // Count attendance by location
                        Map<String, Integer> locationCounts = new HashMap<>();
                        int totalRecords = 0;
                        
                        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
                        String selectedDateStr = sdf.format(selectedDate.getTime());
                        
                        List<AttendanceRecord> detailedRecords = new ArrayList<>();
                        
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            Map<String, Object> data = document.getData();
                            
                            // Check if record matches the selected date
                            boolean isInRange = isRecordInDateRange(data, startDate, endDate, selectedDateStr);
                            String formattedTime = "";
                            
                            if (isInRange) {
                                String location = getLocationName(data);
                                
                                if (location != null && !location.isEmpty()) {
                                    locationCounts.put(location, locationCounts.getOrDefault(location, 0) + 1);
                                    totalRecords++;
                                    
                                    // Extract time for detailed view
                                    formattedTime = extractTimeFromRecord(data);
                                    
                                    // Add to detailed records
                                    String userEmail = (String) data.get("userEmail");
                                    String type = data.get("type") != null ? data.get("type").toString() : "Check-in";
                                    
                                    // Get user name from map, ensure we have a valid name
                                    String userName = users.get(userEmail);
                                    if (userName == null || userName.trim().isEmpty()) {
                                        // If no name found in users map, try to get it from the attendance record
                                        userName = (String) data.get("userName");
                                    }
                                    
                                    // Create detailed record
                                    AttendanceRecord record = new AttendanceRecord(
                                            document.getId(),
                                            formattedTime,
                                            userEmail != null ? userEmail : "Unknown User",
                                            userName != null && !userName.trim().isEmpty() ? userName : "Unknown User",
                                            type,
                                            location
                                    );
                                    
                                    detailedRecords.add(record);
                                }
                            }
                        }
                        
                        if (totalRecords == 0) {
                            Toast.makeText(this, "No attendance records found for selected date", 
                                    Toast.LENGTH_SHORT).show();
                            noDataText.setVisibility(View.VISIBLE);
                            return;
                        }
                        
                        // Create summary data for display
                        List<Map<String, Object>> summaryData = new ArrayList<>();
                        summaryData.add(createSummaryItem("Total Check-ins", totalRecords));
                        
                        // Add location-specific summaries
                        for (Map.Entry<String, Integer> entry : locationCounts.entrySet()) {
                            summaryData.add(createSummaryItem(entry.getKey(), entry.getValue()));
                        }
                        
                        // Set adapter for location reports
                        summaryAdapter.setSummaryData(summaryData);
                        
                        // Sort detailed records by time
                        Collections.sort(detailedRecords, (o1, o2) -> o1.time.compareTo(o2.time));
                        
                        // Set detail adapter data
                        detailAdapter.setAttendanceRecords(detailedRecords);
                        
                        // Show appropriate view based on selection
                        if (summaryRadioButton.isChecked()) {
                            locationReportsList.setVisibility(View.VISIBLE);
                            detailTableCard.setVisibility(View.GONE);
                        } else {
                            locationReportsList.setVisibility(View.GONE);
                            detailTableCard.setVisibility(View.VISIBLE);
                        }
                        
                        noDataText.setVisibility(View.GONE);
                        
                        // Store the reports for email functionality
                        currentReports = summaryData;
                        
                        Log.d(TAG, "Loaded summary for all locations: " + summaryData.size() + " items");
                    } else {
                        Toast.makeText(this, "Error loading attendance records", Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Error getting attendance records", task.getException());
                        noDataText.setVisibility(View.VISIBLE);
                    }
                });
        });
    }
    
    private void loadLocationAttendanceReports(String locationId, String locationName) {
        // Show loading message
        Toast.makeText(this, "Loading reports...", Toast.LENGTH_SHORT).show();
        
        // Create start and end of day timestamps
        Calendar startOfDay = (Calendar) selectedDate.clone();
        startOfDay.set(Calendar.HOUR_OF_DAY, 0);
        startOfDay.set(Calendar.MINUTE, 0);
        startOfDay.set(Calendar.SECOND, 0);
        
        Calendar endOfDay = (Calendar) selectedDate.clone();
        endOfDay.set(Calendar.HOUR_OF_DAY, 23);
        endOfDay.set(Calendar.MINUTE, 59);
        endOfDay.set(Calendar.SECOND, 59);
        
        Date startDate = startOfDay.getTime();
        Date endDate = endOfDay.getTime();
        
        // Fetch user data first
        fetchUsers(userEmailToNameMap -> {
            // Query attendance records
            db.collection("attendance")
                .whereEqualTo("locationId", locationId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        List<Map<String, Object>> detailedReports = new ArrayList<>();
                        List<AttendanceRecord> records = new ArrayList<>();
                        
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            Map<String, Object> data = document.getData();
                            
                            // Check if record is within selected date range
                            if (isRecordInDateRange(data, startDate, endDate, null)) {
                                String userEmail = (String) data.get("userEmail");
                                String userName = userEmailToNameMap.getOrDefault(userEmail, userEmail);
                                
                                // Get location information
                                String recordLocationName = locationName;
                                if (recordLocationName == null || recordLocationName.isEmpty()) {
                                    // Try to get location from the record
                                    if (data.containsKey("locationName")) {
                                        recordLocationName = (String) data.get("locationName");
                                    } else if (data.containsKey("location")) {
                                        Object locObj = data.get("location");
                                        if (locObj instanceof String) {
                                            recordLocationName = (String) locObj;
                                        } else if (locObj instanceof Map) {
                                            @SuppressWarnings("unchecked")
                                            Map<String, Object> locMap = (Map<String, Object>) locObj;
                                            if (locMap.containsKey("officeName")) {
                                                recordLocationName = (String) locMap.get("officeName");
                                            }
                                        }
                                    }
                                }
                                if (recordLocationName == null || recordLocationName.isEmpty()) {
                                    recordLocationName = "Unknown Location";
                                }
                                
                                // Create detailed record with all fields
                                Map<String, Object> detailedRecord = new HashMap<>(data);
                                detailedRecord.put("userName", userName);
                                detailedRecord.put("userEmail", userEmail);
                                detailedRecord.put("location", recordLocationName);
                                detailedRecord.put("locationName", recordLocationName);
                                detailedRecord.put("type", data.getOrDefault("type", "Check-in"));
                                detailedRecord.put("status", "Completed");
                                
                                // Add timestamp if not present
                                if (!detailedRecord.containsKey("timestamp")) {
                                    detailedRecord.put("timestamp", document.getTimestamp("createdAt"));
                                }
                                
                                detailedReports.add(detailedRecord);
                                
                                // Create attendance record for display
                                String time = extractTimeFromRecord(data);
                                records.add(new AttendanceRecord(
                                        document.getId(),
                                    time,
                                    userEmail,
                                        userName,
                                    (String) data.getOrDefault("type", "Check-in"),
                                    recordLocationName
                                ));
                            }
                        }
                        
                        // Store the detailed reports for email functionality
                        currentReports = detailedReports;
                        
                        runOnUiThread(() -> {
                            if (records.isEmpty()) {
                                // Show no data message
                                locationReportsList.setVisibility(View.GONE);
                                detailTableCard.setVisibility(View.GONE);
                                noDataText.setVisibility(View.VISIBLE);
                            } else {
                                // Show appropriate view based on radio selection
                        if (summaryRadioButton.isChecked()) {
                                    // Prepare and show summary view
                                    prepareSummaryView(records);
                        } else {
                                    // Show detailed view
                                    detailAdapter.setAttendanceRecords(records);
                            locationReportsList.setVisibility(View.GONE);
                            detailTableCard.setVisibility(View.VISIBLE);
                        }
                        noDataText.setVisibility(View.GONE);
                            }
                        });
                    } else {
                        runOnUiThread(() -> {
                            Toast.makeText(LocationReportsActivity.this,
                                "Error loading reports: " + task.getException().getMessage(),
                                Toast.LENGTH_SHORT).show();
                        noDataText.setVisibility(View.VISIBLE);
                        });
                    }
                });
        });
    }
    
    // Extract formatted time from record
    private String extractTimeFromRecord(Map<String, Object> data) {
        String formattedTime = "Unknown";
        Object timestampObj = data.get("timestamp");
        
        if (timestampObj instanceof String) {
            String timestampStr = (String) timestampObj;
            // Extract time if possible
            try {
                SimpleDateFormat inputFormat = 
                        new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm:ssa", Locale.US);
                
                // Remove timezone part if exists
                if (timestampStr.contains("UTC")) {
                    timestampStr = timestampStr.substring(0, timestampStr.indexOf("UTC")).trim();
                }
                
                Date recordDate = inputFormat.parse(timestampStr);
                if (recordDate != null) {
                    formattedTime = new SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                            .format(recordDate);
                }
            } catch (ParseException e) {
                formattedTime = "Unknown";
                Log.e(TAG, "Error parsing date: " + timestampStr);
            }
        } else if (timestampObj instanceof Timestamp) {
            Timestamp timestamp = (Timestamp) timestampObj;
            Date recordDate = timestamp.toDate();
            formattedTime = new SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    .format(recordDate);
        }
        
        return formattedTime;
    }
    
    // Helper to extract location name from various possible fields
    private String getLocationName(Map<String, Object> data) {
        String locationName = null;
        
        // Try known location field names in order of likelihood
        String[] locationFieldNames = {"locationName", "location", "officeName", "office"};
        for (String fieldName : locationFieldNames) {
            if (data.containsKey(fieldName) && data.get(fieldName) != null) {
                locationName = data.get(fieldName).toString();
                break;
            }
        }
        
        // Use default if nothing found
        if (locationName == null || locationName.isEmpty()) {
            locationName = "Office Location";
        }
        
        return locationName;
    }
    
    // Helper to check if a record is within the selected date range
    private boolean isRecordInDateRange(Map<String, Object> data, Date startDate, Date endDate, String selectedDateStr) {
        Object timestampObj = data.get("timestamp");
        
        if (timestampObj instanceof String) {
            String timestampStr = (String) timestampObj;
            return timestampStr.contains(selectedDateStr);
        } else if (timestampObj instanceof Timestamp) {
            Timestamp timestamp = (Timestamp) timestampObj;
            Date recordDate = timestamp.toDate();
            return recordDate.after(startDate) && recordDate.before(endDate);
        }
        
        return false;
    }
    
    private Map<String, Object> createSummaryItem(String title, int count) {
        Map<String, Object> item = new HashMap<>();
        item.put("title", title);
        item.put("count", count);
        return item;
    }
    
    // Attendance record model class for detailed view
    private static class AttendanceRecord {
        private final String id;
        private final String time;
        private final String userEmail;
        private final String userName;
        private final String type;
        private final String locationName;
        
        public AttendanceRecord(String id, String time, String userEmail, String userName, String type, String locationName) {
            this.id = id;
            this.time = time;
            this.userEmail = userEmail;
            this.userName = userName;
            this.type = type;
            this.locationName = locationName;
        }
    }
    
    // Adapter for summary view
    private class LocationReportsAdapter extends RecyclerView.Adapter<LocationReportsAdapter.ViewHolder> {
        private List<Map<String, Object>> summaryData;
        
        public LocationReportsAdapter(List<Map<String, Object>> summaryData) {
            this.summaryData = summaryData;
        }
        
        public void setSummaryData(List<Map<String, Object>> summaryData) {
            this.summaryData = summaryData;
            notifyDataSetChanged();
        }
        
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_2, parent, false);
            return new ViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Map<String, Object> item = summaryData.get(position);
            String title = (String) item.get("title");
            int count = (int) item.get("count");
            
            holder.text1.setText(title);
            holder.text2.setText(String.valueOf(count));
            
            // Highlight total count item
            if (position == 0) {
                holder.text1.setTextSize(18);
                holder.text1.setTypeface(null, android.graphics.Typeface.BOLD);
                holder.text2.setTextSize(18);
                holder.text2.setTypeface(null, android.graphics.Typeface.BOLD);
            } else {
                holder.text1.setTextSize(16);
                holder.text1.setTypeface(null, android.graphics.Typeface.NORMAL);
                holder.text2.setTextSize(16);
                holder.text2.setTypeface(null, android.graphics.Typeface.NORMAL);
            }
        }
        
        @Override
        public int getItemCount() {
            return summaryData.size();
        }
        
        class ViewHolder extends RecyclerView.ViewHolder {
            TextView text1;
            TextView text2;
            
            ViewHolder(View itemView) {
                super(itemView);
                text1 = itemView.findViewById(android.R.id.text1);
                text2 = itemView.findViewById(android.R.id.text2);
            }
        }
    }
    
    // Adapter for detailed view
    private class LocationDetailAdapter extends RecyclerView.Adapter<LocationDetailAdapter.ViewHolder> {
        private List<AttendanceRecord> attendanceRecords = new ArrayList<>();
        
        public void setAttendanceRecords(List<AttendanceRecord> attendanceRecords) {
            this.attendanceRecords = attendanceRecords;
            notifyDataSetChanged();
        }
        
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_location_report_detail, parent, false);
            return new ViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AttendanceRecord record = attendanceRecords.get(position);
            
            // Format time for display
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
            
            // Set user name instead of email
            if (record.userName != null && !record.userName.isEmpty()) {
                holder.userEmailText.setText(record.userName);
            } else {
                // Fallback to email if name is not available
                holder.userEmailText.setText(record.userEmail);
            }
            
            // Set type with proper capitalization
            String formattedType = record.type.substring(0, 1).toUpperCase() + record.type.substring(1);
            holder.typeText.setText(formattedType);
            
            // Set location
            holder.locationText.setText(record.locationName);
            
            // Add alternating row background color
            if (position % 2 == 0) {
                holder.itemView.setBackgroundColor(getResources().getColor(R.color.white));
            } else {
                holder.itemView.setBackgroundColor(Color.parseColor("#F5F5F5"));
            }
        }
        
        @Override
        public int getItemCount() {
            return attendanceRecords.size();
        }
        
        class ViewHolder extends RecyclerView.ViewHolder {
            TextView timeText;
            TextView userEmailText;
            TextView typeText;
            TextView locationText;
            
            ViewHolder(View itemView) {
                super(itemView);
                timeText = itemView.findViewById(R.id.timeText);
                userEmailText = itemView.findViewById(R.id.userNameText);
                typeText = itemView.findViewById(R.id.typeText);
                locationText = itemView.findViewById(R.id.locationText);
            }
        }
    }
    
    // Helper method to fetch users and return a map of email to name
    private void fetchUsers(OnUsersLoadedListener listener) {
        Map<String, String> userEmailToNameMap = new HashMap<>();
        
        db.collection("users")
            .get()
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    for (QueryDocumentSnapshot document : task.getResult()) {
                        String userEmail = document.getString("email");
                        String userName = document.getString("name");
                        
                        // Only add users with both email and name
                        if (userEmail != null && !userEmail.trim().isEmpty() && 
                            userName != null && !userName.trim().isEmpty()) {
                            userEmailToNameMap.put(userEmail, userName);
                            Log.d(TAG, "Loaded user: " + userName + " (" + userEmail + ")");
                        } else {
                            Log.w(TAG, "Skipped user with incomplete data: " + document.getId());
                        }
                    }
                    Log.d(TAG, "Loaded " + userEmailToNameMap.size() + " users");
                    listener.onUsersLoaded(userEmailToNameMap);
                } else {
                    Log.e(TAG, "Error getting users", task.getException());
                    listener.onUsersLoaded(userEmailToNameMap); // Return empty map
                }
            });
    }
    
    // Interface for user data callback
    private interface OnUsersLoadedListener {
        void onUsersLoaded(Map<String, String> userEmailToNameMap);
    }
    
    private void prepareSummaryView(List<AttendanceRecord> records) {
        // Create summary data
        List<Map<String, Object>> summaryData = new ArrayList<>();
        
        // Count total check-ins
        summaryData.add(createSummaryItem("Total Check-ins", records.size()));
        
        // Count unique users
        Set<String> uniqueUsers = new HashSet<>();
        Map<String, Integer> userCounts = new HashMap<>();
        
        for (AttendanceRecord record : records) {
            uniqueUsers.add(record.userEmail);
            userCounts.put(record.userEmail, userCounts.getOrDefault(record.userEmail, 0) + 1);
        }
        
        summaryData.add(createSummaryItem("Unique Users", uniqueUsers.size()));
        
        // Add per-user summaries
        for (Map.Entry<String, Integer> entry : userCounts.entrySet()) {
            Map<String, Object> item = new HashMap<>();
            String userEmail = entry.getKey();
            String userName = records.stream()
                    .filter(r -> r.userEmail.equals(userEmail))
                    .map(r -> r.userName)
                    .findFirst()
                    .orElse(userEmail);
            
            item.put("title", "User: " + userName);
            item.put("count", entry.getValue());
            summaryData.add(item);
        }
        
        // Update the UI
        summaryAdapter.setSummaryData(summaryData);
        locationReportsList.setVisibility(View.VISIBLE);
        detailTableCard.setVisibility(View.GONE);
    }
} 