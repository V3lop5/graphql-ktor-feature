package v3.ktor.graphql

import com.expediagroup.graphql.execution.GraphQLContext
import com.expediagroup.graphql.server.execution.DataLoaderRegistryFactory
import io.ktor.request.*
import org.dataloader.DataLoaderRegistry

/**
 * Custom logic for how Ktor handles data loadings.. I guess
 */
interface KtorDataLoaderRegistryFactory : DataLoaderRegistryFactory

/**
 * Default implementation for how Ktor handles data loadings.. I guess
 */
object DefaultKtorDataLoaderRegistryFactory : KtorDataLoaderRegistryFactory {
    override fun generate(): DataLoaderRegistry {
        return DataLoaderRegistry()
    }
}