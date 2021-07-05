package ug.hix.hixnet2.viewmodel

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.runBlocking
import ug.hix.hixnet2.database.DeviceNode
import ug.hix.hixnet2.repository.Repository

class DeviceViewModel: ViewModel() {
    fun getActiveDeviceLiveData(context: Context) : LiveData<List<DeviceNode>> {
        return runBlocking { Repository.getInstance(context).activeDevicesLiveData() }
    }
}