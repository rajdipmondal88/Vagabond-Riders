package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.utils.CustomLogoManager

@Composable
fun VagabondLogoBadge(
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
    showRegistrationText: Boolean = true
) {
    val customLogoBitmap by CustomLogoManager.customLogoBitmap.collectAsState()

    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = Color(0x33000000),
                spotColor = Color(0x33000000)
            )
            .clip(RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (customLogoBitmap != null) {
            Image(
                painter = BitmapPainter(customLogoBitmap!!),
                contentDescription = "Vagabond Riders Official Logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(size)
            )
        } else {
            Image(
                painter = painterResource(id = R.drawable.ic_vr_logo_official),
                contentDescription = "Vagabond Riders Official Logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(size)
            )
        }
    }
}


