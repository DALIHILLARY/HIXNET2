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

class PFileName(
  @field:WireField(
    tag = 1,
    adapter = "com.squareup.wire.ProtoAdapter#STRING"
  )
  val cid: String = "",
  @field:WireField(
    tag = 2,
    adapter = "com.squareup.wire.ProtoAdapter#STRING"
  )
  val name_slub: String = "",
  @field:WireField(
    tag = 3,
    adapter = "com.squareup.wire.ProtoAdapter#STRING"
  )
  val status: String = "",
  @field:WireField(
    tag = 4,
    adapter = "com.squareup.wire.ProtoAdapter#STRING"
  )
  val modified_by: String = "",
  @field:WireField(
    tag = 5,
    adapter = "com.squareup.wire.ProtoAdapter#STRING"
  )
  val modified: String = "",
  /**
   * helloAck hello fileNameUpdate
   */
  @field:WireField(
    tag = 6,
    adapter = "com.squareup.wire.ProtoAdapter#STRING"
  )
  val type: String = "",
  @field:WireField(
    tag = 7,
    adapter = "com.squareup.wire.ProtoAdapter#INT32"
  )
  val file_size: Int = 0,
  unknownFields: ByteString = ByteString.EMPTY
) : Message<PFileName, Nothing>(ADAPTER, unknownFields) {
  @Deprecated(
    message = "Shouldn't be used in Kotlin",
    level = DeprecationLevel.HIDDEN
  )
  override fun newBuilder(): Nothing = throw AssertionError()

  override fun equals(other: Any?): Boolean {
    if (other === this) return true
    if (other !is PFileName) return false
    return unknownFields == other.unknownFields
        && cid == other.cid
        && name_slub == other.name_slub
        && status == other.status
        && modified_by == other.modified_by
        && modified == other.modified
        && type == other.type
        && file_size == other.file_size
  }

  override fun hashCode(): Int {
    var result = super.hashCode
    if (result == 0) {
      result = unknownFields.hashCode()
      result = result * 37 + cid.hashCode()
      result = result * 37 + name_slub.hashCode()
      result = result * 37 + status.hashCode()
      result = result * 37 + modified_by.hashCode()
      result = result * 37 + modified.hashCode()
      result = result * 37 + type.hashCode()
      result = result * 37 + file_size.hashCode()
      super.hashCode = result
    }
    return result
  }

  override fun toString(): String {
    val result = mutableListOf<String>()
    result += """cid=${sanitize(cid)}"""
    result += """name_slub=${sanitize(name_slub)}"""
    result += """status=${sanitize(status)}"""
    result += """modified_by=${sanitize(modified_by)}"""
    result += """modified=${sanitize(modified)}"""
    result += """type=${sanitize(type)}"""
    result += """file_size=$file_size"""
    return result.joinToString(prefix = "PFileName{", separator = ", ", postfix = "}")
  }

  fun copy(
    cid: String = this.cid,
    name_slub: String = this.name_slub,
    status: String = this.status,
    modified_by: String = this.modified_by,
    modified: String = this.modified,
    type: String = this.type,
    file_size: Int = this.file_size,
    unknownFields: ByteString = this.unknownFields
  ): PFileName = PFileName(cid, name_slub, status, modified_by, modified, type, file_size,
      unknownFields)

  companion object {
    @JvmField
    val ADAPTER: ProtoAdapter<PFileName> = object : ProtoAdapter<PFileName>(
      FieldEncoding.LENGTH_DELIMITED, 
      PFileName::class, 
      "type.googleapis.com/ug.hix.hixnet2.models.PFileName"
    ) {
      override fun encodedSize(value: PFileName): Int = 
        ProtoAdapter.STRING.encodedSizeWithTag(1, value.cid) +
        ProtoAdapter.STRING.encodedSizeWithTag(2, value.name_slub) +
        ProtoAdapter.STRING.encodedSizeWithTag(3, value.status) +
        ProtoAdapter.STRING.encodedSizeWithTag(4, value.modified_by) +
        ProtoAdapter.STRING.encodedSizeWithTag(5, value.modified) +
        ProtoAdapter.STRING.encodedSizeWithTag(6, value.type) +
        ProtoAdapter.INT32.encodedSizeWithTag(7, value.file_size) +
        value.unknownFields.size

      override fun encode(writer: ProtoWriter, value: PFileName) {
        if (value.cid != "") ProtoAdapter.STRING.encodeWithTag(writer, 1, value.cid)
        if (value.name_slub != "") ProtoAdapter.STRING.encodeWithTag(writer, 2, value.name_slub)
        if (value.status != "") ProtoAdapter.STRING.encodeWithTag(writer, 3, value.status)
        if (value.modified_by != "") ProtoAdapter.STRING.encodeWithTag(writer, 4, value.modified_by)
        if (value.modified != "") ProtoAdapter.STRING.encodeWithTag(writer, 5, value.modified)
        if (value.type != "") ProtoAdapter.STRING.encodeWithTag(writer, 6, value.type)
        if (value.file_size != 0) ProtoAdapter.INT32.encodeWithTag(writer, 7, value.file_size)
        writer.writeBytes(value.unknownFields)
      }

      override fun decode(reader: ProtoReader): PFileName {
        var cid: String = ""
        var name_slub: String = ""
        var status: String = ""
        var modified_by: String = ""
        var modified: String = ""
        var type: String = ""
        var file_size: Int = 0
        val unknownFields = reader.forEachTag { tag ->
          when (tag) {
            1 -> cid = ProtoAdapter.STRING.decode(reader)
            2 -> name_slub = ProtoAdapter.STRING.decode(reader)
            3 -> status = ProtoAdapter.STRING.decode(reader)
            4 -> modified_by = ProtoAdapter.STRING.decode(reader)
            5 -> modified = ProtoAdapter.STRING.decode(reader)
            6 -> type = ProtoAdapter.STRING.decode(reader)
            7 -> file_size = ProtoAdapter.INT32.decode(reader)
            else -> reader.readUnknownField(tag)
          }
        }
        return PFileName(
          cid = cid,
          name_slub = name_slub,
          status = status,
          modified_by = modified_by,
          modified = modified,
          type = type,
          file_size = file_size,
          unknownFields = unknownFields
        )
      }

      override fun redact(value: PFileName): PFileName = value.copy(
        unknownFields = ByteString.EMPTY
      )
    }
  }
}
