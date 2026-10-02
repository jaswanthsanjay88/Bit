package com.bit.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.bit.agent.harness.model.ResearchPhase
import com.bit.agent.harness.model.ResearchSourceItem
import com.bit.agent.harness.model.ResearchTrace
import com.bit.ui.icons.TnIcons

/**
 * Collapsible research trace card rendering live and persisted search phases,
 * queries, and visited sources with privacy-safe letter avatars.
 */
@Composable
fun ResearchTracePanel(
    trace: ResearchTrace,
    modifier: Modifier = Modifier,
    initialExpanded: Boolean = false
) {
    if (trace.phases.isEmpty() && !trace.isRunning) return

    var isExpanded by remember { mutableStateOf(initialExpanded) }
    val totalSources = remember(trace) { trace.phases.sumOf { it.sources.size } }

    val durationText = remember(trace.durationMs, trace.isRunning) {
        if (trace.isRunning) {
            "Researching..."
        } else {
            val secs = (trace.durationMs / 1000L).coerceAtLeast(1L)
            "Researched ${secs}s"
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(
            0.8.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            // Header: Researched Ns • N sources + Chevron
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (trace.isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 1.8.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = TnIcons.Search,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(Modifier.width(8.dp))

                Text(
                    text = durationText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (totalSources > 0) {
                    Text(
                        text = " • $totalSources source${if (totalSources != 1) "s" else ""}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.weight(1f))

                Icon(
                    imageVector = if (isExpanded) TnIcons.ChevronDown else TnIcons.ChevronRight,
                    contentDescription = if (isExpanded) "Collapse trace" else "Expand trace",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    trace.phases.filter { it.queries.isNotEmpty() || it.sources.isNotEmpty() || it.activeFetchingUrl != null }.forEach { phase ->
                        ResearchPhaseSection(phase = phase, isGlobalRunning = trace.isRunning)
                    }
                }
            }
        }
    }
}

@Composable
private fun ResearchPhaseSection(phase: ResearchPhase, isGlobalRunning: Boolean) {
    if (phase.queries.isEmpty() && phase.sources.isEmpty() && phase.activeFetchingUrl == null) {
        return
    }
    var showAllSources by remember { mutableStateOf(false) }
    val initialDisplayLimit = 4
    val visibleSources = if (showAllSources) phase.sources else phase.sources.take(initialDisplayLimit)
    val overflowCount = phase.sources.size - initialDisplayLimit

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Phase Title Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
        ) {
            Text(
                text = phase.title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isGlobalRunning && phase.activeFetchingUrl != null) {
                Spacer(Modifier.width(6.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(10.dp),
                    strokeWidth = 1.4.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Queries
        phase.queries.forEach { queryText ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = TnIcons.Search,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = queryText,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Sources
        visibleSources.forEach { source ->
            ResearchSourceRow(source = source)
        }

        // Overflow "+N more" toggle
        if (overflowCount > 0 && !showAllSources) {
            Text(
                text = "+$overflowCount more sources",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { showAllSources = true }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun ResearchSourceRow(source: ResearchSourceItem) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(source.url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {}
            }
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Privacy-safe avatar: real favicon if fetched, otherwise letter avatar
        SourceAvatar(source = source)

        Spacer(Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = source.title.ifBlank { source.domain },
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (source.isHttps) {
                    Icon(
                        imageVector = TnIcons.Lock,
                        contentDescription = "HTTPS",
                        modifier = Modifier.size(9.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.width(3.dp))
                }
                Text(
                    text = source.domain,
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SourceAvatar(source: ResearchSourceItem) {
    val initial = remember(source.domain) {
        source.domain.removePrefix("www.").firstOrNull()?.uppercaseChar()?.toString() ?: "W"
    }
    // ponytail: real favicon loaded only for fetched pages, letter avatar for everything else to prevent IP leak
    if (source.fetched && source.domain.isNotBlank()) {
        val faviconUrl = "https://${source.domain}/favicon.ico"
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = faviconUrl,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
        }
    } else {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}
