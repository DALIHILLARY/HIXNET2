package ug.hix.hixnet2

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.FrameLayout

import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.bottomappbar.BottomAppBar
import com.google.android.material.floatingactionbutton.FloatingActionButton

import ug.hix.hixnet2.models.DeviceNode
import ug.hix.hixnet2.services.MeshDaemon

import kotlinx.coroutines.*
import ug.hix.hixnet2.viewmodel.HomeViewModel

class HomeActivity : AppCompatActivity(), CoroutineScope by MainScope(){
    private val TAG = javaClass.simpleName
    private var fabLastClick = 0L
    private lateinit var viewModel: HomeViewModel

    private var PERMISSION_ALL = 1
    private val PERMISSIONS = arrayOf(
        android.Manifest.permission.READ_EXTERNAL_STORAGE,
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.ACCESS_FINE_LOCATION
    )

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_home)
            viewModel = ViewModelProvider(this).get(HomeViewModel::class.java)

            val gray_out_home : FrameLayout = findViewById(R.id.gray_out_home)
            val fabStart : FloatingActionButton = findViewById(R.id.fabStart)
            val bottomAppBar : BottomAppBar = findViewById(R.id.bottomAppBar)
            val navHostFragment = supportFragmentManager.findFragmentById(R.id.navHostFragment) as NavHostFragment
            val navController = navHostFragment.navController

            //Check for permissions
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
                if(!hasPermission(this, *PERMISSIONS)){
                    ActivityCompat.requestPermissions(this,PERMISSIONS,PERMISSION_ALL)
                }
            }

            if(MeshDaemon.isServiceRunning){
                gray_out_home.visibility = GONE
                fabStart.setImageResource(R.drawable.ic_stop)

            }else{
                gray_out_home.visibility = VISIBLE
                fabStart.setImageResource(R.drawable.ic_start)
            }
            fabStart.setOnClickListener {
                if(System.currentTimeMillis() - fabLastClick >= 2000L){
                    fabLastClick = System.currentTimeMillis()
                    if(viewModel.isMyServiceRunning(this,MeshDaemon::class.java)){
                        MeshDaemon.stopService(this)
                        fabStart.setImageResource(R.drawable.ic_start)
                        gray_out_home.visibility = VISIBLE

                    }else{
                        MeshDaemon.startService(this)
                        fabStart.setImageResource(R.drawable.ic_stop)
                        gray_out_home.visibility = GONE
                    }


                }

            }

            bottomAppBar.setOnMenuItemClickListener {
                when(it.itemId){
                    R.id.menuFiles -> {
                        navController.popBackStack()
                        navController.navigate(R.id.fileFragment)
                    }
                    R.id.menuCloud -> {
                        navController.popBackStack()
                        navController.navigate(R.id.cloudFragment2)
                    }
                    R.id.menuDevices -> {
                        navController.popBackStack()
                        navController.navigate(R.id.deviceFragment)
                    }
                }
                true
            }
        }

    private fun hasPermission(context: Context, vararg permissions : String): Boolean = permissions.all{
        ActivityCompat.checkSelfPermission(context,it) == PackageManager.PERMISSION_GRANTED
    }
}
