package v3.ktor.graphql.subscriptions

import com.expediagroup.graphql.execution.GraphQLContext
import io.ktor.http.cio.websocket.*
import v3.ktor.graphql.subscriptions.SubscriptionOperationMessage.ServerMessages.GQL_COMPLETE
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onCompletion
import java.util.concurrent.ConcurrentHashMap

internal class ApolloSubscriptionSessionState {

    // Sessions are saved by web socket session id
    internal val activeKeepAliveSessions =
        ConcurrentHashMap<WebSocketSession, Job>()

    // Operations are saved by web socket session id, then operation id
    internal val activeOperations =
        ConcurrentHashMap<WebSocketSession, ConcurrentHashMap<String, Job>>()

    // OnConnect hooks are saved by web socket session id, then operation id
    private val onConnectHooks = ConcurrentHashMap<WebSocketSession, GraphQLContext>()

    /**
     * Save the context created from the factory and possibly updated in the onConnect hook.
     * This allows us to include some intial state to be used when handling all the messages.
     * This will be removed in [terminateSession].
     */
    fun saveContext(session: WebSocketSession, onConnect: GraphQLContext) {
        onConnectHooks[session] = onConnect
    }

    /**
     * Return the onConnect mono so that the operation can wait to start until it has been resolved.
     */
    fun getContext(session: WebSocketSession): GraphQLContext? = onConnectHooks[session]

    /**
     * Save the session that is sending keep alive messages.
     * This will override values without cancelling the subscription so it is the responsibility of the consumer to cancel.
     * These messages will be stopped on [terminateSession].
     */
    fun saveKeepAliveSubscription(
        session: WebSocketSession,
        collector: Job
    ) {
        activeKeepAliveSessions[session] = collector
    }

    /**
     * Save the operation that is sending data to the client.
     * This will override values without cancelling the subscription so it is the responsibility of the consumer to cancel.
     * These messages will be stopped on [stopOperation].
     */
    fun saveOperation(
        session: WebSocketSession,
        operationMessage: SubscriptionOperationMessage,
        subscription: Job
    ) {
        val id = operationMessage.id
        if (id != null) {
            val operationsForSession: ConcurrentHashMap<String, Job> =
                activeOperations.getOrPut(session) { ConcurrentHashMap() }
            operationsForSession[id] = subscription
        }
    }

    /**
     * Send the [GQL_COMPLETE] message.
     * This can happen when the publisher finishes or if the client manually sends the stop message.
     */
    fun completeOperation(
        session: WebSocketSession,
        operationMessage: SubscriptionOperationMessage
    ): Flow<SubscriptionOperationMessage> {
        return getCompleteMessage(operationMessage)
            .onCompletion { removeActiveOperation(session, operationMessage.id, cancelSubscription = false) }
    }

    /**
     * Stop the subscription sending data and send the [GQL_COMPLETE] message.
     * Does NOT terminate the session.
     */
    fun stopOperation(
        session: WebSocketSession,
        operationMessage: SubscriptionOperationMessage
    ): Flow<SubscriptionOperationMessage> {
        return getCompleteMessage(operationMessage)
            .onCompletion { removeActiveOperation(session, operationMessage.id, cancelSubscription = true) }
    }

    private fun getCompleteMessage(operationMessage: SubscriptionOperationMessage): Flow<SubscriptionOperationMessage> {
        return flowOf(SubscriptionOperationMessage(type = GQL_COMPLETE.type, id = operationMessage.id))
    }

    /**
     * Remove active running subscription from the cache and cancel if needed
     */
    private fun removeActiveOperation(session: WebSocketSession, id: String?, cancelSubscription: Boolean) {
        val operationsForSession = activeOperations[session]
        val subscription = operationsForSession?.get(id)
        if (subscription != null) {
            if (cancelSubscription) {
                subscription.cancel("removeActiveOperation")
            }
            operationsForSession.remove(id)
            if (operationsForSession.isEmpty()) {
                activeOperations.remove(session)
            }
        }
    }

    /**
     * Terminate the session, cancelling the keep alive messages and all operations active for this session.
     */
    suspend fun terminateSession(session: WebSocketSession) {
        activeOperations[session]?.forEach { (_, subscription) ->
            subscription.cancel("Websocket session terminated")
        }
        activeOperations.remove(session)
        onConnectHooks.remove(session)
        activeKeepAliveSessions[session]?.cancel("Websocket session terminated")
        activeKeepAliveSessions.remove(session)

        // cancel incoming, flush outgoing and close session
        session.incoming.cancel()
        session.close()
    }

    /**
     * Looks up the operation for the client, to check if it already exists
     */
    fun operationExists(session: WebSocketSession, operationMessage: SubscriptionOperationMessage): Boolean =
        activeOperations[session]?.containsKey(operationMessage.id) ?: false

}