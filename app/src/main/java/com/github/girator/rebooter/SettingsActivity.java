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
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcelable;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.text.method.DigitsKeyListener;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.DropDownPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;
import com.google.android.material.snackbar.Snackbar;
import java.io.DataOutputStream;
import java.util.Locale;
import java.util.Set;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

// shove new fragment into existing layout
// clarified code from preferences template
        SettingsFragment settings_fragment = new SettingsFragment();
        setContentView(R.layout.settings_activity);
        if (savedInstanceState == null) {
            FragmentManager manager = getSupportFragmentManager();
            FragmentTransaction transaction = manager.beginTransaction();
            transaction = transaction.replace(R.id.settings, settings_fragment);
            transaction.commit();
        }

// fix overlap from edge to edge on android 15+
// pad our fragment by magicaly retrieved values
        if (android.os.Build.VERSION.SDK_INT >= 35) {
            ViewCompat.setOnApplyWindowInsetsListener(getWindow().getDecorView(), new OnApplyWindowInsetsListener() {
                @NonNull
                @Override
                public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat windowInsets) {
                    Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                    settings_fragment.getListView().setPadding(insets.left,insets.top,insets.right,insets.bottom);
                    return windowInsets;
                }
            });
        }

// add icon to that strip on top with app tittle
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowHomeEnabled(true);
            actionBar.setLogo(R.mipmap.ic_launcher);
            actionBar.setDisplayUseLogoEnabled(true);
            actionBar.setTitle(R.string.tittle_app);
            actionBar.setDisplayShowTitleEnabled(true);
            //actionBar.hide();
        }

// create notification channels here because where else
        Resources res = getResources();
        NotificationManager manager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if (manager.getNotificationChannel("com.github.girator.rebooter.service") == null){
            NotificationChannel service_channel = new NotificationChannel("com.github.girator.rebooter.service", res.getString(R.string.tittle_channel_service), NotificationManager.IMPORTANCE_MIN);
            manager.createNotificationChannel(service_channel);
        }
        if (manager.getNotificationChannel("com.github.girator.rebooter.info") == null) {
            NotificationChannel info_channel = new NotificationChannel("com.github.girator.rebooter.info", res.getString(R.string.tittle_channel_info), NotificationManager.IMPORTANCE_MIN);
            manager.createNotificationChannel(info_channel);
        }
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {

        public TextToSpeech TTS;
        public Boolean tts_ok = true;
        public ActivityResultLauncher<Intent> noice_launcher;

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
// init text to speach
            TTS = new TextToSpeech(getContext().getApplicationContext(), new TextToSpeech.OnInitListener() {
                @Override
                public void onInit(int i) {
                    if(i != TextToSpeech.SUCCESS){
                        Log.w("rebooter_log","tts init fail");
                        tts_ok = false;
                    }
                }
            });
// init noice picker
            noice_launcher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        String uri = "";
                        if(result.getResultCode() == RESULT_OK){
                            if(result.getData() != null){
                                Parcelable parcel = result.getData().getParcelableExtra("android.intent.extra.ringtone.PICKED_URI");
                                if (parcel != null)
                                    uri = parcel.toString();
                            }
                            ((EditTextPreference)getPreferenceScreen().findPreference("edit_noice")).setText(uri);
                        }
                    }
                }
            );
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

            setPreferencesFromResource(R.xml.root_preferences, rootKey);

// collect preference objects
// no checks for null
            Resources res = getContext().getApplicationContext().getResources();
            PreferenceScreen screen = getPreferenceScreen();
            SwitchPreference switch_main = screen.findPreference("switch_main");
            SwitchPreference switch_activity = screen.findPreference("switch_activity");
            TimePreference edit_period = screen.findPreference("edit_period");
            SwitchPreference switch_schedule = screen.findPreference("switch_schedule");
            TimePreference edit_time = screen.findPreference("edit_time");
            MultiSelectListPreference edit_day = screen.findPreference("edit_day");
            RangePreference edit_interval = screen.findPreference("edit_interval");
            DropDownPreference mode_usb = screen.findPreference("mode_usb");
            DropDownPreference reboot_method = screen.findPreference("reboot_method");
            EditTextPreference edit_reboot_command = screen.findPreference("edit_reboot_command");
            EditTextPreference edit_tts = screen.findPreference("edit_tts");
            EditTextPreference edit_noice = screen.findPreference("edit_noice");
