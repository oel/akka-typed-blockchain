package akkablockchain.model

import akkablockchain.util.Util._
import akkablockchain.util.Crypto._
import akkablockchain.model.ProofOfWork.{defaultDifficulty, defaultNonce}

sealed trait Block {
  def hash: Array[Byte]
  def hashPrev: Array[Byte]
  def merkleRoot: MerkleTree
  def transactions: Transactions
  def timestamp: Long
  def difficulty: Int
  def nonce: Long
  def length: Int

  override def toString: String = {
    val datetime = timestampToDateTime(timestamp)
    val transInfo = s"T(${transactions.id.substring(0, 4)}, ${transactions.items.map(_.amount).sum}/${transactions.items.size})"
    s"BLK(${bytesToBase64(hash).substring(0, 4)}, ${transInfo}, ${datetime}, ${difficulty}, ${nonce})"
  }
}

case object RootBlock extends Block {
  def hash = hashFcn(merkleRoot.hash ++ hashPrev ++ longToBytes(timestamp))
  def hashPrev = Array.empty[Byte]
  def merkleRoot = MerkleTree(Array.empty[Array[Byte]])
  def transactions = new Transactions("----", Array.empty[TransactionItem], 0L)
  def timestamp = 0L
  def difficulty = 0
  def nonce = 0L
  def length = 1
  def hasValidHash: Boolean = (hash sameElements RootBlock.hash) && hashPrev.isEmpty
  def hasValidMerkleRoot: Boolean = merkleRoot.hash sameElements MerkleTree(Array.empty[Array[Byte]]).hash
}

case class LinkedBlock(
    hash: Array[Byte],
    hashPrev: Array[Byte],
    blockPrev: Block,
    merkleRoot: MerkleTree,
    transactions: Transactions,
    timestamp: Long,
    difficulty: Int = defaultDifficulty,
    nonce: Long = defaultNonce
  ) extends Block {

  def hasValidHash: Boolean =
    (hash sameElements LinkedBlock(blockPrev, transactions, timestamp).hash) &&
      (hashPrev sameElements blockPrev.hash)

  def hasValidMerkleRoot: Boolean =
    merkleRoot.hash sameElements MerkleTree(transactions.items.map(_.id.getBytes)).hash

  def length: Int = {
    @scala.annotation.tailrec
    def loop(blk: Block, len: Int): Int = blk match {
      case RootBlock => len
      case b: LinkedBlock => loop(b.blockPrev, len + 1)
    }
    loop(this, 1)
  }
}

object LinkedBlock {
  def apply(
      blockPrev: Block,
      transactions: Transactions,
      timestamp: Long
    ): LinkedBlock = {
    val mRoot = MerkleTree(transactions.items.map(_.id.getBytes))
    new LinkedBlock(
      hash = hashFcn(mRoot.hash ++ blockPrev.hash ++ longToBytes(timestamp)),
      hashPrev = blockPrev.hash,
      blockPrev = blockPrev,
      merkleRoot = mRoot,
      transactions = transactions,
      timestamp = timestamp
    )
  }
}
