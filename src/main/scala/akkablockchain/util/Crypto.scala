package akkablockchain.util

import akkablockchain.util.Util._

import scala.util.{Try, Success, Failure}
import java.nio.file.{Files, Paths}
import java.io.{OutputStreamWriter, FileOutputStream, IOException}
import java.security.spec.{PKCS8EncodedKeySpec, X509EncodedKeySpec}
import java.security._
import org.apache.commons.codec.binary.Base64
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.util.io.pem.{PemObject, PemWriter}

object Crypto {
  def sha256(byteArr: Array[Byte]): Array[Byte] =
    java.security.MessageDigest.getInstance("SHA-256").digest(byteArr)

  val hashFcn = sha256 _

  def generateKeyPair(keySize: Int = 4096): KeyPair = {
    val generator = java.security.KeyPairGenerator.getInstance("RSA")
    generator.initialize(keySize)
    generator.genKeyPair
  }

  def sign(privateKey: PrivateKey, plainText: Array[Byte]): Array[Byte] = {
    val signer = Signature.getInstance("SHA256withRSA")
    signer.initSign(privateKey)
    signer.update(plainText)
    signer.sign()
  }

  def verify(publicKey: PublicKey, signedCipherText: Array[Byte], plainText: Array[Byte]): Boolean = {
    val signer = Signature.getInstance("SHA256withRSA")
    signer.initVerify(publicKey)
    signer.update(plainText)
    signer.verify(signedCipherText)
  }

  def publicKeyFromBytes(bytes: Array[Byte]): PublicKey =
    KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(bytes))

  def privateKeyFromBytes(bytes: Array[Byte]): PrivateKey =
    KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(bytes))

  def publicKeyToBase64(publicKey: PublicKey): String =
    bytesToBase64(publicKey.getEncoded)

  def privateKeyToBase64(privateKey: PrivateKey): String =
    bytesToBase64(privateKey.getEncoded)

  // -- PKCS#8 DER Files ------

  def writeKeyPairToDerFiles(kp: KeyPair, filePath: String, filePrefix: String): Unit = {
    import java.nio.file.{Files, Paths}
    val publicKeyPath = Paths.get(filePath + filePrefix + "_public.der")
    val privateKeyPath = Paths.get(filePath + filePrefix + "_private.der")
    try {
      Files.write(publicKeyPath, kp.getPublic.getEncoded)
      Files.write(privateKeyPath, kp.getPrivate.getEncoded)
    } catch {
      case e: IOException => println("ERROR: IO Exception $e")
    }
  }

  def publicKeyFromDerFile(publicKeyFile: String): Option[PublicKey] = {
    Try(Files.readAllBytes(Paths.get(publicKeyFile))) match {
      case Success(k) =>
        val key = KeyFactory.getInstance("RSA").
          generatePublic(new X509EncodedKeySpec(k))
        Some(key)
      case Failure(e) =>
        None
    }
  }

  def privateKeyFromDerFile(privateKeyFile: String): Option[PrivateKey] = {
    Try(Files.readAllBytes(Paths.get(privateKeyFile))) match {
      case Success(k) =>
        val key = KeyFactory.getInstance ("RSA").
          generatePrivate(new PKCS8EncodedKeySpec(k))
        Some(key)
      case Failure(e) =>
        None
    }
  }

  // -- PKCS#8 PEM Files ------

  def writePemFile(key: Key, description: String, filename: String): Unit = {
    val pemObject = new PemObject(description, key.getEncoded())
    val pemWriter = new PemWriter(new OutputStreamWriter(new FileOutputStream(filename)))

    try {
      pemWriter.writeObject(pemObject)
    } catch {
      case e: IOException => println("ERROR: IO Exception $e")
    } finally {
      pemWriter.close()
    }
  }

  def generateKeyPairPemFiles(filePrefix: String, keySize: Int = 4096): Unit = {
    Security.addProvider(new BouncyCastleProvider())

    val generator = KeyPairGenerator.getInstance("RSA")
    generator.initialize(keySize)
    val keyPair = generator.generateKeyPair()

    writePemFile(keyPair.getPublic(), "RSA PUBLIC KEY", s"${filePrefix}_public.pem")
    writePemFile(keyPair.getPrivate(), "RSA PRIVATE KEY", s"${filePrefix}_private.pem")
  }

  def publicKeyFromPemFile(keyFile: String): Option[PublicKey] = {
    Try(scala.io.Source.fromFile(keyFile)) match {
      case Success(k) =>
        val keyString = k.mkString.
          replace("-----BEGIN RSA PUBLIC KEY-----\n", "").
          replace("-----END RSA PUBLIC KEY-----\n", "")
        val keyBytes = Base64.decodeBase64(keyString)
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes))
        Some(publicKey)
      case Failure(e) =>
        None
    }
  }

  def privateKeyFromPemFile(keyFile: String): Option[PrivateKey] = {
    Try(scala.io.Source.fromFile(keyFile)) match {
      case Success(k) =>
        val keyString = k.mkString.
          replace("-----BEGIN RSA PRIVATE KEY-----\n", "").
          replace("-----END RSA PRIVATE KEY-----\n", "")
        val keyBytes = Base64.decodeBase64(keyString)
        val privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes))
        Some(privateKey)
      case Failure(e) =>
        None
    }
  }
}
