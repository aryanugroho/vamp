package io.vamp.common.akka

import akka.actor.{ ActorRef, ActorSystem, PoisonPill }
import akka.util.Timeout
import com.typesafe.scalalogging.Logger
import io.vamp.common.{ ClassProvider, Namespace }
import org.slf4j.{ LoggerFactory, MDC }

import scala.concurrent.Future
import scala.reflect.{ ClassTag, classTag }

trait Bootstrap extends BootstrapLogger {

  def start(): Unit = {}

  def stop(): Unit = {}
}

trait ActorBootstrap extends BootstrapLogger {

  private var actors: Future[List[ActorRef]] = Future.successful(Nil)

  def createActors(implicit actorSystem: ActorSystem, namespace: Namespace, timeout: Timeout): Future[List[ActorRef]]

  def start(implicit actorSystem: ActorSystem, namespace: Namespace, timeout: Timeout): Unit = {
    info(s"Starting ${getClass.getSimpleName}")
    actors = createActors(actorSystem, namespace, timeout)
  }

  def restart(implicit actorSystem: ActorSystem, namespace: Namespace, timeout: Timeout): Unit = {
    implicit val executionContext = actorSystem.dispatcher
    stop.map(_ ⇒ start)
  }

  def stop(implicit actorSystem: ActorSystem, namespace: Namespace): Future[Unit] = {
    info(s"Stopping ${getClass.getSimpleName}")
    actors.map(_.reverse.foreach(_ ! PoisonPill))(actorSystem.dispatcher)
  }

  def alias[T: ClassTag](name: String, default: String ⇒ Future[ActorRef])(implicit actorSystem: ActorSystem, namespace: Namespace, timeout: Timeout): Future[ActorRef] = {
    ClassProvider.find[T](name).map { clazz ⇒
      IoC.alias(classTag[T].runtimeClass, clazz)
      IoC.createActor(clazz)
    } getOrElse default(name)
  }
}

trait BootstrapLogger {

  protected val logger = Logger(LoggerFactory.getLogger(getClass))

  protected def info(message: String)(implicit namespace: Namespace) = {
    MDC.put("namespace", namespace.name)
    try logger.info(message) finally MDC.remove("namespace")
  }
}
