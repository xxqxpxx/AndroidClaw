package com.androidclaw.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.androidclaw.db.AndroidClawDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

data class UsageStats(
    val totalConversations: Long = 0,
    val totalMessages: Long = 0,
    val userMessages: Long = 0,
    val assistantMessages: Long = 0,
    val totalTokens: Long = 0,
    val toolUsage: List<Pair<String, Long>> = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageStatsScreen(onBack: () -> Unit) {
    val db = koinInject<AndroidClawDb>()
    var stats by remember { mutableStateOf(UsageStats()) }

    LaunchedEffect(Unit) {
        stats = withContext(Dispatchers.Default) {
            val convCount = db.conversationQueries.getConversationCount().executeAsOne()
            val msgCount = db.messageQueries.getTotalMessageCount().executeAsOne()
            val roleCounts = db.messageQueries.getMessageCountByRole().executeAsList()
            val tokenCount = db.messageQueries.getTotalTokenCount().executeAsOne()
            val toolCounts = db.messageQueries.getToolUsageCounts().executeAsList()

            UsageStats(
                totalConversations = convCount,
                totalMessages = msgCount,
                userMessages = roleCounts.find { it.role == "user" }?.count ?: 0,
                assistantMessages = roleCounts.find { it.role == "assistant" }?.count ?: 0,
                totalTokens = tokenCount,
                toolUsage = toolCounts.map { it.tool_name!! to it.count }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Usage Stats") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SectionHeader("Overview") }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard("Conversations", stats.totalConversations.toString(), Modifier.weight(1f))
                    StatCard("Messages", stats.totalMessages.toString(), Modifier.weight(1f))
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard("Your Messages", stats.userMessages.toString(), Modifier.weight(1f))
                    StatCard("AI Responses", stats.assistantMessages.toString(), Modifier.weight(1f))
                }
            }

            item {
                StatCard(
                    "Estimated Tokens Used",
                    formatNumber(stats.totalTokens),
                    Modifier.fillMaxWidth()
                )
            }

            if (stats.toolUsage.isNotEmpty()) {
                item { SectionHeader("Tool Usage") }

                stats.toolUsage.forEach { (toolName, count) ->
                    item(key = "tool_$toolName") {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    formatToolName(toolName),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "$count uses",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

private fun formatNumber(n: Long): String = when {
    n >= 1_000_000 -> "${n / 1_000_000}.${(n % 1_000_000) / 100_000}M"
    n >= 1_000 -> "${n / 1_000}.${(n % 1_000) / 100}K"
    else -> n.toString()
}

private fun formatToolName(name: String): String = name
    .replace("_", " ")
    .split(" ")
    .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
