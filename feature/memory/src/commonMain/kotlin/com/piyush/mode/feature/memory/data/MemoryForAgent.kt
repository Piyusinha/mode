package com.piyush.mode.feature.memory.data

import com.piyush.mode.agent.AgentMemory

/** Lets the agent look things up in memory mid-task, and get an app's tips when it opens it. */
public class MemoryForAgent(private val retrieval: MemoryRetrieval) : AgentMemory {
    override fun recall(query: String): List<String> = retrieval.recall(query)

    override fun recall(query: String, apps: List<String>): List<String> = retrieval.recall(query, apps = apps)

    override fun tipsFor(app: String): List<String> = retrieval.tipsFor(app)
}
