package akkablockchain.actor

import akkablockchain.model._

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, AbstractBehavior, Behaviors}

object BlockInspector {
  sealed trait Validation
  case class Validate(block: Block, replyTo: ActorRef[Blockchainer.Req]) extends Validation
  case object DoneValidation extends Validation

  def apply(): Behavior[Validation] =
    Behaviors.setup(context => new BlockInspector(context).messageLoop())
}

class BlockInspector private(context: ActorContext[BlockInspector.Validation]) {
  import BlockInspector._

  private def messageLoop(): Behavior[Validation] = idle()

  private def idle(): Behavior[Validation] = Behaviors.receiveMessage{
    case Validate(block, replyTo) =>
      replyTo ! Blockchainer.ValidationResult(validBlockchain(block))
      busy()

    case DoneValidation =>
      context.log.info(s"[Validation] BlockInspector.DoneValidation received.")
      Behaviors.same
  }

  private def busy(): Behavior[Validation] = Behaviors.receiveMessage{
    case Validate(block, replyTo) =>
      context.log.error(s"[Validation] BlockInspector.Validate($block) received but $this is busy!")
      replyTo ! Blockchainer.BusyException(s"$this is busy!")
      Behaviors.same

    case DoneValidation =>
      context.log.info(s"[Validation] BlockInspector.DoneValidation received.")
      idle()
  }

  private def validBlockchain(block: Block): Boolean = {
    @scala.annotation.tailrec
    def loop(blk: Block): Boolean = blk match {
      case b: LinkedBlock =>
        if (ProofOfWork.validProofIn(b) &&
          b.hasValidHash &&
          b.hasValidMerkleRoot &&
          b.transactions.hasValidItems &&
          allNewTrans(block.transactions, b.blockPrev.transactions)
        )
          loop(b.blockPrev)
        else
          false  // Short-circuiting if `false`

      case b: RootBlock.type =>
        b.hasValidHash &&
          b.hasValidMerkleRoot
    }

    block match {
      case _: LinkedBlock => loop(block)
      case RootBlock => false
    }
  }

  private def allNewTrans(trans: Transactions, oldTrans: Transactions): Boolean =
    oldTrans.id != trans.id &&
      !trans.items.exists(item => oldTrans.items.map(_.id).contains(item.id))
}
