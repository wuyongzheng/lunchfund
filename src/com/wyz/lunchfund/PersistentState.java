package com.wyz.lunchfund;

import android.util.Log;
import java.util.*;
import java.io.*;

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
		if (arr[0].equals("add")) {
			return new AddTransaction(arr[1], arr[2]);
		} else if (arr[0].equals("delete")) {
			return new DeleteTransaction(arr[1], arr[2]);
		} else if (arr[0].equals("transfer")) {
			return new TransferTransaction(arr[1], arr[2], Integer.parseInt(arr[3]));
		} else if (arr[0].equals("lunch")) {
			String [] eaters = new String [arr.length - 3];
			System.arraycopy(arr, 3, eaters, 0, eaters.length);
			return new LunchTransaction(arr[1], Integer.parseInt(arr[2]), eaters);
		} else {
			throw new RuntimeException("unknown transaction " + arr[0]);
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
		public abstract void apply (PersistentState pstate);
		public abstract void undo (PersistentState pstate);
		public abstract String save ();
		public abstract String description ();
	}

	public static class AddTransaction extends Transaction {
		private String name;
		private String email;
		public AddTransaction (String name, String email)
		{
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
		public String save () {return "add\t" + name + "\t" + email;}
		public String description () {return "add " + name + " <" + email + ">";}
	}

	public static class DeleteTransaction extends Transaction {
		private String name;
		private String email;
		public DeleteTransaction (String name, String email)
		{
			this.name = name;
			this.email = email;
		}
		public void apply (PersistentState pstate)
		{
			if (!pstate.people.containsKey(name))
				throw new RuntimeException("DeleteTransaction.apply(): no user " + name);
			pstate.people.remove(name);
		}
		public void undo (PersistentState pstate)
		{
			if (pstate.people.containsKey(name))
				throw new RuntimeException("DeleteTransaction.undo(): exist user " + name);
			pstate.people.put(name, new Person(name, email, 0));
		}
		public String save () {return "delete\t" + name + "\t" + email;}
		public String description () {return "delete " + name + " <" + email + ">";}
	}

	public static class TransferTransaction extends Transaction {
		private String from;
		private String to;
		private int amount;
		public TransferTransaction (String from, String to, int amount)
		{
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
		public String save () {return "transfer\t" + from + "\t" + to + "\t" + amount;}
		public String description () {return from + " gave $" + (amount/100.0) + " to " + to;}
	}

	public static class LunchTransaction extends Transaction {
		private String payer;
		private int amount;
		private String [] eaters;
		public LunchTransaction (String payer, int amount, String [] eaters)
		{
			if (eaters.length == 0 || amount <= 0)
				throw new RuntimeException("Invalid parameter for LunchTransaction");
			this.payer = payer;
			this.amount = amount;
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
			StringBuilder sb = new StringBuilder().append("lunch\t").append(payer).append("\t" + amount);
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
			return sb.toString();
		}
	}

	public boolean hasHistory ()
	{
		return history.size() > 0;
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