//  preferences but buttons only
            Preference nag_permission = screen.findPreference("nag_permission");
            Preference test_reboot = screen.findPreference("test_reboot");
            DropDownPreference example_reboot_command = screen.findPreference("example_reboot_command");
// first launch permissions nag
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getContext().getApplicationContext());
            Boolean first_launch_permission_check = preferences.getBoolean("first_launch_permission_check", false);
            if ((first_launch_permission_check == null) || (first_launch_permission_check == false)) {
                first_launch_permission_check();
    // save flag so we dont do it again
                SharedPreferences.Editor editor = preferences.edit();
                editor.putBoolean("first_launch_permission_check", true);
                editor.apply();
            }

// enable/disable preferences based on loaded switch states
            if(switch_schedule.isChecked()) {
                if(!switch_activity.isChecked()) {
                    edit_period.setEnabled(false);
                }
                edit_time.setEnabled(true);
                edit_day.setEnabled(true);
            }else{
                edit_period.setEnabled(true);
                edit_time.setEnabled(false);
                edit_day.setEnabled(false);
            }
            if(reboot_method.equals(res.getString(R.string.opt_reboot_method_intent).toString())){
                edit_reboot_command.setEnabled(false);
                example_reboot_command.setEnabled(false);
            }else{
                edit_reboot_command.setEnabled(true);
                example_reboot_command.setEnabled(true);
            }

