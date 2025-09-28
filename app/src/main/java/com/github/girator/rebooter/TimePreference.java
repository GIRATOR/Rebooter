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

import android.app.TimePickerDialog;
import android.content.Context;
import android.util.AttributeSet;
import android.widget.TimePicker;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;

public class TimePreference extends EditTextPreference {

    public Boolean use_edittext = false;

    public TimePreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public TimePreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public TimePreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public TimePreference(@NonNull Context context) {
        super(context);
    }

    @Override
    protected void onClick() {
        if (use_edittext){
            super.onClick();
            use_edittext = false;
        }else{
            String[] str = (getText() + ":00:00").split(":");
            Preference preference = this;
            TimePickerDialog picker = new TimePickerDialog(
                    getContext(),
                    new TimePickerDialog.OnTimeSetListener() {
                        @Override
                        public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                            String str = String.format("%02d", hourOfDay) + ":" + String.format("%02d", minute);
                            setText(str);
                            getOnPreferenceChangeListener().onPreferenceChange(preference, str);
                        }
                    },
                    Integer.parseInt(str[0]),
                    Integer.parseInt(str[1]),
                    true
            );
            picker.setTitle(this.getDialogTitle());
            picker.setIcon(this.getDialogIcon());
            picker.show();
        }

    }

}
