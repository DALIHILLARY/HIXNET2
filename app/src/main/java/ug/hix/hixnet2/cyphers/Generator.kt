package ug.hix.hixnet2.cyphers

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.util.Base64
import android.util.Log
import ug.hix.hixnet2.database.DeviceDatabase
import ug.hix.hixnet2.database.WifiConfigDatabase
import ug.hix.hixnet2.services.MeshDaemon
import java.security.*
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*

open class Generator {



    companion object : Generator() {
        private val TAG = javaClass.simpleName
        private lateinit var pubKeyS : String
        private lateinit var priKeyS : String
        private lateinit var pid    : String

        private var deviceInstance : DeviceDatabase? = null
        private var configInstance : WifiConfigDatabase? = null

        //private  var deviceDb = DeviceDatabase.dbInstance(MeshDaemon().applicationContext)

        fun getDeviceInstance(context : Context) : DeviceDatabase{
            if(deviceInstance == null){
                deviceInstance = DeviceDatabase.dbInstance(context.applicationContext)
            }

            return deviceInstance as DeviceDatabase
        }

        fun getConfigInstance(context: Context) : WifiConfigDatabase{
            if(configInstance == null){
                configInstance = WifiConfigDatabase.dbInstance(context.applicationContext)
            }
            return configInstance as WifiConfigDatabase
        }
        
        private fun createKeys(){

            val kpg = KeyPairGenerator.getInstance("RSA")
            kpg.initialize(2048)
            val kp = kpg.genKeyPair()

            val pubKey = kp.public  //public key
            val priKey = kp.private    //private key

            pubKeyS = Base64.encodeToString(pubKey.encoded, Base64.DEFAULT)
            priKeyS = Base64.encodeToString(priKey.encoded, Base64.DEFAULT)

            val digest : MessageDigest = MessageDigest.getInstance("SHA-256")
            val encodedHash = digest.digest(pubKey.encoded)

            pid   = Base64.encodeToString(encodedHash, Base64.DEFAULT)


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
            var keyDB : SQLiteDatabase? = null

            try{
                Log.i(TAG, "opening db if exists")
                keyDB = SQLiteDatabase.openDatabase("keycipher.db",null,SQLiteDatabase.OPEN_READONLY)

                val result : Cursor = keyDB.rawQuery("Select * from Keys",null)

                //the hard fucking work
                result.moveToFirst()
                priKeyS =   result.getString(1)
                result.moveToNext()
                pubKeyS  = result.getString(1)
                result.moveToLast()
                pid     = result.getString(1)

                result.close()
                keyDB.close()

            }catch (error : SQLiteException){
                createKeys() //initial run

                Log.d(TAG, "First time creating ")
                keyDB =  SQLiteDatabase.openOrCreateDatabase("keycipher.db",null)

                keyDB?.execSQL("CREATE TABLE IF NOT EXISTS Keys(Name VARCHAR, Value VARCHAR)")
                keyDB?.execSQL("INSERT INTO keys VALUES('privateKey','$priKeyS')")
                keyDB?.execSQL("INSERT INTO keys VALUES('publicKey','$pubKeyS')")
                keyDB?.execSQL("INSERT INTO keys VALUES('PID','$pid')")

            }
        }
    }


}