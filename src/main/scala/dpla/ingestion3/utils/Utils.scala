package dpla.ingestion3.utils

import java.io.File

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{Path, FileSystem}
import org.apache.hadoop.io.SequenceFile.Reader
import org.apache.hadoop.io.Text

import scala.xml.Node

/**
  * Created by scott on 1/18/17.
  */
object Utils {

  /**
    *
    * @param fs
    * @param seqFilePath
    * @param conf
    * @return
    */
  def printSeqFile(fs: FileSystem, seqFilePath: Path, conf: Configuration): Unit = {
    val reader: Reader = new Reader(fs, seqFilePath, conf)


    val k: Text = new Text()
    val v: Text = new Text()

    try {
      while (reader.next(k, v)) {
        println(k)
      }
    } finally {
      reader.close()
    }
  }

  /**
    * Count the number of files in the given directory, outDir.
    *
    * @param outDir Directory to count
    * @param ext File extension to filter by
    * @return The number of files that match the extension
    */
 def countFiles(outDir: File, ext: String): Long = {
   outDir.list()
     .par
     .count(fileName => fileName.endsWith(ext))
 }

  /**
    * Formats the Node in a more human-readable form
    *
    * @param xml An XML node
    * @return Formatted String representation of the node
    */
  def formatXml(xml: Node): String ={
    val prettyPrinter = new scala.xml.PrettyPrinter(80, 2)
    prettyPrinter.format(xml).toString
  }

  /**
    * Delete a directory
    * Taken from http://stackoverflow.com/questions/25999255/delete-directory-recursively-in-scala#25999465
    * @param file
    */
  def deleteRecursively(file: File): Unit = {
    if (file.isDirectory)
      file.listFiles.foreach(deleteRecursively)
    if (file.exists && !file.delete)
      throw new Exception(s"Unable to delete ${file.getAbsolutePath}")
  }
}
