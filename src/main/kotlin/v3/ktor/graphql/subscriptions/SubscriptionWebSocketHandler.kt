package v3.ktor.graphql.subscriptions

import com.google.gson.Gson
import io.ktor.http.cio.websocket.*
import io.ktor.request.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map

/**
 * Default WebSocket handler for handling GraphQL subscriptions.
 */
class SubscriptionWebSocketHandler(
    private val apolloSubscriptionProtocolHandler: ApolloSubscriptionProtocolHandler,
    private val gson: Gson
) {

    companion object {
        const val protocol = "graphql-ws"
    }

    suspend fun handle(session: WebSocketSession, request: ApplicationRequest) {
        val jobs = mutableListOf<Job>()
        // Reads incoming frames until socket gets closed
        coroutineScope {

            session.incoming.consumeEach { frame ->
                if (frame is Frame.Text) {
                    jobs.removeIf { it.isCancelled }

                    // If context is not loaded, await all previous jobs (prevents race conflicts)
                    if (!apolloSubscriptionProtocolHandler.hasContext(session))
                        jobs.forEach { it.join() }

                    jobs += launch {
                        apolloSubscriptionProtocolHandler.handle(frame.readText(), session, request)
                            .map { gson.toJson(it) }
                            .map { Frame.Text(it) }
                            .collect { session.outgoing.send(it) }
                    }
                } else if (frame is Frame.Close)
                    apolloSubscriptionProtocolHandler.handleSocketGone(session)

            }
        }

        // incoming.consumeEach was closed - so close complete session (if not already done)
        apolloSubscriptionProtocolHandler.handleSocketGone(session)
        jobs.removeIf { it.isCancelled }
        jobs.forEach { it.cancel("handle methode completed") }
    }

}