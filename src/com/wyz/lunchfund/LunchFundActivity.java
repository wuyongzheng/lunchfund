package com.wyz.lunchfund;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.widget.*;
import android.view.View;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.util.*;

public class LunchFundActivity extends Activity
{
	private PersistentState pstate;
	Set<String> checkedPeople = new HashSet<String>();

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		try {
			pstate = PersistentState.load(new InputStreamReader(openFileInput("history.txt"), "UTF-8"));
		} catch (FileNotFoundException x) {
		} catch (UnsupportedEncodingException x) {
			Log.e("LunchFundActivity", "onCreate", x);
		}
		if (pstate == null)
			pstate = new PersistentState();
		pstate.clearModified();

		setContentView(R.layout.main);
		redraw();
	}

	public void onConfigurationChanged (Configuration newConfig)
	{
		super.onConfigurationChanged(newConfig);
		redraw();
	}

	protected void onPause ()
	{
		try {
			if (pstate.isModified()) {
				pstate.save(new OutputStreamWriter(openFileOutput("history.txt", MODE_PRIVATE), "UTF-8"));
				pstate.clearModified();
			}
		} catch (Exception x) {
			Log.e("LunchFundActivity", "onPause: save", x);
		}
		super.onPause();
	}

	private void redraw ()
	{
		Button button;

		button = (Button)findViewById(R.id.lunch);
		button.setEnabled(checkedPeople.size() > 0);
		button = (Button)findViewById(R.id.transfer);
		button.setEnabled(true);
		button = (Button)findViewById(R.id.addperson);
		button.setEnabled(true);
		button = (Button)findViewById(R.id.undo);
		button.setEnabled(pstate.hasHistory());

		LinearLayout peoplelayout = (LinearLayout)findViewById(R.id.peoplelayout);
		peoplelayout.removeAllViews();
		for (PersistentState.Person person : pstate.listPeople()) {
			final String name = person.name;
			CheckBox cbox = new CheckBox(this);
			cbox.setText(name + " (" + (person.balance / 100.0) + ")");
			cbox.setChecked(checkedPeople.contains(name));
			cbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
					public void onCheckedChanged (CompoundButton buttonView, boolean isChecked)
					{
						if (isChecked)
							checkedPeople.add(name);
						else
							checkedPeople.remove(name);
						redraw();
					}
				});
			peoplelayout.addView(cbox);
		}

		((TextView)findViewById(R.id.logview)).setText(pstate.showHistory(true));
	}

	private String lunchPayer;
	private int lunchAmount;
	public void onLunch (View view)
	{
		if (checkedPeople.size() == 0)
			return;
		final String [] people = pstate.listPeopleNames();
		lunchPayer = null;
		lunchAmount = 0;
		AlertDialog.Builder alert = new AlertDialog.Builder(this);
		alert.setTitle("Lunch 1/2: Who Paied?");
		alert.setSingleChoiceItems(people, -1, new DialogInterface.OnClickListener() {
				public void onClick (DialogInterface dialog, int which) {
					lunchPayer = people[which];
					dialog.dismiss();
					onLunch2();
				}
			});
/*		alert.setPositiveButton("Next", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					if (lunchPayer != null)
						onLunch2();
				}
			});
*/		alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {}
			});
		alert.show();
	}

	public void onLunch2 ()
	{
		AlertDialog.Builder alert = new AlertDialog.Builder(this);
		alert.setTitle("Lunch 2/2: How much in total?");
		final EditText input = new EditText(this);
		input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
		alert.setView(input);
		alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					String value = input.getText().toString();
					double d = 0;
					try {
						d = Double.parseDouble(value);
					} catch (Exception x) {
						Log.e("LunchFund", "onLunch2() ", x);
					}
					if (d == 0 || Math.abs(d) * 100 >= Integer.MAX_VALUE)
						return;
					pstate.apply(new PersistentState.LunchTransaction(
							lunchPayer, (int)Math.round(d * 100), checkedPeople.toArray(new String[checkedPeople.size()])));
					redraw();
				}
			});
		alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {}
			});
		alert.show();
	}

	public void onTransfer (View view)
	{
		AlertDialog.Builder alert = new AlertDialog.Builder(this);
		alert.setTitle("Transfer Money");
		View alertView = getLayoutInflater().inflate(R.layout.transfer, null);
		String [] people = pstate.listPeopleNames();
		String [] peopleFrom = new String [people.length + 1];
		peopleFrom[0] = "From:";
		System.arraycopy(people, 0, peopleFrom, 1, people.length);
		final Spinner spinnerFrom = (Spinner)alertView.findViewById(R.id.transferFrom);
		spinnerFrom.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, peopleFrom));
		spinnerFrom.setSelection(0);
		String [] peopleTo = new String [people.length + 1];
		peopleTo[0] = "To:";
		System.arraycopy(people, 0, peopleTo, 1, people.length);
		final Spinner spinnerTo = (Spinner)alertView.findViewById(R.id.transferTo);
		spinnerTo.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, peopleTo));
		spinnerTo.setSelection(0);
		final EditText amountEdit = (EditText)alertView.findViewById(R.id.transferAmount);
		alert.setView(alertView);

		alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					String fromString = spinnerFrom.getSelectedItem().toString();
					String toString = spinnerTo.getSelectedItem().toString();
					if (fromString.equals("From:") || toString.equals("To:"))
						return;
					String amountString = amountEdit.getText().toString();
					double d = 0;
					try {
						d = Double.parseDouble(amountString);
					} catch (Exception x) {
						Log.e("LunchFund", "onTransfer() ", x);
					}
					if (d == 0 || Math.abs(d) * 100 >= Integer.MAX_VALUE)
						return;
					pstate.apply(new PersistentState.TransferTransaction(
							fromString, toString, (int)Math.round(d * 100)));
					redraw();
				}
			});
		alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {}
			});
		alert.show();
	}

	public void onAddPerson (View view)
	{
		AlertDialog.Builder alert = new AlertDialog.Builder(this);
		alert.setTitle("Add New Person");
		alert.setView(getLayoutInflater().inflate(R.layout.addperson, null));
		alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					Dialog d = (Dialog)dialog;
					String name = ((EditText)d.findViewById(R.id.newPersonName)).getText().toString().trim();
					String email = ((EditText)d.findViewById(R.id.newPersonEmail)).getText().toString().trim();
					if (name.length() == 0)
						return;
					pstate.apply(new PersistentState.AddTransaction(name, email));
					redraw();
				}
			});
		alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {}
			});
		alert.show();
	}

	public void onUndo (View view)
	{
		pstate.undo();
		redraw();
	}

	public void onEmail (View view)
	{
	}
}
