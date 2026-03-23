package com.androidclaw.shared.di

import com.androidclaw.db.AndroidClawDb
import com.androidclaw.shared.agent.AgentConfig
import com.androidclaw.shared.agent.AgentLoop
import com.androidclaw.shared.llm.ClaudeStreamingClient
import com.androidclaw.shared.llm.LocalLlmStreamingClient
import com.androidclaw.shared.memory.ConversationRepository
import com.androidclaw.shared.memory.ConversationRepositoryImpl
import com.androidclaw.shared.network.createHttpClient
import com.androidclaw.shared.network.createStreamingHttpClient
import com.androidclaw.shared.tools.DeviceActionBridge
import com.androidclaw.shared.tools.ToolRegistry
import org.koin.core.qualifier.named
import org.koin.dsl.module

val sharedModule = module {
    single { createHttpClient() }
    single(named("streaming")) { createStreamingHttpClient() }
    single { get<com.androidclaw.shared.db.DatabaseDriverFactory>().createDriver() }
    single { AndroidClawDb(get()) }
    single<ConversationRepository> { ConversationRepositoryImpl(get()) }
    single { ClaudeStreamingClient(get(named("streaming")), getProperty("baseUrl", "http://10.0.2.2:8080")) }
    single {
        ToolRegistry(
            httpClient = get(),
            tavilyApiKey = getProperty("tavilyApiKey", ""),
            deviceBridge = getOrNull()
        )
    }
    single { AgentConfig() }
    single { LocalLlmStreamingClient(get(named("streaming"))) }
    factory { AgentLoop(get(), get<ToolRegistry>().getTools(), get(), get(), localLlmClient = get()) }
}
