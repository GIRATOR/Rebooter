/*
    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.github.girator.rebooter;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;


public class AlarmReciever extends BroadcastReceiver {
    public RebooterService my_service;

    @Override
    public void onReceive(Context context, Intent intent) {
        Intent service_intent = new Intent(context, RebooterService.class);
// accesing running service
        ServiceConnection service_connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                my_service = ((RebooterService.service_binder)service).getService();
                if (my_service != null){
                    Log.w("rebooter_log","alarm reciever: bind succesfull, notyfing main thread");
    // wakeup waiting thread
                    synchronized(my_service.run_service_thread){
                        my_service.alarm_is_set = false;
                        my_service.run_service_thread.notifyAll();
                    }
                }
            }
            @Override
            public void onServiceDisconnected(ComponentName name) {
                my_service = null;
            }
        };
// check if service is running
        Boolean service_running = false;
        for (ActivityManager.RunningServiceInfo service_info: ((ActivityManager)context.getApplicationContext().getSystemService(Context.ACTIVITY_SERVICE)).getRunningServices(Integer.MAX_VALUE)) {
            if (service_info.service.getClassName().equals(RebooterService.class.getName().toString()))
                service_running = true;
        }
        if (service_running){
            Log.w("rebooter_log","alarm reciever: attemting bind runnig service");
            context.getApplicationContext().bindService(service_intent, service_connection, 0);
        }else{
            Log.w("rebooter_log","alarm reciever: no running service, starting");
            context.getApplicationContext().startForegroundService(service_intent);
        }
    }
}