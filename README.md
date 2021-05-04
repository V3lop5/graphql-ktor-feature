# GraphQL Feature for ktor
GraphQL feature for [ktor](https://github.com/ktorio/ktor) using [graphql-kotlin](https://github.com/ExpediaGroup/graphql-kotlin). 
It's similar to [graphql-ktor-spring-server](https://github.com/ExpediaGroup/graphql-kotlin/tree/master/servers/graphql-kotlin-spring-server) for ktor.

## Functions
:heavy_check_mark: Queries

:heavy_check_mark: Mutations

:heavy_check_mark: Subscriptions

:heavy_check_mark: Custom Context

:heavy_check_mark: Custom Endpoint (for graphql and subscriptions)


## How to Use
Gradle: 
```gradle
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/V3lop5/graphql-ktor-feature")
        credentials {
            username = project.findProperty("gpr.username") as String? ?: System.getenv("USERNAME")
            password = project.findProperty("gpr.token") as String? ?: System.getenv("TOKEN")
        }
    }
}
    
dependencies {    
    implementation("v3:graphql-ktor-feature:1.5.1")
}
```

See [GitHub Package Guide](https://docs.github.com/en/packages/guides/package-client-guides-for-github-packages) for more details. If this will be used I can publish to bintray or something else.

Create a basic query with [graphql-kotlin](https://github.com/ExpediaGroup/graphql-kotlin): 
```kotlin
class HelloQueryService : Query {
    fun hello(context: MyContext) = "World, ${context.userId}!"
}
```
For more example take a look at their documentation.

Install the feature in `Application.module()`:
```kotlin
install(GraphQL) {
    supportedPackages += "jetzt.doppelkopf.server" // add packages from your classes needed or returned from graphql endpoints. (otherwise you will recive an error.)

    queries += TopLevelObject(HelloQueryService()) // add query service 

    // mutations += TopLevelObject(...)
    // subscriptions += TopLevelObject(...)

    contextFactory = MyContextFactory // see next codeblock
}

routing {
    graphQL("/graphql")
    graphQLSubscription("/subscriptions")
}
```

If need you can create a custom ContextFactory. The Context can contain any information about a request. (for example user information) 
```kotlin
data class MyContext(val userId: Int, val customHeader: String?) : GraphQLContext

object MyContextFactory : KtorGraphQLContextFactory<MyContext> {

    override fun generateContext(request: ApplicationRequest): MyContext {

        val userId = 42 // Parse Request to user
        
        // Parse any headers from the Ktor request
        val customHeader: String? = request.headers["my-custom-header"]

        return MyContext(userId, customHeader) // return custom context 
    }
}
```

