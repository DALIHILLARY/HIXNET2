package ug.hix.hixnet2.interlink.salut;

import android.content.Context;

import ug.hix.hixnet2.interlink.salut.Callbacks.SalutDataCallback;

public class SalutDataReceiver {

    protected SalutDataCallback dataCallback;
    protected Context context;

    public SalutDataReceiver(Context applicationContext, SalutDataCallback dataCallback) {
        this.dataCallback = dataCallback;
        this.context = applicationContext;
    }
}
