package ug.hix.hixnet2

import com.snatik.storage.Storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.Test
import ug.hix.hixnet2.models.Packet
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap

class LickliderTest {
    private var group : InetAddress? = null
    private var keyCounter = ConcurrentHashMap<String,Int>()
    private val ackTimerMap = ConcurrentHashMap<String, Job>()
    private val seqTimerMap = ConcurrentHashMap<String, Job>()
    private val nextPacket = ConcurrentHashMap<String,Int>()
    private val outOfSeqPackets = ConcurrentHashMap<String,List<Int>>()
    private val receiverBuffer = ConcurrentHashMap<String,ByteArray>()
    private val senderBuffer = ConcurrentHashMap<String, List<Packet>>()
    private val senderCounter = ConcurrentHashMap<String, Int>()
    private val job = Job()
    private val scopeDefault = CoroutineScope(job + Dispatchers.Default)

    private val PORT = 33456
    private var p2pSocket = MulticastSocket(33456)
    private var wlanSocket = MulticastSocket(33456)
    private val p2p0 = NetworkInterface.getByName("p2p0")
    private val wlan0 = NetworkInterface.getByName("wlan0")
    lateinit var sockAddress : InetSocketAddress

//    private val device  = runBlocking {repo.getMyDeviceInfo()}

    private var primaryBuffers = mutableListOf<Packet>()
    private var queueBuffer = mutableListOf<Packet>()

    private val RESEND_TIMEOUT = 600L
    private val ACK_TIMEOUT = 300L

    private var myMessagesQueue = mutableListOf<Packet>()

    @Test
    fun sendBufferTest() {
        return assert(false)
    }
}