package ug.hix.hixnet2.viewmodel

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat.getSystemService
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import ug.hix.hixnet2.services.MeshDaemon


class HomeViewModel : ViewModel() {

    var isRunning = false
    var lastFragmentLoaded : Fragment? = null

    fun isMyServiceRunning(mContext: Context, serviceClass: Class<*>): Boolean {
        return if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){

            val manager = mContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
            for (service in manager!!.getRunningServices(Int.MAX_VALUE)) {
                if (serviceClass.name == service.service.className) {
                    true
                }
            }
            false
        }else{
            MeshDaemon.isServiceRunning

        }
    }
}