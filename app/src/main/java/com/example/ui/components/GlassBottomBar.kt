package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalLiquidGlass

@Composable
fun GlassBottomBar(
  selectedRoute: String,
  onNavigateToChats: () -> Unit,
  onNavigateToContacts: () -> Unit,
  onNavigateToSettings: () -> Unit,
  modifier: Modifier = Modifier,
  unreadChatsCount: Int = 0
) {
  val items = listOf(
    Triple("chats", "Chats", Icons.Default.ChatBubbleOutline),
    Triple("contacts", "Contacts", Icons.Default.People),
    Triple("settings", "Settings", Icons.Default.Settings)
  )
  val clicks = listOf(onNavigateToChats, onNavigateToContacts, onNavigateToSettings)
  val selectedIndex = items.indexOfFirst { it.first == selectedRoute }
  val config = LocalLiquidGlass.current
  val tabShape = liquidRoundedShape(30f)

  Box(
    modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp),
    contentAlignment = Alignment.Center
  ) {
    GlassCard(
      Modifier.widthIn(max = 480.dp).fillMaxWidth(),
      shape = liquidRoundedShape(40f),
      backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = if (config.isDark) .76f else .84f),
      borderColor = Color.White.copy(alpha = if (config.isDark) .22f else .72f),
      elevation = 8.dp
    ) {
      BoxWithConstraints(Modifier.fillMaxWidth().padding(6.dp)) {
        val itemWidth = maxWidth / items.size
        val selectedOffset by animateDpAsState(
          targetValue = itemWidth * selectedIndex.coerceAtLeast(0),
          animationSpec = if (config.isReducedMotion) tween(0)
          else spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
          label = "tab_glass_pill"
        )
        if (selectedIndex >= 0) {
          Box(
            Modifier
              .offset(x = selectedOffset)
              .width(itemWidth)
              .height(60.dp)
              .clip(tabShape)
              .background(
                Brush.verticalGradient(listOf(config.accentColor.copy(alpha = .26f), config.accentColor.copy(alpha = .12f)))
              )
              .border(1.dp, config.accentColor.copy(alpha = .32f), tabShape)
          )
        }
        Row(Modifier.fillMaxWidth().selectableGroup()) {
          items.forEachIndexed { index, (route, label, icon) ->
            val selected = index == selectedIndex
            val color = if (selected) config.accentColor else MaterialTheme.colorScheme.onSurfaceVariant
            val unread = if (route == "chats") unreadChatsCount.coerceAtLeast(0) else 0
            Column(
              Modifier
                .weight(1f)
                .height(60.dp)
                .clip(tabShape)
                .selectable(selected = selected, role = Role.Tab, onClick = clicks[index])
                .semantics {
                  contentDescription = if (unread > 0) "$label, $unread unread messages" else label
                }
                .testTag("bottom_bar_$route"),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center
            ) {
              Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                GlassBadge(
                  count = unread,
                  color = config.accentColor,
                  modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 15.dp, y = (-6).dp)
                    .clearAndSetSemantics { }
                )
              }
              Spacer(Modifier.height(3.dp))
              Text(label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, color = color, maxLines = 1)
            }
          }
        }
      }
    }
  }
}
