package akkablockchain.model

import akkablockchain.util.Util._
import akkablockchain.util.Crypto._

import com.typesafe.config.ConfigFactory

object ProofOfWork {
  val conf = ConfigFactory.load()

  // Load setting (alternative: use a wrapper like https://github.com/pureconfig/pureconfig)
  val defaultDifficulty: Int = conf.getInt("blockchain.proof-of-work.difficulty")
  val defaultNonce: Long = conf.getLong("blockchain.proof-of-work.nonce")
  val defaultReward: Long = conf.getLong("blockchain.proof-of-work.reward")
  val networkAccount: Account = new Account(
      conf.getString("blockchain.proof-of-work.network-account-key"),
      conf.getString("blockchain.proof-of-work.network-name")
    )

  def generateProof(base64: String, difficulty: Int, nonce: Long): Long = {
    @scala.annotation.tailrec
    def loop(bytes: Array[Byte], n: Long): Long =
      if (validProof(bytes, difficulty, n)) n else loop(bytes, n + 1)

    loop(base64ToBytes(base64), nonce)
  }

  def validProof(bytes: Array[Byte], difficulty: Int, nonce: Long): Boolean =
    hashFcn(bytes ++ longToBytes(nonce)).take(difficulty).forall(_ == 0)

  def validProofIn(block: Block): Boolean = block match {
    case _: LinkedBlock =>
      (block.difficulty >= defaultDifficulty) &&
        validProof(block.hash, block.difficulty, block.nonce)
    case RootBlock =>
      false
  }
}
