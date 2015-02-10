package smoke

import java.net.URL
import java.io.{ BufferedInputStream, FileInputStream, File }
import scala.util.Try
import scala.io.Source
import scala.collection.JavaConversions._

case class Asset(contentType: String, data: Array[Byte])

trait StaticAssets {
  val publicFolder: String
  val cacheAssets: Boolean = false

  lazy val PublicFolderPrefixes =
    this.getClass.getClassLoader.getResources(publicFolder).toList.map(_.getPath())

  private var cachedAssets = Map[String, Asset]()

  private def getExtension(name: String) = {
    val dotIndex = name.lastIndexOf('.')
    if (dotIndex == -1) "" else name.substring(dotIndex + 1)
  }

  private def isStaticAsset(url: URL) =
    url.getProtocol() match {
      case "file" if new File(url.getFile()).isFile() ⇒
        PublicFolderPrefixes.exists(url.getPath.startsWith(_))
      case "jar" ⇒ true
      case _     ⇒ false
    }

  private def readFile(path: String): Option[Array[Byte]] = {
    val resources = this.getClass.getClassLoader.getResources(path).toList
    resources.collectFirst {
      case r if isStaticAsset(r) ⇒
        val connection = r.openConnection()
        val is = connection.getInputStream()
        try {
          val bis = new BufferedInputStream(is)
          Stream.continually(bis.read).takeWhile(-1 !=).map(_.toByte).toArray
        } finally {
          is.close()
        }
    }
  }

  private def loadAsset(p: String): Option[Asset] = {
    val path = s"$publicFolder$p"
    readFile(path) map { bytes ⇒
      val extension = getExtension(path)
      Asset(MimeType(extension), bytes)
    }
  }

  private lazy val getAsset: String ⇒ Option[Asset] =
    if (cacheAssets)
      (path: String) ⇒ cachedAssets.get(path).orElse {
        val asset = loadAsset(path)
        asset.map { a ⇒
          cachedAssets += path -> a
        }
        asset
      }
    else (path: String) ⇒ loadAsset(path)

  def responseFromAsset(path: String): Response = {
    getAsset(path) match {
      case Some(asset) ⇒
        Response(Ok, Seq("Content-Type" -> asset.contentType), RawData(asset.data))
      case None ⇒
        Response(NotFound)
    }
  }
}