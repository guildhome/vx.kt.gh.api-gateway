package guildhome_reverse_proxy

import io.netty.handler.codec.http.HttpHeaderValues
import io.vertx.core.AbstractVerticle
import io.vertx.core.AsyncResult
import io.vertx.core.Handler
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.auth.User
import io.vertx.ext.auth.jwt.JWTAuth
import io.vertx.ext.auth.mongo.MongoAuth
import io.vertx.ext.mongo.MongoClient
import io.vertx.ext.web.Router
import io.vertx.kotlin.core.json.get
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.ext.auth.mongo.HashAlgorithm
import io.vertx.ext.auth.mongo.HashSaltStyle
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.JWTAuthHandler
import io.vertx.kotlin.core.json.JsonObject
import io.vertx.kotlin.ext.auth.KeyStoreOptions
import io.vertx.kotlin.ext.auth.jwt.JWTAuthOptions
import io.vertx.kotlin.ext.auth.jwt.JWTOptions


class MainVerticle : AbstractVerticle() {

  val log = LoggerFactory.getLogger(MainVerticle::class.java)
  override fun start() {
    // your code goes here...
    var server = vertx.createHttpServer()


    val mongoClientConfig = createMongoConfig()
    var mongoClient = MongoClient.createShared(vertx, mongoClientConfig)
    
    val config = JsonObject()
    config.put(MongoAuth.PROPERTY_COLLECTION_NAME, "user")
    config.put(MongoAuth.PROPERTY_SALT_STYLE, HashSaltStyle.COLUMN)
    
    val mongoAuth = MongoAuth.create(mongoClient, config)
    mongoAuth.hashStrategy.setAlgorithm(HashAlgorithm.PBKDF2)

    var jwtConfig = JWTAuthOptions(
            keyStore = KeyStoreOptions(
                    path = "keystore.jceks",
                    password = "secret"))

    var jwtProvider = JWTAuth.create(vertx, jwtConfig)

    var router = Router.router(vertx)
    router.post().handler(BodyHandler.create())
    router.post("/login").consumes("application/json").produces("application/json").handler({ event ->

      val json: JsonObject = event.bodyAsJson

      val userName: String = json["username"]
      val password: String = json["password"]

      var authInfo = JsonObject(
                "username" to "$userName",
                "password" to "$password"
        )

      mongoAuth.authenticate(authInfo, { res ->
        if (res.succeeded()) {
          var user = res.result()

          var token = jwtProvider.generateToken(
            user.principal()
          , JWTOptions())
          // now for any request to protected resources you should pass this string in the HTTP header Authorization as:
          // Authorization: Bearer <token>
          event.response().setStatusCode(201).end(JsonObject("token" to token).toBuffer())
        } else {
          log.error("${res.cause().message}")
          // Failed!
          event.response().setStatusCode(401).end(res.cause().message)
        }
      })

    })

    router.post("/v1/user").consumes("application/json").handler({
      event ->
      val json = event.bodyAsJson
      val userName: String = json["username"]
      val password: String = json["password"]
      mongoAuth.insertUser(userName, password, listOf("normalUser"), ArrayList<String>(), Handler {
        results ->

        if(results.succeeded())
        {
          event.response().setStatusCode(201).end()
        }
        else
        {
          event.response().setStatusCode(500).end()
        }

      })
    })
    router.get("/v1/hello").handler(JWTAuthHandler.create(jwtProvider))
    router.get("/v1/hello").produces(HttpHeaderValues.TEXT_PLAIN.toString()).handler({ event ->
//      val token = event.request().getHeader("Authorization")
//
//      jwtProvider.authenticate(json {
//            obj("jwt" to "$token")
//
//      }, Handler{it: AsyncResult<User> ->
//
//        if(it.succeeded())
//        {
//          //check permission
//          event.response().setStatusCode(200).end("sucess")
//        }
//        else
//        {
//          event.response().setStatusCode(401).end()
//        }
//      })
      event.user().isAuthorized("normalUser", {it ->
        if(it.succeeded())
        {
          //check permission
          event.response().setStatusCode(200).end("sucess")
        }
        else
        {
          event.response().setStatusCode(401).end()
        }
      })

    })

    router.route().handler({ routingContext ->

    // This handler will be called for every request
    var response = routingContext.response()
    response.putHeader("content-type", "text/plain")

    // Write to the response and end it
    response.end("Hello World from Vert.x-Web!")
})

server.requestHandler({ router.accept(it) }).listen(8080)
  }
}


fun createMongoConfig(): JsonObject = JsonObject("{\n" +
//        "  // Single Cluster Settings\n" +
        "  \"host\" : \"192.168.72.107\"," +
        "  \"port\" : 27017,\n" +
        " \"db_name\":\"test\"" +
//        "\n" +
//        "  // Multiple Cluster Settings\n" +
//        "  \"hosts\" : [\n" +
//        "    {\n" +
//        "      \"host\" : \"cluster1\", // string\n" +
//        "      \"port\" : 27000       // int\n" +
//        "    },\n" +
//        "    {\n" +
//        "      \"host\" : \"cluster2\", // string\n" +
//        "      \"port\" : 28000       // int\n" +
//        "    },\n" +
//        "    ...\n" +
//        "  ],\n" +
//        "  \"replicaSet\" :  \"foo\",    // string\n" +
//        "  \"serverSelectionTimeoutMS\" : 30000, // long\n" +
//        "\n" +
//        "  // Connection Pool Settings\n" +
//        "  \"maxPoolSize\" : 50,                // int\n" +
//        "  \"minPoolSize\" : 25,                // int\n" +
//        "  \"maxIdleTimeMS\" : 300000,          // long\n" +
//        "  \"maxLifeTimeMS\" : 3600000,         // long\n" +
//        "  \"waitQueueMultiple\"  : 10,         // int\n" +
//        "  \"waitQueueTimeoutMS\" : 10000,      // long\n" +
//        "  \"maintenanceFrequencyMS\" : 2000,   // long\n" +
//        "  \"maintenanceInitialDelayMS\" : 500, // long\n" +
//        "\n" +
//        "  // Credentials / Auth\n" +
//        "  \"username\"   : \"john\",     // string\n" +
//        "  \"password\"   : \"passw0rd\", // string\n" +
//        "  \"authSource\" : \"some.db\"   // string\n" +
//        "  // Auth mechanism\n" +
//        "  \"authMechanism\"     : \"GSSAPI\",        // string\n" +
//        "  \"gssapiServiceName\" : \"myservicename\", // string\n" +
//        "\n" +
//        "  // Socket Settings\n" +
//        "  \"connectTimeoutMS\" : 300000, // int\n" +
//        "  \"socketTimeoutMS\"  : 100000, // int\n" +
//        "  \"sendBufferSize\"    : 8192,  // int\n" +
//        "  \"receiveBufferSize\" : 8192,  // int\n" +
//        "  \"keepAlive\" : true           // boolean\n" +
//        "\n" +
//        "  // Heartbeat socket settings\n" +
//        "  \"heartbeat.socket\" : {\n" +
//        "  \"connectTimeoutMS\" : 300000, // int\n" +
//        "  \"socketTimeoutMS\"  : 100000, // int\n" +
//        "  \"sendBufferSize\"    : 8192,  // int\n" +
//        "  \"receiveBufferSize\" : 8192,  // int\n" +
//        "  \"keepAlive\" : true           // boolean\n" +
//        "  }\n" +
//        "\n" +
//        "  // Server Settings\n" +
//        "  \"heartbeatFrequencyMS\" :    1000 // long\n" +
//        "  \"minHeartbeatFrequencyMS\" : 500 // long\n" +
        "}")