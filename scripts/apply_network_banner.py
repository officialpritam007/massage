from pathlib import Path

p = Path('app/src/main/java/com/example/ui/LiquidChatApp.kt')
s = p.read_text()

s = s.replace(
    'import androidx.compose.ui.graphics.Color\n',
    'import androidx.compose.ui.Alignment\nimport androidx.compose.ui.Modifier\nimport androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.unit.dp\n',
    1,
)
s = s.replace(
    'import com.example.ui.components.GlassDialog\n',
    'import com.example.ui.components.GlassDialog\nimport com.example.ui.components.NetworkStatusBanner\n',
    1,
)

old = '''    NavHost(\n      navController = navController,\n'''
new = '''    Box(Modifier.fillMaxSize()) {\n      NavHost(\n        navController = navController,\n'''
if old not in s:
    raise SystemExit('NavHost start not found')
s = s.replace(old, new, 1)

old_end = '''      }\n    }\n  }\n}\n'''
new_end = '''      }\n      }\n\n      NetworkStatusBanner(\n        Modifier\n          .align(Alignment.TopCenter)\n          .statusBarsPadding()\n          .padding(top = 8.dp)\n      )\n    }\n  }\n}\n'''
if old_end not in s:
    raise SystemExit('NavHost end not found')
s = s.replace(old_end, new_end, 1)

p.write_text(s)
print('Network banner wired into root app')
