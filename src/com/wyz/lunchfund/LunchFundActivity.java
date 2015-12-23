package com.wyz.lunchfund;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.widget.*;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Window;
import android.view.View;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
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

		if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
			setContentView(R.layout.landscape);
		else
			setContentView(R.layout.main);
		redraw();
	}

	@Override
	public void onConfigurationChanged (Configuration newConfig)
	{
		super.onConfigurationChanged(newConfig);
		if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE)
			setContentView(R.layout.landscape);
		else
			setContentView(R.layout.main);
		redraw();
	}

	@Override
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

	@Override
	public boolean onCreateOptionsMenu (Menu menu)
	{
		super.onCreateOptionsMenu(menu);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main_menu, menu);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu (Menu menu)
	{
		if (!super.onPrepareOptionsMenu(menu))
			return false;
		((TextView)(menu.findItem(R.id.numsel).getActionView())).setText(checkedPeople.size() + "");
		menu.findItem(R.id.lunch).setEnabled(checkedPeople.size() > 0);
		menu.findItem(R.id.transfer).setEnabled(true);
		menu.findItem(R.id.email).setEnabled(checkedPeople.size() > 0);
		menu.findItem(R.id.addperson).setEnabled(true);

		menu.findItem(R.id.undo).setEnabled(pstate.hasHistory());
		menu.findItem(R.id.redo).setEnabled(pstate.hasUndoHistory());
		menu.findItem(R.id.exportToClipboard).setEnabled(true);
		menu.findItem(R.id.mergeFromClipboard).setEnabled(true);
		menu.findItem(R.id.changeEmail).setEnabled(checkedPeople.size() == 1);
		return true;
	}

	private void redraw ()
	{
		invalidateOptionsMenu();

		LinearLayout peoplelayout = (LinearLayout)findViewById(R.id.peoplelayout);
		peoplelayout.removeAllViews();
		for (PersistentState.Person person : pstate.listPeople(2)) {
			final String name = person.name;
			CheckBox cbox = new CheckBox(this);
			cbox.setText(name + (person.balance < 0 ? ": -$" : ": $") + (Math.abs(person.balance) / 100.0));
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

	public void onLunch (MenuItem mitem)
	{
		if (checkedPeople.size() == 0)
			return;

		AlertDialog.Builder alert = new AlertDialog.Builder(this);
		alert.setTitle("Lunch");
		View alertView = getLayoutInflater().inflate(R.layout.lunch, null);
		String [] people = pstate.listPeopleNames();
		String [] peoplePayer = new String [people.length + 1];
		peoplePayer[0] = "Who Paied?";
		System.arraycopy(people, 0, peoplePayer, 1, people.length);
		final Spinner spinnerPayer = (Spinner)alertView.findViewById(R.id.lunchPayer);
		spinnerPayer.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, peoplePayer));
		spinnerPayer.setSelection(0);
		alert.setView(alertView);

		alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					String payer = spinnerPayer.getSelectedItem().toString();
					if (payer.equals("Who Paied?")) {
						Toast.makeText(getApplicationContext(), "Error: Person not selected", Toast.LENGTH_LONG).show();
						return;
					}
					Dialog thedialog = (Dialog)dialog;
					String remarks = ((EditText)thedialog.findViewById(R.id.lunchRemarks)).getText().toString().trim();
					String amountString = ((EditText)thedialog.findViewById(R.id.lunchAmount)).getText().toString().trim();
					double d = 0;
					try {
						d = Double.parseDouble(amountString);
					} catch (Exception x) {
						Log.e("LunchFund", "onTransfer() ", x);
					}
					if (Math.abs(d) * 100 >= Integer.MAX_VALUE || (int)Math.round(d * 100) <= 0) {
						Toast.makeText(getApplicationContext(), "Error: Amount must be positive", Toast.LENGTH_LONG).show();
						return;
					}
					pstate.apply(new PersistentState.LunchTransaction(
							0, payer, (int)Math.round(d * 100), remarks,
							checkedPeople.toArray(new String[checkedPeople.size()])));
					redraw();
				}
			});
		alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {}
			});
		alert.show();
	}

	public void onTransfer (MenuItem mitem)
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
		final EditText remarksEdit = (EditText)alertView.findViewById(R.id.transferRemarks);
		alert.setView(alertView);

		alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					String fromString = spinnerFrom.getSelectedItem().toString();
					String toString = spinnerTo.getSelectedItem().toString();
					if (fromString.equals("From:") || toString.equals("To:")) {
						Toast.makeText(getApplicationContext(), "Error: Person not selected", Toast.LENGTH_LONG).show();
						return;
					}
					String amountString = amountEdit.getText().toString();
					double d = 0;
					try {
						d = Double.parseDouble(amountString);
					} catch (Exception x) {
						Log.e("LunchFund", "onTransfer() ", x);
					}
					if (Math.abs(d) * 100 >= Integer.MAX_VALUE || (int)Math.round(d * 100) <= 0) {
						Toast.makeText(getApplicationContext(), "Error: Amount must be positive", Toast.LENGTH_LONG).show();
						return;
					}
					pstate.apply(new PersistentState.TransferTransaction(
							0, fromString, toString, (int)Math.round(d * 100),
							remarksEdit.getText().toString()));
					redraw();
				}
			});
		alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {}
			});
		alert.show();
	}

	public void onAddPerson (MenuItem mitem)
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
					pstate.apply(new PersistentState.AddTransaction(0, name, email));
					redraw();
				}
			});
		alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {}
			});
		alert.show();
	}

	public void onUndo (MenuItem item)
	{
		pstate.undo();
		checkedPeople.clear();
		redraw();
	}

	public void onRedo (MenuItem item)
	{
		pstate.redo();
		checkedPeople.clear();
		redraw();
	}

	public void onExport (MenuItem item)
	{
		if (!pstate.hasHistory())
			Toast.makeText(this, "nothing to export", Toast.LENGTH_SHORT).show();
		final ArrayList<String> optionStr = new ArrayList<String>();
		final ArrayList<Integer> optionInt = new ArrayList<Integer>();
		for (int i = 1; i <= pstate.historySize(); i *= 2) {
			optionStr.add("" + i);
			optionInt.add(i);
		}
		if (optionInt.get(optionInt.size()-1) == pstate.historySize()) {
			optionStr.set(optionStr.size()-1, optionStr.get(optionStr.size()-1) + " (All)");
		} else {
			optionStr.add(pstate.historySize() + " (All)");
			optionInt.add(pstate.historySize());
		}

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("How many to export?");
		final Spinner spinner = new Spinner(builder.getContext(), Spinner.MODE_DIALOG);
		spinner.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, optionStr));
		spinner.setSelection(0);
		builder.setView(spinner);

		builder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					int pos = spinner.getSelectedItemPosition();
					if (pos == spinner.INVALID_POSITION)
						Toast.makeText(getApplicationContext(), "Export Canceled", Toast.LENGTH_SHORT).show();
					String data = pstate.export(optionInt.get(pos));
					ClipboardManager clipMan = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
					clipMan.setPrimaryClip(ClipData.newPlainText("text", data));
					Toast.makeText(getApplicationContext(), "History exported to clipboard.", Toast.LENGTH_SHORT).show();
				}
			});
		builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {}
			});
		builder.show();
	}

	public void onMerge (MenuItem item)
	{
		ClipboardManager clipMan = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
		ClipData.Item citem = clipMan.getPrimaryClip().getItemAt(0);
		String data = citem.getText().toString();
		final PersistentState.MergeResult result = pstate.merge(data);
		if (result.newPS == null) {
			Toast.makeText(this, result.message, Toast.LENGTH_LONG).show();
			return;
		}

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("Confirm Merge");
		builder.setMessage(result.message);
		builder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					pstate = result.newPS;
					try {
						pstate.save(new OutputStreamWriter(openFileOutput("history.txt", MODE_PRIVATE), "UTF-8"));
					} catch (Exception x) { throw new RuntimeException(x); }
					pstate.clearModified();
					checkedPeople.clear();
					Toast.makeText(getApplicationContext(), "Log merged", Toast.LENGTH_SHORT).show();
					redraw();
				}
			});
		builder.setNegativeButton("Cancel", null);
		//builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
		//		public void onClick(DialogInterface dialog, int id) {}
		//	});
		builder.show();
	}

	public void onEmailLog (MenuItem mitem)
	{
		if (checkedPeople.size() == 0)
			return;

		Intent i = new Intent(Intent.ACTION_SEND);
		i.setType("message/rfc822");
		if (checkedPeople.size() == 1) {
			PersistentState.Person person = pstate.getPerson(checkedPeople.iterator().next());
			if (person == null)
				return;
			i.putExtra(Intent.EXTRA_EMAIL, new String[]{person.email});
			i.putExtra(Intent.EXTRA_SUBJECT, "Lunch Fund Log for " + person.name);
			i.putExtra(Intent.EXTRA_TEXT, pstate.showHistory(true, person.name));
		} else {
			String[] rec = new String[checkedPeople.size()];
			int j = 0;
			for (String p : checkedPeople)
				rec[j++] = pstate.getPerson(p).email;
			i.putExtra(Intent.EXTRA_EMAIL, rec);
			i.putExtra(Intent.EXTRA_SUBJECT, "Lunch Fund Log");
			i.putExtra(Intent.EXTRA_TEXT, pstate.showHistory(true, checkedPeople));
		}

		try {
			startActivity(Intent.createChooser(i, "Send mail..."));
		} catch (ActivityNotFoundException x) {
			Toast.makeText(getApplicationContext(), "There are no email clients installed", Toast.LENGTH_SHORT).show();
		}
	}

	public void onChangeEmail (MenuItem item)
	{
		if (checkedPeople.size() != 1)
			return;
		final PersistentState.Person person = pstate.getPerson(checkedPeople.iterator().next());
		if (person == null)
			return;

		AlertDialog.Builder alert = new AlertDialog.Builder(this);
		alert.setTitle("Change Email for " + person.name);
		final EditText input = new EditText(this);
		input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
		input.setText(person.email);
		alert.setView(input);
		alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					pstate.changeEmail(person.name, input.getText().toString().trim());
					redraw();
				}
			});
		alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {}
			});
		alert.show();
	}
}
