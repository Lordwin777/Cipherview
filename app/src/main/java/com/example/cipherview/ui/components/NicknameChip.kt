package com.example.cipherview.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cipherview.theme.*

@Composable
fun NicknameChip(
    historicalNickname: String,
    currentNickname: String = historicalNickname,
    prefix: String = "Shared by",
    modifier: Modifier = Modifier
) {
    val isChanged = historicalNickname.isNotBlank() &&
            currentNickname.isNotBlank() &&
            !historicalNickname.equals(currentNickname, ignoreCase = true)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(VaultSurfaceVariant)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Avatar circle
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(CyanDark)
                .border(1.dp, CyanNeon, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = historicalNickname.take(1).uppercase(),
                color = CyanNeon,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (prefix.isNotBlank()) {
                    Text(
                        text = "$prefix:",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
                Text(
                    text = historicalNickname,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (isChanged) {
                Text(
                    text = "Current: $currentNickname",
                    color = CyanNeon,
                    fontSize = 10.sp,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
