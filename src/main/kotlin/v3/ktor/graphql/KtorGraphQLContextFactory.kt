package v3.ktor.graphql

import com.expediagroup.graphql.execution.GraphQLContext
import com.expediagroup.graphql.server.execution.GraphQLContextFactory
import com.expediagroup.graphql.types.GraphQLRequest
import io.ktor.application.*
import io.ktor.request.*

/**
 * Custom logic for how Ktor parses the incoming [ApplicationRequest] into the [GraphQLContext]
 */
interface KtorGraphQLContextFactory<T: GraphQLContext> : GraphQLContextFactory<T, ApplicationRequest>

/**
 * Default Context. Used in feature configuration if no other Context is provided.
 * It holds the complete call
 */
data class DefaultContext(val call: ApplicationCall) : GraphQLContext

/**
 * Default Context Factory. Used in feature configuration if no other ContextFactory is provided
 */
object DefaultKtorGraphQLContextFactory : KtorGraphQLContextFactory<DefaultContext> {
    override fun generateContext(request: ApplicationRequest): DefaultContext {
        return DefaultContext(request.call)
    }
}
