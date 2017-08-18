package zeusro.specialalarmclock.activity;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import zeusro.specialalarmclock.bean.Alarm;
import zeusro.specialalarmclock.Database;
import zeusro.specialalarmclock.R;
import zeusro.specialalarmclock.adapter.AlarmListAdapter;
import zeusro.specialalarmclock.receiver.NotificationWakeUpReceiver;
import zeusro.specialalarmclock.repository.HolidayRepository;

/**
 * 主activity
 */
public class AlarmActivity extends BaseActivity implements View.OnClickListener{

    AlarmListAdapter alarmListAdapter;
    ListView lvAlarm;
    ImageButton btnAdd;
    ImageButton btnSetting;
    private boolean isExit;
    public final static int notificationId = 1;
    HolidayRepository repository;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        Toast.makeText(this, R.string.Thank, Toast.LENGTH_SHORT).show();
        repository = new HolidayRepository(this);
        initView();
    }
    private void initView() {
        initAlarmList();
        initAddAlarmButton();
        initSettingButton();
    }

    @Override
    protected void onPause() {
        Database.deactivate();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateAlarmList();
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d("onActivityResult", String.valueOf(resultCode));
        switch (resultCode) {
            case RESULT_OK:
                Bundle b = data.getExtras();
                Alarm alarm = (Alarm) b.getSerializable("object");//回传的值
                if (alarm != null) {
                    Log.d("data", alarm.getAlarmName());
                }
                break;
            default:
                break;
        }
    }


    /**
     * Called when a view has been clicked.
     *
     * @param v The view that was clicked.
     */
    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.checkBox_alarm_active) {
            CheckBox checkBox = (CheckBox) v;
            Alarm alarm = (Alarm) alarmListAdapter.getItem((Integer) checkBox.getTag());
            alarm.setAlarmActive(checkBox.isChecked());
            Database.update(alarm);
            AlarmActivity.this.callAlarmServiceBroadcastReceiver(alarm);
            if (checkBox.isChecked()) {
                Toast.makeText(AlarmActivity.this, alarm.getTimeUntilNextAlarmMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }


    private void initAddAlarmButton() {
        btnAdd = (ImageButton) findViewById(R.id.btn_add_alarm);
        if (btnAdd != null) {
            btnAdd.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    Intent newAlarmIntent = new Intent(getApplicationContext(), AlarmPreferencesActivity.class);
                    startActivityForResult(newAlarmIntent, 0);
                }

            });
        }
    }


    private void initAlarmList() {
        lvAlarm = (ListView) findViewById(R.id.listView);
        if (lvAlarm != null) {
            lvAlarm.setLongClickable(true);
            lvAlarm.setOnItemLongClickListener(new OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> adapterView, View view, int position, long id) {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    final Alarm alarm = (Alarm) alarmListAdapter.getItem(position);
                    Builder dialog = new AlertDialog.Builder(AlarmActivity.this);
//                    dialog.setTitle("删除这个闹钟?");
                    dialog.setMessage("删除这个闹钟?");
                    dialog.setPositiveButton("取消", new OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
                    dialog.setNegativeButton("删除", new OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Database.init(AlarmActivity.this);
                            Database.deleteEntry(alarm);
                            //取消
                            AlarmActivity.this.cancelAlarmServiceBroadcastReceiver();
                            updateAlarmList();
                        }
                    });
                    dialog.show();
                    return true;
                }
            });
            callAlarmServiceBroadcastReceiver(null);
            alarmListAdapter = new AlarmListAdapter(this);
            this.lvAlarm.setAdapter(alarmListAdapter);
            lvAlarm.setOnItemClickListener(new AdapterView.OnItemClickListener() {

                @Override
                public void onItemClick(AdapterView<?> arg0, View v, int position, long id) {
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    Alarm alarm = (Alarm) alarmListAdapter.getItem(position);
                    Intent intent = new Intent(AlarmActivity.this, AlarmPreferencesActivity.class);
                    intent.putExtra("alarm", alarm);
                    startActivityForResult(intent, 0);
                }

            });
        }
    }

    private void initSettingButton() {
        btnSetting = (ImageButton) findViewById(R.id.Setting);
        if (btnSetting != null) {
            btnSetting.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    CreateNotification(null);
                    repository.getHolidayFromRemote("2017", new HolidayRepository.Callback() {
                        @Override
                        public void showResult(String result) {
                            Toast.makeText(AlarmActivity.this,"holiday: "+ result, Toast.LENGTH_SHORT).show();
                        }
                    });
                    repository.getSpecialWorkingDayFromRemote("2017", new HolidayRepository.Callback() {
                        @Override
                        public void showResult(String result) {
                            Toast.makeText(AlarmActivity.this,"workingDay: "+result, Toast.LENGTH_SHORT).show();
                        }
                    });
                }

            });
        }
    }

    public void updateAlarmList() {
        Database.init(AlarmActivity.this);
        final List<Alarm> alarms = Database.getAll();
        alarmListAdapter.setMathAlarms(alarms);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // reload content
                AlarmActivity.this.alarmListAdapter.notifyDataSetChanged();
                TextView text = (TextView) findViewById(R.id.textView);
                if (alarms != null && alarms.size() > 0) {
                    text.setVisibility(View.GONE);
                } else {
                    text.setText(R.string.NoClockAlert);
                    text.setVisibility(View.VISIBLE);
                }
            }
        });
    }


    @Override
    public void onBackPressed() {
        if (!isExit) {
            isExit = true; // 准备退出
            Toast.makeText(this, "再按一次退出", Toast.LENGTH_SHORT).show();
            Timer tExit = new Timer();
            tExit.schedule(new TimerTask() {
                @Override
                public void run() {
                    isExit = false; // 取消退出
                }
            }, 2000); // 如果2秒钟内没有按下返回键，则启动定时器取消掉刚才执行的任务
        } else {
            finish();
        }
    }

    private void CreateNotification(Alarm alarm) {
        Intent intent = new Intent();
        intent.setClass(this, NotificationWakeUpReceiver.class);
        sendBroadcast(intent);//发送广播事件
    }
}
