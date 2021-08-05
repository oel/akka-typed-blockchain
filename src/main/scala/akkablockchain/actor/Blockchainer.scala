package akkablockchain.actor

import akkablockchain.model._

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, AbstractBehavior, Behaviors}
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import akka.actor.typed.pubsub.Topic

import scala.collection.immutable.Queue
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.util.{Try, Failure, Success}

object Blockchainer {
  sealed trait Req

  case class GetTransactionQueue(replyTo: ActorRef[Simulator.Cmd]) extends Req
  case class GetBlockchain(replyTo: ActorRef[Simulator.Cmd]) extends Req
  case class SubmitTransactions(trans: Transactions, append: Boolean = true) extends Req
  case object Mining extends Req

  case class AddTransactions(trans: Transactions, append: Boolean = true) extends Req
  case class UpdateBlockchain(block: Block) extends Req

  case class MiningResult(res: Block) extends Req
  case class ValidationResult(res: Boolean) extends Req

  case class BusyException(message: String, cause: Throwable = None.orNull)
    extends Exception(message, cause) with Req
  case class OtherException(message: String, cause: Throwable = None.orNull)
    extends Exception(message, cause) with Req

  def apply(accountKey: String, timeoutMining: Long, timeoutValidation: Long): Behavior[Req] =
    Behaviors.setup(context =>
      new Blockchainer(context, accountKey, timeoutMining, timeoutValidation).messageLoop()
    )
}

class Blockchainer private(context: ActorContext[Blockchainer.Req],
                           accountKey: String,
                           timeoutMining: Long,
                           timeoutValidation: Long) {
  import Blockchainer._

  implicit val ec: ExecutionContext = context.executionContext

  val timeoutPoW = timeoutMining * 9 / 10

  val topicTrans = context.spawn(Topic[Req]("new-transactions"), "pubsubNewTransactions")
  val topicBlock = context.spawn(Topic[Req]("new-block"), "pubsubNewBlock")

  topicTrans ! Topic.Subscribe(context.self)
  topicBlock ! Topic.Subscribe(context.self)

  val miner: ActorRef[Miner.Mining] = context.spawn(Miner(accountKey, timeoutPoW), "miner")
  val blockInspector: ActorRef[BlockInspector.Validation] = context.spawn(BlockInspector(), "blockInspector")

  private var transactionQueue: Queue[Transactions] = Queue.empty[Transactions]
  private var blockchain: Block = RootBlock

  private def messageLoop(): Behavior[Req] = Behaviors.receiveMessage{
    case GetTransactionQueue(r) =>
      r match {
        case _: ActorRef[Simulator.Cmd] =>
          r ! Simulator.ReportTransactionQueue(transactionQueue)
        case _ =>
      }
      Behaviors.same

    case GetBlockchain(r) =>
      r match {
        case _:
          ActorRef[Simulator.Cmd] => r ! Simulator.ReportBlockchain(blockchain)
        case _ =>
      }
      Behaviors.same

    case SubmitTransactions(trans, append) =>
      if (trans.hasValidItems) {
        topicTrans ! Topic.Publish(AddTransactions(trans, append))
        context.log.info(s"[Req.SubmitTransactions] ${this}: $trans is published.")
      } else {
        context.log.error(s"[Req.SubmitTransactions] ${this}: ERROR: $trans is invalid or already exists in queue!")
      }
      Behaviors.same

    case Mining =>
      if (transactionQueue.isEmpty) {
        context.log.error(s"[Req.Mining] ${this}: ERROR: Transaction queue is empty!")
      }
      else {
        val blockPrev = blockchain
        val (trans, remaining) = transactionQueue.dequeue
        transactionQueue = remaining
        implicit val tmoMining: Timeout = Timeout(timeoutMining.millis)
        context.ask(miner, ref => Miner.Mine(blockPrev, trans, ref)) {
          case Success(r) =>
            r match {
              case MiningResult(block) =>
                topicBlock ! Topic.Publish(UpdateBlockchain(block))
                miner ! Miner.DoneMining
                MiningResult(block)
              case _ =>
                OtherException(s"Unknown mining result $r")
            }
          case Failure(e) =>
            context.log.error(s"[Req.Mining] ${this}: ERROR: $e")
            e match {
              case _: BusyException =>
                context.self ! AddTransactions(trans, append = false)
                BusyException(e.getMessage)
              case _ =>
                miner ! Miner.DoneMining
                OtherException(e.getMessage)
            }
        }
      }
      Behaviors.same

    case AddTransactions(trans, append) =>
      if (trans.hasValidItems && allNewToTransQ(trans, transactionQueue)) {
        if (append) {
          transactionQueue :+= trans
          context.log.info(s"[Req.AddTransactions] ${this}: Appended $trans to transaction queue.")
        } else {
          transactionQueue +:= trans
          context.log.info(s"[Req.AddTransactions] ${this}: Prepended $trans to transaction queue.")
        }
      } else {
        context.log.error(s"[Req.AddTransactions] ${this}: ERROR: $trans is invalid or already exists in queue!")
      }
      Behaviors.same

    case UpdateBlockchain(block) =>
      implicit val tmoValidation: Timeout = Timeout(timeoutValidation.millis)
      context.ask(blockInspector, ref => BlockInspector.Validate(block, ref)) {
        case Success(r) =>
          r match {
            case ValidationResult(isValid) =>
              if (isValid) {
                if (block.length > blockchain.length) {
                  context.log.info(s"[Req.UpdateBlockchain] ${this}: $block is valid. Updating blockchain.")
                  blockchain = block
                } else
                  context.log.warn(s"[Req.UpdateBlockchain] ${this}: $block isn't longer than existing blockchain! Blockchain not updated.")
              } else
                context.log.error(s"[Req.UpdateBlockchain] ${this}: ERROR: $block is invalid!")
              blockInspector ! BlockInspector.DoneValidation
              ValidationResult(isValid)
            case _ =>
              OtherException(s"Unknown validation result $r")
          }
        case Failure(e) =>
          context.log.error(s"[Req.UpdateBlockchain] ${this}: ERROR: Validation of $block failed! $e")
          e match {
            case _: BusyException =>
              BusyException(e.getMessage)
            case _ =>
              blockInspector ! BlockInspector.DoneValidation
              OtherException(e.getMessage)
          }
      }
      Behaviors.same

    case _ =>
      // Behaviors.unhandled
      Behaviors.same
  }

  private def allNewToTransQ(trans: Transactions, transQ: => Queue[Transactions]): Boolean =
    !transQ.map(_.id).contains(trans.id) &&
      !trans.items.exists(item => transQ.flatMap(_.items.map(_.id)).contains(item.id))
}
