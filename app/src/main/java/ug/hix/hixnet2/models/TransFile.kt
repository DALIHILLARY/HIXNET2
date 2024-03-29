// Code generated by Wire protocol buffer compiler, do not edit.
// Source file: device.proto
package ug.hix.hixnet2.models

import com.squareup.wire.FieldEncoding
import com.squareup.wire.Message
import com.squareup.wire.ProtoAdapter
import com.squareup.wire.ProtoReader
import com.squareup.wire.ProtoWriter
import com.squareup.wire.WireField
import com.squareup.wire.internal.sanitize
import kotlin.Any
import kotlin.AssertionError
import kotlin.Boolean
import kotlin.Deprecated
import kotlin.DeprecationLevel
import kotlin.Int
import kotlin.Nothing
import kotlin.String
import kotlin.hashCode
import kotlin.jvm.JvmField
import okio.ByteString

class TransFile(
  @field:WireField(
    tag = 1,
    adapter = "com.squareup.wire.ProtoAdapter#STRING"
  )
  val fileName: String = "",
  @field:WireField(
    tag = 2,
    adapter = "com.squareup.wire.ProtoAdapter#STRING"
  )
  val extension: String = "",
  @field:WireField(
    tag = 3,
    adapter = "com.squareup.wire.ProtoAdapter#BYTES"
  )
  val fileContent: ByteString = ByteString.EMPTY,
  unknownFields: ByteString = ByteString.EMPTY
) : Message<TransFile, Nothing>(ADAPTER, unknownFields) {
  @Deprecated(
    message = "Shouldn't be used in Kotlin",
    level = DeprecationLevel.HIDDEN
  )
  override fun newBuilder(): Nothing = throw AssertionError()

  override fun equals(other: Any?): Boolean {
    if (other === this) return true
    if (other !is TransFile) return false
    return unknownFields == other.unknownFields
        && fileName == other.fileName
        && extension == other.extension
        && fileContent == other.fileContent
  }

  override fun hashCode(): Int {
    var result = super.hashCode
    if (result == 0) {
      result = unknownFields.hashCode()
      result = result * 37 + fileName.hashCode()
      result = result * 37 + extension.hashCode()
      result = result * 37 + fileContent.hashCode()
      super.hashCode = result
    }
    return result
  }

  override fun toString(): String {
    val result = mutableListOf<String>()
    result += """fileName=${sanitize(fileName)}"""
    result += """extension=${sanitize(extension)}"""
    result += """fileContent=$fileContent"""
    return result.joinToString(prefix = "TransFile{", separator = ", ", postfix = "}")
  }

  fun copy(
    fileName: String = this.fileName,
    extension: String = this.extension,
    fileContent: ByteString = this.fileContent,
    unknownFields: ByteString = this.unknownFields
  ): TransFile = TransFile(fileName, extension, fileContent, unknownFields)

  companion object {
    @JvmField
    val ADAPTER: ProtoAdapter<TransFile> = object : ProtoAdapter<TransFile>(
      FieldEncoding.LENGTH_DELIMITED, 
      TransFile::class, 
      "type.googleapis.com/ug.hix.hixnet2.models.TransFile"
    ) {
      override fun encodedSize(value: TransFile): Int = 
        ProtoAdapter.STRING.encodedSizeWithTag(1, value.fileName) +
        ProtoAdapter.STRING.encodedSizeWithTag(2, value.extension) +
        ProtoAdapter.BYTES.encodedSizeWithTag(3, value.fileContent) +
        value.unknownFields.size

      override fun encode(writer: ProtoWriter, value: TransFile) {
        if (value.fileName != "") ProtoAdapter.STRING.encodeWithTag(writer, 1, value.fileName)
        if (value.extension != "") ProtoAdapter.STRING.encodeWithTag(writer, 2, value.extension)
        if (value.fileContent != ByteString.EMPTY) ProtoAdapter.BYTES.encodeWithTag(writer, 3,
            value.fileContent)
        writer.writeBytes(value.unknownFields)
      }

      override fun decode(reader: ProtoReader): TransFile {
        var fileName: String = ""
        var extension: String = ""
        var fileContent: ByteString = ByteString.EMPTY
        val unknownFields = reader.forEachTag { tag ->
          when (tag) {
            1 -> fileName = ProtoAdapter.STRING.decode(reader)
            2 -> extension = ProtoAdapter.STRING.decode(reader)
            3 -> fileContent = ProtoAdapter.BYTES.decode(reader)
            else -> reader.readUnknownField(tag)
          }
        }
        return TransFile(
          fileName = fileName,
          extension = extension,
          fileContent = fileContent,
          unknownFields = unknownFields
        )
      }

      override fun redact(value: TransFile): TransFile = value.copy(
        unknownFields = ByteString.EMPTY
      )
    }
  }
}
