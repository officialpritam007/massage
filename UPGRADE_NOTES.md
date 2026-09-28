# Liquid Chat v4.0.1 build fix

- Fixed invalid MaterialTheme imports in Liquid Glass UI files.
- Fixed Theme.kt using MaterialTheme inside non-composable color scheme declarations.
- Restored ChatsHomeScreen profile navigation callback.
- Calls/Updates screens are removed from this package.
- GitHub Actions defensively deletes stale CallsScreen.kt and UpdatesScreen.kt before build.
- UI direction remains: Chats | Groups | Settings, Ice Blue light theme, black dark theme.
