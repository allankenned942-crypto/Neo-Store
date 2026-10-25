package com.machiav3lli.fdroid.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.machiav3lli.fdroid.data.database.entity.Repository
import com.machiav3lli.fdroid.data.index.GithubSource

@Composable
fun SourceBadge(label: String, kind: Int = 0, modifier: Modifier = Modifier) {
    val bg = when (kind) {
        1 -> MaterialTheme.colorScheme.tertiaryContainer
        2 -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = when (kind) {
        1 -> MaterialTheme.colorScheme.onTertiaryContainer
        2 -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = bg,
        contentColor = fg,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

fun sourceKind(repo: Repository?): Int = when {
    repo == null -> 0
    repo.address.startsWith("aurora:") -> 1
    GithubSource.isGithub(repo.address) -> 2
    else -> 0
}

fun sourceLabel(repo: Repository?): String {
    if (repo == null) return ""
    val a = repo.address.lowercase()
    return when {
        a.startsWith("aurora:") -> "AURORA"
        GithubSource.isGithub(repo.address) -> "KOMI"
        a.contains("f-droid.org/repo") -> "F-Droid"
        a.contains("f-droid.org/archive") -> "F-Droid Archive"
        a.contains("izzysoft") -> "Izzy"
        a.contains("guardianproject") -> "Guardian"
        else -> repo.name.take(14)
    }
}
