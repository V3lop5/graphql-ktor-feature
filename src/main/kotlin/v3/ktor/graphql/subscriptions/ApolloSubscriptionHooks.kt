package v3.ktor.graphql.subscriptions

import com.expediagroup.graphql.execution.GraphQLContext
import io.ktor.http.cio.websocket.*

/**
 * Implementation of Apollo Subscription Server Lifecycle Events
 * https://www.apollographql.com/docs/graphql-subscriptions/lifecycle-events/
 */
interface ApolloSubscriptionHooks {

    /**
     * Allows validation of connectionParams prior to starting the connection.
     * You can reject the connection by throwing an exception.
     * If you need to forward state to execution, update and return the [GraphQLContext].
     */
    suspend fun onConnect(
        connectionParams: Map<String, String>,
        session: WebSocketSession,
        graphQLContext: GraphQLContext
    ) = graphQLContext

    /**
     * Called when the client executes a GraphQL operation.
     * The context can not be updated here, it is read only.
     */
    fun onOperation(
        operationMessage: SubscriptionOperationMessage,
        session: WebSocketSession,
        graphQLContext: GraphQLContext
    ): Unit = Unit

    /**
     * Called when client's unsubscribes
     */
    fun onOperationComplete(session: WebSocketSession): Unit = Unit

    /**
     * Called when the client disconnects
     */
    fun onDisconnect(session: WebSocketSession): Unit = Unit
}

/**
 * Default implementation of Apollo Subscription Lifecycle Events.
 */
open class DefaultApolloSubscriptionHooks : ApolloSubscriptionHooks