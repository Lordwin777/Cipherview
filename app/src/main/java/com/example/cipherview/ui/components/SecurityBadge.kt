package com.example.cipherview.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cipherview.data.model.DocumentStatus
import com.example.cipherview.theme.*

@Composable
fun SecurityBadge(
    status: DocumentStatus,
    modifier: Modifier = Modifier
) {
    val (bgColor, contentColor, icon) = when (status) {
        DocumentStatus.ACTIVE -> Triple(EmeraldDark, EmeraldSecurity, Icons.Default.CheckCircle)
        DocumentStatus.EXPIRED -> Triple(SecurityAmberDark, SecurityAmber, Icons.Default.HourglassBottom)
        DocumentStatus.VIEW_LIMIT_REACHED -> Triple(VaultSurfaceHigh, TextSecondary, Icons.Default.VisibilityOff)
        DocumentStatus.REVOKED -> Triple(SecurityRedDark, SecurityRed, Icons.Default.Block)
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = status.label,
            color = contentColor,
            fontSize = 11.sp,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
