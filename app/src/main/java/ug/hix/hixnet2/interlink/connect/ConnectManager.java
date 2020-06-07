package ug.hix.hixnet2.interlink.connect;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import android.util.Log;

public class ConnectManager {

        static final public int ConectionStateNONE = 0;
        static final public int ConectionStatePreConnecting = 1;
        static final public int ConectionStateConnecting = 2;
        static final public int ConectionStateConnected = 3;
        static final public int ConectionStateDisconnected = 4;

        private int  mConectionState = ConectionStateNONE;
        protected  String TAG = getClass().getSimpleName();

        private boolean hadConnection = false;

        ConnectManager that = this;
        WifiManager wifiManager = null;
        WifiConfiguration wifiConfig = null;
        Context context = null;
        int netId = 0;
        WiFiConnectionReceiver receiver;
        private IntentFilter filter;

        public ConnectManager(Context Context, String SSIS, String password) {
            this.context = Context;

            receiver = new WiFiConnectionReceiver();
            filter = new IntentFilter();
            filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
            this.context.registerReceiver(receiver, filter);

            this.wifiManager = (WifiManager)this.context.getSystemService(this.context.WIFI_SERVICE);

            this.wifiConfig = new WifiConfiguration();
            this.wifiConfig.SSID = String.format("\"%s\"", SSIS);
            this.wifiConfig.preSharedKey = String.format("\"%s\"", password);

            this.netId = this.wifiManager.addNetwork(this.wifiConfig);
            //this.wifiManager.disconnect();
            this.wifiManager.enableNetwork(this.netId, false);
            this.wifiManager.reconnect();


        }

        public void Stop(){
            this.context.unregisterReceiver(receiver);
            this.wifiManager.disconnect();
        }


        private class WiFiConnectionReceiver extends BroadcastReceiver {

            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)) {
                    NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                    if(info != null) {

                        if (info.isConnected()) {
                            hadConnection = true;
                            mConectionState = ConectionStateConnected;
                            Log.d(TAG,"CONNECTED");
                        }else if(info.isConnectedOrConnecting()) {
                            mConectionState = ConectionStateConnecting;
                        }else {
                            if(hadConnection){
                                mConectionState = ConectionStateDisconnected;
                            }else{
                                mConectionState = ConectionStatePreConnecting;
                            }
                        }

                    }

                    WifiInfo wiffo = intent.getParcelableExtra(WifiManager.EXTRA_WIFI_INFO);
                    if(wiffo != null){
                        //Log.v(TAG, wiffo.getIpAddress());
                        Log.d(TAG,"we are live");

                        // you could get otherparty IP via:
                        // http://stackoverflow.com/questions/10053385/how-to-get-each-devices-ip-address-in-wifi-direct-scenario
                        // as well if needed
                    }
                }
            }
        }
}
