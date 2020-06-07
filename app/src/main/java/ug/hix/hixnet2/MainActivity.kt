package ug.hix.hixnet2

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.os.HandlerCompat.postDelayed
import kotlinx.android.synthetic.main.activity_main.*
import ug.hix.hixnet2.meshlink.MeshServiceManager
import ug.hix.hixnet2.meshlink.NetworkCardManager
import ug.hix.hixnet2.models.DeviceNode
//import ug.hix.hixnet2.MeshDaemon
import java.util.*

class MainActivity : AppCompatActivity() {
    var deviceInstance = DeviceNode()
    val mContext : Context = this
    val TAG = javaClass.simpleName



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val cardManager = NetworkCardManager(this)
        val manager = mContext.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        val channel =  manager.initialize(mContext,mContext.mainLooper, null)
        var serviceHandler = MeshServiceManager(this,manager,channel)


        toggleButton.setOnCheckedChangeListener { _, isChecked ->
            if(isChecked) {
                serviceHandler.registerService()
                serviceHandler.startServiceDiscover()

            }else{
                //do nothing
                Toast.makeText(this,"service stop",Toast.LENGTH_SHORT).show()
            }
        }

    }
}

