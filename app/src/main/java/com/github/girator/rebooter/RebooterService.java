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
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.preference.PreferenceManager;
import java.io.DataOutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Set;

public class RebooterService extends Service {
// preferences
    public Resources res;
    public SharedPreferences preferences;
    public Boolean switch_main;
    public Boolean switch_activity;
    public String[] edit_period;
    public Boolean switch_schedule;
    public String[] edit_time;
    public Set<String> edit_day;
    public String edit_interval;
    public String usb_mode;
    public String reboot_method;
    public String edit_reboot_command;
    public String edit_tts;
    public String edit_noice;

    // runtime
    public UnlockReciever unlock_reciever;
    public USBReciever usb_reciever;
    public TextToSpeech TTS;
    public Boolean tts_ok = true;
    public Boolean screen_unlocked = true;
    public service_binder my_binder = new service_binder();
    public Thread run_service_thread;
    public Boolean alarm_is_set = false;
    public Long default_sleep_millis;
    // actual value for thread to wait;
    // exact_sleep_millis assigned default value before run and can be decreased during run to do next run on exact time
    public Long exact_sleep_millis;
    // nearest reboot time acording to schedule, if there is one;
    // next_scheduled_millis calculated only once when service starts, changing settings reqires service restart so no problem
    public Long next_scheduled_millis;
    // actual reboot time for all modes
    // period_end_millis assigned initial value once when service starts, based on period or schedule setting values
    // can be updated by user activity trigger funtion
    public Long period_end_millis;

    @Override
    public IBinder onBind(Intent intent) {
        return my_binder;
    }

    @Override
    public void onCreate() {
        Log.w("rebooter_log","main service: onCreate");
// load preferences so we dont have null objects when do_reboot called by test button on unstarted service
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.w("rebooter_log","main service: onStartCommand");
// initialisation
        start_foreground();
// load preferences
        load_preferences();
// initial period value
        SharedPreferences.Editor editor = preferences.edit();
        editor.putLong("period_end_millis", System.currentTimeMillis() + ((Long.parseLong(edit_period[0]) * 60L) + Long.parseLong(edit_period[1]) ) * 60L * 1000L);
        editor.apply();
// recalculate values preferences
        calculate_runtime();
// register recievers
        // use unlock reciever always to refresh notification on unlock event
        //if (switch_activity){
            IntentFilter unlock_filter = new IntentFilter();
            unlock_filter.addAction("android.intent.action.USER_PRESENT");
            unlock_filter.addAction("android.intent.action.SCREEN_OFF");
            unlock_reciever = new UnlockReciever();
            unlock_reciever.my_service = this;
            registerReceiver(unlock_reciever, unlock_filter, Context.RECEIVER_VISIBLE_TO_INSTANT_APPS);
        //}
        if ((usb_mode.equals(res.getString(R.string.opt_mode_usb_locked).toString())) || (usb_mode.equals(res.getString(R.string.opt_mode_usb_on).toString()))){
            IntentFilter usb_filter = new IntentFilter();
            usb_filter.addAction("android.hardware.usb.action.USB_STATE");
            usb_reciever = new USBReciever();
            usb_reciever.my_service = this;
            registerReceiver(usb_reciever, usb_filter, Context.RECEIVER_VISIBLE_TO_INSTANT_APPS);
        }
// init text to speach
        if((edit_tts != null) && (edit_tts.length() > 0)){
            TTS = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
                @Override
                public void onInit(int i) {
                    if(i != TextToSpeech.SUCCESS){
                        Log.w("rebooter_log","tts init fail");
                        tts_ok = false;
                    }
                }
            });
        }
