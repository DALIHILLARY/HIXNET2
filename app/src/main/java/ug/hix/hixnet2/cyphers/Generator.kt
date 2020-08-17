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


open class Generator {

    companion object : Generator() {
        private val TAG = "Generator"
        private lateinit var pubKeyS : String
        private lateinit var priKeyS : String
        private lateinit var pid    : String
        private lateinit var availableAddress : MutableList<String>

        private var hixNetInstance : HixNetDatabase? = null
        private var configInstance : HixNetDatabase? = null

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

            //store keys in database
            val device = DeviceNode(meshID = pid, privateKey = priKeyS, publicKey = pubKeyS, isMe = true)
            deviceDb?.addDevice(device)

        }

        fun getWifiPassphrase() : Pair<String, String> {
            val  alphaNumericString  = "QWERTYUIOPLKJHGFDSAZXCVBNMqwertyuioplkjhgfdsazxcvbnm1234567890"
            lateinit var passKey : String
            lateinit var hotspotName : String

            passKey = alphaNumericString.toList().shuffled().joinToString(separator = "",limit = 8, truncated = "")

            hotspotName = "HixNet" + passKey.reversed()

            val instanceName = "$hotspotName:$passKey"

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

        fun getMutliAddress() : String{
            var address = ""
            while (true){
                val number = (111111111 + Random().nextInt(255255254 - 11111111 + 1)).toString().chunked(3)
                address = number.joinToString(separator = ".")

                if (availableAddress.contains(address)){
                    continue
                }else{
                    availableAddress.add(address)
                    break
                }
            }
            Log.d(TAG,"Generated address:  $address")

            return availableAddress[-1]

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

            if(pid.length < 6){
                createKeys()
            }
        }
    }


}