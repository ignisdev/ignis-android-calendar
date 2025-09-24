package com.meetingroom.display;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class MeetingListAdapter extends BaseAdapter {
    
    private Context context;
    private List<MainActivity.Meeting> meetings;
    private MainActivity.Meeting currentMeeting;
    private LayoutInflater inflater;
    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    
    public MeetingListAdapter(Context context, List<MainActivity.Meeting> meetings, MainActivity.Meeting currentMeeting) {
        this.context = context;
        this.meetings = meetings;
        this.currentMeeting = currentMeeting;
        this.inflater = LayoutInflater.from(context);
    }
    
    @Override
    public int getCount() {
        return meetings.size();
    }
    
    @Override
    public Object getItem(int position) {
        return meetings.get(position);
    }
    
    @Override
    public long getItemId(int position) {
        return position;
    }
    
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.meeting_list_item, parent, false);
            holder = new ViewHolder();
            holder.organizer = (TextView) convertView.findViewById(R.id.meeting_organizer);
            holder.time = (TextView) convertView.findViewById(R.id.meeting_time);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        
        MainActivity.Meeting meeting = meetings.get(position);
        
        holder.organizer.setText(meeting.organizer);
        holder.time.setText(timeFormat.format(meeting.startTime) + " - " + timeFormat.format(meeting.endTime));
        
        // Set background based on meeting state
        if (meeting.isCurrentlyActive()) {
            convertView.setBackgroundColor(Color.parseColor("#FFFFFF"));
        } else if (meeting.isPast()) {
            convertView.setBackgroundColor(Color.parseColor("#F5F5F5"));
            convertView.setAlpha(0.6f);
        } else {
            // Remove pale blue background for upcoming events - use white instead
            convertView.setBackgroundColor(Color.parseColor("#FFFFFF"));
            convertView.setAlpha(1.0f);
        }
        
        return convertView;
    }
    
    static class ViewHolder {
        TextView organizer;
        TextView time;
    }
}
