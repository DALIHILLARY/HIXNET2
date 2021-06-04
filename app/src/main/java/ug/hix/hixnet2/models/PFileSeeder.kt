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

class PFileSeeder(
  @field:WireField(
    tag = 1,
    adapter = "com.squareup.wire.ProtoAdapter#STRING"
  )
  val cid: String = "",
  @field:WireField(
    tag = 2,
    adapter = "com.squareup.wire.ProtoAdapter#STRING"
  )
  val meshId: String = "",
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
   * helloAck hello fileSeederUpdate
   */
  @field:WireField(
    tag = 6,
    adapter = "com.squareup.wire.ProtoAdapter#STRING"
  )
  val type: String = "",
  unknownFields: ByteString = ByteString.EMPTY
) : Message<PFileSeeder, Nothing>(ADAPTER, unknownFields) {
  @Deprecated(
    message = "Shouldn't be used in Kotlin",
    level = DeprecationLevel.HIDDEN
  )
  override fun newBuilder(): Nothing = throw AssertionError()

  override fun equals(other: Any?): Boolean {
    if (other === this) return true
    if (other !is PFileSeeder) return false
    return unknownFields == other.unknownFields
        && cid == other.cid
        && meshId == other.meshId
        && status == other.status
        && modified_by == other.modified_by
        && modified == other.modified
        && type == other.type
  }

  override fun hashCode(): Int {
    var result = super.hashCode
    if (result == 0) {
      result = unknownFields.hashCode()
      result = result * 37 + cid.hashCode()
      result = result * 37 + meshId.hashCode()
      result = result * 37 + status.hashCode()
      result = result * 37 + modified_by.hashCode()
      result = result * 37 + modified.hashCode()
      result = result * 37 + type.hashCode()
      super.hashCode = result
    }
    return result
  }

  override fun toString(): String {
    val result = mutableListOf<String>()
    result += """cid=${sanitize(cid)}"""
    result += """meshId=${sanitize(meshId)}"""
    result += """status=${sanitize(status)}"""
    result += """modified_by=${sanitize(modified_by)}"""
    result += """modified=${sanitize(modified)}"""
    result += """type=${sanitize(type)}"""
    return result.joinToString(prefix = "PFileSeeder{", separator = ", ", postfix = "}")
  }

  fun copy(
    cid: String = this.cid,
    meshId: String = this.meshId,
    status: String = this.status,
    modified_by: String = this.modified_by,
    modified: String = this.modified,
    type: String = this.type,
    unknownFields: ByteString = this.unknownFields
  ): PFileSeeder = PFileSeeder(cid, meshId, status, modified_by, modified, type, unknownFields)

  companion object {
    @JvmField
    val ADAPTER: ProtoAdapter<PFileSeeder> = object : ProtoAdapter<PFileSeeder>(
      FieldEncoding.LENGTH_DELIMITED, 
      PFileSeeder::class, 
      "type.googleapis.com/ug.hix.hixnet2.models.PFileSeeder"
    ) {
      override fun encodedSize(value: PFileSeeder): Int = 
        ProtoAdapter.STRING.encodedSizeWithTag(1, value.cid) +
        ProtoAdapter.STRING.encodedSizeWithTag(2, value.meshId) +
        ProtoAdapter.STRING.encodedSizeWithTag(3, value.status) +
        ProtoAdapter.STRING.encodedSizeWithTag(4, value.modified_by) +
        ProtoAdapter.STRING.encodedSizeWithTag(5, value.modified) +
        ProtoAdapter.STRING.encodedSizeWithTag(6, value.type) +
        value.unknownFields.size

      override fun encode(writer: ProtoWriter, value: PFileSeeder) {
        if (value.cid != "") ProtoAdapter.STRING.encodeWithTag(writer, 1, value.cid)
        if (value.meshId != "") ProtoAdapter.STRING.encodeWithTag(writer, 2, value.meshId)
        if (value.status != "") ProtoAdapter.STRING.encodeWithTag(writer, 3, value.status)
        if (value.modified_by != "") ProtoAdapter.STRING.encodeWithTag(writer, 4, value.modified_by)
        if (value.modified != "") ProtoAdapter.STRING.encodeWithTag(writer, 5, value.modified)
        if (value.type != "") ProtoAdapter.STRING.encodeWithTag(writer, 6, value.type)
        writer.writeBytes(value.unknownFields)
      }

      override fun decode(reader: ProtoReader): PFileSeeder {
        var cid: String = ""
        var meshId: String = ""
        var status: String = ""
        var modified_by: String = ""
        var modified: String = ""
        var type: String = ""
        val unknownFields = reader.forEachTag { tag ->
          when (tag) {
            1 -> cid = ProtoAdapter.STRING.decode(reader)
            2 -> meshId = ProtoAdapter.STRING.decode(reader)
            3 -> status = ProtoAdapter.STRING.decode(reader)
            4 -> modified_by = ProtoAdapter.STRING.decode(reader)
            5 -> modified = ProtoAdapter.STRING.decode(reader)
            6 -> type = ProtoAdapter.STRING.decode(reader)
            else -> reader.readUnknownField(tag)
          }
        }
        return PFileSeeder(
          cid = cid,
          meshId = meshId,
          status = status,
          modified_by = modified_by,
          modified = modified,
          type = type,
          unknownFields = unknownFields
        )
      }

      override fun redact(value: PFileSeeder): PFileSeeder = value.copy(
        unknownFields = ByteString.EMPTY
      )
    }
  }
}
