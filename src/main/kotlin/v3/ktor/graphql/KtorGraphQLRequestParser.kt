package v3.ktor.graphql

import com.expediagroup.graphql.server.execution.GraphQLRequestParser
import com.expediagroup.graphql.types.GraphQLRequest
import io.ktor.request.*

/**
 * Custom logic for how Ktor parses the incoming [ApplicationRequest] into the [GraphQLRequest]
 */
interface KtorGraphQLRequestParser : GraphQLRequestParser<ApplicationRequest>

/**
 * Default logic for how Ktor parses the incoming [ApplicationRequest] into the [GraphQLRequest]
 */
object DefaultKtorGraphQLRequestParser : KtorGraphQLRequestParser {
    override suspend fun parseRequest(request: ApplicationRequest): GraphQLRequest {
        return request.call.receive()
    }
}