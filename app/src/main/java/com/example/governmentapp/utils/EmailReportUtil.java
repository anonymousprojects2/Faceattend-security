package com.example.governmentapp.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.Timestamp;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.text.ParseException;

public class EmailReportUtil {
    private static final String TAG = "EmailReportUtil";

    public static void generateAndSendReport(Context context, List<Map<String, Object>> records, String reportType) {
        try {
            // Create report file
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = reportType + "_report_" + timestamp + ".csv";
            
            File reportFile = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName);
            FileWriter writer = new FileWriter(reportFile);

            // Write CSV header with all columns
            writer.append("Date,Time,User Name,Email,Location,Type,Status\n");

            // Write records
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
            SimpleDateFormat parseFormat = new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm:ss a", Locale.US);

            for (Map<String, Object> record : records) {
                String date = "";
                String time = "";
                String userName = "";
                String userEmail = "";
                String location = "";
                String type = "";
                String status = "";
                
                // Extract date and time from timestamp
                Object timestampObj = record.get("timestamp");
                if (timestampObj instanceof Timestamp) {
                    Date recordDate = ((Timestamp) timestampObj).toDate();
                    date = dateFormat.format(recordDate);
                    time = timeFormat.format(recordDate);
                } else if (timestampObj instanceof String) {
                    String timestampStr = (String) timestampObj;
                    try {
                        if (timestampStr.contains("at")) {
                            String[] parts = timestampStr.split("at");
                            date = parts[0].trim();
                            time = parts[1].trim();
                            if (time.contains("UTC")) {
                                time = time.substring(0, time.indexOf("UTC")).trim();
                            }
                            // Try to parse and reformat the date for consistency
                            try {
                                Date parsedDate = parseFormat.parse(timestampStr);
                                if (parsedDate != null) {
                                    date = dateFormat.format(parsedDate);
                                    time = timeFormat.format(parsedDate);
                                }
                            } catch (ParseException e) {
                                Log.w(TAG, "Could not parse date: " + timestampStr);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing timestamp: " + timestampStr);
                    }
                }

                // Get user information
                userName = record.get("userName") != null ? record.get("userName").toString() : "";
                userEmail = record.get("userEmail") != null ? record.get("userEmail").toString() : "";
                
                // If we have email but no name, try to get name from email
                if (userName.isEmpty() && !userEmail.isEmpty()) {
                    userName = userEmail.split("@")[0]; // Use part before @ as name
                }

                // Get location information - try multiple possible field names
                if (record.get("locationName") != null) {
                    location = record.get("locationName").toString();
                } else if (record.get("location") != null) {
                    location = record.get("location").toString();
                } else if (record.get("officeName") != null) {
                    location = record.get("officeName").toString();
                } else {
                    location = "Unknown Location";
                }

                // Get type and status
                type = record.get("type") != null ? record.get("type").toString() : "Check-in";
                status = record.get("status") != null ? record.get("status").toString() : "Completed";

                // Escape special characters in CSV
                userName = escapeCsvField(userName);
                userEmail = escapeCsvField(userEmail);
                location = escapeCsvField(location);
                type = escapeCsvField(type);
                status = escapeCsvField(status);

                // Write the record
                writer.append(String.format("%s,%s,%s,%s,%s,%s,%s\n",
                    date, time, userName, userEmail, location, type, status));
            }

            writer.flush();
            writer.close();

            // Create email intent
            Intent emailIntent = new Intent(Intent.ACTION_SEND);
            emailIntent.setType("text/csv");
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, reportType + " Attendance Report");
            emailIntent.putExtra(Intent.EXTRA_TEXT, "Please find attached the attendance report.");
            
            // Attach the file
            Uri uri = androidx.core.content.FileProvider.getUriForFile(context, 
                context.getApplicationContext().getPackageName() + ".provider", reportFile);
            emailIntent.putExtra(Intent.EXTRA_STREAM, uri);
            emailIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            // Start email activity
            context.startActivity(Intent.createChooser(emailIntent, "Send Report"));

        } catch (IOException e) {
            Log.e(TAG, "Error generating report", e);
            Toast.makeText(context, "Error generating report: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // Helper method to escape special characters in CSV fields
    private static String escapeCsvField(String field) {
        if (field == null) return "";
        if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
            field = field.replace("\"", "\"\""); // Escape quotes
            field = "\"" + field + "\""; // Wrap in quotes
        }
        return field;
    }
} 