package com.kieronquinn.app.classicpowermenu.ui.screens.powermenu.walletcode

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.BrightnessLow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.pow

/**
 *  條碼預覽用的螢幕亮度調整條，仿 Material 3 Expressive 滑桿：
 *  - 44dp 粗軌道、全圓角
 *  - 細長直立把手（按住時變細），把手與兩側軌道之間有間隙
 *  - 軌道內左側小太陽、右側大太陽圖示
 *
 *  @param accentColor   已調整部分 / 把手顏色（使用會員卡底色）
 *  @param onAccentColor 已調整部分上的圖示顏色（依會員卡底色深淺決定黑 / 白）
 *  @param onBrightnessChanged 回傳要套用到視窗的亮度（0.01 ~ 1）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrightnessSlider(
    accentColor: Color,
    onAccentColor: Color,
    onBrightnessChanged: (Float) -> Unit
) {
    // 一開啟預設為最亮（與原本自動調到最亮的行為相同）
    var position by remember { mutableFloatStateOf(1f) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isDragged by interactionSource.collectIsDraggedAsState()
    // M3E：拖曳時把手變細
    val thumbWidth by animateDpAsState(if (isPressed || isDragged) 2.dp else 4.dp, label = "thumbWidth")

    Slider(
        value = position,
        onValueChange = {
            position = it
            onBrightnessChanged(positionToBrightness(it))
        },
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .semantics { contentDescription = "螢幕亮度" },
        thumb = {
            Box(
                Modifier
                    .width(thumbWidth)
                    .height(56.dp)
                    .clip(RoundedCornerShape(50))
                    .background(accentColor)
            )
        },
        track = { sliderState ->
            M3ExpressiveTrack(
                fraction = sliderState.value,
                accentColor = accentColor,
                onAccentColor = onAccentColor
            )
        },
        colors = SliderDefaults.colors(thumbColor = accentColor)
    )
}

@Composable
private fun M3ExpressiveTrack(fraction: Float, accentColor: Color, onAccentColor: Color) {
    val trackHeight = 44.dp
    // 把手與軌道之間的間隙（M3E 規格約 6dp）
    val gap = 6.dp
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(trackHeight)
    ) {
        val totalWidth = maxWidth
        val thumbCenter = totalWidth * fraction
        val activeWidth = (thumbCenter - gap).coerceAtLeast(0.dp)
        val inactiveStart = (thumbCenter + gap).coerceAtMost(totalWidth)
        val inactiveWidth = (totalWidth - inactiveStart).coerceAtLeast(0.dp)

        // 未調整部分（右側）：會員卡底色的淡色版本，外側全圓角、靠把手側小圓角
        if (inactiveWidth > 0.dp) {
            Box(
                Modifier
                    .offset(x = inactiveStart)
                    .width(inactiveWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 22.dp, bottomEnd = 22.dp))
                    .background(accentColor.copy(alpha = 0.35f))
            )
        }
        // 已調整部分（左側）：會員卡底色
        if (activeWidth > 0.dp) {
            Box(
                Modifier
                    .width(activeWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp, topEnd = 4.dp, bottomEnd = 4.dp))
                    .background(accentColor)
            )
        }
        // 左側小太陽：已調整部分夠寬時畫在色塊上，否則畫在淡色軌道上
        Icon(
            Icons.Rounded.BrightnessLow,
            contentDescription = null,
            tint = if (activeWidth > 40.dp) onAccentColor else accentColor,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 12.dp)
                .size(20.dp)
        )
        // 右側大太陽：未調整部分夠寬時才顯示（拉到最亮時被把手蓋住就隱藏）
        if (inactiveWidth > 40.dp) {
            Icon(
                Icons.Rounded.BrightnessHigh,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
                    .size(24.dp)
            )
        }
    }
}

/**
 *  滑桿位置 → 視窗亮度。
 *  人眼對亮度的感受接近非線性，用平方曲線讓拖動時「暗 → 亮」的變化比較平均；
 *  下限 0.01，避免螢幕全黑看不到條碼。
 */
internal fun positionToBrightness(position: Float): Float {
    return max(0.01f, position.coerceIn(0f, 1f).pow(2))
}
