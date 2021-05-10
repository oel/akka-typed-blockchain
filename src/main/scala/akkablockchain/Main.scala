package akkablockchain

import akkablockchain.util.Crypto.{publicKeyFromPemFile, publicKeyToBase64}
import akkablockchain.actor._

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, Terminated}
import akka.cluster.typed.Cluster
import akka.NotUsed

import com.typesafe.config.ConfigFactory

import java.nio.file.{Files, Paths}
import scala.util.{Try, Success, Failure}

object Main {
  object Starter {
    def apply(accountKey: String, timeoutMining: Long, timeoutValidation: Long,
              transFeedInterval: Long, miningAvgInterval: Long, test: Boolean): Behavior[NotUsed] =
      Behaviors.setup{ context =>
        Cluster(context.system)

        val blockchainer = context.spawn(Blockchainer(accountKey, timeoutMining, timeoutValidation), "blockchainer")
        val simulator = context.spawn(Simulator(blockchainer, transFeedInterval, miningAvgInterval), "simulator")

        if (test)
          simulator ! Simulator.QuickTest
        else
          simulator ! Simulator.MiningLoop

        Behaviors.receiveSignal{ case (_, Terminated(_)) => Behaviors.stopped }
      }
  }

  def main(args: Array[String]): Unit = {
    val (port: Int, minerKeyFile: String, test: Boolean) =
      if (args.isEmpty) {
        (2551, "src/main/resources/keys/account0_public.pem", true)
      } else if (args.length < 2) {
        Console.err.println("[Main] ERROR: Arguments port# and minerKeyFile required! e.g. 2552 src/main/resources/keys/account1_public.pem [test]")
        sys.exit(1)
      } else {
        Try((args(0).toInt, args(1))) match {
          case Success((port, keyFile)) if Files.exists(Paths.get(keyFile)) =>
            (port, keyFile, args.length >= 3 && args(2).toLowerCase.contains("test"))
          case _ =>
            Console.err.println(s"[Main] ERROR: Either port# $args(0) isn't an integer or minerKeyFile $args(1) doesn't exist!")
            sys.exit(1)
        }
      }

    val minerAccountKey = publicKeyFromPemFile(minerKeyFile) match {
      case Some(key) =>
        publicKeyToBase64(key)
      case None =>
        Console.err.println(s"[Main] ERROR: Problem in getting public key from minerKeyFile!")
        sys.exit(1)
    }

    val conf = ConfigFactory.parseString("akka.remote.artery.canonical.port = " + port).
      withFallback(ConfigFactory.load())

    val timeoutMining = conf.getLong("blockchain.timeout.mining")
    val timeoutValidation = conf.getLong("blockchain.timeout.block-validation")

    val transFeedInterval = conf.getLong("blockchain.simulator.transaction-feed-interval")
    val miningAvgInterval = conf.getLong("blockchain.simulator.mining-average-interval")

    implicit val system = ActorSystem(
      Starter(minerAccountKey, timeoutMining, timeoutValidation, transFeedInterval, miningAvgInterval, test),
      "blockchain",
      conf
    )
  }
}
