package akkablockchain.actor

import akkablockchain.model._

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, AbstractBehavior, Behaviors}
import akka.actor.Cancellable

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object TransactionFeeder {
  sealed trait Cmd
  case object Run extends Cmd
  case object StopRunning extends Cmd
  case object Tick extends Cmd

  def apply(blockchainer: ActorRef[Blockchainer.Req], interval: FiniteDuration): Behavior[Cmd] =
    Behaviors.setup(context =>
      new TransactionFeeder(context, blockchainer, interval).messageLoop()
    )
}

class TransactionFeeder private(context: ActorContext[TransactionFeeder.Cmd],
                                blockchainer: ActorRef[Blockchainer.Req],
                                interval: FiniteDuration) {
  import TransactionFeeder._

  implicit val ec: ExecutionContext = context.executionContext

  val numOfAccounts = 10
  val maxTransItems = 3

  val keyPath = "src/main/resources/keys/"

  // Keypairs as PKCS#8 PEM files already created to save initial setup time
  // (0 until numOfAccounts).foreach(i => generateKeyPairPemFiles(s"${keyPath}account${i}"))
  val keyFiles = List.tabulate(numOfAccounts)(i => s"account${i}_public.pem")

  private def messageLoop(): Behavior[Cmd] = idle()

  private def idle(): Behavior[Cmd] = Behaviors.receiveMessage {
    case Run =>
      val cancellable = context.system.scheduler.
        scheduleAtFixedRate(randomFcn.nextInt(1, 4).seconds, interval)(
          () => context.self ! Tick
        )
      context.log.info(s"[TransactionFeeder] idle: Got $cancellable")
      running(cancellable)

    case _ =>
      Behaviors.same
  }

  private def running(cancellable: Cancellable): Behavior[Cmd] = Behaviors.receiveMessage {
    case Run =>
      context.log.error(s"[TransactionFeeder] Run received but $this is already running!")
      Behaviors.same

    case Tick =>
      val trans = generateTrans(numOfAccounts, maxTransItems, keyPath, keyFiles)
      context.log.info(s"[TransactionFeeder] Submitting $trans to Blockchainer.")
      blockchainer ! Blockchainer.SubmitTransactions(trans)
      Behaviors.same

    case StopRunning =>
      cancellable.cancel()
      context.log.info(s"[TransactionFeeder] StopRunning received. $this stopped!")
      idle()
  }

  private def randomFcn = java.util.concurrent.ThreadLocalRandom.current

  private def generateTrans(numOfAccounts: Int,
                            maxTransItems: Int,
                            keyPath: String,
                            keyFiles: List[String]): Transactions = {
    def genTransItem: TransactionItem = {
      val idx = distinctRandomIntPair(0, numOfAccounts)
      val accountFrom = Account.fromKeyFile(s"${keyPath}${keyFiles(idx(0))}", s"User${idx(0)}")
      val accountTo = Account.fromKeyFile(s"${keyPath}${keyFiles(idx(1))}", s"User${idx(1)}")
      val amount = 1000L + randomFcn.nextInt(0, 5) * 500L

      TransactionItem(accountFrom, accountTo, amount, System.currentTimeMillis)
    }

    val numOfTransItems = randomFcn.nextInt(1, maxTransItems + 1)
    val transItems = Array.tabulate(numOfTransItems)(_ => genTransItem)

    Transactions(transItems, System.currentTimeMillis)
  }

  private def distinctRandomIntPair(lower: Int, upper: Int): List[Int] = {
    val rand1 = randomFcn.nextInt(lower, upper)
    val rand2 = randomFcn.nextInt(lower, upper)
    if (rand1 != rand2)
      List(rand1, rand2)
    else
      List(rand1, if (rand2 < upper - 1) rand2 + 1 else lower)
  }
}