// cycle run_service in separate thread
        run_service_thread = new Thread(new Runnable(){
            public void run() {
                switch_main = true;
                while(true){
                    exact_sleep_millis = default_sleep_millis;
                    run_service();
                    try {
                        synchronized(Thread.currentThread()){
                            Thread.currentThread().wait(exact_sleep_millis);
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        run_service_thread.start();
        return super.onStartCommand(intent, flags, startId);
    }

    public void run_service(){
        Log.w("rebooter_log","main service: run_service");
// lock time values so logic holds up
        Long now_millis = System.currentTimeMillis();
        Long uptime_millis = SystemClock.elapsedRealtime();
// try to set alarm
        if (!alarm_is_set)
            set_alarm_watchdog(period_end_millis + 5000L); // bit later to be sure it triggers
// dont even try do anything if just rebooted
        Long min_uptime_millis = res.getInteger(R.integer.uptime_minutes_min)*60L*1000L;
        if (uptime_millis < min_uptime_millis){
    // set sleep delay to match end of safe period
            exact_sleep_millis = Math.max(1000L, (min_uptime_millis - uptime_millis));
            Log.w("rebooter_log","main service: wait safe period ");
        }else{
    // reboot now
            if (now_millis >= period_end_millis){
                if(edit_noice.length() > 0) {
                    Log.w("rebooter_log","main service: atempting sound");
                    Uri uri = Uri.parse(edit_noice);
                    if(uri != null){
                        RingtoneManager.getRingtone(getApplicationContext(), uri).play();
                    }
                }
                if((edit_tts.length() > 0) && (TTS != null) && (tts_ok)){
                    Log.w("rebooter_log","main service: atempting tts");
                    TTS.setLanguage(Locale.getDefault());
                    TTS.speak(edit_tts,TextToSpeech.QUEUE_FLUSH,null, null);
                    while(TTS.isSpeaking()){
                        // pause to finish speaking
                    }
                }
                do_reboot();
            }else{
    // calculate exact_sleep_millis for next run
                exact_sleep_millis = Math.min(exact_sleep_millis, (period_end_millis - now_millis + 5000L)); // bit later to be sure it triggers
            }
        }
    }

    public void do_reboot(){
        Log.w("rebooter_log","main service: do_reboot");
        try{
// data stream
            if (reboot_method.equals(res.getString(R.string.opt_reboot_method_inline).toString())) {
                Runtime.getRuntime().exec(edit_reboot_command);
                return;
            }
// data stream
            if (reboot_method.equals(res.getString(R.string.opt_reboot_method_stream).toString())){
                Process su = Runtime.getRuntime().exec("su");
                DataOutputStream outputStream = new DataOutputStream(su.getOutputStream());
                String[] commands = edit_reboot_command.split(";");
                for (String cmd : commands) {
                    outputStream.writeBytes(cmd+"\n");
                    outputStream.flush();
                }
                outputStream.writeBytes("exit\n");
                outputStream.flush();
                try {
                    su.waitFor();
                } catch (Exception e) {
    //  error after su
                    Log.w("rebooter_log","su exception: " + e.getMessage().toString());
                    NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), "com.github.girator.rebooter.info");
                    builder.setSmallIcon(R.drawable.ic_interval);
                    builder.setContentTitle(res.getString(R.string.desc_notification_reboot_error));
                    builder.setContentText(e.getMessage().toString());
                    NotificationManagerCompat.from(getApplicationContext()).notify(1, builder.build());
                }
                outputStream.close();
                return;
            }
// direct intent
            if (reboot_method.equals(res.getString(R.string.opt_reboot_method_intent).toString())) {
                getApplicationContext().sendBroadcast(new Intent("android.intent.action.ACTION_REBOOT"));
                return;
            }
            Log.w("rebooter_log","main service: do_reboot should not be able to get here");
        }catch(Exception e){
//  error while rebooting
            Log.w("rebooter_log","reboot exception: " + e.getMessage().toString());
            NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), "com.github.girator.rebooter.info");
            builder.setSmallIcon(R.drawable.ic_interval);
            builder.setContentTitle(res.getString(R.string.desc_notification_reboot_error));
            builder.setContentText(e.getMessage().toString());
            NotificationManagerCompat.from(getApplicationContext()).notify(1, builder.build());
            return;
        }
    }

    public void set_alarm_watchdog(Long triggerAtMillis){
        try{
            AlarmManager manager = (AlarmManager)getApplicationContext().getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(getApplicationContext(), AlarmReciever.class);
            PendingIntent pending_intent = PendingIntent.getBroadcast(
                    getApplicationContext(),
                    1,
                    intent,
                    PendingIntent.FLAG_CANCEL_CURRENT|PendingIntent.FLAG_IMMUTABLE
            );
    // check permission on android 12+
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                if(manager.canScheduleExactAlarms()){
                    manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending_intent);
                    alarm_is_set = true;
                    Log.w("rebooter_log","main service: alarm watchdog set to " + (new SimpleDateFormat(res.getString(R.string.date_format_switch_main))).format(triggerAtMillis));
                }else{
                    alarm_is_set = false;
                    Log.w("rebooter_log","main service: no permission to set alarm");
                }
            }else{
    //  on android 11- mainfest should be enough
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending_intent);
                alarm_is_set = true;
                Log.w("rebooter_log","main service: alarm watchdog set to" + (new SimpleDateFormat(res.getString(R.string.date_format_switch_main))).format(triggerAtMillis));
            }
        }catch(Exception e){
            alarm_is_set = false;
            Log.w("rebooter_log","main service: exception while setting alarm");
            e.printStackTrace();
        }
    }

    public void load_preferences(){
        Log.w("rebooter_log","main service: load_preferences");
// load from settings
        res = getApplicationContext().getResources();
        preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        switch_main = preferences.getBoolean("switch_main", true);
        switch_activity = preferences.getBoolean("switch_activity", true);
        edit_period = preferences.getString("edit_period", "null").split(":");
        switch_schedule = preferences.getBoolean("switch_schedule", true);
        edit_time = preferences.getString("edit_time", "null").split(":");
        edit_day = preferences.getStringSet("edit_day", null);
        edit_interval = preferences.getString("edit_interval", "15");
        usb_mode = preferences.getString("mode_usb", res.getString(R.string.summ_switch_off));
        reboot_method = preferences.getString("reboot_method", res.getString(R.string.opt_reboot_method_inline));
        edit_reboot_command = preferences.getString("edit_reboot_command", res.getString(R.string.inline_command_1));
        edit_tts = preferences.getString("edit_tts", "");
        edit_noice = preferences.getString("edit_noice", "");
    }

    public void calculate_runtime(){
        Log.w("rebooter_log","main service: calculate_runtime");
// calculate values
        default_sleep_millis = Long.parseLong(edit_interval)*60L*1000L;
        period_end_millis = preferences.getLong("period_end_millis", System.currentTimeMillis());
// if schedule enabled
        if (switch_schedule){
            calculate_closest_scheduled();
            if(next_scheduled_millis != null){
                // valid schedule exist
                if(switch_activity){
                    // compare with value by user activity trigger
                    if (next_scheduled_millis < period_end_millis) {
                        period_end_millis = next_scheduled_millis;
                    }
                }else{
                    // no compromise if no user activity trigger
                    period_end_millis = next_scheduled_millis;
                }
            }
        }
    }

    public void calculate_closest_scheduled(){
        Log.w("rebooter_log","main service: calculate_closest_scheduled");
// get settings
        CharSequence[] values = res.getStringArray(R.array.weekday_names);
        Set<String> selected = preferences.getStringSet("edit_day", null);
        String[] time = preferences.getString("edit_time", "null").split(":");
        Integer HH = Integer.parseInt(time[0]);
        Integer MM = Integer.parseInt(time[1]);
// set scheduled time today
        Calendar cal = Calendar.getInstance();
        cal.setFirstDayOfWeek(Calendar.MONDAY);
        cal.set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH), HH, MM);
