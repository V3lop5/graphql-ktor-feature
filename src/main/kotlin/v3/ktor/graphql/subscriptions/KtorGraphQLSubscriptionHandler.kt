package v3.ktor.graphql.subscriptions

import com.expediagroup.graphql.execution.GraphQLContext
import com.expediagroup.graphql.server.exception.KotlinGraphQLError
import com.expediagroup.graphql.server.extensions.toExecutionInput
import com.expediagroup.graphql.server.extensions.toGraphQLKotlinType
import com.expediagroup.graphql.server.extensions.toGraphQLResponse
import com.expediagroup.graphql.types.GraphQLRequest
import com.expediagroup.graphql.types.GraphQLResponse
import graphql.ExecutionResult
import graphql.GraphQL
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactive.asFlow
import org.reactivestreams.Publisher

/**
 * Default KTor implementation of GraphQL subscription handler.
 */
open class KtorGraphQLSubscriptionHandler(private val graphQL: GraphQL) {

    fun executeSubscription(graphQLRequest: GraphQLRequest, graphQLContext: GraphQLContext?): Flow<GraphQLResponse<*>> {
        val executionResult = graphQL.execute(graphQLRequest.toExecutionInput(graphQLContext))

        if (!executionResult.isDataPresent)
            return flowOf(GraphQLResponse<Any>(errors = executionResult.errors.map { it.toGraphQLKotlinType() }))

        return executionResult.getData<Publisher<ExecutionResult>>()
            .asFlow()
            .map { result -> result.toGraphQLResponse() }
            .catch { throwable ->
                val error = KotlinGraphQLError(throwable).toGraphQLKotlinType()
                emit(GraphQLResponse<Any>(errors = listOf(error)))
            }
    }
}