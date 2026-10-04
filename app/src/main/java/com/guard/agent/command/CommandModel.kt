package com.guard.agent.command

data class CommandModel(
    val cmd: String = "",
    val status: String = "pending",
    val sentAt: Long = 0L,
    val executedAt: Long = 0L,
    val extra: String? = null
)

data class CommandResult(
    val success: Boolean,
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
