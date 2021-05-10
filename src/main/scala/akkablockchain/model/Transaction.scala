package akkablockchain.model

import akkablockchain.util.Util._
import akkablockchain.util.Crypto._

import java.util.UUID.randomUUID

case class Account(key: String, name: String) {
  override def toString: String =
    s"A(${key.substring(0,2)}*${key.substring(128,132)}*, $name)"
}

object Account {
  def fromKeyFile(keyFile: String, name: String): Account =
    publicKeyFromPemFile(keyFile) match {
      case Some(key) =>
        new Account(publicKeyToBase64(key), name)
      case None =>
        throw new Exception(s"ERROR: Problem loading $keyFile!")
    }
}

case class TransactionItem(id: String, accountFrom: Account, accountTo: Account, amount: Long, timestamp: Long) {
  def hasValidHash: Boolean =
    id sameElements TransactionItem(accountFrom, accountTo, amount, timestamp).id

  override def toString: String = {
    val datetime = timestampToDateTime(timestamp)
    s"TI(${id.substring(0, 4)}, ${accountFrom.name} -> ${accountTo.name}, ${amount}, ${datetime})"
  }
}

object TransactionItem {
  def apply(accountFrom: Account, accountTo: Account, amount: Long, timestamp: Long): TransactionItem = {
    val bytes = accountFrom.key.getBytes ++ accountTo.key.getBytes ++ longToBytes(amount) ++ longToBytes(timestamp)
    new TransactionItem(bytesToBase64(hashFcn(bytes)), accountFrom, accountTo, amount, timestamp)
  }
}

case class Transactions(id: String, items: Array[TransactionItem], timestamp: Long) {
  def hasValidItems: Boolean = {
    val len = items.length

    @scala.annotation.tailrec
    def loop(idx: Int): Boolean = {
      if (idx < len) {
        if (items(idx).hasValidHash)
          loop(idx + 1)
        else
          false  // Short-circuiting if `false`
      }
      else
        true
    }

    if (len <= 0) false else loop(0)
  }

  override def toString: String = {
    val datetime = timestampToDateTime(timestamp)
    s"T(${id.substring(0, 4)}, ${items.map(_.amount).sum}/${items.size}, ${datetime})"
  }
}

object Transactions {
  def apply(transactions: Array[TransactionItem], timestamp: Long): Transactions =
    new Transactions(randomUUID.toString, transactions, timestamp)
}
