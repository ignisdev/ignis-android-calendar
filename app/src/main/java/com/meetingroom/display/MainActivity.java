package com.meetingroom.display;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import com.microsoft.identity.client.AuthenticationCallback;
import com.microsoft.identity.client.IAuthenticationResult;
import com.microsoft.identity.client.IPublicClientApplication;
import com.microsoft.identity.client.PublicClientApplication;
import com.microsoft.identity.client.exception.MsalException;
// Removed Microsoft Graph SDK imports - using direct HTTP calls instead
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    
    
    // Room/Resource Calendar Configuration - Ignis Meeting Rooms
    private static final String PETER_3RD_FLOOR = "meetingroom-peter3rdfloor@ignis.co.uk";
    private static final String LUKE_2ND_FLOOR = "meetingroom-luke2ndfloor@ignis.co.uk";
    private static final String MATTHEW_1ST_FLOOR = "meetingroom-matthew1stfloor@ignis.co.uk";
    
    // Set which room this device should display (change this for each room)
    private static final String CURRENT_ROOM_EMAIL = LUKE_2ND_FLOOR;
    private static final String CURRENT_ROOM_NAME = "Luke";
    private static final String CURRENT_ROOM_LOCATION = "2nd Floor";
    
    private String getRoomEmail() {
        // You can also read this from shared preferences for dynamic configuration
        return CURRENT_ROOM_EMAIL;
    }
    
    private String getRoomName() {
        return CURRENT_ROOM_NAME;
    }
    
    private String getRoomLocation() {
        return CURRENT_ROOM_LOCATION;
    }
    
    private TextView currentTimeText;
    private TextView currentDateText;
    private TextView roomStatusText;
    private TextView nextAvailableText;
    private LinearLayout currentMeetingLayout;
    private TextView currentMeetingTitle;
    private TextView currentMeetingOrganizer;
    private TextView currentMeetingTime;
    private TextView currentMeetingAttendees;
    private TextView remainingTimeText;
    private ListView todayScheduleList;
    private LinearLayout statusContainer;
    
    private Handler handler;
    private Runnable updateRunnable;
    
    private List<Meeting> todaysMeetings;
    private Meeting currentMeeting;
    private RoomStatus currentStatus = RoomStatus.AVAILABLE;
    
    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault());
    
    public enum RoomStatus {
        AVAILABLE, BUSY, UPCOMING
    }
    
    private IPublicClientApplication msalApp;
    private IAuthenticationResult mAuthResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        
        // Keep screen on and hide system UI for kiosk mode
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        // Temporarily disable full kiosk mode for authentication testing
        // TODO: Re-enable after authentication works
        /*
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
        */
        
        initializeViews();
        initializeMockData();
        setupUpdateHandler();
        updateDisplay();

        PublicClientApplication.create(this, R.raw.auth_config_single_account, new IPublicClientApplication.ApplicationCreatedListener() {
            @Override
            public void onCreated(IPublicClientApplication application) {
                msalApp = application;
                signIn();
            }

            @Override
            public void onError(MsalException exception) {
            }
        });
    }

    private void signIn() {
        String[] scopes = {"User.Read", "Calendars.Read", "Calendars.Read.Shared", "Mail.Read"};
        
        try {
            msalApp.acquireToken(this, scopes, getAuthInteractiveCallback());
            
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private AuthenticationCallback getAuthInteractiveCallback() {
        return new AuthenticationCallback() {
            @Override
            public void onSuccess(IAuthenticationResult authenticationResult) {
                
                mAuthResult = authenticationResult;
                
                // Now you can make Graph API calls
                getCalendarEvents();
                
            }

            @Override
            public void onError(MsalException exception) {
            }

            @Override
            public void onCancel() {
            }
        };
    }

    private void getCalendarEvents() {
        if (mAuthResult == null) {
            initializeMockData();
            updateDisplay();
            return;
        }

        getRoomCalendarEvents();
    }
    
    private void getRoomCalendarEvents() {
        if (mAuthResult == null) {
            initializeMockData();
            updateDisplay();
            return;
        }

        
        // Use getSchedule API instead of direct calendar access
        tryAlternativeRoomAccess();
        
        // Disabled old calendar events API since getSchedule is working
        // The old fetchCalendarEventsFromGraph approach doesn't work for room mailboxes
    }

    private void testUserCalendarAccess() {
        new Thread(() -> {
            try {
                String accessToken = mAuthResult.getAccessToken();
                
                // Test 1: Try user's own calendar
                String userCalendarUrl = "https://graph.microsoft.com/v1.0/me/calendar/events?$top=1";
                testApiCall(accessToken, userCalendarUrl, "User's own calendar");
                
                // Test 2: Try different room calendar formats
                String[] testRoomEmails = {
                    "meetingroom-luke2ndfloor@ignis.co.uk",
                    "luke2ndfloor@ignis.co.uk", 
                    "Luke 2nd Floor@ignis.co.uk",
                    "meetingroom.luke2ndfloor@ignis.co.uk"
                };
                
                for (String email : testRoomEmails) {
                    // Try different endpoints for room access
                    String roomUrl1 = "https://graph.microsoft.com/v1.0/users/" + email + "/calendar";
                    String roomUrl2 = "https://graph.microsoft.com/v1.0/users/" + email + "/calendars";
                    String roomUrl3 = "https://graph.microsoft.com/v1.0/users/" + email;  // Just check if the user exists
                    
                    testApiCall(accessToken, roomUrl3, "Room user exists: " + email);
                    Thread.sleep(200);
                    testApiCall(accessToken, roomUrl1, "Room calendar: " + email);
                    Thread.sleep(200);
                    testApiCall(accessToken, roomUrl2, "Room calendars: " + email);
                    
                    // Small delay between requests
                    Thread.sleep(500);
                }
                
                // Test 3: Try to discover actual room mailboxes using Microsoft Graph
                discoverRoomMailboxes(accessToken);
                
                // Test 4: Try your own calendar to see if room meetings show up there
                testPersonalCalendarForRoomMeetings(accessToken);
                
            } catch (Exception e) {
            }
        }).start();
    }
    
    private void testApiCall(String accessToken, String urlString, String description) {
        try {
            java.net.URL url = new java.net.URL(urlString);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            conn.setRequestProperty("Content-Type", "application/json");
            
            int responseCode = conn.getResponseCode();
            
            if (responseCode == 200) {
            } else {
                java.io.BufferedReader errorReader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getErrorStream()));
                StringBuilder errorResponse = new StringBuilder();
                String line;
                while ((line = errorReader.readLine()) != null) {
                    errorResponse.append(line);
                }
                errorReader.close();
            }
            
            conn.disconnect();
            
        } catch (Exception e) {
        }
    }
    
    private void discoverRoomMailboxes(String accessToken) {
        try {
            // Method 1: Try to find rooms using the findRooms API
            String findRoomsUrl = "https://graph.microsoft.com/v1.0/me/calendar/getSchedule";
            
            // Method 2: Try to search for users with "room" or "meeting" in their name
            String searchUrl = "https://graph.microsoft.com/v1.0/users?$filter=startswith(displayName,'Meeting')&$select=displayName,mail,userPrincipalName";
            testApiCallWithDetails(accessToken, searchUrl, "Search Meeting users");
            
            Thread.sleep(500);
            
            String searchUrl2 = "https://graph.microsoft.com/v1.0/users?$filter=startswith(displayName,'Room')&$select=displayName,mail,userPrincipalName";
            testApiCallWithDetails(accessToken, searchUrl2, "Search Room users");
            
            Thread.sleep(500);
            
            // Method 3: Try to search for users with "luke" in their name
            String searchUrl3 = "https://graph.microsoft.com/v1.0/users?$filter=contains(displayName,'Luke')&$select=displayName,mail,userPrincipalName";
            testApiCallWithDetails(accessToken, searchUrl3, "Search Luke users");
            
            Thread.sleep(500);
            
            // Method 4: List first 20 users to see what's available
            String listUsersUrl = "https://graph.microsoft.com/v1.0/users?$top=20&$select=displayName,mail,userPrincipalName";
            testApiCallWithDetails(accessToken, listUsersUrl, "List first 20 users");
            
        } catch (Exception e) {
        }
    }
    
    private void testApiCallWithDetails(String accessToken, String urlString, String description) {
        try {
            java.net.URL url = new java.net.URL(urlString);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            conn.setRequestProperty("Content-Type", "application/json");
            
            int responseCode = conn.getResponseCode();
            
            if (responseCode == 200) {
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                
                // Try to parse and extract useful information
                try {
                    org.json.JSONObject jsonResponse = new org.json.JSONObject(response.toString());
                    if (jsonResponse.has("value")) {
                        org.json.JSONArray users = jsonResponse.getJSONArray("value");
                        for (int i = 0; i < users.length(); i++) {
                            org.json.JSONObject user = users.getJSONObject(i);
                            String displayName = user.optString("displayName", "N/A");
                            String mail = user.optString("mail", "N/A"); 
                            String upn = user.optString("userPrincipalName", "N/A");
                        }
                    }
                } catch (Exception parseEx) {
                }
                
            } else {
                java.io.BufferedReader errorReader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getErrorStream()));
                StringBuilder errorResponse = new StringBuilder();
                String line;
                while ((line = errorReader.readLine()) != null) {
                    errorResponse.append(line);
                }
                errorReader.close();
            }
            
            conn.disconnect();
            
        } catch (Exception e) {
        }
    }
    
    private void testPersonalCalendarForRoomMeetings(String accessToken) {
        try {
            // Get today's events from your personal calendar - sometimes room meetings show up here
            Calendar startOfDay = Calendar.getInstance();
            startOfDay.set(Calendar.HOUR_OF_DAY, 0);
            startOfDay.set(Calendar.MINUTE, 0);
            startOfDay.set(Calendar.SECOND, 0);
            
            Calendar endOfDay = Calendar.getInstance();
            endOfDay.set(Calendar.HOUR_OF_DAY, 23);
            endOfDay.set(Calendar.MINUTE, 59);
            endOfDay.set(Calendar.SECOND, 59);

            String startTime = formatDateTimeForGraph(startOfDay.getTime());
            String endTime = formatDateTimeForGraph(endOfDay.getTime());
            
            String personalCalendarUrl = "https://graph.microsoft.com/v1.0/me/calendar/events" +
                "?$filter=start/dateTime ge '" + startTime + "' and end/dateTime le '" + endTime + "'" +
                "&$select=subject,start,end,organizer,attendees,location" +
                "&$orderby=start/dateTime";
                
            testApiCallWithDetails(accessToken, personalCalendarUrl, "Personal calendar today");
            
            Thread.sleep(1000);
            
            // Also try the shared calendars endpoint
            String sharedCalendarsUrl = "https://graph.microsoft.com/v1.0/me/calendars";
            testApiCallWithDetails(accessToken, sharedCalendarsUrl, "Accessible calendars");
            
        } catch (Exception e) {
        }
    }
    
    private void tryAlternativeRoomAccess() {
        new Thread(() -> {
            try {
                String accessToken = mAuthResult.getAccessToken();
                
                // Method 1: Try to find the room calendar using getSchedule API (works for room resources)
                tryGetScheduleAPI(accessToken);
                
                Thread.sleep(1000);
                
                // Method 2: Try using findMeetingTimes API
                tryFindMeetingTimesAPI(accessToken);
                
            } catch (Exception e) {
            }
        }).start();
    }
    
    private void tryGetScheduleAPI(String accessToken) {
        try {
            // Use the getSchedule API which is designed for room resources
            String getScheduleUrl = "https://graph.microsoft.com/v1.0/me/calendar/getSchedule";
            
            // Create POST body with room email
            String roomEmail = getRoomEmail();
            Calendar startOfDay = Calendar.getInstance();
            startOfDay.set(Calendar.HOUR_OF_DAY, 0);
            startOfDay.set(Calendar.MINUTE, 0);
            startOfDay.set(Calendar.SECOND, 0);
            
            Calendar endOfDay = Calendar.getInstance();
            endOfDay.set(Calendar.HOUR_OF_DAY, 23);
            endOfDay.set(Calendar.MINUTE, 59);
            endOfDay.set(Calendar.SECOND, 59);

            String startTime = formatDateTimeForGraph(startOfDay.getTime());
            String endTime = formatDateTimeForGraph(endOfDay.getTime());
            
            String postBody = String.format(
                "{\"schedules\": [\"%s\"], \"startTime\": {\"dateTime\": \"%s\", \"timeZone\": \"UTC\"}, \"endTime\": {\"dateTime\": \"%s\", \"timeZone\": \"UTC\"}}",
                roomEmail, startTime, endTime
            );
            
            
            java.net.URL url = new java.net.URL(getScheduleUrl);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            
            // Write POST body
            try (java.io.OutputStream os = conn.getOutputStream()) {
                byte[] input = postBody.getBytes("utf-8");
                os.write(input, 0, input.length);
            }
            
            int responseCode = conn.getResponseCode();
            
            if (responseCode == 200) {
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                
                // Try to parse the schedule response
                parseGetScheduleResponse(response.toString());
                
            } else {
                java.io.BufferedReader errorReader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getErrorStream()));
                StringBuilder errorResponse = new StringBuilder();
                String line;
                while ((line = errorReader.readLine()) != null) {
                    errorResponse.append(line);
                }
                errorReader.close();
            }
            
            conn.disconnect();
            
        } catch (Exception e) {
        }
    }
    
    private void parseGetScheduleResponse(String jsonResponse) {
        try {
            org.json.JSONObject response = new org.json.JSONObject(jsonResponse);
            if (response.has("value") && response.getJSONArray("value").length() > 0) {
                org.json.JSONObject schedule = response.getJSONArray("value").getJSONObject(0);
                if (schedule.has("scheduleItems")) {
                    org.json.JSONArray scheduleItems = schedule.getJSONArray("scheduleItems");
                    
                    List<Meeting> meetings = new ArrayList<>();
                    for (int i = 0; i < scheduleItems.length(); i++) {
                        org.json.JSONObject item = scheduleItems.getJSONObject(i);
                        
                        try {
                            // Extract meeting details from schedule item
                            String subject = item.optString("subject", "Meeting").trim();
                            String organizer = subject; // In getSchedule, subject often contains the organizer name
                            
                            // Parse start and end times
                            org.json.JSONObject startObj = item.getJSONObject("start");
                            org.json.JSONObject endObj = item.getJSONObject("end");
                            
                            String startDateTimeStr = startObj.getString("dateTime");
                            String endDateTimeStr = endObj.getString("dateTime");
                            
                            
                            Date startTime = parseScheduleDateTime(startDateTimeStr);
                            Date endTime = parseScheduleDateTime(endDateTimeStr);
                            
                            // Create meeting object
                            Meeting meeting = new Meeting(subject, organizer, startTime, endTime, 0);
                            meetings.add(meeting);
                            
                            
                        } catch (Exception e) {
                        }
                    }
                    
                    if (meetings.size() > 0) {
                        runOnUiThread(() -> {
                            todaysMeetings = meetings;
                            updateDisplay();
                        });
                        return;
                    }
                }
            }
        } catch (Exception e) {
        }
    }
    
    private Date parseScheduleDateTime(String dateTimeStr) {
        try {
            // Parse format: "2025-09-24T09:00:00.0000000"
            // Microsoft Graph returns UTC times - parse as UTC, then let Date represent it in local timezone
            SimpleDateFormat utcFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSS", Locale.getDefault());
            utcFormatter.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            
            Date parsedDate = utcFormatter.parse(dateTimeStr);
            
            
            return parsedDate;
            
        } catch (Exception e) {
            // Fallback: simple parse without microseconds
            try {
                SimpleDateFormat simpleFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
                simpleFormatter.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                Date parsedDate = simpleFormatter.parse(dateTimeStr.substring(0, 19));
                return parsedDate;
            } catch (Exception e2) {
                return new Date();
            }
        }
    }
    
    private void tryFindMeetingTimesAPI(String accessToken) {
        // This API might also work for room resources - simpler fallback
    }

    private List<Meeting> fetchCalendarEventsFromGraph(String accessToken) throws Exception {
        // Format today's date range
        Calendar startOfDay = Calendar.getInstance();
        startOfDay.set(Calendar.HOUR_OF_DAY, 0);
        startOfDay.set(Calendar.MINUTE, 0);
        startOfDay.set(Calendar.SECOND, 0);
        
        Calendar endOfDay = Calendar.getInstance();
        endOfDay.set(Calendar.HOUR_OF_DAY, 23);
        endOfDay.set(Calendar.MINUTE, 59);
        endOfDay.set(Calendar.SECOND, 59);

        String startTime = formatDateTimeForGraph(startOfDay.getTime());
        String endTime = formatDateTimeForGraph(endOfDay.getTime());
        
        // Build Graph API URL
        String urlString = "https://graph.microsoft.com/v1.0/users/" + getRoomEmail() + "/calendar/events" +
            "?$filter=start/dateTime ge '" + startTime + "' and end/dateTime le '" + endTime + "'" +
            "&$select=subject,start,end,organizer,attendees" +
            "&$orderby=start/dateTime";
        
        
        // Make HTTP request
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setRequestProperty("Content-Type", "application/json");
        
        int responseCode = conn.getResponseCode();
        
        if (responseCode != 200) {
            // Read error response
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
            StringBuilder errorResponse = new StringBuilder();
            String line;
            while ((line = errorReader.readLine()) != null) {
                errorResponse.append(line);
            }
            errorReader.close();
            throw new Exception("Graph API returned error: " + responseCode + " - " + errorResponse.toString());
        }
        
        // Read response
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        
        // Parse JSON response
        return parseGraphResponse(response.toString());
    }

    private List<Meeting> parseGraphResponse(String jsonResponse) throws Exception {
        List<Meeting> meetings = new ArrayList<>();
        
        JSONObject jsonObject = new JSONObject(jsonResponse);
        JSONArray eventsArray = jsonObject.getJSONArray("value");
        
        for (int i = 0; i < eventsArray.length(); i++) {
            JSONObject event = eventsArray.getJSONObject(i);
            
            try {
                String title = event.optString("subject", "Untitled Meeting");
                
                String organizer = "Unknown";
                if (event.has("organizer") && event.getJSONObject("organizer").has("emailAddress")) {
                    JSONObject emailAddress = event.getJSONObject("organizer").getJSONObject("emailAddress");
                    organizer = emailAddress.optString("name", "Unknown");
                }
                
                Date startTime = parseGraphDateTimeFromJson(event.getJSONObject("start"));
                Date endTime = parseGraphDateTimeFromJson(event.getJSONObject("end"));
                
                int attendeeCount = 0;
                if (event.has("attendees")) {
                    attendeeCount = event.getJSONArray("attendees").length();
                }
                
                meetings.add(new Meeting(title, organizer, startTime, endTime, attendeeCount));
                
            } catch (Exception e) {
                // Skip problematic events
            }
        }
        
        return meetings;
    }

    private Date parseGraphDateTimeFromJson(JSONObject dateTimeObj) {
        try {
            String dateTimeStr = dateTimeObj.getString("dateTime");
            // Parse ISO 8601 format: 2023-12-07T09:00:00.0000000
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSS", Locale.getDefault());
            return formatter.parse(dateTimeStr);
        } catch (Exception e) {
            return new Date();
        }
    }

    private String formatDateTimeForGraph(Date date) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());
        return formatter.format(date);
    }

    // Removed Graph SDK-specific methods - using getSchedule API instead
    
    private void initializeViews() {
        currentTimeText = (TextView) findViewById(R.id.current_time);
        currentDateText = (TextView) findViewById(R.id.current_date);
        roomStatusText = (TextView) findViewById(R.id.room_status);
        nextAvailableText = (TextView) findViewById(R.id.next_available);
        currentMeetingLayout = (LinearLayout) findViewById(R.id.current_meeting_layout);
        currentMeetingTitle = (TextView) findViewById(R.id.current_meeting_title);
        currentMeetingOrganizer = (TextView) findViewById(R.id.current_meeting_organizer);
        currentMeetingTime = (TextView) findViewById(R.id.current_meeting_time);
        currentMeetingAttendees = (TextView) findViewById(R.id.current_meeting_attendees);
        remainingTimeText = (TextView) findViewById(R.id.remaining_time);
        todayScheduleList = (ListView) findViewById(R.id.today_schedule_list);
        statusContainer = (LinearLayout) findViewById(R.id.status_container);
    }
    
    private void initializeMockData() {
        todaysMeetings = new ArrayList<>();
        
        // Create mock meetings for today
        Calendar cal = Calendar.getInstance();
        
        // Meeting 1: 9:00 - 9:30
        cal.set(Calendar.HOUR_OF_DAY, 9);
        cal.set(Calendar.MINUTE, 0);
        Date start1 = (Date) cal.getTime().clone();
        cal.set(Calendar.MINUTE, 30);
        Date end1 = (Date) cal.getTime().clone();
        todaysMeetings.add(new Meeting("Team Standup", "Sarah Johnson", start1, end1, 8));
        
        // Meeting 2: 11:00 - 12:00
        cal.set(Calendar.HOUR_OF_DAY, 11);
        cal.set(Calendar.MINUTE, 0);
        Date start2 = (Date) cal.getTime().clone();
        cal.set(Calendar.HOUR_OF_DAY, 12);
        Date end2 = (Date) cal.getTime().clone();
        todaysMeetings.add(new Meeting("Product Review", "Mike Chen", start2, end2, 6));
        
        // Meeting 3: 14:30 - 15:30
        cal.set(Calendar.HOUR_OF_DAY, 14);
        cal.set(Calendar.MINUTE, 30);
        Date start3 = (Date) cal.getTime().clone();
        cal.set(Calendar.HOUR_OF_DAY, 15);
        Date end3 = (Date) cal.getTime().clone();
        todaysMeetings.add(new Meeting("Client Presentation", "Lisa Wang", start3, end3, 4));
        
        // Meeting 4: 16:00 - 17:00
        cal.set(Calendar.HOUR_OF_DAY, 16);
        cal.set(Calendar.MINUTE, 0);
        Date start4 = (Date) cal.getTime().clone();
        cal.set(Calendar.HOUR_OF_DAY, 17);
        Date end4 = (Date) cal.getTime().clone();
        todaysMeetings.add(new Meeting("Weekly Team Sync", "David Park", start4, end4, 10));
    }
    
    private void setupUpdateHandler() {
        handler = new Handler();
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                updateDisplay();
                handler.postDelayed(this, 30000); // Update every 30 seconds
            }
        };
    }
    
    private void updateDisplay() {
        Date now = new Date();
        
        // Update time and date
        currentTimeText.setText(timeFormat.format(now));
        currentDateText.setText(dateFormat.format(now));
        
        // Determine current room status
        updateRoomStatus(now);
        
        // Update status display
        updateStatusDisplay();
        
        // Update meeting list
        updateMeetingList();
    }
    
    private void updateRoomStatus(Date now) {
        currentMeeting = null;
        
        // Check if there's a current meeting
        for (Meeting meeting : todaysMeetings) {
            if (now.after(meeting.startTime) && now.before(meeting.endTime)) {
                currentMeeting = meeting;
                currentStatus = RoomStatus.BUSY;
                return;
            }
        }
        
        // Check for upcoming meeting within 15 minutes
        long fifteenMinutesFromNow = now.getTime() + (15 * 60 * 1000);
        for (Meeting meeting : todaysMeetings) {
            if (meeting.startTime.getTime() > now.getTime() && 
                meeting.startTime.getTime() <= fifteenMinutesFromNow) {
                currentMeeting = meeting;
                currentStatus = RoomStatus.UPCOMING;
                return;
            }
        }
        
        currentStatus = RoomStatus.AVAILABLE;
    }
    
    private void updateStatusDisplay() {
        switch (currentStatus) {
            case AVAILABLE:
                statusContainer.setBackgroundColor(Color.parseColor("#2E7D32"));
                roomStatusText.setText("AVAILABLE");
                currentMeetingLayout.setVisibility(View.GONE);
                nextAvailableText.setVisibility(View.GONE);
                break;
                
            case BUSY:
                statusContainer.setBackgroundColor(Color.parseColor("#C62828"));
                roomStatusText.setText("BOOKED");
                showCurrentMeetingDetails();
                nextAvailableText.setVisibility(View.GONE);
                break;
                
            case UPCOMING:
                statusContainer.setBackgroundColor(Color.parseColor("#FFB74D"));
                roomStatusText.setText("AVAILABLE");
                showUpcomingMeetingDetails();
                nextAvailableText.setVisibility(View.GONE);
                break;
        }
    }
    
    private void showCurrentMeetingDetails() {
        if (currentMeeting != null) {
            currentMeetingLayout.setVisibility(View.VISIBLE);
            currentMeetingTitle.setText(currentMeeting.organizer);
            currentMeetingOrganizer.setVisibility(View.GONE);
            currentMeetingTime.setText(timeFormat.format(currentMeeting.startTime) + 
                                    " - " + timeFormat.format(currentMeeting.endTime));
            currentMeetingAttendees.setVisibility(View.GONE);
            remainingTimeText.setVisibility(View.GONE);
        }
    }
    
    private void showUpcomingMeetingDetails() {
        showCurrentMeetingDetails(); // Same display, different timing calculation
    }
    
    private void updateNextAvailableText() {
        Date now = new Date();
        Meeting nextMeeting = null;
        
        for (Meeting meeting : todaysMeetings) {
            if (meeting.startTime.after(now)) {
                if (nextMeeting == null || meeting.startTime.before(nextMeeting.startTime)) {
                    nextMeeting = meeting;
                }
            }
        }
        
        if (currentStatus == RoomStatus.BUSY && currentMeeting != null) {
            nextAvailableText.setText("Available at " + timeFormat.format(currentMeeting.endTime));
        } else if (nextMeeting != null) {
            nextAvailableText.setText("Next meeting at " + timeFormat.format(nextMeeting.startTime));
        } else {
            nextAvailableText.setText("Available for the rest of the day");
        }
    }
    
    private void updateRemainingTime() {
        if (currentMeeting != null) {
            Date now = new Date();
            long remainingMs = currentMeeting.endTime.getTime() - now.getTime();
            long remainingMinutes = remainingMs / (1000 * 60);
            remainingTimeText.setText(remainingMinutes + " minutes remaining");
            remainingTimeText.setVisibility(View.VISIBLE);
        } else {
            remainingTimeText.setVisibility(View.GONE);
        }
    }
    
    private void updateStartingTime() {
        if (currentMeeting != null) {
            Date now = new Date();
            long untilMs = currentMeeting.startTime.getTime() - now.getTime();
            long untilMinutes = untilMs / (1000 * 60);
            remainingTimeText.setText("Meeting starts in " + untilMinutes + " minutes");
            remainingTimeText.setVisibility(View.VISIBLE);
        } else {
            remainingTimeText.setVisibility(View.GONE);
        }
    }
    
    private void updateMeetingList() {
        // Show only upcoming meetings (those that haven't ended yet)
        Date now = new Date();
        List<Meeting> upcomingMeetings = new ArrayList<>();
        
        
        for (Meeting meeting : todaysMeetings) {
            if (meeting.endTime.after(now)) {  // Only include meetings that haven't ended yet
                upcomingMeetings.add(meeting);
            } else {
            }
        }
        
        MeetingListAdapter adapter = new MeetingListAdapter(this, upcomingMeetings, currentMeeting);
        todayScheduleList.setAdapter(adapter);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        handler.post(updateRunnable);
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(updateRunnable);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        // MSAL handles this automatically in newer versions
        // Just log that we received a result from an activity
    }
    
    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
    }
    
    // Meeting data class
    public static class Meeting {
        public String title;
        public String organizer;
        public Date startTime;
        public Date endTime;
        public int attendees;
        
        public Meeting(String title, String organizer, Date startTime, Date endTime, int attendees) {
            this.title = title;
            this.organizer = organizer;
            this.startTime = startTime;
            this.endTime = endTime;
            this.attendees = attendees;
        }
        
        public boolean isCurrentlyActive() {
            Date now = new Date();
            return now.after(startTime) && now.before(endTime);
        }
        
        public boolean isPast() {
            Date now = new Date();
            return now.after(endTime);
        }
    }
}