// preferences edit and summarries logic
    // main switch
            switch_main.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    Resources res = getContext().getApplicationContext().getResources();
                    Intent service_intent = new Intent(getContext(), RebooterService.class);
                    disable_alarm_watchdog();
                    getContext().getApplicationContext().stopService(service_intent);
        // start service if checked
                    if ((Boolean) newValue) {
                        getContext().getApplicationContext().startForegroundService(service_intent);
                    }
                    return true;
                }
            });
            switch_main.setSummaryProvider(new Preference.SummaryProvider() {
                @Override
                public CharSequence provideSummary(Preference preference) {
                    Resources res = getContext().getApplicationContext().getResources();
        // detect running background job
                    String summ = "";
                    Boolean service_running = false;
                    for (ActivityManager.RunningServiceInfo service_info : ((ActivityManager) getContext().getApplicationContext().getSystemService(Context.ACTIVITY_SERVICE)).getRunningServices(Integer.MAX_VALUE)) {
                        if (service_info.service.getClassName().equals(RebooterService.class.getName().toString()))
                            service_running = true;
                    }
        // pack summary
                    if (((SwitchPreference) preference).isChecked()) {
                        summ = res.getString(R.string.summ_switch_on) + " ";
                    } else {
                        summ = res.getString(R.string.summ_switch_off) + " ";
                    }
                    if (service_running) {
                        if (((SwitchPreference) preference).isChecked()) {
                            summ = summ + res.getString(R.string.summ_switch_main_running);
                        } else {
                            summ = summ + res.getString(R.string.summ_switch_main_error) + ": " + res.getString(R.string.summ_switch_main_running);
                        }
                    } else {
                        if (((SwitchPreference) preference).isChecked()) {
                            summ = summ + res.getString(R.string.summ_switch_main_error) + ": " + res.getString(R.string.summ_switch_main_not);
                        } else {
                            summ = summ + res.getString(R.string.summ_switch_main_not);
                        }
                    }
                    return summ;
                }
            });
    // user activity trigger switch
            switch_activity.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    Resources res = getContext().getApplicationContext().getResources();
        // enable period, disable only if schedule active
                    if ((Boolean) newValue) {
                        edit_period.setEnabled(true);
                    } else {
                        if (switch_schedule.isChecked()) {
                            edit_period.setEnabled(false);
                        }
                    }
        //stop service
                    stop_main_switch(switch_main);
                    return true;
                }
            });
    // reboot period edit
            edit_period.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener(){
                @Override
                public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
                    Resources res = getContext().getApplicationContext().getResources();
                    String[] str = (((String)newValue) + ":00:00").split(":");
                    Integer HH = Integer.parseInt(str[0]);
                    Integer MM = Integer.parseInt(str[1]);
                    if ((HH == 0) && (MM < res.getInteger(R.integer.period_minutes_min))){
                        MM = res.getInteger(R.integer.period_minutes_min);
                        ((EditTextPreference) preference).setText(String.format("%02d", HH) + ":" + String.format("%02d", MM));
                        String summ = res.getString(R.string.desc_edit_minimum_period) + " ";
                        summ = summ + String.valueOf(res.getInteger(R.integer.period_minutes_min)) + " ";
                        summ = summ + res.getString(R.string.summ_edit_period_minutes);
                        Snackbar.make(getView(), summ, Snackbar.LENGTH_LONG).show();
                        return false;
                    }
        // suggest larger value
                    if ((HH >= 20) && (HH <= 23)){
                        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                        builder.setTitle(res.getString(R.string.tittle_edit_period_more_dialog));
                        builder.setIcon(((EditTextPreference) preference).getDialogIcon());
                        builder.setPositiveButton(res.getString(R.string.desc_alert_yes), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int id) {
                                ((TimePreference)preference).use_edittext = true;
                                ((TimePreference)preference).onClick();
                            }
                        });
                        builder.setNegativeButton(res.getString(R.string.desc_alert_no), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int id) {

                            }
                        });
                        builder.create().show();
                    }
                    stop_main_switch(switch_main);
                    return true;
                }
            });
            edit_period.setOnBindEditTextListener(new EditTextPreference.OnBindEditTextListener() {
                @Override
                public void onBindEditText(@NonNull EditText editText) {
                    editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                    editText.setKeyListener(DigitsKeyListener.getInstance("123456789"));
                    editText.setText("24");
                    editText.setSelected(true);
                }
            });
            edit_period.setSummaryProvider(new Preference.SummaryProvider() {
                @Override
                public CharSequence provideSummary(Preference preference) {
                    Resources res = getContext().getApplicationContext().getResources();
                    String[] str = (((EditTextPreference) preference).getText() + ":00:00").split(":");
                    Integer HH = Integer.parseInt(str[0]);
                    Integer MM = Integer.parseInt(str[1]);
                    String summ = "";
                    if (HH > 0) {
                        summ = summ + HH.toString() + " " + res.getString(R.string.summ_edit_period_hours) + "  ";
                    }
                    if (MM > 0) {
                        summ = summ + MM.toString() + " " + res.getString(R.string.summ_edit_period_minutes) + "  ";
                    }
                    return summ;
                }
            });
    // schedule switch
            switch_schedule.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                Resources res = getContext().getApplicationContext().getResources();

                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
        // disable only if no user activity trigger, enable period
                    if ((Boolean) newValue) {
                        if (!switch_activity.isChecked()) {
                            edit_period.setEnabled(false);
                        }
                        edit_time.setEnabled(true);
                        edit_day.setEnabled(true);
                    } else {
                        edit_period.setEnabled(true);
                        edit_time.setEnabled(false);
                        edit_day.setEnabled(false);
                    }
            //stop service
                    stop_main_switch(switch_main);
                    return true;
                }
            });
    // schedule time edit
            edit_time.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    stop_main_switch(switch_main);
                    return true;
                }
            });
            edit_time.setSummaryProvider(new Preference.SummaryProvider() {
                @Override
                public CharSequence provideSummary(Preference preference) {
                    Resources res = getContext().getApplicationContext().getResources();
                    String[] str = (((EditTextPreference) preference).getText() + ":00:00").split(":");
                    return String.format("%02d", Integer.parseInt(str[0])) + ":" + String.format("%02d", Integer.parseInt(str[1]));
                }
            });
    // schedule day edit
            edit_day.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                Resources res = getContext().getApplicationContext().getResources();
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    if(((Set<String>)newValue).size() > 0){
                        //stop service
                        stop_main_switch(switch_main);
                        return true;
                    }else{
                        Snackbar.make(getView(), res.getString(R.string.desc_edit_day_zero), Snackbar.LENGTH_LONG).show();
                        return false;
                    }

                }
            });
            edit_day.setSummaryProvider(new Preference.SummaryProvider() {
                @Override
                public CharSequence provideSummary(Preference preference) {
                    Resources res = getContext().getApplicationContext().getResources();
                    Set<String> selected = ((MultiSelectListPreference) preference).getValues();
                    CharSequence[] values = ((MultiSelectListPreference) preference).getEntryValues();
                    CharSequence[] names = ((MultiSelectListPreference) preference).getEntries();
                    String summ = "[ ";
                    for (int iv = 0; iv < values.length; iv++) {
                        Boolean empty_flag = true;
                        for (String day : selected) {
                            if (day.matches(values[iv].toString())) {
                                empty_flag = false;
                                summ = summ + names[iv].toString().substring(0, 3) + ", ";
                            }
                        }
                        if (empty_flag) {
                            summ = summ + "___, ";
                        }
                    }
                    summ = summ.substring(0, summ.length() - 2);
                    summ = summ + " ]";
                    return summ;
                }
            });
    // check interval setting
            edit_interval.set_range(res.getInteger(R.integer.interval_minutes_min), res.getInteger(R.integer.interval_minutes_max), false);
            edit_interval.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    stop_main_switch(switch_main);
                    return true;
                }
            });
            edit_interval.setSummaryProvider(new Preference.SummaryProvider() {
                @Override
                public CharSequence provideSummary(Preference preference) {
                    Resources res = getContext().getApplicationContext().getResources();
                    String summ = res.getString(R.string.summ_edit_interval) + "\n";
                    summ = summ + ((EditTextPreference) preference).getText().toString() + " ";
                    summ = summ + res.getString(R.string.summ_edit_period_minutes);
                    return summ;
                }
            });
    // reboot method
            reboot_method.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    Resources res = getContext().getApplicationContext().getResources();
                    if (((String)newValue).equals(res.getString(R.string.opt_reboot_method_intent).toString())){
                        edit_reboot_command.setEnabled(false);
                        example_reboot_command.setEnabled(false);
                    }else{
                        edit_reboot_command.setEnabled(true);
                        example_reboot_command.setEnabled(true);
                    }
                    return true;
                }
            });
            reboot_method.setSummaryProvider(new Preference.SummaryProvider() {
                @Nullable
                @Override
                public CharSequence provideSummary(@NonNull Preference preference) {
                    Resources res = getContext().getApplicationContext().getResources();
                    String summ = "";
                    String method = ((DropDownPreference) preference).getValue().toString();
                    if (method.equals(res.getString(R.string.opt_reboot_method_inline).toString()))
                        summ = res.getString(R.string.summ_reboot_method_inline).toString();
                    if (method.equals(res.getString(R.string.opt_reboot_method_stream).toString()))
                        summ = res.getString(R.string.summ_reboot_method_stream).toString();
                    if (method.equals(res.getString(R.string.opt_reboot_method_intent).toString()))
                        summ = res.getString(R.string.summ_reboot_method_intent).toString();
                    return summ;
                }
            });
    // reboot command
            edit_reboot_command.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    stop_main_switch(switch_main);
                    return true;
                }
            });
            edit_reboot_command.setSummaryProvider(new Preference.SummaryProvider() {
                @Override
                public CharSequence provideSummary(Preference preference) {
                    Resources res = getContext().getApplicationContext().getResources();
                    String summ = ((EditTextPreference) preference).getText().toString();
                    return summ;
                }
            });
    // command examples
            example_reboot_command.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    if( ((((String)newValue).equals(res.getString(R.string.stream_command_1).toString()))) || ((((String)newValue).equals(res.getString(R.string.stream_command_2).toString()))) ){
                        reboot_method.setValue(res.getString(R.string.opt_reboot_method_stream).toString());
                    }else{
                        reboot_method.setValue(res.getString(R.string.opt_reboot_method_inline).toString());
                    }
                    edit_reboot_command.setText((String)newValue);
                    return true;
                }
            });
    // test reboot
            test_reboot.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Resources res = getContext().getApplicationContext().getResources();
                    AlertDialog.Builder alert = new AlertDialog.Builder(getContext());
                    alert.setTitle(res.getString(R.string.tittle_reboot_test).toString());
                    alert.setMessage(res.getString(R.string.desc_reboot_dialog).toString());
                    alert.setCancelable(true);
                    alert.setPositiveButton(res.getString(R.string.desc_alert_proceeed).toString(), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            test_reboot();
                        }
                    });
                    alert.setNegativeButton(res.getString(R.string.desc_alert_cancel).toString(), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {

                        }
                    });
                    alert.show();
                    return false;
                }
            });
    // nag permissions
            nag_permission.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    first_launch_permission_check();
                    return false;
                }
            });
    // usb trigger
            mode_usb.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    stop_main_switch(switch_main);
                    return true;
                }
            });
            mode_usb.setSummaryProvider(new Preference.SummaryProvider() {
                @Nullable
                @Override
                public CharSequence provideSummary(@NonNull Preference preference) {
                    Resources res = getContext().getApplicationContext().getResources();
                    String usb_mode = ((DropDownPreference)preference).getEntry().toString();
                    String summ = "";
                    if (usb_mode.equals(res.getString(R.string.opt_mode_usb_off).toString()))
                        summ = res.getString(R.string.summ_switch_off);
                    if (usb_mode.equals(res.getString(R.string.opt_mode_usb_locked).toString())){
                        summ = res.getString(R.string.summ_switch_on) + " ";
                        summ = summ + res.getString(R.string.desc_mode_usb_on) + ", ";
                        summ = summ + res.getString(R.string.desc_mode_usb_locked);
                    }
                    if (usb_mode.equals(res.getString(R.string.opt_mode_usb_on).toString())){
                        summ = res.getString(R.string.summ_switch_on) + " ";
                        summ = summ + res.getString(R.string.desc_mode_usb_on);
                    }
                    return summ;
                }
            });
    // tts warning
            edit_tts.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    if((TTS != null) && (tts_ok)){
                        TTS.setLanguage(Locale.getDefault());
                        TTS.speak((String)newValue,TextToSpeech.QUEUE_FLUSH,null, null);
                        stop_main_switch(switch_main);
                        return true;
                    }else{
                        edit_tts.setText("");
                        Snackbar.make(getView(), res.getString(R.string.tittle_edit_tts_error), Snackbar.LENGTH_LONG).show();
                        Log.w("rebooter_log","tts speak fail");
                        stop_main_switch(switch_main);
                        return false;
                    }
                }
            });
            edit_tts.setSummaryProvider(new Preference.SummaryProvider() {
                @Override
                public CharSequence provideSummary(Preference preference) {
                    String summ = ((EditTextPreference) preference).getText().toString();
                    return summ;
                }
            });
    // noice warning
            edit_noice.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(@NonNull Preference preference) {
        // sound pick dialog activity
                    Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
                    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
                    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, res.getString(R.string.tittle_edit_noice_dialog));
                    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, (Uri) null);
                    noice_launcher.launch(intent);
                    return true;
                }
            });
            edit_noice.setSummaryProvider(new Preference.SummaryProvider() {
                @Override
                public CharSequence provideSummary(Preference preference) {
                    String summ = "";
                    Uri uri = Uri.parse(((EditTextPreference) preference).getText());
                    if (uri != null){
                        Cursor cursor = getContext().getContentResolver().query(uri, null, null, null, null);
                        if (cursor != null){
                            int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                            cursor.moveToFirst();
                            summ = cursor.getString(nameIndex);
                        }
                    }
                    return summ;
                }
            });

        }

        public void test_reboot(){
            Resources res = getContext().getApplicationContext().getResources();
            Intent service_intent = new Intent(getContext(), RebooterService.class);
            ServiceConnection service_connection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    RebooterService my_service = ((RebooterService.service_binder)service).getService();
                    if (my_service != null){
                        Log.w("rebooter_log","settings activity: bind succesfull, triggering reboot");
                        my_service.load_preferences();
                        my_service.do_reboot();
                // should be rebooting here
                        getContext().getApplicationContext().stopService(service_intent);
                    }
                }
                @Override
                public void onServiceDisconnected(ComponentName name) {

                }
            };
