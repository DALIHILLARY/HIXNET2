// Code generated by Wire protocol buffer compiler, do not edit.
// Source file: device.proto
package ug.hix.hixnet2.models

import com.squareup.wire.FieldEncoding
import com.squareup.wire.Message
import com.squareup.wire.ProtoAdapter
import com.squareup.wire.ProtoReader
import com.squareup.wire.ProtoWriter
import com.squareup.wire.WireField
import com.squareup.wire.internal.redactElements
import com.squareup.wire.internal.sanitize
import kotlin.Any
import kotlin.AssertionError
import kotlin.Boolean
import kotlin.Deprecated
import kotlin.DeprecationLevel
import kotlin.Int
import kotlin.Nothing
import kotlin.String
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.hashCode
import kotlin.jvm.JvmField
import okio.ByteString

class DeviceNode(    
  @field:WireField(
    tag = 1,
    adapter = "com.squareup.wire.ProtoAdapter#STRING"
  )
  val instanceName: String = "",
  @field:WireField(
    tag = 2,
    adapter = "com.squareup.wire.ProtoAdapter#STRING"
  )
  val meshID: String = "",
  @field:WireField(
    tag = 3,
    adapter = "com.squareup.wire.ProtoAdapter#STRING"
  )
  val serviceAddress: String = "",
  @field:WireField(
    tag = 4,
    adapter = "com.squareup.wire.ProtoAdapter#STRING"
  )
  val multicastAddress: String = "",
  @field:WireField(
    tag = 5,
    adapter = "com.squareup.wire.ProtoAdapter#STRING"
  )
  val macAddress: String = "",
  @field:WireField(
    tag = 6,
    adapter = "ug.hix.hixnet2.models.DeviceNode#ADAPTER",
    label = WireField.Label.REPEATED
  )
  var peers: List<DeviceNode> = emptyList(),
  @field:WireField(
    tag = 7,
    adapter = "ug.hix.hixnet2.models.DeviceNode#ADAPTER"
  )
  val relayDevice: DeviceNode? = null,
  @field:WireField(
    tag = 8,
    adapter = "ug.hix.hixnet2.models.Service#ADAPTER",
    label = WireField.Label.REPEATED
  )
  val services: List<Service> = emptyList(),
  @field:WireField(
    tag = 9,
    adapter = "com.squareup.wire.ProtoAdapter#INT32"
  )
  val Hops: Int = 0,
  @field:WireField(
    tag = 10,
    keyAdapter = "com.squareup.wire.ProtoAdapter#STRING",
    adapter = "com.squareup.wire.ProtoAdapter#STRING"
  )
  val txtRecord: Map<String, String> = emptyMap(),
  @field:WireField(
    tag = 11,
    adapter = "com.squareup.wire.ProtoAdapter#BOOL"
  )
  val hasMaster: Boolean = false,
  @field:WireField(
    tag = 12,
    adapter = "com.squareup.wire.ProtoAdapter#BOOL"
  )
  val isMaster: Boolean = false,
  @field:WireField(
    tag = 13,
    adapter = "com.squareup.wire.ProtoAdapter#BOOL"
  )
  val hasInternetWifi: Boolean = false,
  @field:WireField(
    tag = 14,
    adapter = "com.squareup.wire.ProtoAdapter#STRING"
  )
  val deviceName: String = "",
  unknownFields: ByteString = ByteString.EMPTY
) : Message<DeviceNode, Nothing>(ADAPTER, unknownFields) {
  @Deprecated(
    message = "Shouldn't be used in Kotlin",
    level = DeprecationLevel.HIDDEN
  )
  override fun newBuilder(): Nothing = throw AssertionError()

  override fun equals(other: Any?): Boolean {
    if (other === this) return true
    if (other !is DeviceNode) return false
    return unknownFields == other.unknownFields
        && instanceName == other.instanceName
        && meshID == other.meshID
        && serviceAddress == other.serviceAddress
        && multicastAddress == other.multicastAddress
        && macAddress == other.macAddress
        && peers == other.peers
        && relayDevice == other.relayDevice
        && services == other.services
        && Hops == other.Hops
        && txtRecord == other.txtRecord
        && hasMaster == other.hasMaster
        && isMaster == other.isMaster
        && hasInternetWifi == other.hasInternetWifi
        && deviceName == other.deviceName
  }

  override fun hashCode(): Int {
    var result = super.hashCode
    if (result == 0) {
      result = unknownFields.hashCode()
      result = result * 37 + instanceName.hashCode()
      result = result * 37 + meshID.hashCode()
      result = result * 37 + serviceAddress.hashCode()
      result = result * 37 + multicastAddress.hashCode()
      result = result * 37 + macAddress.hashCode()
      result = result * 37 + peers.hashCode()
      result = result * 37 + relayDevice.hashCode()
      result = result * 37 + services.hashCode()
      result = result * 37 + Hops.hashCode()
      result = result * 37 + txtRecord.hashCode()
      result = result * 37 + hasMaster.hashCode()
      result = result * 37 + isMaster.hashCode()
      result = result * 37 + hasInternetWifi.hashCode()
      result = result * 37 + deviceName.hashCode()
      super.hashCode = result
    }
    return result
  }

  override fun toString(): String {
    val result = mutableListOf<String>()
    result += """instanceName=${sanitize(instanceName)}"""
    result += """meshID=${sanitize(meshID)}"""
    result += """serviceAddress=${sanitize(serviceAddress)}"""
    result += """multicastAddress=${sanitize(multicastAddress)}"""
    result += """macAddress=${sanitize(macAddress)}"""
    if (peers.isNotEmpty()) result += """peers=$peers"""
    if (relayDevice != null) result += """relayDevice=$relayDevice"""
    if (services.isNotEmpty()) result += """services=$services"""
    result += """Hops=$Hops"""
    if (txtRecord.isNotEmpty()) result += """txtRecord=$txtRecord"""
    result += """hasMaster=$hasMaster"""
    result += """isMaster=$isMaster"""
    result += """hasInternetWifi=$hasInternetWifi"""
    result += """deviceName=${sanitize(deviceName)}"""
    return result.joinToString(prefix = "DeviceNode{", separator = ", ", postfix = "}")
  }

  fun copy(
    instanceName: String = this.instanceName,
    meshID: String = this.meshID,
    serviceAddress: String = this.serviceAddress,
    multicastAddress: String = this.multicastAddress,
    macAddress: String = this.macAddress,
    peers: List<DeviceNode> = this.peers,
    relayDevice: DeviceNode? = this.relayDevice,
    services: List<Service> = this.services,
    Hops: Int = this.Hops,
    txtRecord: Map<String, String> = this.txtRecord,
    hasMaster: Boolean = this.hasMaster,
    isMaster: Boolean = this.isMaster,
    hasInternetWifi: Boolean = this.hasInternetWifi,
    deviceName: String = this.deviceName,
    unknownFields: ByteString = this.unknownFields
  ): DeviceNode = DeviceNode(instanceName, meshID, serviceAddress, multicastAddress, macAddress,
      peers, relayDevice, services, Hops, txtRecord, hasMaster, isMaster, hasInternetWifi,
      deviceName, unknownFields)

  companion object {
    @JvmField
    val ADAPTER: ProtoAdapter<DeviceNode> = object : ProtoAdapter<DeviceNode>(
      FieldEncoding.LENGTH_DELIMITED, 
      DeviceNode::class, 
      "type.googleapis.com/ug.hix.hixnet2.models.DeviceNode"
    ) {
      private val txtRecordAdapter: ProtoAdapter<Map<String, String>> =
          ProtoAdapter.newMapAdapter(ProtoAdapter.STRING, ProtoAdapter.STRING)

      override fun encodedSize(value: DeviceNode): Int = 
        ProtoAdapter.STRING.encodedSizeWithTag(1, value.instanceName) +
        ProtoAdapter.STRING.encodedSizeWithTag(2, value.meshID) +
        ProtoAdapter.STRING.encodedSizeWithTag(3, value.serviceAddress) +
        ProtoAdapter.STRING.encodedSizeWithTag(4, value.multicastAddress) +
        ProtoAdapter.STRING.encodedSizeWithTag(5, value.macAddress) +
        DeviceNode.ADAPTER.asRepeated().encodedSizeWithTag(6, value.peers) +
        DeviceNode.ADAPTER.encodedSizeWithTag(7, value.relayDevice) +
        Service.ADAPTER.asRepeated().encodedSizeWithTag(8, value.services) +
        ProtoAdapter.INT32.encodedSizeWithTag(9, value.Hops) +
        txtRecordAdapter.encodedSizeWithTag(10, value.txtRecord) +
        ProtoAdapter.BOOL.encodedSizeWithTag(11, value.hasMaster) +
        ProtoAdapter.BOOL.encodedSizeWithTag(12, value.isMaster) +
        ProtoAdapter.BOOL.encodedSizeWithTag(13, value.hasInternetWifi) +
        ProtoAdapter.STRING.encodedSizeWithTag(14, value.deviceName) +
        value.unknownFields.size

      override fun encode(writer: ProtoWriter, value: DeviceNode) {
        if (value.instanceName != "") ProtoAdapter.STRING.encodeWithTag(writer, 1,
            value.instanceName)
        if (value.meshID != "") ProtoAdapter.STRING.encodeWithTag(writer, 2, value.meshID)
        if (value.serviceAddress != "") ProtoAdapter.STRING.encodeWithTag(writer, 3,
            value.serviceAddress)
        if (value.multicastAddress != "") ProtoAdapter.STRING.encodeWithTag(writer, 4,
            value.multicastAddress)
        if (value.macAddress != "") ProtoAdapter.STRING.encodeWithTag(writer, 5, value.macAddress)
        DeviceNode.ADAPTER.asRepeated().encodeWithTag(writer, 6, value.peers)
        if (value.relayDevice != null) DeviceNode.ADAPTER.encodeWithTag(writer, 7,
            value.relayDevice)
        Service.ADAPTER.asRepeated().encodeWithTag(writer, 8, value.services)
        if (value.Hops != 0) ProtoAdapter.INT32.encodeWithTag(writer, 9, value.Hops)
        txtRecordAdapter.encodeWithTag(writer, 10, value.txtRecord)
        if (value.hasMaster != false) ProtoAdapter.BOOL.encodeWithTag(writer, 11, value.hasMaster)
        if (value.isMaster != false) ProtoAdapter.BOOL.encodeWithTag(writer, 12, value.isMaster)
        if (value.hasInternetWifi != false) ProtoAdapter.BOOL.encodeWithTag(writer, 13,
            value.hasInternetWifi)
        if (value.deviceName != "") ProtoAdapter.STRING.encodeWithTag(writer, 14, value.deviceName)
        writer.writeBytes(value.unknownFields)
      }

      override fun decode(reader: ProtoReader): DeviceNode {
        var instanceName: String = ""
        var meshID: String = ""
        var serviceAddress: String = ""
        var multicastAddress: String = ""
        var macAddress: String = ""
        val peers = mutableListOf<DeviceNode>()
        var relayDevice: DeviceNode? = null
        val services = mutableListOf<Service>()
        var Hops: Int = 0
        val txtRecord = mutableMapOf<String, String>()
        var hasMaster: Boolean = false
        var isMaster: Boolean = false
        var hasInternetWifi: Boolean = false
        var deviceName: String = ""
        val unknownFields = reader.forEachTag { tag ->
          when (tag) {
            1 -> instanceName = ProtoAdapter.STRING.decode(reader)
            2 -> meshID = ProtoAdapter.STRING.decode(reader)
            3 -> serviceAddress = ProtoAdapter.STRING.decode(reader)
            4 -> multicastAddress = ProtoAdapter.STRING.decode(reader)
            5 -> macAddress = ProtoAdapter.STRING.decode(reader)
            6 -> peers.add(DeviceNode.ADAPTER.decode(reader))
            7 -> relayDevice = DeviceNode.ADAPTER.decode(reader)
            8 -> services.add(Service.ADAPTER.decode(reader))
            9 -> Hops = ProtoAdapter.INT32.decode(reader)
            10 -> txtRecord.putAll(txtRecordAdapter.decode(reader))
            11 -> hasMaster = ProtoAdapter.BOOL.decode(reader)
            12 -> isMaster = ProtoAdapter.BOOL.decode(reader)
            13 -> hasInternetWifi = ProtoAdapter.BOOL.decode(reader)
            14 -> deviceName = ProtoAdapter.STRING.decode(reader)
            else -> reader.readUnknownField(tag)
          }
        }
        return DeviceNode(
          instanceName = instanceName,
          meshID = meshID,
          serviceAddress = serviceAddress,
          multicastAddress = multicastAddress,
          macAddress = macAddress,
          peers = peers,
          relayDevice = relayDevice,
          services = services,
          Hops = Hops,
          txtRecord = txtRecord,
          hasMaster = hasMaster,
          isMaster = isMaster,
          hasInternetWifi = hasInternetWifi,
          deviceName = deviceName,
          unknownFields = unknownFields
        )
      }

      override fun redact(value: DeviceNode): DeviceNode = value.copy(
        peers = value.peers.redactElements(DeviceNode.ADAPTER),
        relayDevice = value.relayDevice?.let(DeviceNode.ADAPTER::redact),
        services = value.services.redactElements(Service.ADAPTER),
        unknownFields = ByteString.EMPTY
      )
    }
  }
}