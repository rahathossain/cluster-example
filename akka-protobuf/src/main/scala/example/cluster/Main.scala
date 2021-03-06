package example.cluster

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Timers}
import akka.cluster.pubsub.{DistributedPubSub, DistributedPubSubMediator}
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.duration._

object Main extends App {

  val system1 = startNoode(2551)
  val system2  = startNoode(2552)

  system1.actorOf(Props[Hello], name="Hello")
  system2.actorOf(Props[Hi], name="Hi")

  private def startNoode(port: Int) = ActorSystem("ClusterSystem", config(port, "worker"))

  private def config(port: Int, role: String): Config =
    ConfigFactory.parseString(s"""
      akka.remote.netty.tcp.port=$port
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
      val ref = sender()
      log.info(s"\n\n ACTOR::HELLO >>> Tick from >>> ${ref.path}\n")

    case Work(w) =>
      val ref = sender()
      log.info(s"\n\n ACTOR::HELLO >>> ${Work(w)} from >>> ${ref.path}\n")
      ref !  Result("Done!")
      ref ! Tick(remote = true)
    case _ =>
  }
}


// Hi - Actor
class Hi extends Actor with ActorLogging with Timers {

  val mediator: ActorRef = DistributedPubSub(context.system).mediator
  mediator ! DistributedPubSubMediator.Subscribe("outTopic", self)


  override def preStart(): Unit = {
    timers.startSingleTimer("tick", Tick, 8.seconds)
  }

  override def receive: Receive = {
    case Tick =>
      val ref = sender()
      log.info(s"\n\n ACTOR::HI >>> Tick from >>>  ${ref.path}\n")
      mediator ! DistributedPubSubMediator.Publish("inTopic", Work("do-it"))

    case Tick(remote) =>
      val ref = sender()
      log.info(s"\n\n ACTOR::HI >>> Tick(remote = $remote) from >>> ${ref.path}\n")


    case Result(r) =>
      val ref = sender()
      log.info(s"\n\n ACTOR::HI >>> ${Result(r)} from >>> ${ref.path}\n")

  }
}

// # Protocols
//case class Work(message: String)
//case class Result(response: String)
//case object Tick
