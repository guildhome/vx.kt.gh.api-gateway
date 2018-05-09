package com.mtt.guildhome.api.gateway

import io.netty.handler.codec.http.HttpHeaderValues
import io.vertx.core.AbstractVerticle
import io.vertx.core.AsyncResult
import io.vertx.core.Handler
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.auth.jwt.JWTAuth
import io.vertx.ext.auth.mongo.MongoAuth
import io.vertx.ext.mongo.MongoClient
import io.vertx.ext.web.Router
import io.vertx.kotlin.core.json.get
import io.vertx.ext.auth.mongo.HashAlgorithm
import io.vertx.ext.auth.mongo.HashSaltStyle
import io.vertx.ext.web.client.HttpResponse
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.JWTAuthHandler
import io.vertx.kotlin.core.json.JsonObject
import io.vertx.kotlin.ext.auth.KeyStoreOptions
import io.vertx.kotlin.ext.auth.jwt.JWTAuthOptions
import io.vertx.kotlin.ext.auth.jwt.JWTOptions
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.client.WebClient


class MainVerticle : AbstractVerticle() {

    val log = LoggerFactory.getLogger(MainVerticle::class.java)
    override fun start() {
        // your code goes here...
        var server = vertx.createHttpServer()
        var userServiceClient = WebClient.create(vertx)

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
        router.route().handler(CorsHandler.create("*"))
        router.post().handler(BodyHandler.create())
        router.post("/v1/login").consumes("application/json").produces("application/json").handler(Handler { event ->

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
                    log.info("Found Mongo Auth User")
                    userServiceClient.get(8081, "localhost", "/v1/user")
                            .addQueryParam("username", userName)
                            .send(Handler { it ->
                                log.info("Got Response from the user Service")
                                if (it.succeeded()) {
                                    val response = it.result()

                                    if (response.statusCode() == 200) {

                                        //normally I would parse this out but I know I just need to add the token to it and return it for now

//                                 val userProfile = parseUserProfileFromJson(response.bodyAsJsonObject())
                                        var userProfileAsJson = response.bodyAsJsonObject()
                                        var token = jwtProvider.generateToken(
                                                user.principal()
                                                , JWTOptions())
                                        // now for any request to protected resources you should pass this string in the HTTP header Authorization as:
                                        // Authorization: Bearer <token>
                                        event.response().setStatusCode(200).end(userProfileAsJson.put("token", token).toBuffer())
                                    } else {
                                        //otherwise more than likely they the caller fucked up
                                        event.response().setStatusCode(400).end(JsonObject("msg" to response.body()).toBuffer())
                                    }
                                } else {
                                    //something is messed up on our end so bubble up.
                                    event.response().setStatusCode(500).end(JsonObject("error" to it.cause().toString()).toBuffer())

                                }
                            })
                } else {
                    log.error("${res.cause().message}")
                    // Failed!
                    event.response().setStatusCode(401).end(res.cause().message)
                }
            })

        })

        router.post("/v1/user").consumes("application/json").handler({ event ->
            val json = event.bodyAsJson
            val userName: String = json["username"]
            val handle: String = json["handle"]
            val password: String = json["password"]

            val newUserProfile: JsonObject = UserProfile("", userName, handle, userName, ArrayList()).toJson()

            //remove blank id
            newUserProfile.remove("id")

            mongoAuth.insertUser(userName, password, listOf("normalUser"), ArrayList<String>(), Handler { results ->

                if (results.succeeded()) {
                    userServiceClient.post(8081, "localhost", "/v1/user")
                            .sendJsonObject(newUserProfile, Handler { it: AsyncResult<HttpResponse<Buffer>> ->

                                if (it.succeeded()) {
                                    event.response().setStatusCode(201).end()
                                } else {
                                    //we need roll back the mongo update.... Or delete it directly.
                                    //It might just be easier to do this in the opposite order.
                                    event.response().setStatusCode(500).end()
                                }
                            })
                } else {
                    event.response().setStatusCode(500).end(results.cause().toString())
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
            event.user().isAuthorized("normalUser", { it ->
                if (it.succeeded()) {
                    //check permission
                    event.response().setStatusCode(200).end("sucess")
                } else {
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