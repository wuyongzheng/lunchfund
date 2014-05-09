package com.wyz.lunchfund;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.widget.*;
import android.view.View;
import java.util.*;

public class LunchFundActivity extends Activity
{
	private PersistentState pstate = new PersistentState();
	Set<String> checkedPeople = new HashSet<String>();

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
	}

	public void onConfigurationChanged (Configuration newConfig)
	{
		super.onConfigurationChanged(newConfig);
		redraw();
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

		((TextView)findViewById(R.id.logview)).setText(pstate.printHistory());
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
				}
			});
		alert.setPositiveButton("Next", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					onLunch2();
				}
			});
		alert.show();
	}

	public void onLunch2 ()
	{
		AlertDialog.Builder alert = new AlertDialog.Builder(this);
		alert.setTitle("Transfer 2/2: How much in total?");
		final EditText input = new EditText(this);
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
		alert.show();
	}

	private String [] transferPeople;
	private int transferFrom;
	private int transferTo;
	private int transferAmount;
	public void onTransfer (View view)
	{
		transferFrom = transferTo = -1;
		transferAmount = 0;
		transferPeople = pstate.listPeopleNames();
		AlertDialog.Builder alert = new AlertDialog.Builder(this);
		alert.setTitle("Transfer 1/3: From Who?");
		alert.setSingleChoiceItems(transferPeople, -1, new DialogInterface.OnClickListener() {
				public void onClick (DialogInterface dialog, int which) {
					transferFrom = which;
				}
			});
		alert.setPositiveButton("Next", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					onTransfer2();
				}
			});
		alert.show();
	}

	public void onTransfer2 ()
	{
		AlertDialog.Builder alert = new AlertDialog.Builder(this);
		alert.setTitle("Transfer 2/3: To Who?");
		alert.setSingleChoiceItems(pstate.listPeopleNames(), -1, new DialogInterface.OnClickListener() {
				public void onClick (DialogInterface dialog, int which) {
					transferTo = which;
				}
			});
		alert.setPositiveButton("Next", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					onTransfer3();
				}
			});
		alert.show();
	}

	public void onTransfer3 ()
	{
		AlertDialog.Builder alert = new AlertDialog.Builder(this);
		alert.setTitle("Transfer 3/3: How Much?");
		final EditText input = new EditText(this);
		alert.setView(input);
		alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					String value = input.getText().toString();
					double d = 0;
					try {
						d = Double.parseDouble(value);
					} catch (Exception x) {
						Log.e("LunchFund", "onTransfer3() ", x);
					}
					if (d == 0 || Math.abs(d) * 100 >= Integer.MAX_VALUE)
						return;
					pstate.apply(new PersistentState.TransferTransaction(
							transferPeople[transferFrom], transferPeople[transferTo],
							(int)Math.round(d * 100)));
					redraw();
				}
			});
		alert.show();
	}

	public void onAddPerson (View view)
	{
		String name = "p" + new Random().nextInt(100);
		pstate.apply(new PersistentState.AddTransaction(name, name + "@gmail.com"));
		redraw();
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
