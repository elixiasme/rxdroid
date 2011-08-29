/**
 * Copyright (C) 2011 Joseph Lehner <joseph.c.lehner@gmail.com>
 * 
 * This file is part of RxDroid.
 *
 * RxDroid is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * RxDroid is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with RxDroid.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * 
 */

package at.caspase.rxdroid;

import java.sql.Date;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.RemoteViews;
import at.caspase.rxdroid.Database.Drug;
import at.caspase.rxdroid.Database.Intake;
import at.caspase.rxdroid.Database.OnDatabaseChangedListener;
import at.caspase.rxdroid.util.Constants;
import at.caspase.rxdroid.util.DateTime;
import at.caspase.rxdroid.util.L10N;

import com.j256.ormlite.android.apptools.OrmLiteBaseService;
import com.j256.ormlite.dao.Dao;

/**
 * Primary notification service.
 * 
 * @author Joseph Lehner
 * 
 */
public class NotificationService extends OrmLiteBaseService<Database.Helper> implements 
		OnDatabaseChangedListener, OnSharedPreferenceChangeListener
{
	public static final String EXTRA_FORCE_RESTART = "force_restart";

	private static final String TAG = NotificationService.class.getName();

	private Dao<Drug, Integer> mDrugDao;
	private Dao<Intake, Integer> mIntakeDao;
	private Intent mIntent;
	private SharedPreferences mSharedPreferences;

	private NotificationManager mNotificationManager;
	private String[] mNotificationMessages;
	
	Thread mThread;

	@Override
	public void onCreate()
	{
		super.onCreate();
		mDrugDao = getHelper().getDrugDao();
		mIntakeDao = getHelper().getIntakeDao();

		mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		cancelAllNotifications();
		
		mIntent = new Intent(Intent.ACTION_VIEW);
		mIntent.setClass(getApplicationContext(), DrugListActivity.class);
		
		Settings.INSTANCE.setApplicationContext(getApplicationContext());

		mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		mSharedPreferences.registerOnSharedPreferenceChangeListener(this);
		Database.registerOnChangedListener(this);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		super.onStartCommand(intent, flags, startId);
		
		if(intent != null)
			restartThread(intent.getBooleanExtra(EXTRA_FORCE_RESTART, false));
		else
			Log.w(TAG, "Intent was null");
		
		return START_STICKY;
	}

	@Override
	public synchronized void onDestroy()
	{
		super.onDestroy();
		mThread.interrupt();
		mThread = null;
		mSharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
		Database.unregisterOnChangedListener(this);
	}

	@Override
	public IBinder onBind(Intent arg0)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void onCreateEntry(Drug drug)
	{
		// checkForgottenIntakes(drug, null, false);
		restartThread(true, true);
	}

	@Override
	public void onDeleteEntry(Drug drug)
	{
		// checkForgottenIntakes(drug, null, true);
		restartThread(true, true);
	}

	@Override
	public void onUpdateEntry(Drug drug)
	{
		// checkForgottenIntakes(drug, null, false);
		restartThread(true, true);
	}

	@Override
	public void onCreateEntry(Intake intake)
	{
		// checkForgottenIntakes(null, intake, false);
		restartThread(true);
	}

	@Override
	public void onDeleteEntry(Intake intake)
	{
		// checkForgottenIntakes(null, intake, true);
		restartThread(true);
	}

	@Override
	public void onDatabaseDropped()
	{
		restartThread(true, true);
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPrefs, String key)
	{
		if(key.startsWith("time_"))
			restartThread(true);
		else
			Log.d(TAG, "Ignoring preference change of " + key);
	}

	/**
	 * (Re)starts the worker thread.
	 * 
	 * Calling this function will cause the service to consult the DB in order
	 * to determine when the next notification should be posted. Currently, the
	 * worker thread is restarted when <em>any</em> database changes occur (see
	 * DatabaseWatcher) or when the user opens the app.
	 */

	private void restartThread(boolean forceRestart) {
		restartThread(forceRestart, false);
	}
	
	private synchronized void restartThread(boolean forceRestart, final boolean forceSupplyCheck)
	{
		final boolean wasRunning;
		
		if(mThread != null)
		{
			if(!forceRestart)
			{
				Log.d(TAG, "Ignoring service restart request");
				return;
			}

			wasRunning = true;
			mThread.interrupt();
		}
		else
			wasRunning = false;
		
		Log.d(TAG, "restartThread(" + forceRestart + "): wasRunning=" + wasRunning);

		mThread = new Thread(new Runnable() {

			@Override
			public void run()
			{
				/**
				 * TODO:
				 * 
				 * - on start, clear all notifications
				 * 
				 * - collect forgotten intakes & display notifications if
				 * necessary - if a dose time is active, collect pending intakes
				 * & display notifications if neccessary. do so every N minutes
				 * (as specified by the snooze time), until the next dose time
				 * becomes active
				 * 
				 * - if the active dose time is TIME_MORNING, also check supply
				 * levels & display notifications, if applicable
				 * 
				 * - if no dose time is active, sleep until the start of the
				 * next dose time
				 * 
				 * 
				 * 
				 * 
				 * 
				 * 
				 * 
				 * 
				 */

				cancelAllNotifications();
				if(!wasRunning || forceSupplyCheck)
					checkSupplies();
				
				boolean delayFirstNotification = true;
								
				try
				{					
					while(true)
					{
						final Date date = DateTime.today();
						mIntent.putExtra(DrugListActivity.EXTRA_DAY, date);

						final int activeDoseTime = Settings.INSTANCE.getActiveDoseTime();
						final int nextDoseTime = Settings.INSTANCE.getNextDoseTime();
						final int lastDoseTime = (activeDoseTime == -1) ? (nextDoseTime - 1) : (activeDoseTime - 1);

						Log.d(TAG, "times: active=" + activeDoseTime + ", next=" + nextDoseTime + ", last=" + lastDoseTime);

						if(lastDoseTime >= 0)
							checkIntakes(date, lastDoseTime);

						if(activeDoseTime == -1)
						{
							long sleepTime = Settings.INSTANCE.getMillisFromNowUntilDoseTimeBegin(nextDoseTime);

							Log.d(TAG, "Time until next dose time (" + nextDoseTime + "): " + sleepTime + "ms (" + 
									new DumbTime(sleepTime).toString(true) + ")");

							Thread.sleep(sleepTime);
							delayFirstNotification = false;

							if(Settings.INSTANCE.getActiveDoseTime() != nextDoseTime)
								Log.e(TAG, "Unexpected dose time, expected " + nextDoseTime);
							
							continue;
						}
						else if(activeDoseTime == Drug.TIME_MORNING)
						{
							mNotificationManager.cancel(R.id.notification_intake_forgotten);
							checkSupplies();
						}

						long millisUntilDoseTimeEnd = Settings.INSTANCE.getMillisFromNowUntilDoseTimeEnd(activeDoseTime);

						final Set<Intake> pendingIntakes = getAllOpenIntakes(date, activeDoseTime);

						Log.d(TAG, "Pending intakes: " + pendingIntakes.size());
						
						if(!pendingIntakes.isEmpty())
						{
							if(delayFirstNotification && wasRunning)
							{
								delayFirstNotification = false;
								Log.d(TAG, "Delaying first notification");
								// FIXME export this
								Thread.sleep(10000);
							}

							final String contentText = Integer.toString(pendingIntakes.size());
							final long snoozeTime = Settings.INSTANCE.getSnoozeTime();
							
							do
							{
								postNotification(R.id.notification_intake_pending, Notification.DEFAULT_ALL, contentText);
								Thread.sleep(snoozeTime);
								millisUntilDoseTimeEnd -= snoozeTime;
								
							} while(millisUntilDoseTimeEnd > snoozeTime);
							
							Log.d(TAG, "Finished loop");
						}

						if(millisUntilDoseTimeEnd > 0)
						{
							Log.d(TAG, "Sleeping " + millisUntilDoseTimeEnd + "ms (" + new DumbTime(millisUntilDoseTimeEnd).toString(true) + ") " +
									"until end of dose time " + activeDoseTime);
							Thread.sleep(millisUntilDoseTimeEnd);
						}
						
						cancelNotification(R.id.notification_intake_pending);

						Log.d(TAG, "Finished iteration");
					}
				}
				catch(InterruptedException e)
				{
					Log.d(TAG, "Thread interrupted, exiting...");
				}
			}
		});

		mThread.start();
	}

	private Set<Intake> getAllOpenIntakes(Date date, int doseTime)
	{
		final Set<Intake> openIntakes = new HashSet<Database.Intake>();
		final List<Drug> drugs;

		try
		{
			drugs = mDrugDao.queryForAll();
		}
		catch(SQLException e)
		{
			throw new RuntimeException(e);
		}

		for(Drug drug : drugs)
		{
			if(drug.isActive())
			{
				final List<Intake> intakes = Database.findIntakes(mIntakeDao, drug, date, doseTime);

				if(drug.getDose(doseTime).compareTo(0) != 0 && intakes.size() == 0)
				{
					Log.d(TAG, "getAllOpenIntakes: adding " + drug);
					openIntakes.add(new Intake(drug, date, doseTime));
				}
			}
		}

		return openIntakes;
	}

	private Set<Intake> getAllForgottenIntakes(Date date, int lastDoseTime)
	{
		final Date today = DateTime.today();

		if(date.after(today))
			return Collections.emptySet();

		if(date.before(today))
			lastDoseTime = -1;

		final int doseTimes[] = { Drug.TIME_MORNING, Drug.TIME_NOON, Drug.TIME_EVENING, Drug.TIME_NIGHT };
		final Set<Intake> forgottenIntakes = new HashSet<Database.Intake>();

		for(int doseTime : doseTimes)
		{
			forgottenIntakes.addAll(getAllOpenIntakes(date, doseTime));

			if(doseTime == lastDoseTime)
				break;
		}

		return forgottenIntakes;
	}

	private void checkIntakes(Date date, int lastDoseTime)
	{
		final Set<Intake> forgottenIntakes = getAllForgottenIntakes(date, lastDoseTime);
		
		Log.d(TAG, forgottenIntakes.size() + " forgotten intakes");

		if(!forgottenIntakes.isEmpty())
		{
			final String contentText = Integer.toString(forgottenIntakes.size());
			postNotification(R.id.notification_intake_forgotten, Notification.DEFAULT_LIGHTS, contentText);
		}
		else
		{
			cancelNotification(R.id.notification_intake_forgotten);
			//mNotificationManager.cancel(R.id.notification_intake_forgotten);
		}
	}

	private void checkSupplies()
	{
		// FIXME
		final int MIN_DAYS = 7;
		
		final List<Drug> drugsWithLowSupply = getAllDrugsWithLowSupply(MIN_DAYS);
		
		if(!drugsWithLowSupply.isEmpty())
		{
			final String firstDrugName = drugsWithLowSupply.get(0).getName();
			final String contentText;
			
			if(drugsWithLowSupply.size() == 1)
				contentText = getString(R.string._msg_low_supply_single, firstDrugName);
			else
				contentText = getString(R.string._msg_low_supply_multiple, firstDrugName, drugsWithLowSupply.size() - 1);
			
			postNotification(R.id.notification_low_supplies, Notification.DEFAULT_LIGHTS, contentText);
		}
		else
			cancelNotification(R.id.notification_low_supplies);
	}

	private List<Drug> getAllDrugsWithLowSupply(int minDays)
	{
		final List<Drug> drugs;

		try
		{
			drugs = mDrugDao.queryForAll();
		}
		catch(SQLException e)
		{
			throw new RuntimeException(e);
		}

		ArrayList<Drug> drugsWithLowSupply = new ArrayList<Drug>();

		for(Drug drug : drugs)
		{
			// refill size of zero means ignore supply values
			if(drug.getRefillSize() == 0)
				continue;

			final int doseTimes[] = { Drug.TIME_MORNING, Drug.TIME_NOON, Drug.TIME_EVENING, Drug.TIME_NIGHT };
			double dailyDose = 0;

			for(int doseTime : doseTimes)
			{
				final Fraction dose = drug.getDose(doseTime);
				if(dose.compareTo(0) != 0)
					dailyDose += dose.doubleValue();
			}

			if(dailyDose != 0)
			{
				final double currentSupply = drug.getCurrentSupply().doubleValue();

				if(Double.compare(currentSupply / dailyDose, (double) minDays) == -1)
					drugsWithLowSupply.add(drug);
			}
		}

		return drugsWithLowSupply;
	}
	
	private void postNotification(int id, int defaults, String message)
	{
		mNotificationMessages[notificationIdToIndex(id)] = message;
		int notificationCount;
		
		if((notificationCount = getNotificationCount()) == 0)
		{
			mNotificationManager.cancel(R.id.notification);
			return;
		}
		
		final String bullet;
		
		if(mNotificationMessages[2] != null && notificationCount != 1)
		{
			// we have 2 notifications, use bullets!
			bullet = Constants.NOTIFICATION_BULLET;
		}
		else
			bullet = "";
				
		StringBuilder msgBuilder = new StringBuilder();
				
		final String doseMsgPending = mNotificationMessages[0];
		final String doseMsgForgotten = mNotificationMessages[1];
		
		int stringId = -1;
		
		if(doseMsgPending != null && doseMsgForgotten != null)
			stringId = R.string._msg_doses_fp;
		else if(doseMsgPending != null)
			stringId = R.string._msg_doses_p;
		else if(doseMsgForgotten != null)
			stringId = R.string._msg_doses_f;
		
		if(stringId != -1)
			msgBuilder.append(bullet + getString(stringId, doseMsgForgotten, doseMsgPending) + "\n");
				
		final String doseMsgLowSupply = mNotificationMessages[2];
		
		if(doseMsgLowSupply != null)
			msgBuilder.append(bullet + doseMsgLowSupply);
		
				
		final RemoteViews views = new RemoteViews(getPackageName(), R.layout.notification);
		views.setTextViewText(R.id.stat_title, getString(R.string._title_notifications));
		views.setTextViewText(R.id.stat_text, msgBuilder.toString());
		views.setTextViewText(R.id.stat_time, new SimpleDateFormat("HH:mm").format(new Date(System.currentTimeMillis())));
		
		final Notification notification = new Notification();
		notification.icon = R.drawable.ic_stat_pill;
		notification.tickerText = getString(R.string._msg_new_notification);
		notification.flags |= Notification.FLAG_NO_CLEAR;
		notification.defaults |= defaults;
		notification.contentIntent = PendingIntent.getActivity(getApplicationContext(), 0, mIntent, 0);
		notification.contentView = views;
		if(notificationCount > 1)
			notification.number = notificationCount;
				
		mNotificationManager.notify(R.id.notification, notification);		
	}
	
	private void cancelNotification(int id) {
		postNotification(id, 0, null);			
	}
	
	private void cancelAllNotifications() {
		mNotificationManager.cancel(R.id.notification);
		mNotificationMessages = new String[3];
	}
	
	private static int notificationIdToIndex(int id)
	{
		switch(id)
		{
			case R.id.notification_intake_pending:
				return 0;
				
			case R.id.notification_intake_forgotten:
				return 1;
				
			case R.id.notification_low_supplies:
				return 2;
		}
		
		throw new IllegalArgumentException();
	}
	
	private int getNotificationCount()
	{
		int count = 0;
		
		for(String msg : mNotificationMessages)
		{
			if(msg != null)
				++count;
		}
		
		return count;
	}
}