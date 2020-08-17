package ug.hix.hixnet2

import android.content.Intent
import android.os.Build
import android.os.Bundle

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider

import ug.hix.hixnet2.models.DeviceNode
import ug.hix.hixnet2.services.MeshDaemon

import kotlinx.android.synthetic.main.activity_home.*
import ug.hix.hixnet2.fragments.FileFragment

class HomeActivity : AppCompatActivity() {
        var deviceInstance = DeviceNode()
        val TAG = javaClass.simpleName


        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_home)

            val filesFragment = FileFragment(this)

            if(MeshDaemon.isServiceRunning){
                fabStart?.setImageResource(R.drawable.ic_stop)

            }else{
                fabStart?.setImageResource(R.drawable.ic_start)

            }
            fabStart?.setOnClickListener {
                val intent = Intent(this,MeshDaemon::class.java)
                if(MeshDaemon.isServiceRunning){

                    stopService(intent)
                    fabStart?.setImageResource(R.drawable.ic_start)

                }else{
                    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
                        startForegroundService(intent)
                    }else{
                        startService(intent)

                    }
                    fabStart?.setImageResource(R.drawable.ic_stop)
                }


            }

            bottomAppBar?.setOnMenuItemClickListener {
                when(it.itemId){
                    R.id.menuFiles -> loadFragment(filesFragment)
                }
                true
            }


        }

    private fun loadFragment(fragment: FileFragment){
        supportFragmentManager.beginTransaction().apply {
            replace(R.id.fl_wrapper,fragment)
            commit()
        }
    }

}
