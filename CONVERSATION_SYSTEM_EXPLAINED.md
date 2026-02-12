# Conversation-Based Messaging - How It Works

## The Core Concept

Just like in WhatsApp or Messenger:
1. **Inbox shows OTHER people** - You see who you're chatting with, not yourself
2. **Messages show WHO sent them** - Each bubble identifies the sender
3. **Conversations grouped by person** - All messages from one person in one thread

## Data Flow

### When a Notification Arrives

```
Notification from WhatsApp
├─ Title: "Biplov Gautam"
├─ Text: "Hey, how are you?"
├─ Sender: "Biplov Gautam"
└─ ConversationId: "com.whatsapp_170762839"
    ↓
NotificationMemoryStore.addNotification()
    ↓
Creates/Updates Conversation:
├─ convo_id: "com.whatsapp_170762839"
├─ display_name: "Biplov Gautam" ← OTHER person's name
├─ last_msg_content: "Hey, how are you?"
└─ unread_count: +1
    ↓
Creates Message:
├─ msg_id: "843f...07f1"
├─ convo_id: "com.whatsapp_170762839"
├─ sender_name: "Biplov Gautam" ← WHO sent THIS message
├─ msg_content: "Hey, how are you?"
├─ is_outgoing: false ← Because it's an incoming notification
└─ timestamp: 1770851045221
    ↓
UI Auto-Updates (Compose State)
    ↓
Shows in Inbox or Updates Chat View
```

## UI Structure

### Level 1: Inbox (Conversation List)

Shows all conversations, each with:
- **Avatar** - First letter of other person's name
- **Platform Badge** - WhatsApp/Instagram/Messenger indicator
- **Display Name** - OTHER person's name (e.g., "Biplov Gautam")
- **Last Message** - Preview of most recent message
- **Timestamp** - When last message was received
- **Unread Badge** - Count of unread messages

```kotlin
// Inbox shows:
Conversation(
    display_name = "Biplov Gautam",  // ← OTHER person
    last_msg_content = "Hey!",
    unread_count = 2
)
```

### Level 2: Chat View (Message Thread)

When you click a conversation, shows:
- **Header** - Other person's name and platform
- **Message Bubbles** - All messages in chronological order
  - Left-aligned (gray) = Messages FROM other person
  - Right-aligned (green) = AI-generated replies
- **Sender Name** - On each message bubble
- **Timestamp** - On each message
- **Action Buttons** - Generate Reply, Send

```kotlin
// Messages show actual sender:
Message(
    sender_name = "Biplov Gautam",  // ← WHO sent this
    msg_content = "Hey, how are you?",
    is_outgoing = false  // ← Incoming
)

Message(
    sender_name = "AI",  // ← Marked as AI reply
    msg_content = "I'm good, thanks!",
    ai_reply = "I'm good, thanks!",
    is_outgoing = true  // ← Would be outgoing if sent
)
```

## Key Fields Explained

### conversation_id
- Unique identifier for a conversation
- Format: `packageName_uniqueId`
- Example: `"com.whatsapp_170762839"`
- **Purpose:** Groups messages from same chat/person

### display_name (in Conversation)
- Shows in inbox list
- **Always the OTHER person's name**
- Never shows "You" or current user
- Extracted from `notification.sender` or `notification.title`

### sender_name (in Message)
- Shows who sent THIS specific message
- Can be different for each message (group chats)
- Used to identify message owner
- **In WhatsApp:**
  - Individual chat: Same as display_name
  - Group chat: Different people

### is_outgoing
- `false` = Message FROM other person (left-aligned)
- `true` = Message FROM you (right-aligned)
- **Currently:** All notifications are `is_outgoing = false`
- **Future:** If we capture sent messages, those would be `true`

## Real-World Example

### Scenario: Chat with "Biplov Gautam"

