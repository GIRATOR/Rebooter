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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.view.View;
import android.widget.NumberPicker;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;

public class RangePreference extends EditTextPreference {
    private Integer mMinValue;
    private Integer mMaxValue;
    private Boolean mWrap;

    public void set_range(Integer new_min, Integer new_max, Boolean new_wrap){
        mMinValue = new_min;
        mMaxValue = new_max;
        mWrap = new_wrap;
    }

    public RangePreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }
    public RangePreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }
    public RangePreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }
    public RangePreference(@NonNull Context context) {
        super(context);
    }

    @Override
    protected void onClick() {
        Resources res = getContext().getApplicationContext().getResources();
        Preference preference = this;
        final View view = ((AppCompatActivity)getContext()).getLayoutInflater().inflate(R.layout.range_preference, null);
        final NumberPicker picker = (NumberPicker) view.findViewById(R.id.picker);
        picker.setMinValue(mMinValue);
        picker.setMaxValue(mMaxValue);
        picker.setWrapSelectorWheel(mWrap);
        picker.setValue(Integer.valueOf(((RangePreference)preference).getText()));
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setView(view);
        builder.setTitle(getDialogTitle());
        builder.setIcon(getDialogIcon());
        builder.setPositiveButton(res.getString(R.string.tittle_edit_interval_ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                String str = String.valueOf(picker.getValue());
                setText(str);
                getOnPreferenceChangeListener().onPreferenceChange(preference, str);
            }
        });
        builder.setNegativeButton(res.getString(R.string.tittle_edit_interval_cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {

            }
        });
        builder.create().show();

    }

}