package akkablockchain.actor

import akkablockchain.util.Util._
import akkablockchain.model.ProofOfWork.{defaultDifficulty, defaultNonce}
import akkablockchain.model._

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, AbstractBehavior, Behaviors}

import scala.concurrent.{ExecutionContext, Future, Promise, TimeoutException}
import scala.concurrent.duration._
import scala.util.{Try, Success, Failure}

object Miner {
  sealed trait Mining
  case class Mine(blockPrev: Block, trans: Transactions, replyTo: ActorRef[Blockchainer.Req]) extends Mining
  case object DoneMining extends Mining

  def apply(accountKey: String, timeoutPoW: Long): Behavior[Mining] =
    Behaviors.setup(context =>
      new Miner(context, accountKey, timeoutPoW).messageLoop()
    )
}

class Miner private(context: ActorContext[Miner.Mining], accountKey: String, timeoutPoW: Long) {
  import Miner._

  implicit val ec: ExecutionContext = context.executionContext
  implicit val timeout = timeoutPoW.millis

  private def messageLoop(): Behavior[Mining] = idle()

  private def idle(): Behavior[Mining] = Behaviors.receiveMessage{
    case Mine(blockPrev, trans, replyTo) =>
      val newBlock = generateNewBlock(blockPrev, trans)
      generatePoW(newBlock).map(newNonce =>
          replyTo ! Blockchainer.MiningResult(newBlock.copy(nonce = newNonce))
        ).
        recover{ case e: Exception => Blockchainer.OtherException(s"$e") }
      busy()

    case DoneMining =>
      Behaviors.same
  }

  private def busy(): Behavior[Mining] = Behaviors.receiveMessage{
    case Mine(blockPrev, trans, replyTo) =>
      context.log.error(s"[Mining] Miner.Mine($blockPrev, $trans) received but $this is busy!")
      replyTo ! Blockchainer.BusyException(s"$this is busy!")
      Behaviors.same

    case DoneMining =>
      context.log.info(s"[Mining] Miner.DoneMining received.")
      idle()
  }

  private def generateNewBlock(blockPrev: Block, trans: Transactions): LinkedBlock = {
    val newTransItem = TransactionItem(
      ProofOfWork.networkAccount,
      new Account(accountKey, "miner"),
      ProofOfWork.defaultReward,
      System.currentTimeMillis
    )
    val newTrans = new Transactions(trans.id, newTransItem +: trans.items, System.currentTimeMillis)
    LinkedBlock(blockPrev, newTrans, System.currentTimeMillis)
  }

  private def generatePoW(block: Block)(implicit ec: ExecutionContext, timeout: FiniteDuration): Future[Long] = {
    val promise = Promise[Long]()
    context.system.scheduler.scheduleOnce(
      timeout, () => promise tryFailure new TimeoutException(s"$block: $timeout")
    )
    Future{
      Try{
        val incrementedNonce =
          ProofOfWork.generateProof(bytesToBase64(block.hash), defaultDifficulty, defaultNonce)
        promise success incrementedNonce
      }.
      recover{
        case e: Exception => promise failure e
      }
    }
    promise.future
  }
}
