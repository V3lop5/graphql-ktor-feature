package v3.ktor.graphql

import com.expediagroup.graphql.SchemaGeneratorConfig
import com.expediagroup.graphql.TopLevelObject
import com.expediagroup.graphql.server.execution.GraphQLRequestHandler
import com.expediagroup.graphql.server.execution.GraphQLServer
import com.expediagroup.graphql.toSchema
import com.google.gson.Gson
import feature.subscriptions.*
import io.ktor.application.*
import io.ktor.util.*
import v3.ktor.graphql.subscriptions.*


class GraphQL(config: Configuration) {

    private val qlSchema = with(config) { toSchema(schemaGeneratorConfig, queries, mutations, subscriptions) }

    private val qlInstance = graphql.GraphQL.newGraphQL(qlSchema).build()

    val qlServer = with(config) {
        GraphQLServer(
            requestParser,
            contextFactory,
            GraphQLRequestHandler(qlInstance, dataLoaderRegistryFactory)
        )
    }

    private val apolloSubscriptionProtocolHandler = with(config) {
        ApolloSubscriptionProtocolHandler(
            contextFactory,
            KtorGraphQLSubscriptionHandler(qlInstance),
            subscriptionHooks,
            subscriptionKeepAliveInterval,
            gson
        )
    }

    val qlSubscriptionHandler = SubscriptionWebSocketHandler(apolloSubscriptionProtocolHandler, config.gson)


    @Suppress("MemberVisibilityCanBePrivate")
    class Configuration {
        val supportedPackages = mutableListOf<String>()
        var schemaGeneratorConfig = SchemaGeneratorConfig(supportedPackages)

        val queries = mutableListOf<TopLevelObject>()
        val mutations = mutableListOf<TopLevelObject>()
        val subscriptions = mutableListOf<TopLevelObject>()

        var requestParser: KtorGraphQLRequestParser = DefaultKtorGraphQLRequestParser
        var contextFactory: KtorGraphQLContextFactory<*> = DefaultKtorGraphQLContextFactory
        var dataLoaderRegistryFactory: KtorDataLoaderRegistryFactory = DefaultKtorDataLoaderRegistryFactory

        var subscriptionHooks: ApolloSubscriptionHooks = DefaultApolloSubscriptionHooks()
        var subscriptionKeepAliveInterval: Long = 10_000

        var gson = Gson()
    }

    /**
     * Installable feature for [GraphQL].
     */
    companion object Feature : ApplicationFeature<ApplicationCallPipeline, Configuration, GraphQL> {
        override val key = AttributeKey<GraphQL>("GraphQL")

        override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): GraphQL {
            // Call user code to configure a feature
            val config = Configuration().apply(configure)

            // Create & Return a feature instance so that client code can use it
            return GraphQL(config)
        }
    }
}