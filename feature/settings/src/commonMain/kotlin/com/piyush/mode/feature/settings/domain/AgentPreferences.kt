package com.piyush.mode.feature.settings.domain

/** The agent settings as the user entered them. */
public data class AgentPreferences(
    val model: String,
    /** Empty: never step up to a stronger model. */
    val strongModel: String,
    val maxSteps: String,
    val hasApiKey: Boolean,
)
