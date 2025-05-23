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
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
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

public class UserReportsActivity extends AppCompatActivity {
    
    private static final String TAG = "UserReportsActivity";
    private FirebaseFirestore db;
    private RecyclerView userReportsList;
    private TextView titleText;
    private TextView startDateText;
    private TextView endDateText;
    private ImageView backButton;
    private Spinner userSpinner;
    private Button startDateButton;
    private Button endDateButton;
    private Button generateReportButton;
    private Button emailReportButton;
    private CardView reportCardView;
    private View reportResultsSection;
    private TextView noDataText;
    
    private List<UserModel> userList;
    private UserReportAdapter adapter;
    
    private Calendar startDate;
    private Calendar endDate;
    private SimpleDateFormat dateFormat;
    private SimpleDateFormat displayDateFormat;
    private String selectedUserEmail;
    
    private List<Map<String, Object>> currentReports = new ArrayList<>();
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_reports);
        
        // Initialize Firestore
        db = FirebaseFirestore.getInstance();
        
        // Initialize date formats
        dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        displayDateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        
        // Initialize dates to current month range
        startDate = Calendar.getInstance();
        startDate.set(Calendar.DAY_OF_MONTH, 1); // First day of current month
        endDate = Calendar.getInstance(); // Today
        
        // Initialize UI components
        titleText = findViewById(R.id.titleText);
        backButton = findViewById(R.id.backButton);
        userSpinner = findViewById(R.id.userSpinner);
        startDateButton = findViewById(R.id.startDateButton);
        endDateButton = findViewById(R.id.endDateButton);
        startDateText = findViewById(R.id.startDateText);
        endDateText = findViewById(R.id.endDateText); 
        generateReportButton = findViewById(R.id.generateReportButton);
        emailReportButton = findViewById(R.id.emailReportButton);
        userReportsList = findViewById(R.id.userReportsList);
        reportCardView = findViewById(R.id.reportCardView);
        reportResultsSection = findViewById(R.id.reportResultsSection);
        noDataText = findViewById(R.id.noDataText);
        
        // Initially hide report section
        reportResultsSection.setVisibility(View.GONE);
        
        // Update date texts
        updateDateButtonsText();
        
        // Set up RecyclerView
        userReportsList.setLayoutManager(new LinearLayoutManager(this));
        userReportsList.setHasFixedSize(true);
        adapter = new UserReportAdapter();
        userReportsList.setAdapter(adapter);
        
        // Set up back button
        backButton.setOnClickListener(v -> finish());
        
        // Set up date buttons
        startDateButton.setOnClickListener(v -> showDatePickerDialog(true));
        endDateButton.setOnClickListener(v -> showDatePickerDialog(false));
        
        // Set up generate report button
        generateReportButton.setOnClickListener(v -> generateReport());
        
        // Set up email report button
        emailReportButton.setOnClickListener(v -> {
            if (!currentReports.isEmpty()) {
                // Create a list of properly formatted records for the email report
                List<Map<String, Object>> emailReports = new ArrayList<>();
                
                for (Map<String, Object> record : currentReports) {
                    Map<String, Object> emailRecord = new HashMap<>();
                    
                    // Copy all existing fields
                    emailRecord.putAll(record);
                    
                    // Ensure timestamp is included
                    if (!record.containsKey("timestamp") && record.containsKey("date") && record.containsKey("time")) {
                        String dateStr = record.get("date").toString();
                        String timeStr = record.get("time").toString();
                        emailRecord.put("timestamp", dateStr + " at " + timeStr);
                    }
                    
                    // Ensure user information is included
                    if (selectedUserEmail != null && !selectedUserEmail.isEmpty()) {
                        emailRecord.put("userEmail", selectedUserEmail);
                        // Try to get user name from spinner
                        if (userSpinner != null && userSpinner.getSelectedItem() != null) {
                            UserModel selectedUser = (UserModel) userSpinner.getSelectedItem();
                            if (selectedUser != null && selectedUser.getName() != null) {
                                emailRecord.put("userName", selectedUser.getName());
                            }
                        }
                    }
                    
                    emailReports.add(emailRecord);
                }
                
                String userName = selectedUserEmail != null ? selectedUserEmail.split("@")[0] : "All";
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                String startDateStr = sdf.format(startDate.getTime());
                String endDateStr = sdf.format(endDate.getTime());
                EmailReportUtil.generateAndSendReport(this, emailReports, 
                    "User_" + userName + "_" + startDateStr + "_to_" + endDateStr);
            } else {
                Toast.makeText(this, "No data available to generate report", Toast.LENGTH_SHORT).show();
            }
        });
        
        // Load users for dropdown
        loadUsers();
    }
    
    private void showDatePickerDialog(boolean isStartDate) {
        Calendar calendar = isStartDate ? startDate : endDate;
        
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    if (isStartDate) {
                        startDate.set(Calendar.YEAR, year);
                        startDate.set(Calendar.MONTH, month);
                        startDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    } else {
                        endDate.set(Calendar.YEAR, year);
                        endDate.set(Calendar.MONTH, month);
                        endDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    }
                    updateDateButtonsText();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        
        datePickerDialog.show();
    }
    
    private void updateDateButtonsText() {
        String startDateStr = displayDateFormat.format(startDate.getTime());
        String endDateStr = displayDateFormat.format(endDate.getTime());
        
        startDateText.setText(startDateStr);
        endDateText.setText(endDateStr);
    }
    
    private void loadUsers() {
        userList = new ArrayList<>();
        
        // Add "All Users" option
        UserModel allUsers = new UserModel();
        allUsers.setName("All Users");
        allUsers.setEmail("");
        userList.add(allUsers);
        
        // Get all users
        db.collection("users")
            .get()
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    for (QueryDocumentSnapshot document : task.getResult()) {
                        String userId = document.getId();
                        String userName = document.getString("name");
                        String userEmail = document.getString("email");
                        
                        if (userName != null && userEmail != null) {
                            UserModel user = new UserModel();
                            user.setId(userId);
                            user.setName(userName);
                            user.setEmail(userEmail);
                            userList.add(user);
                        }
                    }
                    
                    // Set up spinner adapter
                    ArrayAdapter<UserModel> adapter = new ArrayAdapter<UserModel>(
                            this, android.R.layout.simple_spinner_item, userList) {
                        @Override
                        public View getDropDownView(int position, View convertView, ViewGroup parent) {
                            View view = super.getDropDownView(position, convertView, parent);
                            TextView text = (TextView) view;
                            text.setText(userList.get(position).getName());
                            return view;
                        }
                        
                        @Override
                        public View getView(int position, View convertView, ViewGroup parent) {
                            View view = super.getView(position, convertView, parent);
                            TextView text = (TextView) view;
                            text.setText(userList.get(position).getName());
                            return view;
                        }
                    };
                    
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    userSpinner.setAdapter(adapter);
                    
                    // Set spinner selection listener
                    userSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                            selectedUserEmail = userList.get(position).getEmail();
                        }
                        
                        @Override
                        public void onNothingSelected(AdapterView<?> parent) {
                            selectedUserEmail = "";
                        }
                    });
                } else {
                    Toast.makeText(UserReportsActivity.this, "Error loading users: " + 
                            task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
    }
    
    private void generateReport() {
        // Set start of day for start date
        Calendar startOfDay = (Calendar) startDate.clone();
        startOfDay.set(Calendar.HOUR_OF_DAY, 0);
        startOfDay.set(Calendar.MINUTE, 0);
        startOfDay.set(Calendar.SECOND, 0);
        startOfDay.set(Calendar.MILLISECOND, 0);
        
        // Set end of day for end date
        Calendar endOfDay = (Calendar) endDate.clone();
        endOfDay.set(Calendar.HOUR_OF_DAY, 23);
        endOfDay.set(Calendar.MINUTE, 59);
        endOfDay.set(Calendar.SECOND, 59);
        endOfDay.set(Calendar.MILLISECOND, 999);
        
        Date startDateTime = startOfDay.getTime();
        Date endDateTime = endOfDay.getTime();
        
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        String startDateStr = sdf.format(startDateTime);
        String endDateStr = sdf.format(endDateTime);
        
        Log.d(TAG, "Generating report for date range: " + startDateStr + " to " + endDateStr);
        
        // Show loading toast
        Toast.makeText(this, "Generating report...", Toast.LENGTH_SHORT).show();
        
        // Hide previous results
        reportResultsSection.setVisibility(View.GONE);
        noDataText.setVisibility(View.GONE);
        
        // Query to get attendance records
        Query query = db.collection("attendance");
        
        // Add filter for specific user if selected
        if (selectedUserEmail != null && !selectedUserEmail.isEmpty()) {
            query = query.whereEqualTo("userEmail", selectedUserEmail);
        }
        
        // Execute query
        query.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                List<Map<String, Object>> reports = new ArrayList<>();
                for (DocumentSnapshot document : task.getResult().getDocuments()) {
                    Map<String, Object> data = document.getData();
                    if (data != null) {
                        String recordDateStr = null;
                        Date recordDate = null;
                        
                        // Try to get timestamp from document
                        Object timestampObj = data.get("timestamp");
                        if (timestampObj instanceof Timestamp) {
                            Timestamp timestamp = (Timestamp) timestampObj;
                            recordDate = timestamp.toDate();
                            recordDateStr = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(recordDate);
                        } else if (timestampObj instanceof String) {
                            recordDateStr = (String) timestampObj;
                            // Try to extract date from string timestamp
                            try {
                                if (recordDateStr.contains("at")) {
                                    String datePart = recordDateStr.split("at")[0].trim();
                                    recordDate = new SimpleDateFormat("MMM dd, yyyy", Locale.US).parse(datePart);
                                }
                            } catch (ParseException e) {
                                Log.e(TAG, "Error parsing date: " + recordDateStr, e);
                            }
                        }
                        
                        // Check if record is within date range
                        boolean isInRange = false;
                        if (recordDate != null) {
                            isInRange = !recordDate.before(startDateTime) && !recordDate.after(endDateTime);
                        } else if (recordDateStr != null) {
                            // If we couldn't parse the date, try to check by string comparison
                            try {
                                Date parsedRecordDate = new SimpleDateFormat("MMM dd, yyyy", Locale.US).parse(recordDateStr);
                                if (parsedRecordDate != null) {
                                    isInRange = !parsedRecordDate.before(startDateTime) && !parsedRecordDate.after(endDateTime);
                                }
                            } catch (ParseException e) {
                                // If date can't be parsed, check if it's between start and end date strings
                                SimpleDateFormat sdfMonth = new SimpleDateFormat("MMM", Locale.US);
                                String startMonth = startDateStr.split(" ")[0];
                                String endMonth = endDateStr.split(" ")[0];
                                String recordMonth = recordDateStr.split(" ")[0];
                                
                                // Very basic string comparison by month
                                if (recordMonth.equals(startMonth) || recordMonth.equals(endMonth)) {
                                    isInRange = true;
                                }
                            }
                        }
                        
                        if (isInRange) {
                            String dateStr = "Unknown";
                            String timeStr = "Unknown";
                            String typeStr = (String) data.get("type");
                            String locationStr = "Unknown Location";
                            
                            // Extract date and time
                            if (recordDate != null) {
                                dateStr = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(recordDate);
                                timeStr = new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(recordDate);
                            } else if (recordDateStr != null) {
                                dateStr = recordDateStr;
                                if (recordDateStr.contains("at")) {
                                    String[] parts = recordDateStr.split("at");
                                    if (parts.length > 1) {
                                        dateStr = parts[0].trim();
                                        timeStr = parts[1].trim();
                                    }
                                }
                            }
                            
                            // Get location name - improved location extraction logic
                            if (data.containsKey("locationName")) {
                                locationStr = (String) data.get("locationName");
                            } else if (data.containsKey("location")) {
                                Object locationObj = data.get("location");
                                if (locationObj instanceof String) {
                                    locationStr = (String) locationObj;
                                } else if (locationObj instanceof Map) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> locationMap = (Map<String, Object>) locationObj;
                                    if (locationMap.containsKey("name")) {
                                        locationStr = (String) locationMap.get("name");
                                    } else if (locationMap.containsKey("locationName")) {
                                        locationStr = (String) locationMap.get("locationName");
                                    }
                                }
                            } else if (data.containsKey("officeName")) {
                                locationStr = (String) data.get("officeName");
                            }
                            
                            // Create record with all necessary fields
                            Map<String, Object> record = new HashMap<>();
                            record.put("timestamp", data.get("timestamp")); // Original timestamp
                            record.put("date", dateStr);
                            record.put("time", timeStr);
                            record.put("userEmail", selectedUserEmail);
                            record.put("userName", selectedUserEmail != null ? selectedUserEmail.split("@")[0] : "All");
                            record.put("type", typeStr != null ? typeStr : "Check in");
                            record.put("location", locationStr);
                            record.put("status", "Completed");
                            
                            reports.add(record);
                        }
                    }
                }
                
                // Store the reports for email functionality
                currentReports = reports;
                
                if (reports.isEmpty()) {
                    // No records found
                    noDataText.setVisibility(View.VISIBLE);
                    reportResultsSection.setVisibility(View.GONE);
                    Toast.makeText(this, "No attendance records found for selected criteria", Toast.LENGTH_SHORT).show();
                } else {
                    // Sort records by date and time
                    Collections.sort(reports, new Comparator<Map<String, Object>>() {
                        private final SimpleDateFormat dateTimeFormat = 
                                new SimpleDateFormat("dd MMM yyyy hh:mm a", Locale.getDefault());
                        
                        @Override
                        public int compare(Map<String, Object> o1, Map<String, Object> o2) {
                            try {
                                Date date1 = dateTimeFormat.parse((String) o1.get("date") + " " + (String) o1.get("time"));
                                Date date2 = dateTimeFormat.parse((String) o2.get("date") + " " + (String) o2.get("time"));
                                
                                if (date1 != null && date2 != null) {
                                    // Reverse order (newest first)
                                    return date2.compareTo(date1);
                                }
                            } catch (ParseException e) {
                                Log.e(TAG, "Error sorting dates", e);
                            }
                            
                            // Fallback to string comparison if parsing fails
                            return ((String) o2.get("date") + (String) o2.get("time")).compareTo((String) o1.get("date") + (String) o1.get("time"));
                        }
                    });
                    
                    // Display records
                    adapter.setAttendanceRecords(reports);
                    noDataText.setVisibility(View.GONE);
                    reportResultsSection.setVisibility(View.VISIBLE);
                }
            } else {
                Toast.makeText(UserReportsActivity.this, "Error generating report: " + 
                        task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                noDataText.setVisibility(View.VISIBLE);
            }
        });
    }
    
    private static class UserModel {
        private String id;
        private String name;
        private String email;
        
        public String getId() {
            return id;
        }
        
        public void setId(String id) {
            this.id = id;
        }
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public String getEmail() {
            return email;
        }
        
        public void setEmail(String email) {
            this.email = email;
        }
        
        @Override
        public String toString() {
            return name; // For display in spinner
        }
    }
    
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
    
    private class UserReportAdapter extends RecyclerView.Adapter<UserReportAdapter.ViewHolder> {
        private List<Map<String, Object>> attendanceRecords = new ArrayList<>();
        
        public void setAttendanceRecords(List<Map<String, Object>> attendanceRecords) {
            this.attendanceRecords = attendanceRecords;
            notifyDataSetChanged();
        }
        
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_user_report_row, parent, false);
            return new ViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Map<String, Object> record = attendanceRecords.get(position);
            
            holder.dateText.setText((String) record.get("date"));
            holder.timeText.setText((String) record.get("time"));
            
            // Format type text with proper capitalization
            String typeText = (String) record.get("type");
            if (typeText != null && typeText.length() > 0) {
                typeText = typeText.substring(0, 1).toUpperCase() + typeText.substring(1).toLowerCase();
            }
            holder.typeText.setText(typeText);
            
            holder.locationText.setText((String) record.get("location"));
            
            // Set alternating row background colors
            if (position % 2 == 0) {
                holder.itemView.setBackgroundColor(Color.WHITE);
            } else {
                holder.itemView.setBackgroundColor(Color.parseColor("#F5F7FF"));
            }
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