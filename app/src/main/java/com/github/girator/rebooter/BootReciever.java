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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import androidx.preference.PreferenceManager;
import java.io.DataOutputStream;

public class BootReciever extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
// disable alarm in case one is left after manual reboot
        disable_alarm_watchdog(context);
// test root access
        try{
            Process su = Runtime.getRuntime().exec("su");
            DataOutputStream outputStream = new DataOutputStream(su.getOutputStream());
            outputStream.writeBytes("exit\n");
            outputStream.flush();
            su.waitFor();
        }catch(Exception e){
            Log.w("rebooter_log","boot reciever: root test exception: " + e.getMessage().toString());
        }
// check if service setting enabled
        Boolean switch_main = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext()).getBoolean("switch_main", false);
        if (switch_main){
            Log.w("rebooter_log","boot reciever: starting service");
            Intent service_intent = new Intent(context, RebooterService.class);
            context.startForegroundService(service_intent);
        }
    }

    // if changing do same in SettingsActivity
    public void disable_alarm_watchdog(Context context){
        try{
            AlarmManager manager = (AlarmManager)context.getApplicationContext().getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(context.getApplicationContext(), AlarmReciever.class);
            PendingIntent pending_intent = PendingIntent.getBroadcast(
                    context.getApplicationContext(),
                    1,
                    intent,
                    PendingIntent.FLAG_CANCEL_CURRENT|PendingIntent.FLAG_IMMUTABLE
            );
            // check permission on android 12+
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                if(manager.canScheduleExactAlarms()){
                    manager.cancel(pending_intent);
                    Log.w("rebooter_log","alarm watchdog canceled");
                }else{
                    Log.w("rebooter_log","no permission to set alarm");
                }
            }else{
                //  on android 11- mainfest should be enough
                manager.cancel(pending_intent);
                Log.w("rebooter_log","alarm watchdog canceled");;
            }
        }catch(Exception e){
            Log.w("rebooter_log","exception while canceling alarm");
            e.printStackTrace();
        }
    }
}
