# Meeting Room Display Setup

## Available Rooms

Your app is configured for these Ignis meeting rooms:

1. **Peter 3rd Floor**: `meetingroom-peter3rdfloor@ignis.co.uk`
2. **Luke 2nd Floor**: `meetingroom-luke2ndfloor@ignis.co.uk`  
3. **Matthew 1st Floor**: `meetingroom-matthew1stfloor@ignis.co.uk`

## How to Configure for Each Room

In `MainActivity.java` (lines 35-36), change these constants:

### For Peter 3rd Floor (Current Default):
```java
private static final String CURRENT_ROOM_EMAIL = PETER_3RD_FLOOR;
private static final String CURRENT_ROOM_NAME = "Meeting Room - Peter 3rd Floor";
```

### For Luke 2nd Floor:
```java
private static final String CURRENT_ROOM_EMAIL = LUKE_2ND_FLOOR;
private static final String CURRENT_ROOM_NAME = "Meeting Room - Luke 2nd Floor";
```

### For Matthew 1st Floor:
```java
private static final String CURRENT_ROOM_EMAIL = MATTHEW_1ST_FLOOR;
private static final String CURRENT_ROOM_NAME = "Meeting Room - Matthew 1st Floor";
```

## Build Process

1. Change the room configuration in `MainActivity.java`
2. Run `./gradlew assembleRelease`
3. Install the APK on the respective room's device
4. Repeat for each room

## Required Azure AD Permissions

Your IT admin needs to grant these permissions for the app:
- `Calendars.Read` - to read room calendars
- `User.Read` - for authentication

The app will authenticate and read from the specific room's calendar.