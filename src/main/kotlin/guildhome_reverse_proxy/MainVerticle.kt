package guildhome_reverse_proxy

import io.vertx.core.AbstractVerticle
import io.vertx.ext.web.Router

class MainVerticle : AbstractVerticle() {

  override fun start() {
    // your code goes here...
    var server = vertx.createHttpServer()

    var router = Router.router(vertx)

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
