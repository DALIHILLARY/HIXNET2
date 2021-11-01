package ug.hix.hixnet2.viewmodel

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import ug.hix.hixnet2.cyphers.Generator

class MainActivityViewModel : ViewModel(){

    val finished = MutableLiveData<Boolean>(false)

    fun config(applicationContext: Context) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                Generator.getDatabaseInstance(applicationContext)
                Generator.loadKeys(applicationContext)

                delay(3000L)
            }
            finished.value = true

        }
    }

}