package ug.hix.hixnet2

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.knexis.hotspot.Hotspot
import kotlinx.android.synthetic.main.activity_main.*
import ug.hix.hixnet2.cyphers.Generator
import ug.hix.hixnet2.services.MeshDaemon
//import ug.hix.hixnet2.MeshDaemon

class MainActivity : AppCompatActivity() {
    var deviceInstance = DeviceNode()
    val TAG = javaClass.simpleName


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val hotspot = Hotspot(this)

        toggleButton.setOnCheckedChangeListener { _, isChecked ->
            if(isChecked) {
                val (hotspotName,keyPass) = Generator.getWifiPassphrase()

                startService(Intent(this,MeshDaemon::class.java))

            }else{
                //do nothing
                Toast.makeText(this,"service stop",Toast.LENGTH_SHORT).show()
                stopService(Intent(this,MeshDaemon::class.java))
            }
        }

    }
}

