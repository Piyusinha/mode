package com.piyush.mode.feature.compare.domain

/** One task run with one model. */
public data class CompareRow(
    val model: String,
    val task: String,
    /** Starts with ✅ when the task was done. */
    val outcome: String,
    val steps: Int,
    val seconds: Long,
    val costUsd: Double,
    val costReported: Boolean = true,
)

public data class CompareState(val running: Boolean = false, val rows: List<CompareRow> = emptyList())
