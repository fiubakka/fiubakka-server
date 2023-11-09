import akka.actor.typed.{ ActorSystem, Behavior }
import akka.actor.typed.scaladsl.Behaviors

object HelloWorld {
  def apply(number: Int): Behavior[String] = Behaviors.receive { (context, message) =>
    context.log.info("Hello, {}! Number {}", message, number)
    if (number <= 3) {
      apply(number + 1)
    } else {
      Behaviors.same
    }
  }
}

object Main extends App {
  val system: ActorSystem[String] = ActorSystem(HelloWorld(1), "helloWorldSystem")
  system ! "World"
  system ! "World"
  system ! "World"
  system.terminate()
}
