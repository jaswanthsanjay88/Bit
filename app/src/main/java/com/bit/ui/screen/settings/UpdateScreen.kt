package com.bit.ui.screen.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bit.BuildConfig
import com.bit.global.Standards
import com.bit.ui.icons.TnIcons
import com.bit.update.UpdateCheckResult
import com.bit.update.UpdateChecker
import com.bit.update.UpdateInfo
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val updateChecker = remember { UpdateChecker(context) }

    var isChecking by remember { mutableStateOf(true) }
    var updateResult by remember { mutableStateOf<UpdateCheckResult?>(null) }

    fun checkUpdate() {
        scope.launch {
            isChecking = true
            updateResult = updateChecker.checkForUpdate()
            isChecking = false
        }
    }

    LaunchedEffect(Unit) {
        checkUpdate()
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("System Update") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = TnIcons.ArrowLeft, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { checkUpdate() }, enabled = !isChecking) {
                        Icon(imageVector = TnIcons.Refresh, contentDescription = "Check for updates")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = Standards.SpacingLg),
            verticalArrangement = Arrangement.spacedBy(Standards.SpacingLg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(modifier = Modifier.height(Standards.SpacingXl))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = TnIcons.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(Standards.SpacingMd))
                    Text(
                        text = "BIT Version ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Build ${BuildConfig.VERSION_CODE}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                if (isChecking) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "Checking for updates...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                } else {
                    when (val result = updateResult) {
                        is UpdateCheckResult.UpdateAvailable -> {
                            UpdateAvailableCard(info = result.info)
                        }
                        is UpdateCheckResult.UpToDate -> {
                            StatusCard(
                                title = "Up to Date",
                                subtitle = "You are running the latest version of BIT.",
                                isSuccess = true
                            )
                        }
                        is UpdateCheckResult.DisabledForStoreInstall -> {
                            StatusCard(
                                title = "Managed by Store",
                                subtitle = "Updates are managed automatically by your app store.",
                                isSuccess = null
                            )
                        }
                        is UpdateCheckResult.Error -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                StatusCard(
                                    title = "Check Failed",
                                    subtitle = result.message,
                                    isSuccess = false
                                )
                                Button(
                                    onClick = { checkUpdate() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Retry")
                                }
                            }
                        }
                        null -> {}
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    title: String,
    subtitle: String,
    isSuccess: Boolean?
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = when (isSuccess) {
                        true -> TnIcons.Check
                        false -> TnIcons.AlertTriangle
                        null -> TnIcons.Package
                    },
                    contentDescription = null,
                    tint = when (isSuccess) {
                        true -> MaterialTheme.colorScheme.primary
                        false -> MaterialTheme.colorScheme.error
                        null -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun UpdateAvailableCard(
    info: UpdateInfo
) {
    val context = LocalContext.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "New Version Available: ${info.version}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (info.title.isNotBlank()) {
                    Text(
                        text = info.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (info.changelog.isNotBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Changelog",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = info.changelog.take(500) + if (info.changelog.length > 500) "..." else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Button(
                onClick = {
                    val url = if (info.downloadUrl.isNotBlank()) info.downloadUrl else info.releasePageUrl
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(intent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Icon(
                    imageVector = TnIcons.Download,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Get Update")
            }
        }
    }
}
