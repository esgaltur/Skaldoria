package com.skaldoria.ui.layouts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skaldoria.core.models.Slide
import com.skaldoria.core.models.SlideElement
import com.skaldoria.remote.RemoteCompanionServer
import com.skaldoria.theme.PresentationTheme

@Composable
fun PollSlide(
    slide: Slide,
    theme: PresentationTheme,
    votes: Map<Int, Int> = emptyMap(),
    onVote: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val pollElement = slide.elements.filterIsInstance<SlideElement.Poll>().firstOrNull()
    val options = pollElement?.options ?: listOf("Option A", "Option B", "Option C")
    val question = pollElement?.question?.ifBlank { slide.title } ?: slide.title

    val totalVotes = votes.values.sum().coerceAtLeast(0)

    val barColors = listOf(
        Pair(Color(0xFF38BDF8), Color(0xFF0284C7)), // Cyan/Blue
        Pair(Color(0xFF818CF8), Color(0xFF4F46E5)), // Indigo/Purple
        Pair(Color(0xFF34D399), Color(0xFF059669)), // Emerald/Green
        Pair(Color(0xFFF472B6), Color(0xFFDB2777)), // Pink
        Pair(Color(0xFFFBBF24), Color(0xFFD97706)), // Amber
        Pair(Color(0xFFA78BFA), Color(0xFF7C3AED))  // Violet
    )

    val serverUrl = "http://${RemoteCompanionServer.getLocalIpAddress()}:${RemoteCompanionServer.currentPort}/audience"

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Header
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(theme.primary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.BarChart, null, tint = theme.primary, modifier = Modifier.size(20.dp))
                }
                Text(
                    text = "LIVE AUDIENCE POLL",
                    color = theme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.5.sp
                )
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = question,
                color = theme.textPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.SansSerif,
                lineHeight = 40.sp
            )
        }

        // Animated Voting Bars
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            options.forEachIndexed { index, optionText ->
                val voteCount = votes[index] ?: 0
                val percentage = if (totalVotes > 0) (voteCount.toFloat() / totalVotes.toFloat()) else 0f
                val animatedProgress by animateFloatAsState(
                    targetValue = percentage,
                    animationSpec = tween(durationMillis = 500)
                )

                val (gradientStart, gradientEnd) = barColors[index % barColors.size]
                val optionLetter = ('A' + index).toString()

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.surface)
                        .border(1.dp, theme.cardBorder, RoundedCornerShape(12.dp))
                        .clickable { onVote?.invoke(index) }
                ) {
                    // Background Fill Bar
                    if (animatedProgress > 0.005f) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(animatedProgress)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(gradientStart.copy(alpha = 0.25f), gradientEnd.copy(alpha = 0.45f))
                                    )
                                )
                        )
                    }

                    // Content Row
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(gradientStart.copy(alpha = 0.2f))
                                    .border(1.dp, gradientStart.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = optionLetter,
                                    color = gradientStart,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }

                            Text(
                                text = optionText,
                                color = theme.textPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "$voteCount votes",
                                color = theme.textMuted,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "${(percentage * 100).toInt()}%",
                                color = gradientStart,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // Live Joining Banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(theme.surfaceVariant.copy(alpha = 0.6f))
                .border(1.dp, theme.cardBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.PhoneAndroid, null, tint = theme.primary, modifier = Modifier.size(16.dp))
                Text(
                    text = "Vote from phone:",
                    color = theme.textMuted,
                    fontSize = 13.sp
                )
                Text(
                    text = serverUrl,
                    color = theme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Text(
                text = "Total Votes: $totalVotes",
                color = theme.accent,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
