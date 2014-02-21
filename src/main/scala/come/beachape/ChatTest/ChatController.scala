package come.beachape.ChatTest

import org.scalatra._
import scalate.ScalateSupport
import org.scalatra.atmosphere._
import org.scalatra.json.{JValueResult, JacksonJsonSupport}
import org.json4s._
import JsonDSL._
import java.util.Date
import org.atmosphere.cpr.RedisScalatraBroadcasterConfig

import scala.concurrent._
import ExecutionContext.Implicits.global
import java.net.URI

class ChatController extends ScalatraServlet
  with ScalateSupport
  with JValueResult
  with JacksonJsonSupport
  with SessionSupport
  with AtmosphereSupport {

  implicit val jsonFormats = DefaultFormats

  override val broadcasterConfig = RedisScalatraBroadcasterConfig(URI.create("redis://127.0.0.1:6379"), Some("foobared"))

  get("/") {
    contentType="text/html"
    ssp("index")
  }

  atmosphere("/the-chat") {
    new AtmosphereClient {
      def receive = {
        case Connected => {
          println("Client %s is connected" format uuid)
          broadcast(("author" -> "Someone") ~ ("message" -> "joined the room") ~ ("time" -> (new Date().getTime.toString )), Everyone)
        }
        case Disconnected(ClientDisconnected, _) => {
          broadcast(("author" -> "Someone") ~ ("message" -> "has left the room") ~ ("time" -> (new Date().getTime.toString )), Everyone)
        }
        case Disconnected(ServerDisconnected, _) =>
          println("Server disconnected the client %s" format uuid)
        case _: TextMessage =>
          send(("author" -> "system") ~ ("message" -> "Only json is allowed") ~ ("time" -> (new Date().getTime.toString )))
        case JsonMessage(json) => {
          println("Got message %s from %s".format((json \ "message").extract[String], (json \ "author").extract[String]))
          val msg = json merge (("time" -> (new Date().getTime().toString)): JValue)
          broadcast(msg) // by default a broadcast is to everyone but self
        }
      }
    }
  }



  error {
    case t: Throwable => t.printStackTrace()
  }

  notFound {
    // remove content type in case it was set through an action
    contentType = null
    // Try to render a ScalateTemplate if no route matched
    findTemplate(requestPath) map { path =>
      contentType = "text/html"
      layoutTemplate(path)
    } orElse serveStaticResource() getOrElse resourceNotFound()
  }

}
