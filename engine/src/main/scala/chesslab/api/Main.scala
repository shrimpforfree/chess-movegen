package chesslab.api

import cats.effect.*
import com.comcast.ip4s.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.CORS

object Main extends IOApp.Simple:

  val run: IO[Unit] =
    val portNum = sys.env.getOrElse("PORT", "8080").toInt
    val serverPort = Port.fromInt(portNum).getOrElse(port"8080")
    val app = CORS.policy.withAllowOriginAll(Routes.chessRoutes).orNotFound

    EmberServerBuilder
      .default[IO]
      .withHost(host"0.0.0.0")
      .withPort(serverPort)
      .withHttpApp(app)
      .build
      .use { server =>
        IO.println(s"Chess engine API running on port $portNum") *>
        IO.never
      }
