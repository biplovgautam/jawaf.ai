# WhatsApp "You" Title Handling - Complete Guide

## The Problem

In WhatsApp notifications:
- **Incoming message**: `title = "Biplov Gautam"` (sender's name)
- **Outgoing message**: `title = "You"` (you sent it)

We need to handle these differently so the inbox shows the OTHER person's name, not "You".

## The Solution

### Detection Logic

```kotlin
val title = notification.title
val is_outgoing = title.equals("You", ignoreCase = true)
```

### Behavior

#### Case 1: Incoming Message (title ≠ "You")
```
Notification:
├─ title: "Biplov Gautam"
├─ text: "Hey, how are you?"
└─ is_outgoing: false

Action:
✅ Update conversation display_name to "Biplov Gautam"
✅ Increment unread_count
✅ Show message on LEFT (gray bubble)
✅ Show sender name "Biplov Gautam" on message
✅ Show "Generate Reply" button
```

#### Case 2: Outgoing Message (title = "You")
```
Notification:
├─ title: "You"
├─ text: "I'm good, thanks!"
└─ is_outgoing: true

Action:
✅ DON'T update conversation display_name (keep recipient's name)
✅ DON'T increment unread_count
✅ Show message on RIGHT (green bubble)
✅ DON'T show sender name (it's "You")
✅ DON'T show "Generate Reply" button (you sent it!)
```

## Implementation

### NotificationMemoryStore

```kotlin
private fun addToConversationStore(notification: ExternalNotification) {
    val title = notification.title
    val is_outgoing = title.equals("You", ignoreCase = true)
    
    val sender_name: String
    val should_update_display_name: Boolean
    
    if (is_outgoing) {
        // Outgoing: You sent the message
        sender_name = "You"
        should_update_display_name = false  // ← Keep recipient's name
    } else {
        // Incoming: Someone sent to you
        sender_name = notification.sender ?: title
        should_update_display_name = true  // ← Update to sender's name
    }
    
    // Update conversation
    if (existingConvoIndex != -1) {
        conversations.add(0, existingConvo.copy(
            last_msg_time = timestamp,
            last_msg_content = msg_content,
            unread_count = if (is_outgoing) existingConvo.unread_count else existingConvo.unread_count + 1,
            display_name = if (should_update_display_name) sender_name else existingConvo.display_name  // ← Key logic
        ))
    }
    
    // Create message
    val message = Message(
        sender_name = sender_name,  // "You" or actual sender
        is_outgoing = is_outgoing   // true if title="You"
    )
}
```

### UI: MessageBubble

```kotlin
Row(
    horizontalArrangement = if (message.is_outgoing) Arrangement.End else Arrangement.Start
) {
    Card(
        shape = if (message.is_outgoing) {
            RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 4.dp, bottomStart = 16.dp)
        } else {
            RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp)
        },
        colors = CardDefaults.cardColors(
            containerColor = if (message.is_outgoing) 
                Color(0xFFDCF8C6)  // Green (like WhatsApp)
            else 
                Color(0xFFF0F0F0)  // Gray
        )
    ) {
        // Only show sender name for incoming
        if (!message.is_outgoing) {
            Text(text = message.sender_name)
        }
        
        Text(text = message.msg_content)
    }
}

// Action buttons - only for incoming
if (!message.is_sent && !message.is_outgoing) {
    Button(onClick = onGenerateReply) {
        Text("Generate Reply")
    }
}
```

## Visual Examples

### Example 1: Conversation with Biplov

#### Scenario: You receive 3 messages, then send 1 reply

**Inbox:**
```
┌────────────────────────────────┐
│ B  Biplov Gautam        2m ago │  ← Shows OTHER person
│    WhatsApp                  [3]│  ← 3 unread (only incoming)
│    I'm good too!               │  ← Last message (your reply)
└────────────────────────────────┘
```

**Chat View:**
```
┌────────────────────────────────┐
│ ← B  Biplov Gautam             │  ← Header: OTHER person
│      WhatsApp                   │
├────────────────────────────────┤
│                                 │
│  ┌─────────────────────┐       │  ← Incoming (LEFT, GRAY)
│  │ Biplov Gautam       │       │
│  │ Hey, how are you?   │       │
│  │ 14:30               │       │
│  └─────────────────────┘       │
│  [Generate Reply]               │
│                                 │
│  ┌─────────────────────┐       │  ← Incoming (LEFT, GRAY)
│  │ Biplov Gautam       │       │
│  │ Long time no see!   │       │
│  │ 14:31               │       │
│  └─────────────────────┘       │
│  [Generate Reply]               │
│                                 │
│  ┌─────────────────────┐       │  ← Incoming (LEFT, GRAY)
│  │ Biplov Gautam       │       │
│  │ What are you up to? │       │
│  │ 14:32               │       │
│  └─────────────────────┘       │
│  [Generate Reply]               │
│                                 │
│      ┌─────────────────────┐   │  ← Outgoing (RIGHT, GREEN)
│      │ I'm good too!       │   │  ← No sender name
│      │ 14:33        ✓✓     │   │
│      └─────────────────────┘   │  ← No buttons
│                                 │
└────────────────────────────────┘
```

### Data Flow

#### Message 1: Incoming from Biplov
```
Notification:
  title: "Biplov Gautam"
  text: "Hey, how are you?"
  
↓ Processing ↓

Conversation:
  convo_id: "com.whatsapp_170762839"
  display_name: "Biplov Gautam"  ✅ Updated
  unread_count: 1  ✅ Incremented

Message:
  sender_name: "Biplov Gautam"
  is_outgoing: false
  ↓
  UI: LEFT, GRAY, Show sender name, Show buttons
```

#### Message 2: Outgoing from You
```
Notification:
  title: "You"
  text: "I'm good too!"
  
↓ Processing ↓

Conversation:
  convo_id: "com.whatsapp_170762839"
  display_name: "Biplov Gautam"  ✅ NOT updated (stays as is)
  unread_count: 1  ✅ NOT incremented

Message:
  sender_name: "You"
  is_outgoing: true
  ↓
  UI: RIGHT, GREEN, No sender name, No buttons
```

## Why This Matters

### ✅ Correct Inbox Display
- Inbox always shows the OTHER person's name
- Never shows "You" as a conversation name
- Matches WhatsApp behavior exactly

### ✅ Correct Unread Count
- Only incoming messages increment unread
- Your own sent messages don't count as "unread"

### ✅ Correct Message Display
- Incoming: Left, gray, show sender
- Outgoing: Right, green, hide sender
- Matches messaging app conventions

### ✅ Correct Actions
- Generate Reply only for incoming messages
- No buttons for your own sent messages

## Edge Cases Handled

### 1. First Message is Outgoing
```
If title="You" and conversation doesn't exist:
  Create conversation with display_name="Unknown"
  Will update to actual name when first incoming message arrives
```

### 2. Group Chats
```
Multiple incoming messages from different people:
  Each message has different sender_name
  Conversation display_name updates to latest sender
  All non-"You" messages are incoming
```

### 3. Case Insensitive
```
title.equals("You", ignoreCase = true)
  Handles: "You", "you", "YOU", "yOu"
```

## Status

✅ **Title="You" detection working**
✅ **Conversation display_name preserved**
✅ **Unread count accurate**
✅ **Message bubbles positioned correctly**
✅ **Action buttons shown only for incoming**
✅ **Visual styling matches WhatsApp**

The system now perfectly handles WhatsApp's "You" title! 🎉

