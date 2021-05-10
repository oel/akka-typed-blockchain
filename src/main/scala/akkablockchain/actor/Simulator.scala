package akkablockchain.actor

import akkablockchain.model._

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, AbstractBehavior, Behaviors}

import scala.collection.immutable.Queue
import scala.concurrent.duration._

object Simulator {
  sealed trait Cmd
  case object QuickTest extends Cmd
  case object MiningLoop extends Cmd
  case object Tick extends Cmd
  case object Tock extends Cmd
  case class ReportTransactionQueue(queue: Queue[Transactions]) extends Cmd
  case class ReportBlockchain(block: Block) extends Cmd

  def apply(blockchainer: ActorRef[Blockchainer.Req],
            transFeedInterval: Long,
            miningAvgInterval: Long): Behavior[Cmd] =
    Behaviors.setup(context =>
      new Simulator(context, blockchainer, transFeedInterval, miningAvgInterval).messageLoop()
    )
}

class Simulator private(context: ActorContext[Simulator.Cmd],
                        blockchainer: ActorRef[Blockchainer.Req],
                        transFeedInterval: Long,
                        miningAvgInterval: Long) {
  import Simulator._

  val feeder: ActorRef[TransactionFeeder.Cmd] =
    context.spawn(TransactionFeeder(blockchainer, transFeedInterval.millis), "feeder")

  private def messageLoop(): Behavior[Cmd] = Behaviors.receiveMessage{
    case MiningLoop =>
      feeder ! TransactionFeeder.Run
      Thread.sleep(transFeedInterval * 2)
      context.scheduleOnce(10.millis, context.self, Tick)
      scheduleMining()

    case QuickTest =>
      context.log.info("[QuickTest] Getting transaction queue and blockchain ...")
      blockchainer ! Blockchainer.GetTransactionQueue(context.self)
      blockchainer ! Blockchainer.GetBlockchain(context.self)

      feeder ! TransactionFeeder.Run

      Thread.sleep(miningAvgInterval) // Initial time for populating the empty queue

      context.log.info("[QuickTest] Getting transaction queue and blockchain ...")
      blockchainer ! Blockchainer.GetTransactionQueue(context.self)
      blockchainer ! Blockchainer.GetBlockchain(context.self)

      context.log.info("[QuickTest] Mining #1 ...")
      blockchainer ! Blockchainer.Mining

      Thread.sleep(10) // To trigger BusyException

      context.log.info("[QuickTest] Mining #2 ...")
      blockchainer ! Blockchainer.Mining

      Thread.sleep(miningAvgInterval)

      context.log.info("[QuickTest] Getting transaction queue and blockchain ...")
      blockchainer ! Blockchainer.GetTransactionQueue(context.self)
      blockchainer ! Blockchainer.GetBlockchain(context.self)

      context.log.info("[QuickTest] Mining #3 ...")
      blockchainer ! Blockchainer.Mining

      Thread.sleep(miningAvgInterval)

      feeder ! TransactionFeeder.StopRunning

      context.log.info("[QuickTest] Getting transaction queue and blockchain ...")
      blockchainer ! Blockchainer.GetTransactionQueue(context.self)
      blockchainer ! Blockchainer.GetBlockchain(context.self)
      Behaviors.same

    case ReportTransactionQueue(queue) =>
      context.log.info(s"Transaction queue: ${queue}")
      Behaviors.same

    case ReportBlockchain(block) =>
      context.log.info(s"Blockchain: ${blockchainToList(block)}")
      Behaviors.same

    case _ =>
      Behaviors.same
  }

  private def scheduleMining(): Behavior[Cmd] = Behaviors.receiveMessage{
    case Tick =>
      val interval = randomInterval(miningAvgInterval, 0.3)
      context.scheduleOnce(interval.millis, context.self, Tock)
      // context.system.scheduler.scheduleOnce(interval.millis, context.self, Tock)
      context.log.info(s"[MiningLoop] Start mining in ${interval} millis")
      context.log.info("[MiningLoop] Getting transaction queue and blockchain ...")
      blockchainer ! Blockchainer.GetTransactionQueue(context.self)
      blockchainer ! Blockchainer.GetBlockchain(context.self)
      waitingToMine()

    case _ =>
      Behaviors.same
  }

  private def waitingToMine(): Behavior[Cmd] = Behaviors.receiveMessage{
    case Tock =>
      blockchainer ! Blockchainer.Mining
      context.scheduleOnce(10.millis, context.self, Tick)
      scheduleMining()

    case ReportTransactionQueue(queue) =>
      context.log.info(s"[MiningLoop] Transaction queue: ${queue}")
      Behaviors.same

    case ReportBlockchain(block) =>
      context.log.info(s"[MiningLoop] Blockchain: ${blockchainToList(block)}")
      Behaviors.same

    case _ =>
      Behaviors.same
  }

  private def blockchainToList(block: Block): List[Block] = {
    @scala.annotation.tailrec
    def loop(blk: Block, ls: List[Block]): List[Block] = blk match {
      case b: LinkedBlock => loop(b.blockPrev, b :: ls)
      case b: RootBlock.type => b :: ls
    }
    loop(block, List.empty[Block]).reverse
  }

  private def randomFcn = java.util.concurrent.ThreadLocalRandom.current

  private def randomInterval(average: Long, delta: Double): Long = {
    val rand = randomFcn.nextDouble(average * (1.0 - delta), average * (1.0 + delta))
    Math.round(rand / 1000) * 1000
  }
}
