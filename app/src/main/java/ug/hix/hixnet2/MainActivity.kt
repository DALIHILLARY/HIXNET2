package ug.hix.hixnet2

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.HandlerCompat.postDelayed
import kotlinx.android.synthetic.main.activity_main.*
import ug.hix.hixnet2.interlink.salut.Callbacks.SalutCallback
import ug.hix.hixnet2.interlink.salut.Callbacks.SalutDataCallback
import ug.hix.hixnet2.interlink.salut.Callbacks.SalutDeviceCallback
import ug.hix.hixnet2.interlink.salut.Salut
import ug.hix.hixnet2.interlink.salut.SalutDataReceiver
import ug.hix.hixnet2.interlink.salut.SalutServiceData
import com.thanosfisherman.wifiutils.*
import com.thanosfisherman.wifiutils.wifiConnect.ConnectionErrorCode
import com.thanosfisherman.wifiutils.wifiConnect.ConnectionSuccessListener
import ug.hix.hixnet2.interlink.connect.ConnectManager
import java.util.*

class MainActivity : AppCompatActivity() {

    inner class MySalut(dataReceiver: SalutDataReceiver?, salutServiceData: SalutServiceData?, deviceNotSupported: SalutCallback?) : Salut(dataReceiver, salutServiceData, deviceNotSupported) {
        override fun serialize(o: Any?): String {
            TODO("Not yet implemented")
        }
    }


    val  TAG = javaClass.simpleName

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val dataReceiver = SalutDataReceiver(this, SalutDataCallback {

        })

        val instanceName = "Demo ID ${Random().nextInt(200)}"

        textView.text = "My Instance Name: $instanceName\n"

        val serviceData = SalutServiceData("sas", 50489, instanceName)

        val salut = MySalut(dataReceiver, serviceData, SalutCallback {
            Log.e(TAG, "Device does not support WiFi P2P")
        })
        var ssidPass = ""

        toggleButton.setOnCheckedChangeListener { _, isChecked ->
            if(isChecked) {
                salut.createGroup(null, null)
                Toast.makeText(this, "started", Toast.LENGTH_SHORT)
                textView.text = textView.text as String + "Group started\n"


                salut.startNetworkService({
                    textView.text = textView.text as String +"fix"+ it.instanceName
                    Toast.makeText(this, it.readableName + " connected.", Toast.LENGTH_SHORT).show();
                    }, {
                        textView.text = textView.text as String + "Network service Started\n"
                        Log.d(TAG, "Network service started")
                    }, {
                        textView.text = textView.text as String + "Network service cant start\n"
                        Log.e(TAG, "Can not start network service")
                    })

                Handler().postDelayed({
                    for (device in salut.foundDevices){
                        textView.text = textView.text as String + device.instanceName
                    }
                },3000)

//
//                Handler().postDelayed({
//                    Log.v(TAG,"INSIDE COONECTION HANDLER")
//                    if(ssidPass.startsWith("DIRECT")) {
//                        Log.v(TAG,"INSIDE IF")
//
//                        val passCode = ssidPass.split(":").toTypedArray()
//                        Log.d(TAG,"passcode ${passCode[0]},  ${passCode[1]}")
//
//                        ConnectManager(applicationContext,passCode[0],passCode[1])
//                    }
//
//
//
//                },3000)


            }else{
                salut.stopServiceDiscovery(true)
                textView.text = textView.text as String + "Network service Stopped\n"

            }
        }

    }
}

