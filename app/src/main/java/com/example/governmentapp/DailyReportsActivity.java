package com.example.governmentapp;

import android.app.DatePickerDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.example.governmentapp.utils.EmailReportUtil;

public class DailyReportsActivity extends AppCompatActivity {
    
    private static final String TAG = "DailyReportsActivity";
    private FirebaseFirestore db;
    private RecyclerView dailyReportsList;
    private RecyclerView detailedReportsList;
    private TextView titleText;
    private ImageView backButton;
    private Button selectDateButton;
    private TextView dateRangeText;
    private Calendar selectedDate;
    private RadioGroup viewTypeRadioGroup;
    private RadioButton summaryRadioButton;
    private RadioButton detailRadioButton;
    private CardView detailTableCard;
    private TextView noDataText;
    private Button emailReportButton;
    private List<Map<String, Object>> currentReports = new ArrayList<>();
    
    private DailySummaryAdapter summaryAdapter;
    private DailyDetailAdapter detailAdapter;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_daily_reports);
        
        // Initialize Firestore
        db = FirebaseFirestore.getInstance();
        
        // Initialize UI components
        titleText = findViewById(R.id.titleText);
        backButton = findViewById(R.id.backButton);
        dailyReportsList = findViewById(R.id.dailyReportsList);
        detailedReportsList = findViewById(R.id.detailedReportsList);
        selectDateButton = findViewById(R.id.selectDateButton);
        dateRangeText = findViewById(R.id.dateRangeText);
        viewTypeRadioGroup = findViewById(R.id.viewTypeRadioGroup);
        summaryRadioButton = findViewById(R.id.summaryRadioButton);
        detailRadioButton = findViewById(R.id.detailRadioButton);
        detailTableCard = findViewById(R.id.detailTableCard);
        noDataText = findViewById(R.id.noDataText);
        emailReportButton = findViewById(R.id.emailReportButton);
        
        // Set up RecyclerView for summary view
        dailyReportsList.setLayoutManager(new LinearLayoutManager(this));
        summaryAdapter = new DailySummaryAdapter();
        dailyReportsList.setAdapter(summaryAdapter);
        
        // Set up RecyclerView for detailed view
        detailedReportsList.setLayoutManager(new LinearLayoutManager(this));
        detailAdapter = new DailyDetailAdapter();
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
                dailyReportsList.setVisibility(View.VISIBLE);
                detailTableCard.setVisibility(View.GONE);
            } else {
                // Show detailed view
                dailyReportsList.setVisibility(View.GONE);
                detailTableCard.setVisibility(View.VISIBLE);
            }
        });
        
        // Set up email report button
        emailReportButton.setOnClickListener(v -> {
            if (!currentReports.isEmpty()) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                String reportDate = sdf.format(selectedDate.getTime());
                EmailReportUtil.generateAndSendReport(this, currentReports, "Daily_" + reportDate);
            } else {
                Toast.makeText(this, "No data available to generate report", Toast.LENGTH_SHORT).show();
            }
        });
        
        // Load today's attendance data
        loadDailyAttendanceReports();
    }
    
    private void showDatePickerDialog() {
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    selectedDate.set(Calendar.YEAR, year);
                    selectedDate.set(Calendar.MONTH, month);
                    selectedDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    updateDateDisplay();
                    loadDailyAttendanceReports();
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
    
    private void loadDailyAttendanceReports() {
        // Show loading message
        Toast.makeText(this, "Loading reports...", Toast.LENGTH_SHORT).show();
        
        // Hide views
        dailyReportsList.setVisibility(View.GONE);
        detailTableCard.setVisibility(View.GONE);
        noDataText.setVisibility(View.GONE);
        
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
        
        Log.d(TAG, "Loading attendance records between " + startDate + " and " + endDate);
        
        // First fetch users to get names
        fetchUsers(users -> {
            // Query all attendance records for the selected day
            db.collection("attendance")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        List<Map<String, Object>> dailyReports = new ArrayList<>();
                        Map<String, Integer> officeCounts = new HashMap<>();
                        
                        if (task.getResult().isEmpty()) {
                            Toast.makeText(this, "No attendance records found", Toast.LENGTH_SHORT).show();
                            noDataText.setVisibility(View.VISIBLE);
                            return;
                        }
                        
                        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
                        String selectedDateStr = sdf.format(selectedDate.getTime());
                        
                        List<AttendanceRecord> detailedRecords = new ArrayList<>();
                        
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            Map<String, Object> data = document.getData();
                            Log.d(TAG, "Checking record: " + data.toString());
                            
                            // Check if the record belongs to the selected date
                            Object timestampObj = data.get("timestamp");
                            boolean isInRange = false;
                            String formattedTime = "";
                            
                            if (timestampObj instanceof String) {
                                String timestampStr = (String) timestampObj;
                                if (timestampStr.contains(selectedDateStr)) {
                                    isInRange = true;
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
                                }
                            } else if (timestampObj instanceof Timestamp) {
                                Timestamp timestamp = (Timestamp) timestampObj;
                                Date recordDate = timestamp.toDate();
                                if (recordDate.after(startDate) && recordDate.before(endDate)) {
                                    isInRange = true;
                                    formattedTime = new SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                                            .format(recordDate);
                                }
                            }
                            
                            if (isInRange) {
                                dailyReports.add(data);
                                
                                // Count by office location
                                String location = getLocationName(data);
                                if (location != null) {
                                    officeCounts.put(location, officeCounts.getOrDefault(location, 0) + 1);
                                }
                                
                                // Add to detailed records
                                String userEmail = (String) data.get("userEmail");
                                String type = data.get("type") != null ? data.get("type").toString() : "Attendance";
                                
                                // Get user name from map
                                String userName = users.getOrDefault(userEmail, "");
                                
                                // Create detailed record
                                AttendanceRecord record = new AttendanceRecord(
                                        document.getId(),
                                        formattedTime,
                                        userEmail != null ? userEmail : "Unknown User",
                                        userName,
                                        type,
                                        location
                                );
                                
                                detailedRecords.add(record);
                            }
                        }
                        
                        if (dailyReports.isEmpty()) {
                            Toast.makeText(this, "No attendance records found for selected date", 
                                    Toast.LENGTH_SHORT).show();
                            noDataText.setVisibility(View.VISIBLE);
                        } else {
                            Log.d(TAG, "Found " + dailyReports.size() + " attendance records for " + selectedDateStr);
                            
                            // Create summary data for display
                            List<Map<String, Object>> summaryData = new ArrayList<>();
                            summaryData.add(createSummaryItem("Total Check-ins", dailyReports.size()));
                            
                            // Add office-specific summaries
                            for (Map.Entry<String, Integer> entry : officeCounts.entrySet()) {
                                summaryData.add(createSummaryItem(entry.getKey(), entry.getValue()));
                            }
                            
                            // Set summary adapter data
                            summaryAdapter.setSummaryData(summaryData);
                            
                            // Sort detailed records by time
                            Collections.sort(detailedRecords, (o1, o2) -> o1.time.compareTo(o2.time));
                            
                            // Set detail adapter data
                            detailAdapter.setAttendanceRecords(detailedRecords);
                            
                            // Show appropriate view based on selection
                            if (summaryRadioButton.isChecked()) {
                                dailyReportsList.setVisibility(View.VISIBLE);
                                detailTableCard.setVisibility(View.GONE);
                            } else {
                                dailyReportsList.setVisibility(View.GONE);
                                detailTableCard.setVisibility(View.VISIBLE);
                            }
                            
                            noDataText.setVisibility(View.GONE);
                            
                            // Store the reports for email functionality
                            currentReports = dailyReports;
                        }
                    } else {
                        Toast.makeText(this, "Error loading attendance records", Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Error getting attendance records", task.getException());
                        noDataText.setVisibility(View.VISIBLE);
                    }
                });
        });
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
                        
                        if (userEmail != null && userName != null) {
                            userEmailToNameMap.put(userEmail, userName);
                        }
                    }
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
    private class DailySummaryAdapter extends RecyclerView.Adapter<DailySummaryAdapter.ViewHolder> {
        private List<Map<String, Object>> summaryData = new ArrayList<>();
        
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
    private class DailyDetailAdapter extends RecyclerView.Adapter<DailyDetailAdapter.ViewHolder> {
        private List<AttendanceRecord> attendanceRecords = new ArrayList<>();
        
        public void setAttendanceRecords(List<AttendanceRecord> attendanceRecords) {
            this.attendanceRecords = attendanceRecords;
            notifyDataSetChanged();
        }
        
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_daily_report_detail, parent, false);
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
            if (record.locationName != null && !record.locationName.equals("Unknown")) {
                holder.locationText.setText(record.locationName);
            } else {
                holder.locationText.setText("Office Location");
            }
            
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
} 