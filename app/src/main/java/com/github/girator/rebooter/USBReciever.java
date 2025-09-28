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
import android.util.Log;

public class USBReciever extends BroadcastReceiver {
    public RebooterService my_service;

    @Override
    public void onReceive(Context context, Intent intent) {

        if(intent.getExtras().getBoolean("connected")) {
            Log.w("rebooter_log","usb reciever: connectded");
            if (my_service.usb_mode.equals(my_service.res.getString(R.string.opt_mode_usb_locked).toString())){
                if(!my_service.screen_unlocked){
                    my_service.do_reboot();
                }
            }
            if (my_service.usb_mode.equals(my_service.res.getString(R.string.opt_mode_usb_on).toString())){
                my_service.do_reboot();
            }
        }

    }
}
