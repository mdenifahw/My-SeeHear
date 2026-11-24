package id.sch.osis.paperless.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets

actual fun createRealtimeHttpClient(): HttpClient = HttpClient(OkHttp) {
    install(WebSockets) {
        pingIntervalMillis = 20_000L
    }
}
