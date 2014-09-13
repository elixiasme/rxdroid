/**
 * RxDroid - A Medication Reminder
 * Copyright (C) 2011-2014 Joseph Lehner <joseph.c.lehner@gmail.com>
 *
 *
 * RxDroid is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version. Additional terms apply (see LICENSE).
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

package at.jclehner.rxdroid;

import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.DatePickerDialog.OnDateSetListener;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceScreen;
import android.support.v4.preference.PreferenceFragment;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.ListView;
import android.widget.Toast;
import at.jclehner.androidutils.AdvancedDialogPreference;
import at.jclehner.androidutils.otpm.AdvancedDialogPreferenceController;
import at.jclehner.androidutils.otpm.CheckboxPreferenceController;
import at.jclehner.androidutils.otpm.DialogPreferenceController;
import at.jclehner.androidutils.otpm.ListPreferenceWithIntController;
import at.jclehner.androidutils.otpm.OTPM;
import at.jclehner.androidutils.otpm.OTPM.CreatePreference;
import at.jclehner.rxdroid.db.Database;
import at.jclehner.rxdroid.db.Drug;
import at.jclehner.rxdroid.db.Entries;
import at.jclehner.rxdroid.db.Patient;
import at.jclehner.rxdroid.db.Schedule;
import at.jclehner.rxdroid.preferences.DosePreference;
import at.jclehner.rxdroid.preferences.DrugNamePreference2;
import at.jclehner.rxdroid.preferences.FractionPreference;
import at.jclehner.rxdroid.util.CollectionUtils;
import at.jclehner.rxdroid.util.Constants;
import at.jclehner.rxdroid.util.DateTime;
import at.jclehner.rxdroid.util.SimpleBitSet;
import at.jclehner.rxdroid.util.Util;

/**
 * Edit a drug's database entry.
 * @author Joseph Lehner
 *
 */
public class DrugEditFragment extends PreferenceFragment implements OnPreferenceClickListener
{
	private static final int MENU_DELETE = 0;

	//private static final String ARG_DRUG = "drug";

	private static final String TAG = DrugEditFragment.class.getSimpleName();
	private static final boolean LOGV = true;

	private DrugWrapper mWrapper;
	private int mDrugHash;

	// if true, we're editing an existing drug; if false, we're adding a new one
	private boolean mIsEditing;

	private boolean mFocusOnCurrentSupply = false;

	public void onBackPressed()
	{
		final Intent intent = getActivity().getIntent();
		final String action = intent.getAction();

		final Drug drug = mWrapper.get();
		final String drugName = drug.getName();

		if(drugName == null || drugName.length() == 0)
		{
			showDrugDiscardDialog();
			return;
		}

		if(Intent.ACTION_EDIT.equals(action))
		{
			if(mDrugHash != drug.hashCode())
			{
				if(LOGV) Util.dumpObjectMembers(TAG, Log.VERBOSE, drug, "drug 2");

				showSaveChangesDialog();
				return;
			}
		}
		else if(Intent.ACTION_INSERT.equals(action))
		{
			Database.create(drug, 0);
			Toast.makeText(getActivity(), getString(R.string._toast_saved), Toast.LENGTH_SHORT).show();
		}

		getActivity().finish();
	}

	@Override
	public boolean onPreferenceClick(Preference preference)
	{
		if(preference.getKey().equals("delete"))
		{
			showDrugDeleteDialog();
			return true;
		}

		return false;
	}

