package sttp.client.httpclient.monix

import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import sttp.client._
import sttp.client.httpclient.WebSocketHandler
import sttp.client.impl.monix.{MonixStreams, TaskMonadAsyncError, convertMonixTaskToFuture}
import sttp.client.monad.MonadError
import sttp.client.testing.ConvertToFuture
import sttp.client.testing.websocket.WebSocketTest
import sttp.client.ws.WebSocket
import sttp.client.testing.HttpTest.wsEndpoint

import scala.concurrent.duration._

class HttpClientHighLevelMonixWebsocketTest extends WebSocketTest[Task, WebSocketHandler] {
  implicit val backend: SttpBackend[Task, MonixStreams, WebSocketHandler] =
    HttpClientMonixBackend().runSyncUnsafe()
  implicit val convertToFuture: ConvertToFuture[Task] = convertMonixTaskToFuture
  implicit val monad: MonadError[Task] = TaskMonadAsyncError

  override def createHandler: Option[Int] => Task[WebSocketHandler[WebSocket[Task]]] = _ => MonixWebSocketHandler()

  it should "handle backpressure correctly" in {
    basicRequest
      .get(uri"$wsEndpoint/ws/echo")
      .openWebsocketF(createHandler(None))
      .flatMap { response =>
        val ws = response.result
        send(ws, 1000) >> eventually(10.millis, 500) { ws.isOpen.map(_ shouldBe true) }
      }
      .toFuture()
  }

  override def eventually[T](interval: FiniteDuration, attempts: Int)(f: => Task[T]): Task[T] = {
    (Task.sleep(interval) >> f).onErrorRestart(attempts.toLong)
  }
}
