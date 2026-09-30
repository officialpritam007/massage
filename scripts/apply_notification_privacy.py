from pathlib import Path


def once(s, old, new, label):
    if old not in s:
        raise SystemExit(f'{label}: source block not found')
    return s.replace(old, new, 1)

# Model
p = Path('app/src/main/java/com/example/data/model/Models.kt')
s = p.read_text()
s = once(s,
'''data class NotificationSettings(
  val messages: Boolean = true,
  val vibration: Boolean = true
)
''',
'''data class NotificationSettings(
  val messages: Boolean = true,
  val vibration: Boolean = true,
  val showPreview: Boolean = true
)
''', 'NotificationSettings')
p.write_text(s)

# Repository parse/persist
p = Path('app/src/main/java/com/example/data/repository/ChatRepository.kt')
s = p.read_text()
s = once(s,
'''          _notifications.value = NotificationSettings(
            anyBoolean(n["messages"], true),
            anyBoolean(n["vibration"], true)
          )
''',
'''          _notifications.value = NotificationSettings(
            messages = anyBoolean(n["messages"], true),
            vibration = anyBoolean(n["vibration"], true),
            showPreview = anyBoolean(n["showPreview"], true)
          )
''', 'notification parse')
s = once(s,
'''    save("notifications", mapOf("messages" to settings.messages, "vibration" to settings.vibration))
''',
'''    save("notifications", mapOf(
      "messages" to settings.messages,
      "vibration" to settings.vibration,
      "showPreview" to settings.showPreview
    ))
''', 'notification persist')
p.write_text(s)

# Settings UI
p = Path('app/src/main/java/com/example/ui/screens/SettingsScreen.kt')
s = p.read_text()
s = s.replace('SettingRow("Notifications", "Messages and vibration")', 'SettingRow("Notifications", "Messages, vibration and previews")', 1)
s = once(s,
'''            Toggle("Vibration", notifications.vibration) {
              viewModel.updateNotifications(notifications.copy(vibration = it))
            }
''',
'''            Toggle("Vibration", notifications.vibration) {
              viewModel.updateNotifications(notifications.copy(vibration = it))
            }
            Toggle("Show message previews", notifications.showPreview) {
              viewModel.updateNotifications(notifications.copy(showPreview = it))
            }
            Text(
              "When previews are off, notifications hide the sender and message text.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
''', 'notification settings UI')
p.write_text(s)

# Backend FCM payload privacy
p = Path('appwrite-functions/liquid-api/src/main.js')
s = p.read_text()
s = once(s,
'''  const tokens = Object.values(u.tokens || {}).filter(x => typeof x === 'string').slice(0, 10);
  if (tokens.length) {
    await getMessaging().sendEachForMulticast({
      tokens,
      android: {priority: 'high'},
      data: {
        type: 'message',
        conversationId: cid,
        messageId: id,
        title: m.senderName || 'Liquid Chat',
        body: m.type === 'TEXT' ? String(m.text || '').slice(0, 120) : m.type.toLowerCase() + ' message',
        vibration: String(u.notifications?.vibration !== false)
      }
    });
  }
''',
'''  const tokens = Object.values(u.tokens || {}).filter(x => typeof x === 'string').slice(0, 10);
  if (tokens.length) {
    const showPreview = u.notifications?.showPreview !== false;
    await getMessaging().sendEachForMulticast({
      tokens,
      android: {priority: 'high'},
      data: {
        type: 'message',
        conversationId: cid,
        messageId: id,
        title: showPreview ? (m.senderName || 'Liquid Chat') : 'Liquid Chat',
        body: showPreview
          ? (m.type === 'TEXT' ? String(m.text || '').slice(0, 120) : m.type.toLowerCase() + ' message')
          : 'New message',
        vibration: String(u.notifications?.vibration !== false),
        preview: String(showPreview)
      }
    });
  }
''', 'backend notification privacy')
p.write_text(s)

# Android explicit notification grouping
p = Path('app/src/main/java/com/example/notifications/LiquidFirebaseMessagingService.kt')
s = p.read_text()
s = once(s,
'''    val notification = NotificationCompat.Builder(this, channel)
      .setSmallIcon(R.drawable.ic_launcher_foreground)
      .setContentTitle(message.data["title"] ?: "Liquid Chat")
      .setContentText(message.data["body"] ?: "New message")
      .setContentIntent(pending)
      .setAutoCancel(true)
      .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
      .build()

    NotificationManagerCompat.from(this).notify(id.hashCode(), notification)
''',
'''    val groupKey = "liquid_chat_messages"
    val notification = NotificationCompat.Builder(this, channel)
      .setSmallIcon(R.drawable.ic_launcher_foreground)
      .setContentTitle(message.data["title"] ?: "Liquid Chat")
      .setContentText(message.data["body"] ?: "New message")
      .setContentIntent(pending)
      .setAutoCancel(true)
      .setGroup(groupKey)
      .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
      .build()

    val summary = NotificationCompat.Builder(this, channel)
      .setSmallIcon(R.drawable.ic_launcher_foreground)
      .setContentTitle("Liquid Chat")
      .setContentText("New messages")
      .setGroup(groupKey)
      .setGroupSummary(true)
      .setOnlyAlertOnce(true)
      .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
      .build()

    NotificationManagerCompat.from(this).notify(id.hashCode(), notification)
    NotificationManagerCompat.from(this).notify(-7717, summary)
''', 'notification grouping')
p.write_text(s)

print('Notification privacy and grouping patch applied')
