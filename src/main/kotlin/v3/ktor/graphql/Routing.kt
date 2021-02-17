package v3.ktor.graphql

import v3.ktor.graphql.subscriptions.SubscriptionWebSocketHandler
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.websocket.*

fun Route.graphQL(path: String) {
    val graphQL = application.feature(GraphQL)

    post(path) {
        val result = graphQL.qlServer.execute(call.request)

        if (result != null) {
            call.respond(result) // graphQL executed successfully
        } else {
            call.respond(HttpStatusCode.BadRequest, "Invalid request")
        }
    }
}

fun Route.graphQLSubscription(path: String) {
    val graphQL = application.feature(GraphQL)

    webSocketRaw(path, SubscriptionWebSocketHandler.protocol) {
        graphQL.qlSubscriptionHandler.handle(this, call.request)
    }
}