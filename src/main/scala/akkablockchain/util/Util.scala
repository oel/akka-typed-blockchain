package akkablockchain.util

import java.time.{Instant, LocalDateTime, ZoneId}
import java.time.format.DateTimeFormatter

object Util {
  def bytesToBase64(bytes: Array[Byte]): String =
    java.util.Base64.getEncoder.encodeToString(bytes)

  def base64ToBytes(base64: String): Array[Byte] =
    java.util.Base64.getDecoder.decode(base64)

  def bytesToHex(bytes: Array[Byte]): String =
    bytes.map("%02x".format(_)).mkString

  def hexToBytes(hex: String): Array[Byte] =
    hex.grouped(2).toArray.map(BigInt(_, 16).toByte)

  def intToBytes(num: Int): Array[Byte] =
    java.nio.ByteBuffer.allocate(4).putInt(num).array

  def longToBytes(num: Long): Array[Byte] =
    java.nio.ByteBuffer.allocate(8).putLong(num).array

  def timestampToDateTime(timestamp: Long, zone: String = "UTC"): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(
        LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.of(zone))
      )
}
