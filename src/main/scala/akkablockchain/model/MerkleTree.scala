package akkablockchain.model

import akkablockchain.util.Util._
import akkablockchain.util.Crypto._

class MerkleTree(
    val hash: Array[Byte],
    val left: Option[MerkleTree] = None,
    val right: Option[MerkleTree] = None
  ) extends Serializable {

  override def toString: String = s"MT(${bytesToBase64(hash).substring(0, 4)})"
}

object MerkleTree {
  def apply(data: Array[Array[Byte]]): MerkleTree = {
    @scala.annotation.tailrec
    def buildTree(nodes: Array[MerkleTree]): Array[MerkleTree] = nodes match {
      case ns if ns.size <= 1 =>
        ns
      case ns =>
        val pairedNodes = ns.grouped(2).map{
          case Array(a, b) => new MerkleTree(hashFcn(a.hash ++ b.hash), Some(a), Some(b))
          case Array(a)    => new MerkleTree(hashFcn(a.hash), Some(a), None)
        }.toArray
        buildTree(pairedNodes)
    }

    if (data.isEmpty)
      new MerkleTree(hashFcn(Array.empty[Byte]))
    else {
      val nodes = data.map(byteArr => new MerkleTree(hashFcn(byteArr)))
      buildTree(nodes)(0)  // Return root of the tree
    }
  }
}
