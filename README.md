# FaceAttend - Government Office Attendance System

## Overview
FaceAttend is a modern attendance management system designed specifically for Government Offices in the Hingoli sub-division. The application uses facial recognition and location verification to ensure accurate attendance tracking and prevent proxy attendance.

## Features

### User Features
- **Face-based Authentication**: Secure login using facial recognition
- **Location Verification**: GPS-based verification ensures employees are physically present at their assigned locations
- **Multiple Office Support**: Users can be assigned to multiple office locations
- **Attendance History**: View personal attendance records with timestamps
- **Simple Check-in/Check-out**: Easy one-tap process to mark attendance

### Admin Features
- **User Management**: Add, edit, and remove users with proper role assignments
- **Office Management**: Add and configure office locations with geofencing capabilities
- **Taluka-based Organization**: Organize offices by taluka (Hingoli, Sengaon)
- **Comprehensive Reports**: Generate and view attendance reports by:
  - User
  - Location
  - Date range
- **Email Reports**: Send reports via email for record-keeping

## Technology Stack
- **Programming Language**: Java for Android
- **Backend & Authentication**: Firebase Authentication
- **Database**: Cloud Firestore
- **Storage**: Firebase Storage (for face images)
- **Location Services**: Google Play Location Services
- **Face Detection**: ML Kit Face Recognition
- **UI Framework**: Android Material Design components

## Screenshots
[Screenshots will be added here]

## Installation & Setup

### Prerequisites
- Android Studio Arctic Fox (2021.3.1) or newer
- Java Development Kit (JDK) 11
- Android SDK 31 (Android 12)
- Google Play Services

### Setup Instructions
1. Clone the repository
```
git clone https://github.com/username/GovernmentApp.git
```

2. Open the project in Android Studio

3. Configure Firebase:
   - Create a new Firebase project at [Firebase Console](https://console.firebase.google.com/)
   - Add an Android app to your Firebase project with package name `com.example.governmentapp`
   - Download the `google-services.json` file and place it in the app directory
   - Enable Authentication (Email/Password), Firestore Database, and Storage services

4. Build the project:
```
./gradlew build
```

5. Run the application on an emulator or physical device

### Database Setup
The application requires the following Firestore collections:
- `users`: Stores user information and credentials
- `locations`: Stores office location details with coordinates and geofence radius
- `attendance`: Records attendance data with timestamps

## Usage

### Admin Usage
1. Log in with admin credentials
2. Add office locations with proper GPS coordinates and radius
3. Add users and assign them to specific locations and talukas
4. View and generate attendance reports

### User Usage
1. Log in with provided credentials
2. Select your assigned location
3. Verify you are within the office radius
4. Complete face verification for attendance
5. View your attendance history

## Project Structure
- `app/src/main/java/com/example/governmentapp/`: Contains all Java source files
- `app/src/main/res/layout/`: Contains XML layout files
- `app/src/main/res/drawable/`: Contains images and drawable resources
- `app/src/main/res/values/`: Contains string, color, and style definitions

## Permissions
The app requires the following permissions:
- Camera access (for face verification)
- Location access (for office verification)
- Internet access (for Firebase connectivity)

## Developed By
Sanjivani University

## License
[License Information to be added] 