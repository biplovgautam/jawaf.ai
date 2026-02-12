# Notification Management System - Current Implementation

## Overview
The notification system uses an **in-memory store** (NotificationMemoryStore) to manage notifications efficiently without database overhead. This is perfect for the current use case since notifications are temporary by nature.

## Current Structure

### 1. NotificationMemoryStore (Service Layer)
**Location:** `/app/src/main/java/com/example/jawafai/service/NotificationMemoryStore.kt`

**Data Structure:**
```kotlin
data class ExternalNotification(
    val title: String,                    // Group name or sender
    val text: String,                     // Message content
    val packageName: String,              // App package name (e.g., "com.whatsapp")
    val time: Long,                       // Timestamp in milliseconds
    val sender: String? = null,           // Actual sender name
    val conversationTitle: String? = null, // For group chats
    val conversationId: String,           // Unique conversation identifier
    val hasReplyAction: Boolean = false,  // Whether reply action is available
    val replyAction: Notification.Action? = null,
    val remoteInput: RemoteInput? = null,
    val hash: String,                     // For deduplication
    val ai_reply: String = "",            // AI generated reply
    val is_sent: Boolean = false,         // Whether reply was sent
    val rawExtras: Map<String, String> = emptyMap() // ALL raw notification extras
)
```

**Key Features:**
- ✅ In-memory storage using Compose's `mutableStateListOf`
- ✅ Automatic deduplication using hash
- ✅ Size limit of 500 notifications to prevent memory issues
- ✅ Conversation-based grouping via `conversationId`
- ✅ Complete raw extras storage for pattern analysis

### 2. Timestamp Handling

**Storage:** Timestamps are stored as `Long` (milliseconds since epoch)

**Display Formats:**

#### A. Smart Tab (User-Friendly)
- "now" - less than 1 minute ago
- "5m ago" - minutes ago
- "2h ago" - hours ago
- "yesterday" - 24-48 hours ago
- "Jan 15" - older dates

#### B. Raw Tab (Analysis)
- **Processed Fields:**
  - `Timestamp (Formatted)`: "2026-02-12 14:30:45" (yyyy-MM-dd HH:mm:ss)
  - `Timestamp (Raw)`: "1770851045221" (milliseconds)
  
- **JSON Structure:**
```json
{
  "timestamp": 1770851045221,
  "timestamp_formatted": "2026-02-12 14:30:45",
  ...
}
```

### 3. Conversation Management

The system automatically groups messages into conversations using `conversationId`:

**Conversation ID Generation Logic:**
1. Uses notification key if available
2. Falls back to: `packageName_title_sender` hash

**Conversation Methods:**
- `getConversationContext()` - Get last N messages in a conversation
- `getNotificationsByConversation()` - Get all messages in a conversation

### 4. Message Storage Pattern

While we use in-memory storage, the data model maps to your analyst dashboard structure:

**Virtual Table 1: Conversations (In-Memory)**
```
conversationId     -> convo_id
packageName        -> package_name
sender/title       -> display_name
time               -> last_msg_time
```

**Virtual Table 2: Messages (In-Memory)**
```
hash               -> msg_id (deduplication)
conversationId     -> convo_id (FK)
sender             -> sender_name
text               -> msg_content
time               -> timestamp
```

## Why In-Memory Instead of Database?

### Advantages:
1. **Performance:** Instant access, no disk I/O
2. **Simplicity:** No database migrations or schema changes
3. **Privacy:** Data is cleared when app closes
4. **Real-time:** Compose state automatically updates UI
5. **Sufficient:** 500 notifications limit is more than enough

### When to Consider Database:
- If you need **persistent** conversation history
- If you want to **analyze patterns** across app restarts
- If you want to implement **conversation backups**

## Notification Flow

```
1. Android System
   ↓
2. JawafaiNotificationListenerService
   ↓ (extracts data + raw extras)
3. NotificationMemoryStore.addNotification()
   ↓ (deduplication + grouping)
4. UI (NotificationScreen)
   ├─ Smart Tab: User-friendly view
   └─ Raw Tab: Complete data with timestamps
```

## Raw Data Access

The Raw tab now shows:
1. **Processed Fields** - Structured data we extract
2. **Raw Notification Extras** - ALL Android notification fields
3. **Complete JSON** - Copyable JSON with both raw and formatted timestamps

### Example JSON Output:
```json
{
  "title": "Biplov Gautam",
  "text": "hellooo",
  "packageName": "com.whatsapp",
  "timestamp": 1770851045221,
  "timestamp_formatted": "2026-02-12 14:30:45",
  "sender": "Biplov Gautam",
  "conversationTitle": null,
  "conversationId": "com.whatsapp_170762839",
  "hasReplyAction": true,
  "ai_reply": "Hey! What's up?",
  "is_sent": false,
  "hash": "843f...07f1",
  "rawExtras": {
    "android.title": "Biplov Gautam",
    "android.text": "hellooo",
    "android.subText": "WhatsApp",
    ...all other Android fields...
  }
}
```

## Future Enhancements (When Needed)

If you later need persistent storage, the database structure is ready:

### Table 1: conversations
```sql
convo_id (PK)        TEXT    -- e.g., "com.whatsapp_170762839"
package_name         TEXT    -- e.g., "com.whatsapp"
display_name         TEXT    -- e.g., "Biplov Gautam"
last_msg_time        INTEGER -- Milliseconds timestamp
platform_id          TEXT    -- Optional specific ID
```

### Table 2: messages
```sql
msg_id (PK)          INTEGER -- Auto-increment
convo_id (FK)        TEXT    -- Links to conversations
sender_name          TEXT    -- Who sent this message
msg_content          TEXT    -- The text
timestamp            INTEGER -- Milliseconds
is_outgoing          BOOLEAN -- True if from user
msg_hash             TEXT    -- Deduplication
```

## Current Status

✅ **In-memory storage working perfectly**
✅ **Timestamps stored accurately (milliseconds)**
✅ **Both raw and formatted timestamps available**
✅ **Complete raw data captured**
✅ **Copyable JSON for analysis**
✅ **No data loss or timestamp conversion issues**

The system is production-ready without requiring a database at this stage!

