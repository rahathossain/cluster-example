package example.cluster.pubsub

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Timers}
import akka.cluster.pubsub.{DistributedPubSub, DistributedPubSubMediator}
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.duration._

object Main extends App {

  val system1 = startNoode(2651)
  val system2  = startNoode(2652)

  system1.actorOf(Props[Hello], name="Hello")
  system2.actorOf(Props[Hi], name="Hi")

  private def startNoode(port: Int) = ActorSystem("ClusterSystem", config(port, "worker"))

  private def config(port: Int, role: String): Config =
    ConfigFactory.parseString(s"""
      akka.remote.artery.canonical.port=$port
      akka.cluster.roles=[$role]
    """).withFallback(ConfigFactory.load())

}

// # Hello - actor
class Hello extends Actor with ActorLogging with Timers {

  val mediator: ActorRef = DistributedPubSub(context.system).mediator

  mediator ! DistributedPubSubMediator.Subscribe("inTopic", self)

  override def preStart(): Unit = {
    timers.startSingleTimer("tick", Tick, 5.seconds)
  }
  override def receive: Receive = {
    case _: DistributedPubSubMediator.SubscribeAck =>
    case Tick =>
      log.info(s"\n\n HELLO actor >>> ${context.self.path}")

    case Work(w) =>
      log.info(s"\n\n HELLO actor >>> ${Work(w)}")
      mediator ! DistributedPubSubMediator.Publish("outTopic", Result("Done!"))
    case _ =>
  }
}


// Hi - Actor
class Hi extends Actor with ActorLogging with Timers {

  val mediator: ActorRef = DistributedPubSub(context.system).mediator

  mediator ! DistributedPubSubMediator.Subscribe("outTopic", self)


  override def preStart(): Unit = {
    timers.startSingleTimer("tick", Tick, 15.seconds)
  }

  override def receive: Receive = {
    case Tick =>
      log.info(s"\n\n Hi actor >>> ${context.self.path}")
      mediator ! DistributedPubSubMediator.Publish("inTopic", Work("do-it"))

    case Result(r) =>
      log.info(s"\n\n *** Hi Actor >> ${Result(r)}")

  }
}

// # Protocols
case class Work(message: String)
case class Result(response: String)
case object Tick