#### Inbox View:
```
┌────────────────────────────────┐
│ B  Biplov Gautam        5m ago │  ← Shows OTHER person
│    WhatsApp                  [3]│  ← 3 unread messages
│    Are you free tomorrow?      │
└────────────────────────────────┘
```

#### Chat View (when clicked):
```
┌────────────────────────────────┐
│ ← B  Biplov Gautam             │  ← Header: OTHER person
│      WhatsApp                   │
├────────────────────────────────┤
│                                 │
│  ┌─────────────────────┐       │
│  │ Biplov Gautam       │       │  ← Message 1 sender
│  │ Hey, how are you?   │       │
│  │ 14:30               │       │
│  └─────────────────────┘       │
│  [Generate Reply]               │
│                                 │
│  ┌─────────────────────┐       │
│  │ Biplov Gautam       │       │  ← Message 2 sender
│  │ Long time no see!   │       │
│  │ 14:31               │       │
│  └─────────────────────┘       │
│  [Generate Reply]               │
│                                 │
│  ┌─────────────────────┐       │
│  │ Biplov Gautam       │       │  ← Message 3 sender
│  │ Are you free        │       │
│  │ tomorrow?           │       │
│  │ 14:32               │       │
│  └─────────────────────┘       │
│                                 │
│      ┌─────────────────────┐   │
│      │ ✨ AI Reply         │   │  ← AI generated reply
│      │ Yes, I'm available  │   │
│      │ tomorrow!           │   │
│      └─────────────────────┘   │
│      [Send]                     │
│                                 │
└────────────────────────────────┘
```

## Database Structure (In-Memory)

### Table 1: Conversations
| convo_id | package_name | display_name | last_msg_time | last_msg_content | unread_count |
|----------|--------------|--------------|---------------|------------------|--------------|
| com.whatsapp_170762839 | com.whatsapp | Biplov Gautam | 1770851045221 | Are you free tomorrow? | 3 |

**Note:** `display_name = "Biplov Gautam"` (OTHER person, not "You")

### Table 2: Messages
| msg_id | convo_id | sender_name | msg_content | timestamp | is_outgoing |
|--------|----------|-------------|-------------|-----------|-------------|
| 843f...1 | com.whatsapp_170762839 | Biplov Gautam | Hey, how are you? | 1770851045221 | false |
| 843f...2 | com.whatsapp_170762839 | Biplov Gautam | Long time no see! | 1770851045222 | false |
| 843f...3 | com.whatsapp_170762839 | Biplov Gautam | Are you free tomorrow? | 1770851045223 | false |

**Note:** Each message has `sender_name = "Biplov Gautam"` (who actually sent it)

## Why This Design?

### ✅ Natural Experience
- Matches WhatsApp/Messenger behavior
- Users instantly understand the interface
- No confusion about "who is who"

### ✅ Accurate Attribution
- Each message knows WHO sent it
- Important for group chats (multiple senders)
- Clear distinction between incoming and AI replies

### ✅ Scalable
- Works for 1-on-1 chats
- Works for group chats (multiple senders)
- Works for broadcast messages

### ✅ Consistent
- Inbox shows conversations (people you chat with)
- Chat view shows messages (what was said)
- AI replies clearly marked

## Future Enhancements

When we want to support sent messages:
```kotlin
// Detect if message is from current user
val currentUserName = getCurrentUserDisplayName()
val is_outgoing = (sender_name == currentUserName)

// Then in UI:
if (message.is_outgoing) {
    // Show right-aligned (blue bubble)
    // Label: "You"
} else {
    // Show left-aligned (gray bubble)
    // Label: sender_name
}
```

## Summary

✅ **Inbox** - Shows OTHER people's names (who you're chatting with)
✅ **Messages** - Shows WHO sent each message (actual sender)
✅ **Conversation ID** - Groups all messages from same chat
✅ **is_outgoing** - Distinguishes incoming vs outgoing messages
✅ **Natural UX** - Works like WhatsApp/Messenger

The system now perfectly mimics real messaging apps! 🎉

