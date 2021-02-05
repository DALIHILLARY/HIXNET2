package ug.hix.hixnet2.cyphers

import android.content.Context
import android.util.Base64
import android.util.Log

import java.security.*
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*

import ug.hix.hixnet2.database.HixNetDatabase
import ug.hix.hixnet2.database.DeviceNode
import ug.hix.hixnet2.util.Base58
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import ug.hix.hixnet2.models.DeviceNode as Device

import java.lang.Exception
import java.net.NetworkInterface


open class Generator {

    companion object : Generator() {
        private val TAG = "Generator"
        private lateinit var pubKeyS : String
        private lateinit var priKeyS : String
        private lateinit var pid    : String

        private var hixNetInstance : HixNetDatabase? = null

        fun getDatabaseInstance(context : Context) : HixNetDatabase{
            if(hixNetInstance == null){
                hixNetInstance = HixNetDatabase.dbInstance(context.applicationContext)
            }

            return hixNetInstance as HixNetDatabase
        }

        
        private fun createKeys(){
            val deviceDb = hixNetInstance?.deviceNodeDao()

            val kpg = KeyPairGenerator.getInstance("RSA")
            kpg.initialize(2048)
            val kp = kpg.genKeyPair()

            val pubKey = kp.public  //public key
            val priKey = kp.private    //private key

            pubKeyS = Base64.encodeToString(pubKey.encoded, Base64.DEFAULT)
            priKeyS = Base64.encodeToString(priKey.encoded, Base64.DEFAULT)

            val digest : MessageDigest = MessageDigest.getInstance("SHA-256")
            val encodedHash = digest.digest(pubKey.encoded)

            pid = "HixNet${Base58.encode(encodedHash)}"
            val macAddress = getMacAddress()
            val multicastAddress =  getMultiAddress(null,null)
            //store keys in database
            val device = DeviceNode(meshID = pid, privateKey = priKeyS, publicKey = pubKeyS, multicastAddress = multicastAddress, isMe = true)
            deviceDb?.addDevice(device)

        }

        fun getWifiPassphrase() : Pair<String, String> {
            val  alphaNumericString  = "QWERTYUIOPLKJHGFDSAZXCVBNMqwertyuioplkjhgfdsazxcvbnm1234567890"
            lateinit var passKey : String
            lateinit var hotspotName : String

            passKey = alphaNumericString.toList().shuffled().joinToString(separator = "",limit = 8, truncated = "")

            hotspotName = "HixNet" + passKey.reversed()

            return Pair(hotspotName,passKey)
        }

        fun genMID() : String{
            val uuid = UUID.randomUUID().toString()
            uuid.replace("-","")
            return uuid
        }

        fun getPID() : String{
            return pid
        }

        fun getMultiAddress(device: Device?, scanAddress: String?) : String{
            var address = ""
            if(device != null && scanAddress != null){
                val badAddresses = getBadMultiAddress(device).split("::")
                val scanAddresses = scanAddress.split("::")

                if(!scanAddresses.contains(device.multicastAddress) && !badAddresses.contains(scanAddresses[0]) ){
                    Log.d(TAG,"Generated address:  ${device.multicastAddress}")

                    return device.multicastAddress
                }else{
                    while (true){
                        address = addressGen()

                        if (badAddresses.contains(address) && scanAddresses.contains(address)){
                            continue
                        }else{
                            Log.d(TAG,"Generated address:  $address")
                            device.copy(multicastAddress = address)
                            return device.multicastAddress
                        }
                    }

                }

            }else{
                return  addressGen()
            }

        }
        private fun addressGen() : String {
            val numberList = mutableListOf<String>()
            var count = 0
            while(true){
                count += 1
                if(count > 3){
                    break
                }
                var number = (1 + Random().nextInt(254 - 1)).toString()
                if(number.length == 1){
                    number = "00$number"
                }else if(number.length == 2){
                    number = "0$number"
                }
                numberList.add(number)

            }
            return "230." + numberList.joinToString(separator = ".")

        }
        fun getBadMultiAddress(device: Device) : String{
            var addresses = device.multicastAddress
            device.peers.forEach {peer ->
                if(peer.multicastAddress.contains(".")){
                    addresses += "::${peer.multicastAddress}"
                }
            }
            return addresses
        }

        fun getPrivateKey() : PrivateKey{
            val priKey = Base64.decode(priKeyS,Base64.DEFAULT) as ByteArray
            val ks   = PKCS8EncodedKeySpec(priKey)
            val kf = KeyFactory.getInstance("RSA")

            return kf.generatePrivate(ks)
        }

        fun getPublicKey()  : PublicKey{
            val pubKey = Base64.decode(pubKeyS,Base64.DEFAULT) as ByteArray
            val ks = X509EncodedKeySpec(pubKey)
            val kf = KeyFactory.getInstance("RSA")

            return kf.generatePublic(ks)
        }

        fun loadKeys(){
            val deviceDb = hixNetInstance?.deviceNodeDao()
            priKeyS = deviceDb?.getMyPrivateKey().toString()
            pubKeyS = deviceDb?.getMyPublicKey().toString()
            pid     = deviceDb?.getMyPid().toString()

            Log.d(TAG," $pid,\n $pubKeyS")

            if(pid == "null"){
                createKeys()
            }
        }
        private fun getMacAddress() : String {
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces().toList()

                for (nif in interfaces){
                    if(nif.name == "wlan0"){
                        val macBytes = nif.hardwareAddress ?: return ""

                        val res1 = StringBuffer()
                        macBytes.forEach {
                            res1.append(String.format("%02X:",it))
                        }

                        if(res1.isNotEmpty()){
                            res1.deleteCharAt(res1.length - 1)
                        }

                        return res1.toString()
                    }

                }
            }catch (e : Exception){ }
            return "02:00:00:00:00:00:00"
        }
        fun getEncodedHash(file: File) : ByteArray{
            val digest : MessageDigest = MessageDigest.getInstance("SHA-256")
            val fileStream = FileInputStream(file)
            val digestStream = DigestInputStream(fileStream,digest)
            var readBuffer = ByteArray(1024*1024)

            while(true){
                if (digestStream.available() < 1024 * 1024) {
                    if (digestStream.available() == 0) break
                    readBuffer = ByteArray((digestStream.available().rem(1024 * 1024)))
                }
                if(digestStream.read(readBuffer,0,readBuffer.size) < 0) break
            }

            fileStream.close()
            digestStream.close()
            return digestStream.messageDigest.digest()

        }
    }

}