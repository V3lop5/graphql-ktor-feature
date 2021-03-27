package v3.ktor.graphql.subscriptions

import com.expediagroup.graphql.execution.DefaultGraphQLContext
import com.expediagroup.graphql.execution.GraphQLContext
import com.expediagroup.graphql.types.GraphQLRequest
import com.google.gson.Gson
import v3.ktor.graphql.KtorGraphQLContextFactory
import v3.ktor.graphql.subscriptions.SubscriptionOperationMessage.ClientMessages.*
import v3.ktor.graphql.subscriptions.SubscriptionOperationMessage.ServerMessages.*
import io.ktor.http.cio.websocket.*
import io.ktor.request.*
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import org.slf4j.LoggerFactory

/**
 * Implementation of the `graphql-ws` protocol defined by Apollo
 * https://github.com/apollographql/subscriptions-transport-ws/blob/master/PROTOCOL.md
 */
class ApolloSubscriptionProtocolHandler(
    private val contextFactory: KtorGraphQLContextFactory<*>,
    private val subscriptionHandler: KtorGraphQLSubscriptionHandler,
    private val subscriptionHooks: ApolloSubscriptionHooks,
    keepAliveInterval: Long,
    private val gson: Gson
) {

    private val sessionState = ApolloSubscriptionSessionState()
    private val logger = LoggerFactory.getLogger(ApolloSubscriptionProtocolHandler::class.java)
    private val keepAliveMessage = SubscriptionOperationMessage(type = GQL_CONNECTION_KEEP_ALIVE.type)
    private val basicConnectionErrorMessage = SubscriptionOperationMessage(type = GQL_CONNECTION_ERROR.type)
    private val acknowledgeMessage = SubscriptionOperationMessage(GQL_CONNECTION_ACK.type)

    @Suppress("EXPERIMENTAL_API_USAGE")
    private val keepAliveTicker = if (keepAliveInterval > 0) ticker(keepAliveInterval) else null

    @Suppress("Detekt.TooGenericExceptionCaught")
    suspend fun handle(
        payload: String,
        session: WebSocketSession,
        request: ApplicationRequest
    ): Flow<SubscriptionOperationMessage> {
        val operationMessage = convertToMessageOrNull(payload) ?: return flowOf(basicConnectionErrorMessage)
        logger.debug("GraphQL subscription client message, sessionId=${session} operationMessage=$operationMessage")

        return try {
            when (operationMessage.type) {
                GQL_CONNECTION_INIT.type -> onInit(operationMessage, session, request)
                GQL_START.type -> onStart(operationMessage, session)
                GQL_STOP.type -> onStop(operationMessage, session)
                GQL_CONNECTION_TERMINATE.type -> onDisconnect(session)
                else -> onUnknownOperation(operationMessage, session)
            }
        } catch (exception: Exception) {
            onException(exception)
        }
    }

    /**
     * Double check if socket gets closed. This will terminate all active Flows for sure
     */
    suspend fun handleSocketGone(session: WebSocketSession) {
        sessionState.terminateSession(session)
    }

    fun hasContext(session: WebSocketSession) = sessionState.getContext(session) != null


    @Suppress("Detekt.TooGenericExceptionCaught")
    private fun convertToMessageOrNull(payload: String): SubscriptionOperationMessage? {
        return try {
            gson.fromJson(payload, SubscriptionOperationMessage::class.java)
        } catch (exception: Exception) {
            logger.error("Error parsing the subscription message", exception)
            null
        }
    }

    /**
     * If the keep alive configuration is set, send a message back to client at every interval until the session is terminated.
     * Otherwise just return empty flux to append to the acknowledge message.
     */
    private fun getKeepAliveFlow(session: WebSocketSession): Flow<SubscriptionOperationMessage> {
        if (keepAliveTicker != null) {
            return keepAliveTicker.consumeAsFlow().map { keepAliveMessage }
                .onStart { sessionState.saveKeepAliveSubscription(session, currentCoroutineContext().job) }
        }

        return emptyFlow()
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    private suspend fun startSubscription(
        operationMessage: SubscriptionOperationMessage,
        session: WebSocketSession,
        context: GraphQLContext
    ): Flow<SubscriptionOperationMessage> {
        subscriptionHooks.onOperation(operationMessage, session, context)

        if (operationMessage.id == null) {
            logger.error("GraphQL subscription operation id is required")
            return flowOf(basicConnectionErrorMessage)
        }

        if (sessionState.operationExists(session, operationMessage)) {
            logger.info("Already subscribed to operation ${operationMessage.id} for session ${session}")
            return emptyFlow()
        }

        val payload = operationMessage.payload

        if (payload == null) {
            logger.error("GraphQL subscription payload was null instead of a GraphQLRequest object")
            sessionState.stopOperation(session, operationMessage)
            return flowOf(SubscriptionOperationMessage(type = GQL_CONNECTION_ERROR.type, id = operationMessage.id))
        }

        try {
            val request = gson.fromJson(gson.toJsonTree(payload), GraphQLRequest::class.java)
            return subscriptionHandler.executeSubscription(request, context)
                .takeWhile { currentCoroutineContext().isActive }
                .map {
                    if (it.errors?.isNotEmpty() == true) {
                        SubscriptionOperationMessage(type = GQL_ERROR.type, id = operationMessage.id, payload = it)
                    } else {
                        SubscriptionOperationMessage(type = GQL_DATA.type, id = operationMessage.id, payload = it)
                    }
                }
                .onCompletion { emitAll(onComplete(operationMessage, session)) }
                .onStart { sessionState.saveOperation(session, operationMessage, currentCoroutineContext().job) }
        } catch (exception: Exception) {
            logger.error("Error running graphql subscription", exception)
            // Do not terminate the session, just stop the operation messages
            sessionState.stopOperation(session, operationMessage)
            return flowOf(SubscriptionOperationMessage(type = GQL_CONNECTION_ERROR.type, id = operationMessage.id))
        }
    }

    private suspend fun onInit(
        operationMessage: SubscriptionOperationMessage,
        session: WebSocketSession,
        request: ApplicationRequest
    ): Flow<SubscriptionOperationMessage> {
        val connectionParams = getConnectionParams(operationMessage.payload)
        val graphQLContext = contextFactory.generateContext(request) ?: DefaultGraphQLContext()
        val onConnect = subscriptionHooks.onConnect(connectionParams, session, graphQLContext)
        sessionState.saveContext(session, onConnect)
        val acknowledgeMessage = flowOf(acknowledgeMessage)
        val keepAliveFlow = getKeepAliveFlow(session)
        return acknowledgeMessage.onCompletion { emitAll(keepAliveFlow) }
    }

    /**
     * This is the best cast saftey we can get with the generics
     */
    @Suppress("UNCHECKED_CAST")
    private fun getConnectionParams(payload: Any?): Map<String, String> {
        if (payload != null && payload is Map<*, *> && payload.isNotEmpty()) {
            if (payload.keys.first() is String && payload.values.first() is String) {
                return payload as Map<String, String>
            }
        }

        return emptyMap()
    }

    /**
     * Called when the client sends the start message.
     * It triggers the specific hooks first, runs the operation, and appends it with a complete message.
     */
    private suspend fun onStart(
        operationMessage: SubscriptionOperationMessage,
        session: WebSocketSession
    ): Flow<SubscriptionOperationMessage> {
        val context = sessionState.getContext(session)

        // If we do not have a context, that means the init message was never sent
        return if (context != null) {
            startSubscription(operationMessage, session, context)
        } else {
            val message = getConnectionErrorMessage(operationMessage)
            flowOf(message)
        }
    }

    /**
     * Called with the publisher has completed on its own.
     */
    private fun onComplete(
        operationMessage: SubscriptionOperationMessage,
        session: WebSocketSession
    ): Flow<SubscriptionOperationMessage> {
        subscriptionHooks.onOperationComplete(session)
        return sessionState.completeOperation(session, operationMessage)
    }

    /**
     * Called with the client has called stop manually, or on error, and we need to cancel the publisher
     */
    private fun onStop(
        operationMessage: SubscriptionOperationMessage,
        session: WebSocketSession
    ): Flow<SubscriptionOperationMessage> {
        subscriptionHooks.onOperationComplete(session)
        return sessionState.stopOperation(session, operationMessage)
    }

    private suspend fun onDisconnect(session: WebSocketSession): Flow<SubscriptionOperationMessage> {
        subscriptionHooks.onDisconnect(session)
        sessionState.terminateSession(session)
        return emptyFlow()
    }

    private fun onUnknownOperation(
        operationMessage: SubscriptionOperationMessage,
        session: WebSocketSession
    ): Flow<SubscriptionOperationMessage> {
        logger.error("Unknown subscription operation $operationMessage")
        sessionState.stopOperation(session, operationMessage)
        return flowOf(getConnectionErrorMessage(operationMessage))
    }

    private fun onException(exception: Exception): Flow<SubscriptionOperationMessage> {
        logger.error("Error parsing the subscription message", exception)
        return flowOf(basicConnectionErrorMessage)
    }

    private fun getConnectionErrorMessage(operationMessage: SubscriptionOperationMessage): SubscriptionOperationMessage {
        return SubscriptionOperationMessage(type = GQL_CONNECTION_ERROR.type, id = operationMessage.id)
    }
}