	@TargetApi(11)
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.empty);
		setHasOptionsMenu(true);

		//final ListView list = getListView();
		//if(list != null)
		//	list.setSelector(Theme.getResourceAttribute(R.attr.selectableItemBackground));
	}

	@Override
	public void onResume()
	{
		super.onResume();

		Intent intent = getActivity().getIntent();
		String action = intent.getAction();

		Drug drug = null;

		mWrapper = new DrugWrapper();
		mFocusOnCurrentSupply = false;

		if(Intent.ACTION_EDIT.equals(action))
		{
			final int drugId = intent.getIntExtra(DrugEditActivity2.EXTRA_DRUG_ID, -1);
			if(drugId == -1)
				throw new IllegalStateException("ACTION_EDIT requires EXTRA_DRUG_ID");

			drug = Drug.get(drugId);

			if(LOGV) Util.dumpObjectMembers(TAG, Log.VERBOSE, drug, "drug");

			mWrapper.set(drug);
			mDrugHash = drug.hashCode();
			mIsEditing = true;

			if(intent.getBooleanExtra(DrugEditActivity2.EXTRA_FOCUS_ON_CURRENT_SUPPLY, false))
				mFocusOnCurrentSupply = true;

			setActivityTitle(drug.getName());
		}
		else if(Intent.ACTION_INSERT.equals(action))
		{
			mIsEditing = false;
			mWrapper.set(new Drug());
			setActivityTitle(R.string._title_new_drug);
		}
		else
			throw new IllegalArgumentException("Unhandled action " + action);

		OTPM.mapToPreferenceHierarchy(getPreferenceScreen(), mWrapper);
		getPreferenceScreen().setOnPreferenceChangeListener(mListener);

		/*Preference deletePref = findPreference("delete");
		if(deletePref != null)
		{
			if(Version.SDK_IS_HONEYCOMB_OR_NEWER || !mIsEditing)
				getPreferenceScreen().removePreference(deletePref);
			else
				deletePref.setOnPreferenceClickListener(this);
		}*/

		if(mFocusOnCurrentSupply)
		{
			Log.i(TAG, "Will focus on current supply preference");
			performPreferenceClick("currentSupply");
		}

		getActivity().supportInvalidateOptionsMenu();
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
	{
		if(mIsEditing && !getActivity().getIntent().getBooleanExtra(DrugEditActivity2.EXTRA_DISALLOW_DELETE, false))
		{
			MenuItem item = menu.add(0, MENU_DELETE, 0, R.string._title_delete)
					.setIcon(R.drawable.ic_action_delete_white);

			MenuItemCompat.setShowAsAction(item, MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
		}

		super.onCreateOptionsMenu(menu, inflater);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		final int itemId = item.getItemId();

		if(itemId == android.R.id.home)
		{
			// We can do this since this Activity can only be launched from
			// DrugListActivity at the moment.
			onBackPressed();
			return true;
		}
		else if(itemId == MENU_DELETE)
		{
			showDrugDeleteDialog();
			return true;
		}

		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig)
	{
		super.onConfigurationChanged(newConfig);
		// This activity will not be restarted when the screen orientation changes, otherwise
		// the OTPM stuff in onCreate() would reinitialize the Preferences in the hierarchy,
		// thus not restoring their original state.
	}

	private void setActivityTitle(String title) {
		((ActionBarActivity) getActivity()).getSupportActionBar().setTitle(title);
	}

	private void setActivityTitle(int resId) {
		((ActionBarActivity) getActivity()).getSupportActionBar().setTitle(resId);
	}

	private void showDrugDeleteDialog()
	{
		final String message = getString(R.string._title_delete_drug, mWrapper.get().getName())
				+ " " + getString(R.string._msg_delete_drug);

		final AlertDialog.Builder ab = new AlertDialog.Builder(getActivity());

		ab.setIcon(android.R.drawable.ic_dialog_alert);
		ab.setMessage(message);
		ab.setNegativeButton(android.R.string.no, null);
		ab.setPositiveButton(android.R.string.yes, new OnClickListener()
		{
			@Override
			public void onClick(DialogInterface dialog, int which)
			{
				Database.delete(mWrapper.get());
				Toast.makeText(getActivity(), R.string._toast_deleted, Toast.LENGTH_SHORT).show();
				getActivity().finish();
			}
		});

		ab.show();
	}

	private void showDrugDiscardDialog()
	{
		final AlertDialog.Builder ab = new AlertDialog.Builder(getActivity());
		ab.setMessage(R.string._msg_err_empty_drug_name);
		ab.setNegativeButton(android.R.string.cancel, null);
		ab.setPositiveButton(android.R.string.ok, new OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which)
			{
				getActivity().finish();
			}
		});

		ab.show();
	}

	private void showSaveChangesDialog()
	{
		final AlertDialog.Builder ab = new AlertDialog.Builder(getActivity());
		ab.setMessage(R.string._msg_save_drug_changes);

		final DialogInterface.OnClickListener l = new DialogInterface.OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which)
			{
				if(which == Dialog.BUTTON_POSITIVE)
				{
					Database.update(mWrapper.get());
					Toast.makeText(getActivity(), R.string._toast_saved, Toast.LENGTH_SHORT).show();
				}

				getActivity().finish();
			}
		};

		ab.setNegativeButton(R.string._btn_discard, l);
		ab.setPositiveButton(R.string._btn_save, l);

		ab.show();
	}

	private void performPreferenceClick(String key)
	{
		final PreferenceScreen ps = getPreferenceScreen();
		for(int i = 0; i != ps.getPreferenceCount(); ++i)
		{
			if(key.equals(ps.getPreference(i).getKey()))
			{
				ps.onItemClick(getListView(), null, i, 0);
				break;
			}
		}
	}

	private static class DrugWrapper
	{
		@CreatePreference
		(
			titleResId = R.string._title_drug_name,
			order = 1,
			type = DrugNamePreference2.class,
			controller = DrugNamePreferenceController.class
		)
		private String name;

		@CreatePreference
		(
			titleResId = R.string._title_morning,
			key = "morning",
			categoryResId = R.string._title_intake_schedule,
			order = 3,
			type = DosePreference.class,
			controller = AdvancedDialogPreferenceController.class
		)
		private Fraction doseMorning;

		@CreatePreference
		(
			titleResId = R.string._title_noon,
			key = "noon",
			order = 4,
			type = DosePreference.class,
			controller = AdvancedDialogPreferenceController.class
		)
		private Fraction doseNoon;

		@CreatePreference
		(
			titleResId = R.string._title_evening,
			key = "evening",
			order = 5,
			type = DosePreference.class,
			controller = AdvancedDialogPreferenceController.class
		)
		private Fraction doseEvening;

		@CreatePreference
		(
			titleResId = R.string._title_night,
			key = "night",
			endActiveCategory = true,
			order = 6,
			type = DosePreference.class,
			controller = AdvancedDialogPreferenceController.class
		)
		private Fraction doseNight;

		@CreatePreference
		(
			titleResId = R.string._title_repeat,
			order = 7,
			type = ListPreference.class,
			controller = RepeatModePreferenceController.class,
			fieldDependencies = { "repeatArg", "repeatOrigin" }
		)
		private int repeat;

		@CreatePreference
		(
			titleResId = R.string._title_icon,
			categoryResId = R.string._title_misc,
			order = 8,
			type = ListPreference.class,
			controller = FormPreferenceController.class
		)
		private int form;

		@CreatePreference
		(
			titleResId = R.string._title_refill_size,
			order = 10,
			type = FractionPreference.class,
			controller = RefillSizePreferenceController.class
		)
		private int refillSize;

		@CreatePreference
		(
			titleResId = R.string._title_current_supply,
			order = 11,
			type = CurrentSupplyPreference.class,
			controller = CurrentSupplyPreferenceController.class,
			reverseDependencies = { "morning", "noon", "evening", "night", "refillSize", "repeat"},
			fieldDependencies = { "repeatArg", "repeatOrigin" }
		)
		private Fraction currentSupply;

		@CreatePreference
		(
			titleResId = R.string._title_per_drug_reminders,
			order = 12,
			type = ListPreference.class,
			controller = NotificationsPreferenceController.class
		)
		private boolean autoAddIntakes;

		@CreatePreference
		(
			titleResId = R.string._title_active,
			summary = "",
			order = 13,
			type = CheckBoxPreference.class,
			controller = CheckboxPreferenceController.class
		)
		private boolean active;

		private int id;

		private long repeatArg;
		private Date repeatOrigin;
		private int sortRank;
		private List<Schedule> schedules;
		private Patient patient;
		private String comment;

		/*@CreatePreference
		(
			title = "lastAutoIntakeCreationDate",
			order = 14,
			type = Preference.class,
			helper = ReadonlyPreferenceHelper.class,
			reverseDependencies = "autoAddIntakes"
		)*/
		private Date lastAutoIntakeCreationDate;

		public void set(Drug drug)
		{
			id = drug.getId();
			active = drug.isActive();
			comment = drug.getComment();
			currentSupply = drug.getCurrentSupply();
			doseMorning = drug.getDose(Drug.TIME_MORNING);
			doseNoon = drug.getDose(Drug.TIME_NOON);
			doseEvening = drug.getDose(Drug.TIME_EVENING);
			doseNight = drug.getDose(Drug.TIME_NIGHT);
			refillSize = drug.getRefillSize();
			repeat = drug.getRepeatMode();
			repeatArg = drug.getRepeatArg();
			repeatOrigin = drug.getRepeatOrigin();
			//schedule = drug.getSchedule();
			sortRank = drug.getSortRank();
			autoAddIntakes = drug.hasAutoDoseEvents();
			lastAutoIntakeCreationDate = drug.getLastAutoDoseEventCreationDate();
			patient = drug.getPatient();
			schedules = drug.getSchedules();

			name = drug.getName();
			form = drug.getIcon();

			if(LOGV) Log.v(TAG, "DrugWrapper.set: repeatOrigin=" + repeatOrigin);
		}

		public Drug get()
		{
			Drug drug = new Drug();
			drug.setId(id);
			drug.setName(name);
			drug.setIcon(form);
			drug.setActive(active);
			drug.setComment(comment);
			drug.setCurrentSupply(currentSupply);
			drug.setRefillSize(refillSize);
			drug.setRepeatMode(repeat);
			drug.setSortRank(sortRank);
			//drug.setSchedule(schedule);
			drug.setLastAutoDoseEventCreationDate(lastAutoIntakeCreationDate);
			drug.setAutoAddIntakesEnabled(autoAddIntakes);
			drug.setPatient(patient);
			drug.setSchedules(schedules);

			final Fraction doses[] = { doseMorning, doseNoon, doseEvening, doseNight };

			for(int i = 0; i != doses.length; ++i)
				drug.setDose(Constants.DOSE_TIMES[i], doses[i]);

			drug.setRepeatArg(repeatArg);
			drug.setRepeatOrigin(repeatOrigin);

			if(LOGV) Log.v(TAG, "DrugWrapper.get: repeatOrigin=" + repeatOrigin);

			return drug;
		}
	}

	private static class DrugNamePreferenceController extends AdvancedDialogPreferenceController
	{
		public DrugNamePreferenceController() {}

		@Override
		public boolean updatePreference(AdvancedDialogPreference preference, Object newValue)
		{
			try
			{
				((ActionBarActivity) preference.getContext()).getSupportActionBar().setTitle((String) newValue);
			}
			catch(ClassCastException e)
			{
				e.printStackTrace();
			}

			return super.updatePreference(preference, newValue);
		}
	}

	private static class RepeatModePreferenceController extends ListPreferenceWithIntController
	{
		private ListPreference mPref;
		private Context mContext;

		public RepeatModePreferenceController() {
			super(R.array.drug_repeat);
		}

		@Override
		public void initPreference(ListPreference preference, Integer fieldValue)
		{
			super.initPreference(preference, fieldValue);

			mPref = preference;
			mContext = preference.getContext();

			//preference.setDependency("currentSupply");

			updateSummary();
		}

		@Override
		public Integer toFieldType(Object prefValue) {
			return Integer.valueOf((String) prefValue, 10);
		}

		@Override
		public boolean updatePreference(ListPreference preference, Integer newValue)
		{
			switch(newValue)
			{
				case Drug.REPEAT_EVERY_N_DAYS:
					handleEveryNDaysRepeatMode();
					return false;

				case Drug.REPEAT_WEEKDAYS:
					handleWeekdaysRepeatMode();
					return false;

				case Drug.REPEAT_21_7:
					handle21_7RepeatMode();
					return false;

				case Drug.REPEAT_DAILY:
				case Drug.REPEAT_AS_NEEDED:
					return super.updatePreference(preference, newValue);

				default:
					Toast.makeText(mContext, "Not implemented", Toast.LENGTH_LONG).show();
					return false;
			}
		}

		@Override
		public void updateSummary(ListPreference preference, Integer newValue)
		{
			switch(newValue)
			{
				case Drug.REPEAT_DAILY:
					preference.setSummary(R.string._title_daily);
					break;

				case Drug.REPEAT_AS_NEEDED:
					preference.setSummary(R.string._title_on_demand);
					break;

				case Drug.REPEAT_EVERY_N_DAYS:
				case Drug.REPEAT_WEEKDAYS:
				case Drug.REPEAT_21_7:
					// summary is updated from the handle<repeat mode>() functions
					break;

				default:
					super.updateSummary(preference, newValue);
			}
		}

		private void handleEveryNDaysRepeatMode()
		{
			final Date repeatOrigin;
			final long repeatArg;

			int oldRepeatMode = getFieldValue();

			if(oldRepeatMode != Drug.REPEAT_EVERY_N_DAYS)
			{
				repeatOrigin = DateTime.today();
				repeatArg = 2;
			}
			else
			{
				repeatOrigin = (Date) getFieldValue("repeatOrigin");
				repeatArg = (Long) getFieldValue("repeatArg");
			}

			final NumberPickerWrapper picker = new NumberPickerWrapper(mContext);
			picker.setMinValue(2);
			picker.setWrapSelectorWheel(false);
			picker.setValue((int) repeatArg);
			picker.setGravity(Gravity.CENTER_HORIZONTAL);

			picker.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT,
					LayoutParams.WRAP_CONTENT));


			final AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
			builder.setTitle(R.string._title_every_n_days);
			builder.setMessage(R.string._msg_every_n_days_distance);
			builder.setView(picker);
			builder.setCancelable(true);
			builder.setNegativeButton(android.R.string.cancel, null);
			builder.setPositiveButton(android.R.string.ok, new OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which)
				{
					final long repeatArg = picker.getValue();
					showRepeatOriginDateDialog(Drug.REPEAT_EVERY_N_DAYS, repeatOrigin, repeatArg);
				}
			});

			builder.show();
		}

		private void handleWeekdaysRepeatMode()
		{
			if(getFieldValue() != Drug.REPEAT_WEEKDAYS)
			{
				setFieldValue("repeatArg", 0);
				setFieldValue("repeatOrigin", DateTime.today());
			}

			long repeatArg = (Long) getFieldValue("repeatArg");
			final boolean[] checkedItems = SimpleBitSet.toBooleanArray(repeatArg, Constants.LONG_WEEK_DAY_NAMES.length);

			if(repeatArg == 0)
			{
				// check the current weekday if none is selected
				final int weekday = DateTime.nowCalendarMutable().get(Calendar.DAY_OF_WEEK);
				final int index = CollectionUtils.indexOf(weekday, Constants.WEEK_DAYS);
				checkedItems[index] = true;
				repeatArg |= 1 << index;
			}

			final SimpleBitSet bitset = new SimpleBitSet(repeatArg);

			final AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
			builder.setTitle(R.string._title_weekdays);
			builder.setMultiChoiceItems(Constants.LONG_WEEK_DAY_NAMES, checkedItems, new OnMultiChoiceClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which, boolean isChecked)
				{
					bitset.set(which, isChecked);

					final Button positiveButton = ((AlertDialog) dialog).getButton(DialogInterface.BUTTON_POSITIVE);
					positiveButton.setEnabled(bitset.longValue() != 0);
				}
			});
			builder.setNegativeButton(android.R.string.cancel, null);
			builder.setPositiveButton(android.R.string.ok, new OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which)
				{
					setFieldValue(Drug.REPEAT_WEEKDAYS);
					setFieldValue("repeatArg", bitset.longValue());

					updateSummary();
					notifyForwardDependencies();
				}
			});

			builder.show();
		}

		private void handle21_7RepeatMode()
		{
			if(getFieldValue() != Drug.REPEAT_21_7)
				setFieldValue("repeatOrigin", DateTime.today());

			showRepeatOriginDateDialog(Drug.REPEAT_21_7, (Date) getFieldValue("repeatOrigin"), 0);
		}

		private void showRepeatOriginDateDialog(final int repeatMode, Date repeatOrigin, final long repeatArg)
		{
			final OnDateSetListener onDateSetListener = new OnDateSetListener() {

				@Override
				public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth)
				{
					final Date newRepeatOrigin = DateTime.date(year, monthOfYear, dayOfMonth);

					setFieldValue(repeatMode);
					setFieldValue("repeatOrigin", newRepeatOrigin);
					setFieldValue("repeatArg", repeatArg);

					updateSummary();
					notifyForwardDependencies();
				}
			};



			final Calendar cal = DateTime.calendarFromDate(repeatOrigin);
			final int year = cal.get(Calendar.YEAR);
			final int month = cal.get(Calendar.MONTH);
			final int day = cal.get(Calendar.DAY_OF_MONTH);

			final DatePickerDialog datePickerDialog = new DatePickerDialog(mContext, onDateSetListener, year, month, day);
			datePickerDialog.setCancelable(false);
			datePickerDialog.setTitle(R.string._title_repetition_origin);
			datePickerDialog.show();
		}

		private void updateSummary()
		{
			//final int repeatMode = (Integer) getFieldValue("repeat");
			final int repeatMode = getFieldValue();
			final long repeatArg = (Long) getFieldValue("repeatArg");
			final Date repeatOrigin = (Date) getFieldValue("repeatOrigin");

			final String summary;

			if(repeatMode == Drug.REPEAT_EVERY_N_DAYS)
			{
				// FIXME change to next occurence
				summary = mContext.getString(
						R.string._msg_freq_every_n_days,
						repeatArg,
						DateTime.toNativeDate(repeatOrigin)
				);
			}
			else if(repeatMode == Drug.REPEAT_WEEKDAYS)
				summary = getWeekdayRepeatSummary(repeatArg);
			else if(repeatMode == Drug.REPEAT_21_7)
			{
				summary = mContext.getString(
						R.string._msg_freq_21days_on_7days_off,
						DateTime.toNativeDate(repeatOrigin)
				);
			}
			else
				summary = null;

			if(summary != null)
				mPref.setSummary(summary);
		}

		private String getWeekdayRepeatSummary(long repeatArg)
		{
			final LinkedList<String> weekdays = new LinkedList<String>();

			for(int i = 0; i != 7; ++i)
			{
				if((repeatArg & (1 << i)) != 0)
					weekdays.add(Constants.SHORT_WEEK_DAY_NAMES[i]);
			}

			if(weekdays.isEmpty())
				return mContext.getString(R.string._summary_intake_never);

			StringBuilder sb = new StringBuilder(weekdays.get(0));

			for(int i = 1; i != weekdays.size(); ++i)
				sb.append(", " + weekdays.get(i));

			return sb.toString();
		}
	}

	@SuppressWarnings("rawtypes")
	private static class CurrentSupplyPreferenceController extends AdvancedDialogPreferenceController
	{
		private Context mContext;
		private Object mValue;

		@SuppressWarnings({ "unused" })
		public CurrentSupplyPreferenceController() {
			// TODO Auto-generated constructor stub
		}

		@Override
		public void initPreference(AdvancedDialogPreference preference, Object fieldValue)
		{
			super.initPreference(preference, fieldValue);
			mContext = preference.getContext().getApplicationContext();

			((CurrentSupplyPreference) preference).setRefillSize((Integer) getFieldValue("refillSize"));
		}

		@Override
		public boolean updatePreference(AdvancedDialogPreference preference, Object newValue)
		{
			super.updatePreference(preference, newValue);
			//preference.setSummary(getSummary());
			//preference.notifyDependencyChange(false);
			return true;
		}

		@Override
		public void updateSummary(AdvancedDialogPreference preference, Object newValue)
		{
			preference.setSummary(getSummary(newValue));
			mValue = newValue;
		}

		@Override
		public void onDependencyChange(AdvancedDialogPreference preference, String depKey)
		{
			preference.setSummary(getSummary(mValue));
			if("refillSize".equals(depKey))
				((CurrentSupplyPreference) preference).setRefillSize((Integer) getFieldValue("refillSize"));
		}

		private String getSummary(Object value)
		{
			final Drug drug = ((DrugWrapper) mObject).get();
			final Fraction currentSupply = (Fraction) value;

			if(currentSupply.isZero())
			{
				if(drug.getRefillSize() == 0)
					return mContext.getString(R.string._summary_not_available);

				return "0";
			}

			if(drug.getRepeatMode() == Drug.REPEAT_AS_NEEDED || drug.hasNoDoses())
			{
				// TODO change?
				return currentSupply.toString();
			}

			final int currentSupplyDays = Math.max(Entries.getSupplyDaysLeftForDrug(drug, null), 0);
			final Date end = DateTime.add(DateTime.today(), Calendar.DAY_OF_MONTH, currentSupplyDays);
			return mContext.getString(R.string._msg_supply, currentSupply, DateTime.toNativeDate(end));
		}
	}

	@SuppressWarnings("rawtypes")
	private static class RefillSizePreferenceController extends AdvancedDialogPreferenceController
	{
		@SuppressWarnings("unused")
		public RefillSizePreferenceController() {
			// TODO Auto-generated constructor stub
		}

		@Override
		public void initPreference(AdvancedDialogPreference preference, Object fieldValue)
		{
			super.initPreference(preference, new Fraction((Integer) fieldValue));
			((FractionPreference) preference).disableFractionInputMode(true);
		}

		@Override
		public void updateSummary(AdvancedDialogPreference preference, Object newValue)
		{
			final int value = (Integer) newValue;

			if(value != 0)
				preference.setSummary(Integer.toString(value));
			else
				preference.setSummary(R.string._summary_not_available);
		}

		@Override
		public Object toFieldType(Object prefValue) {
			return ((Fraction) prefValue).intValue();
		}
	}

	public static class FormPreferenceController extends ListPreferenceWithIntController
	{
		public FormPreferenceController() {
			super(R.array.drug_forms);
		}
	}

	private static class CurrentSupplyPreference extends FractionPreference
	{
		private int mRefillSize;

		public CurrentSupplyPreference(Context context) {
			super(context);
		}

		public void setRefillSize(int refillSize) {
			mRefillSize = refillSize;
		}

		@Override
		protected Dialog onGetCustomDialog()
		{
			final DrugSupplyEditFragment.Dialog d =
					new DrugSupplyEditFragment.Dialog(getContext(), getDialogValue(), mRefillSize, this);

			d.setTitle(getDialogTitle());
			d.setIcon(getDialogIcon());

			return d;
		}
	}

	private static class NotificationsPreferenceController extends DialogPreferenceController<ListPreference, Boolean>
	{
		private static final int NOTIFY_ALL = 0;
		private static final int NOTIFY_SUPPLIES_ONLY = 1;
		private String[] mEntries;

		@SuppressWarnings("unused")
		public NotificationsPreferenceController() {
			// TODO Auto-generated constructor stub
		}

		@Override
		public void initPreference(ListPreference preference, Boolean fieldValue)
		{
			if(mEntries == null)
			{
				final Resources r = preference.getContext().getResources();
				mEntries = r.getStringArray(R.array.drug_notifications);
			}

			preference.setEntries(mEntries);
			Util.populateListPreferenceEntryValues(preference);
			preference.setValueIndex(fieldValue ? NOTIFY_SUPPLIES_ONLY : NOTIFY_ALL);
			preference.setDialogTitle(preference.getTitle());

//			preference.setOnPreferenceClickListener(new OnPreferenceClickListener() {
//
//				@Override
//				public boolean onPreferenceClick(Preference preference)
//				{
//					if(Settings.containsStringSetEntry(Settings.Keys.DISPLAYED_ONCE, entry))
//
//
//
//
//					// TODO Auto-generated method stub
//					return false;
//				}
//			});
		}

		@Override
		public void updateSummary(ListPreference preference, Boolean newValue)
		{
			preference.setSummary(mEntries[newValue ? NOTIFY_SUPPLIES_ONLY : NOTIFY_ALL]);
		}

		@Override
		public Boolean toFieldType(Object prefValue)
		{
			final int i = Integer.parseInt((String) prefValue);
			return i == NOTIFY_SUPPLIES_ONLY;
		}
	}

	private final OnPreferenceChangeListener mListener = new OnPreferenceChangeListener() {

		@Override
		public boolean onPreferenceChange(Preference preference, Object newValue)
		{
			Log.v(TAG, "onPreferenceChange: " + preference.getKey() + " => " + newValue);
			return false;
		}
	};
}