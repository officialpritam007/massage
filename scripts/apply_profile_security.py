from pathlib import Path


def once(s, old, new, label):
    if old not in s:
        raise SystemExit(f'{label}: source block not found')
    return s.replace(old, new, 1)

# Backend username availability + report throttle
p = Path('appwrite-functions/liquid-api/src/main.js')
s = p.read_text()
s = once(s,
'''    if (p.action === 'profile') {
''',
'''    if (p.action === 'usernameCheck') {
      const username = String(p.username || '').trim().toLowerCase();
      if (!/^[a-z0-9_.]{3,32}$/.test(username)) {
        throw new Error('Username must have 3–32 letters, numbers, dots or underscores');
      }
      const reserved = await db.doc('usernames/' + username).get();
      return res.json({available: !reserved.exists || reserved.data()?.uid === uid});
    }

    if (p.action === 'profile') {
''', 'username check action')

s = once(s,
'''    if (p.action === 'report') {
      if (!p.otherUid || p.otherUid === uid || String(p.reason || '').trim().length < 4) throw new Error('Please enter a report reason');
      await db.collection('reports').add({
''',
'''    if (p.action === 'report') {
      if (!p.otherUid || p.otherUid === uid || String(p.reason || '').trim().length < 4) throw new Error('Please enter a report reason');
      const reportRate = db.doc('_reportRate/' + uid);
      await db.runTransaction(async t => {
        const previous = (await t.get(reportRate)).data();
        const hour = Math.floor(now / 3600000);
        const sameHour = previous?.hour === hour;
        const count = sameHour ? Number(previous?.count || 0) : 0;
        if (count >= 5) throw new Error('Report limit reached. Try again later.');
        t.set(reportRate, {hour, count: count + 1, updatedAt: now});
      });
      await db.collection('reports').add({
''', 'report throttle')

s = s.replace(
'''      await db.doc('_rate/' + uid).delete();
''',
'''      await db.doc('_rate/' + uid).delete();
      await db.doc('_reportRate/' + uid).delete();
''', 1)
p.write_text(s)

# Repository
p = Path('app/src/main/java/com/example/data/repository/ChatRepository.kt')
s = p.read_text()
s = once(s,
'''  fun updateProfile(displayName: String, username: String, bio: String, phoneNumber: String) = runAction {
''',
'''  suspend fun checkUsernameAvailability(username: String): Result<Boolean> = runCatching {
    LiquidApi.call("usernameCheck", mapOf("username" to username.trim().lowercase())).optBoolean("available", false)
  }

  fun updateProfile(displayName: String, username: String, bio: String, phoneNumber: String) = runAction {
''', 'repository username check')
p.write_text(s)

# ViewModel
p = Path('app/src/main/java/com/example/ui/viewmodel/LiquidChatViewModel.kt')
s = p.read_text()
s = once(s,
'''  fun getOrCreateConversationId(otherUid: String): String = repository.getOrCreateConversationId(otherUid)
''',
'''  suspend fun checkUsernameAvailability(username: String): Result<Boolean> =
    repository.checkUsernameAvailability(username)

  fun getOrCreateConversationId(otherUid: String): String = repository.getOrCreateConversationId(otherUid)
''', 'viewmodel username check')
p.write_text(s)

# Settings profile UI
p = Path('app/src/main/java/com/example/ui/screens/SettingsScreen.kt')
s = p.read_text()
s = once(s,
'''  var username by remember(me.username) { mutableStateOf(me.username) }
  var bio by remember(me.bio) { mutableStateOf(me.bio) }
  var busy by remember { mutableStateOf(false) }
''',
'''  var username by remember(me.username) { mutableStateOf(me.username) }
  var bio by remember(me.bio) { mutableStateOf(me.bio) }
  var phone by remember(me.phoneNumber) { mutableStateOf(me.phoneNumber) }
  var usernameStatus by remember { mutableStateOf<String?>(null) }
  var checkingUsername by remember { mutableStateOf(false) }
  var busy by remember { mutableStateOf(false) }
''', 'profile states')

s = once(s,
'''            GlassTextField(username, { username = it.lowercase().take(32) }, placeholder = "Username")
            GlassTextField(
              bio,
''',
'''            GlassTextField(
              username,
              {
                username = it.lowercase().take(32)
                usernameStatus = null
              },
              placeholder = "Username"
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
              TextButton(
                enabled = !checkingUsername && username.isNotBlank(),
                onClick = {
                  checkingUsername = true
                  scope.launch {
                    val result = viewModel.checkUsernameAvailability(username)
                    usernameStatus = result.fold(
                      onSuccess = { if (it) "Username is available" else "Username is already taken" },
                      onFailure = { it.message ?: "Could not check username" }
                    )
                    checkingUsername = false
                  }
                }
              ) { Text(if (checkingUsername) "Checking…" else "Check availability") }
              usernameStatus?.let {
                Text(
                  it,
                  style = MaterialTheme.typography.bodySmall,
                  color = if (it.contains("available", ignoreCase = true) && !it.contains("taken", ignoreCase = true))
                    MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
              }
            }
            GlassTextField(
              bio,
''', 'username check UI')

s = once(s,
'''              maxLines = 4
            )
            GlassButton(
''',
'''              maxLines = 4
            )
            GlassTextField(
              phone,
              { phone = it.filter { ch -> ch.isDigit() || ch == '+' || ch == ' ' || ch == '-' }.take(30) },
              placeholder = "Phone (optional)"
            )
            GlassButton(
''', 'phone input')

s = s.replace(
'''                viewModel.updateProfile(name.trim(), username.trim(), bio.trim(), me.phoneNumber)
''',
'''                viewModel.updateProfile(name.trim(), username.trim(), bio.trim(), phone.trim())
''', 1)
p.write_text(s)

print('Profile availability, phone editing and report throttle patch applied')
