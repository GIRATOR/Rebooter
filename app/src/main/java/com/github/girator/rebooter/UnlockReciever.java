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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import java.text.SimpleDateFormat;

// this reciever registered dynamically from service so should work without being in manifest
public class UnlockReciever extends BroadcastReceiver {
    public RebooterService my_service;

    @Override
    public void onReceive(Context context, Intent intent) {
        switch(intent.getAction().toString()) {
            case "android.intent.action.USER_PRESENT":
                Log.w("rebooter_log","unlock reciever: USER_PRESENT");
                my_service.screen_unlocked = true;
                run_unlocked(context);
                break;
            case "android.intent.action.SCREEN_OFF":
                Log.w("rebooter_log","unlock reciever: SCREEN_OFF");
                my_service.screen_unlocked = false;
                break;
        }
    }

    public void run_unlocked(Context context){
// update period_end_millis if user activity trigger
        if (my_service.switch_activity){
    // calculate new end of period from now
            String[] period_str = my_service.preferences.getString("edit_period", "24:00").split(":");
            Long new_period_end_millis = System.currentTimeMillis() + ((Long.parseLong(period_str[0]) * 60L) + Long.parseLong(period_str[1]) ) * 60L * 1000L;
        // check maybe scheduled value is closer
            if (my_service.switch_schedule){
                if(my_service.next_scheduled_millis != null){
                    // valid schedule exist
                    if (my_service.next_scheduled_millis < new_period_end_millis) {
                        new_period_end_millis = my_service.next_scheduled_millis;
                    }
                }
            }
        // store value in settings
            SharedPreferences.Editor editor = my_service.preferences.edit();
            editor.putLong("period_end_millis", new_period_end_millis);
            editor.apply();
        // update preferences again
            my_service.load_preferences();
            my_service.calculate_runtime();
        }
// notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context.getApplicationContext(), "com.github.girator.rebooter.info");
        builder.setSmallIcon(R.drawable.ic_service);
        builder.setContentTitle(my_service.res.getString(R.string.desc_notification_scheduled));
        builder.setContentText((new SimpleDateFormat(my_service.res.getString(R.string.date_format_switch_main))).format(my_service.period_end_millis));
        NotificationManagerCompat.from(context.getApplicationContext()).notify(1, builder.build());
    }


}
