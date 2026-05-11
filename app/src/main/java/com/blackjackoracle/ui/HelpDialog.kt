package com.blackjackoracle.ui

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun HelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How To Play") },
        text = {
            LazyColumn {
                item {
                    Text(
                        "Beat the dealer without going over 21. Blackjack pays 3 to 2. " +
                            "Dealer hits soft 17. Double after split and late surrender are allowed. " +
                            "Oliver explains the best play when you ask for advice.",
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}
