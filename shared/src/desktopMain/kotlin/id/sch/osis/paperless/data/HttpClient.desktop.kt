package id.sch.osis.paperless.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets

actual fun createRealtimeHttpClient(): HttpClient = HttpClient(CIO) {
    install(WebSockets) {
        pingIntervalMillis = 20_000L
    }
}