//  autocreate bind without checking
            Log.w("rebooter_log","settings activity: attemting bind_auto_create service");
            getContext().getApplicationContext().bindService(service_intent, service_connection, BIND_AUTO_CREATE);
        }

        // if changing do same in BootReciever
        public void disable_alarm_watchdog(){
            try{
                AlarmManager manager = (AlarmManager)getContext().getApplicationContext().getSystemService(Context.ALARM_SERVICE);
                Intent intent = new Intent(getContext().getApplicationContext(), AlarmReciever.class);
                PendingIntent pending_intent = PendingIntent.getBroadcast(
                        getContext().getApplicationContext(),
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

        public void first_launch_permission_check(){
            Resources res = getContext().getApplicationContext().getResources();
// first launch permissions nag
            AlertDialog.Builder alert = new AlertDialog.Builder(getContext());
            alert.setTitle(res.getString(R.string.tittle_alert_permissions));
            alert.setMessage(res.getString(R.string.desc_alert_permissions));
            alert.setCancelable(true);
            alert.setPositiveButton(res.getString(R.string.desc_alert_proceeed), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
    // root
                    try{
                        Process su = Runtime.getRuntime().exec("su");
                        DataOutputStream outputStream = new DataOutputStream(su.getOutputStream());
                        outputStream.writeBytes("exit\n");
                        outputStream.flush();
                        su.waitFor();
                    }catch(Exception e){
                        Log.w("rebooter_log","permissions exception: " + e.getMessage().toString());
                    }
    // optimisation exclusion
                    try{
                        Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                        i.setData(Uri.parse("package:" + getContext().getApplicationContext().getPackageName()));
                        startActivity(i);
                    }catch(Exception e){
                        Log.w("rebooter_log","permissions exception: " + e.getMessage().toString());
                    }
    // exact alarm
                    try{
                        Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                        i.setData(Uri.parse("package:" + getContext().getApplicationContext().getPackageName()));
                        startActivity(i);
                    }catch(Exception e){
                        Log.w("rebooter_log","permissions exception: " + e.getMessage().toString());
                    }
    // notifications
                    try{
                        Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                        i.putExtra(Settings.EXTRA_APP_PACKAGE, getContext().getApplicationContext().getPackageName());
                        startActivity(i);
                    }catch(Exception e){
                        Log.w("rebooter_log","permissions exception: " + e.getMessage().toString());
                    }
                }
            });
            alert.setNegativeButton(res.getString(R.string.desc_alert_cancel), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {

                }
            });
            alert.show();
        }

        public void stop_main_switch(SwitchPreference main_switch){
            if (main_switch.isChecked()){
                Intent service_intent = new Intent(getContext(), RebooterService.class);
                getContext().stopService(service_intent);
                Resources res = getContext().getResources();
                main_switch.setChecked(false);
                Snackbar.make(getView(), res.getString(R.string.desc_notification_stopped), Snackbar.LENGTH_LONG).show();
            }
        }

    }

}
