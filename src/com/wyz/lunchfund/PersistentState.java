package com.wyz.lunchfund;

import android.util.Log;
import java.io.*;
import java.text.DateFormat;
import java.util.*;

public class PersistentState
{
	private SortedMap<String, Person> people = new TreeMap<String, Person>();
	private Stack<Transaction> history = new Stack<Transaction>();
	private Stack<Transaction> undoHistory = new Stack<Transaction>();
	private boolean modified = false;

	public static PersistentState load (String text)
	{
		return load(new StringReader(text));
	}

	public static PersistentState load (Reader in)
	{
		try {
			return _load(in);
		} catch (Exception x) {
			Log.e("PersistentState", "load()", x);
			return null;
		}
	}

	public static PersistentState _load (Reader in) throws Exception
	{
		PersistentState pstate = new PersistentState();
		BufferedReader reader = new BufferedReader(in);
		while (true) {
			String line = reader.readLine();
			if (line == null)
				break;
			line = line.trim();
			if (line.length() == 0)
				continue;
			Transaction trans = loadTransaction(line);
			pstate.apply(trans);
		}
		return pstate;
	}

	public void save (Writer out) throws IOException
	{
		PrintWriter writer = new PrintWriter(out);
		for (Transaction trans : history) {
			writer.println(trans.save());
		}
		writer.close();
	}

	public boolean isModified ()
	{
		return modified;
	}

	public void clearModified ()
	{
		modified = false;
	}

	private static Transaction loadTransaction (String line)
	{
		String [] arr = line.split("\t");
		long date = Long.parseLong(arr[0]);
		if (arr[1].equals("add")) {
			return new AddTransaction(date, arr[2], arr[3]);
		} else if (arr[1].equals("delete")) {
			return new DeleteTransaction(date, arr[2], arr[3]);
		} else if (arr[1].equals("transfer")) {
			return new TransferTransaction(date, arr[2], arr[3], Integer.parseInt(arr[4]));
		} else if (arr[1].equals("lunch")) {
			String [] eaters = new String [arr.length - 5];
			System.arraycopy(arr, 5, eaters, 0, eaters.length);
			return new LunchTransaction(date, arr[2], Integer.parseInt(arr[3]), arr[4], eaters);
		} else {
			throw new RuntimeException("unknown transaction " + arr[1]);
		}
	}

	public static class Person {
		public String name;
		public String email;
		public int balance;
		public Person (String name, String email, int balance) {
			this.name = name;
			this.email = email;
			this.balance = balance;
		}
	}

	public static abstract class Transaction {
		protected long date; // Number of milliseconds since 1970 GMT. Same as Date.getTime() and System.currentTimeMillis()
		public abstract void apply (PersistentState pstate);
		public abstract void undo (PersistentState pstate);
		public abstract String save ();
		public abstract String description ();
	}

	public static class AddTransaction extends Transaction {
		private String name;
		private String email;
		public AddTransaction (long date, String name, String email)
		{
			this.date = date == 0 ? System.currentTimeMillis() : date;
			this.name = name;
			this.email = email;
		}
		public void apply (PersistentState pstate)
		{
			if (pstate.people.containsKey(name))
				throw new RuntimeException("AddTransaction.apply(): exist user " + name);
			pstate.people.put(name, new Person(name, email, 0));
		}
		public void undo (PersistentState pstate)
		{
			if (!pstate.people.containsKey(name))
				throw new RuntimeException("AddTransaction.undo(): no user " + name);
			pstate.people.remove(name);
		}
		public String save () {return date + "\tadd\t" + name + "\t" + email;}
		public String description () {return "add " + name + " <" + email + ">";}
	}

	public static class DeleteTransaction extends Transaction {
		private String name;
		private String email;
		public DeleteTransaction (long date, String name, String email)
		{
			this.date = date == 0 ? System.currentTimeMillis() : date;
			this.name = name;
			this.email = email;
		}
		public void apply (PersistentState pstate)
		{
			if (!pstate.people.containsKey(name))
				throw new RuntimeException("DeleteTransaction.apply(): no user " + name);
			if (pstate.people.get(name).balance != 0)
				throw new RuntimeException("DeleteTransaction.apply(): balance not 0 for " + name);
			pstate.people.remove(name);
		}
		public void undo (PersistentState pstate)
		{
			if (pstate.people.containsKey(name))
				throw new RuntimeException("DeleteTransaction.undo(): exist user " + name);
			pstate.people.put(name, new Person(name, email, 0));
		}
		public String save () {return date + "\tdelete\t" + name + "\t" + email;}
		public String description () {return "delete " + name + " <" + email + ">";}
	}

