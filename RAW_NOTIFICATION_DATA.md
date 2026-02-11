# Raw Notification Data - Complete Implementation

## What's Now Truly RAW

The Raw tab in the Notifications screen now displays **COMPLETELY RAW** notification data without any filtering or processing.

### Changes Made:

1. **NotificationMemoryStore.kt**
   - Added `rawExtras: Map<String, String>` field to `ExternalNotification` data class
   - This captures ALL fields from the Android notification extras Bundle

2. **JawafaiNotificationListenerService.kt**
   - Modified `extractNotificationData()` to capture ALL extras from the notification Bundle
   - Every single key-value pair from `notification.extras.keySet()` is now stored
   - Handles different data types (CharSequence, Array, etc.)

3. **NotificationScreen.kt (Raw Tab)**
   - Displays two sections:
     - **Processed Fields**: The structured data we extract (title, text, sender, etc.)
     - **Raw Notification Extras**: ALL the raw fields from the Android notification Bundle
   - Shows a count of how many raw extras fields were captured
   - Includes complete JSON structure with all raw extras

### What You'll See in the Raw Tab:

#### Processed Fields (Our structured data):
- Title
- Text
- Package Name
- Timestamp
- Sender
- Conversation Title
- Conversation ID
- Has Reply Action
- AI Reply
- Is Sent
- Full Hash

#### Raw Notification Extras (EVERYTHING from Android):
This section shows ALL fields that Android includes in the notification, which may include:
- `android.title`
- `android.text`
- `android.subText`
- `android.conversationTitle`
- `android.largeIcon`
- `android.messages` (for messaging notifications)
- `android.person` (for person info)
- `android.remoteInputHistory`
- `android.showWhen`
- `android.showChronometer`
- `android.messagingUser`
- And many more app-specific fields...

### Why This Is Important:

Different messaging apps (WhatsApp, Instagram, Messenger) may include different custom fields in their notifications. By capturing ALL raw extras, you can:

1. **Analyze patterns** - See what fields different apps use
2. **Find hidden data** - Discover fields we're not currently using
3. **Debug issues** - See exactly what Android is giving us
4. **Improve extraction** - Identify better ways to extract sender names, message content, etc.

### JSON Structure:

The Raw tab now shows a complete JSON representation including:
```json
{
  "title": "...",
  "text": "...",
  "packageName": "...",
  "rawExtras": {
    "android.title": "...",
    "android.text": "...",
    "android.subText": "...",
    "android.conversationTitle": "...",
    ...all other fields...
  }
}
```

## Usage:

1. Navigate to **Notifications** screen
2. Switch to the **Raw** tab
3. Tap on any notification card to expand it
4. Scroll through the **Raw Notification Extras** section to see ALL fields
5. Compare different apps to see pattern differences

This is now **COMPLETELY RAW** - nothing is filtered or left out!

