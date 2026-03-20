package com.androidclaw.shared.di

import com.androidclaw.db.AndroidClawDb
import com.androidclaw.shared.agent.AgentConfig
import com.androidclaw.shared.agent.AgentLoop
import com.androidclaw.shared.llm.ClaudeStreamingClient
import com.androidclaw.shared.memory.ConversationRepository
import com.androidclaw.shared.memory.ConversationRepositoryImpl
import com.androidclaw.shared.network.createHttpClient
import com.androidclaw.shared.tools.DeviceActionBridge
import com.androidclaw.shared.tools.ToolRegistry
import org.koin.dsl.module

val sharedModule = module {
    single { createHttpClient() }
    single { get<com.androidclaw.shared.db.DatabaseDriverFactory>().createDriver() }
    single { AndroidClawDb(get()) }
    single<ConversationRepository> { ConversationRepositoryImpl(get()) }
    single { ClaudeStreamingClient(get(), getProperty("baseUrl", "http://10.0.2.2:8080")) }
    single {
        ToolRegistry(
            httpClient = get(),
            tavilyApiKey = getProperty("tavilyApiKey", ""),
            deviceBridge = getOrNull()
        )
    }
    single { AgentConfig() }
    factory { AgentLoop(get(), get<ToolRegistry>().getTools(), get(), get()) }
}