	public static class TransferTransaction extends Transaction {
		private String from;
		private String to;
		private int amount;
		public TransferTransaction (long date, String from, String to, int amount)
		{
			this.date = date == 0 ? System.currentTimeMillis() : date;
			if (from.equals(to) || amount <= 0)
				throw new RuntimeException("Invalid parameter for TransferTransaction");
			this.from = from;
			this.to = to;
			this.amount = amount;
		}
		public void apply (PersistentState pstate)
		{
			pstate.people.get(from).balance += amount;
			pstate.people.get(to).balance -= amount;
		}
		public void undo (PersistentState pstate)
		{
			pstate.people.get(from).balance -= amount;
			pstate.people.get(to).balance += amount;
		}
		public String save () {return date + "\ttransfer\t" + from + "\t" + to + "\t" + amount;}
		public String description () {return from + " gave $" + (amount/100.0) + " to " + to + " on " + DateFormat.getDateInstance().format(new Date(date));}
	}

	public static class LunchTransaction extends Transaction {
		private String payer;
		private int amount;
		private String remarks;
		private String [] eaters;
		public LunchTransaction (long date, String payer, int amount, String remarks, String [] eaters)
		{
			this.date = date == 0 ? System.currentTimeMillis() : date;
			if (eaters.length == 0 || amount <= 0)
				throw new RuntimeException("Invalid parameter for LunchTransaction");
			this.payer = payer;
			this.amount = amount;
			this.remarks = remarks.length() == 0 ? "nothing" : remarks;
			this.eaters = eaters;
		}
		public void apply (PersistentState pstate)
		{
			for (String eater : eaters)
				pstate.people.get(eater).balance -= amount / eaters.length; //TODO round
			pstate.people.get(payer).balance += amount;
		}
		public void undo (PersistentState pstate)
		{
			for (String eater : eaters)
				pstate.people.get(eater).balance += amount / eaters.length;
			pstate.people.get(payer).balance -= amount;
		}
		public String save () {
			StringBuilder sb = new StringBuilder().
				append(date).append("\tlunch\t").append(payer).
				append("\t" + amount).append("\t" + remarks);
			for (String eater : eaters)
				sb.append("\t").append(eater);
			return sb.toString();
		}
		public String description () {
			StringBuilder sb = new StringBuilder().append(payer).append(" payed $").append(amount/100.0).append(" for ");
			for (int i = 0; i < eaters.length - 2; i ++)
				sb.append(eaters[i]).append(", ");
			if (eaters.length >= 2)
				sb.append(eaters[eaters.length - 2]).append(" and ");
			sb.append(eaters[eaters.length - 1]);
			sb.append(" on " + DateFormat.getDateInstance().format(new Date(date)));
			if (!remarks.equals("nothing"))
				sb.append(" (" + remarks + ")");
			return sb.toString();
		}
	}

	public boolean hasHistory ()
	{
		return history.size() > 0;
	}

	public boolean hasUndoHistory ()
	{
		return undoHistory.size() > 0;
	}

	public Iterable<Person> listPeople ()
	{
		return people.values();
	}

	public String[] listPeopleNames ()
	{
		return people.keySet().toArray(new String[people.size()]);
	}

	public String showHistory (boolean reverse)
	{
		StringBuilder sb = new StringBuilder();
		if (reverse)
			for (int i = history.size() - 1; i >= 0; i --)
				sb.append(history.get(i).description() + "\n");
		else
			for (Transaction trans : history)
				sb.append(trans.description() + "\n");
		return sb.toString();
	}

	public Person getPerson (String name)
	{
		return people.get(name);
	}

	public void apply (Transaction trans)
	{
		undoHistory.clear();
		trans.apply(this);
		history.push(trans);
		modified = true;
	}

	public void undo ()
	{
		if (history.size() == 0)
			throw new RuntimeException("PersistentState.undo while history is empty");
		Transaction trans = history.pop();
		trans.undo(this);
		undoHistory.push(trans);
		modified = true;
	}

	public void redo ()
	{
		if (undoHistory.size() == 0)
			throw new RuntimeException("PersistentState.redo while undoHistory is empty");
		Transaction trans = undoHistory.pop();
		trans.apply(this);
		history.push(trans);
		modified = true;
	}
}