// try this time 6 days in future
        next_scheduled_millis = null;
        for (int iw=0; iw < 7; iw++){
    // calculate this day of week 0..6 index
            Integer day_int = cal.get(Calendar.DAY_OF_WEEK) - 2;
            if (day_int < 0) { day_int = day_int + 7; }
    // compare to all selected days
            for(String day:selected){
                if (day.matches(values[day_int].toString())){ // this day matches one of selected
            // if later then now
                    if (cal.getTimeInMillis() > System.currentTimeMillis()){
                // if erlier or no previous value
                        if ((next_scheduled_millis == null) || (cal.getTimeInMillis() < next_scheduled_millis)){ // if after now
                            next_scheduled_millis = cal.getTimeInMillis();
                        }
                    }
                }
            }
    // increase by one day
            cal.setTimeInMillis(cal.getTimeInMillis() + 24L*60L*60L*1000L);
        }
    }

    public void start_foreground(){
        Log.w("rebooter_log","main service: start_foreground");
        Resources res = getApplicationContext().getResources();
// notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "com.github.girator.rebooter.service");
        builder.setSmallIcon(R.drawable.ic_service);
        builder.setContentTitle(res.getString(R.string.desc_notification_service));
        builder.setContentText("");
// start foreground
        startForeground(1, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
    }

    public class service_binder extends Binder {
        RebooterService getService() {
            return RebooterService.this;
        }
    }

    @Override
    public void onDestroy() {
        Log.w("rebooter_log","main service: onDestroy");
// unregister recievers
        if (unlock_reciever != null)
            unregisterReceiver(unlock_reciever);
        if (usb_reciever != null)
            unregisterReceiver(usb_reciever);

// do not cancel alarm here, because service could be stoped non intentionally
// cancel alarm manually in settings before stopping service

// deinitialisation
        stopForeground(true);

        super.onDestroy();
    }

}


