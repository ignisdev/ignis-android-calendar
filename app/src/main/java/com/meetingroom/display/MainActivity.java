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

public class MainActivity extends Activity {
    
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
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Keep screen on and hide system UI for kiosk mode
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
        
        initializeViews();
        initializeMockData();
        setupUpdateHandler();
        updateDisplay();
    }
    
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
                updateNextAvailableText();
                break;
                
            case BUSY:
                statusContainer.setBackgroundColor(Color.parseColor("#C62828"));
                roomStatusText.setText("IN USE");
                showCurrentMeetingDetails();
                updateRemainingTime();
                break;
                
            case UPCOMING:
                statusContainer.setBackgroundColor(Color.parseColor("#F57C00"));
                roomStatusText.setText("AVAILABLE");
                showUpcomingMeetingDetails();
                updateStartingTime();
                break;
        }
    }
    
    private void showCurrentMeetingDetails() {
        if (currentMeeting != null) {
            currentMeetingLayout.setVisibility(View.VISIBLE);
            currentMeetingTitle.setText(currentMeeting.title);
            currentMeetingOrganizer.setText("Organizer: " + currentMeeting.organizer);
            currentMeetingTime.setText(timeFormat.format(currentMeeting.startTime) + 
                                    " - " + timeFormat.format(currentMeeting.endTime));
            currentMeetingAttendees.setText(currentMeeting.attendees + " attendees");
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
        MeetingListAdapter adapter = new MeetingListAdapter(this, todaysMeetings, currentMeeting);
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
