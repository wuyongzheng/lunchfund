package com.wyz.lunchfund;

import android.util.Log;
import android.util.Base64;
import java.util.zip.CRC32;
import java.util.zip.GZIPOutputStream;
import java.util.zip.GZIPInputStream;
import java.text.DateFormat;
import java.io.*;
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
			return new AddTransaction(date, arr[2], arr.length < 4 ? "" : arr[3]);
		} else if (arr[1].equals("transfer")) {
			return new TransferTransaction(date, arr[2], arr[3],
					Integer.parseInt(arr[4]),
					arr.length < 6 ? "" : arr[5]);
		} else if (arr[1].equals("lunch")) {
			String [] eaters = new String [arr.length - 5];
			System.arraycopy(arr, 5, eaters, 0, eaters.length);
			return new LunchTransaction(date, arr[2], Integer.parseInt(arr[3]), arr[4], eaters);
		} else if (arr[1].equals("chemail")) {
			return new ChangeEmailTransaction(date, arr[2], arr[3], arr[4]);
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

	private static abstract class Transaction {
		protected final long date; // Number of milliseconds since 1970 GMT. Same as Date.getTime() and System.currentTimeMillis()
		public abstract void apply (PersistentState pstate);
		public abstract void undo (PersistentState pstate);
		public abstract String save ();
		public abstract String description ();
		public abstract int effectToPerson (String name);
		public Transaction (long date) {
			this.date = date == 0 ? System.currentTimeMillis() : date;
		}
		public boolean equals (Object o) {
			return o instanceof Transaction ? ((Transaction)o).save().equals(save()) : false;
		}
	}

	private static class AddTransaction extends Transaction {
		private final String name;
		private final String email;
		public AddTransaction (long date, String name, String email)
		{
			super(date);
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
		public int effectToPerson (String name) {return 0;}
	}

	private static class TransferTransaction extends Transaction {
		private final String from;
		private final String to;
		private final int amount;
		private final String remarks;
		public TransferTransaction (long date, String from, String to, int amount, String remarks)
		{
			super(date);
			if (from.equals(to) || amount <= 0)
				throw new RuntimeException("Invalid parameter for TransferTransaction");
			this.from = from;
			this.to = to;
			this.amount = amount;
			this.remarks = remarks.length() == 0 ? "nothing" : remarks;
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
		public String save () {return date + "\ttransfer\t" + from + "\t" + to + "\t" + amount + "\t" + remarks;}
		public String description () {
			return from + " gave $" + (amount/100.0) +
				" to " + to + " on " +
				DateFormat.getDateInstance().format(new Date(date)) +
				(remarks.equals("nothing") ? "" : " (" + remarks + ")");
		}
		public int effectToPerson (String name)
		{
			if (name.equals(from))
				return amount;
			if (name.equals(to))
				return -amount;
			return 0;
		}
	}

	private static class LunchTransaction extends Transaction {
		private final String payer;
		private final int amount;
		private final String remarks;
		private final String [] eaters;
		private final int split;
		public LunchTransaction (long date, String payer, int amount, String remarks, String [] eaters)
		{
			super(date);
			if (eaters.length == 0 || amount <= 0)
				throw new RuntimeException("Invalid parameter for LunchTransaction");
			this.payer = payer;
			this.amount = amount;
			this.remarks = remarks.length() == 0 ? "nothing" : remarks;
			this.eaters = eaters;
			split = roundDiv(amount, eaters.length);
		}
		public void apply (PersistentState pstate)
		{
			for (String eater : eaters)
				pstate.people.get(eater).balance -= split;
			pstate.people.get(payer).balance += split * eaters.length;
		}
		public void undo (PersistentState pstate)
		{
			for (String eater : eaters)
				pstate.people.get(eater).balance += split;
			pstate.people.get(payer).balance -= split * eaters.length;
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
			StringBuilder sb = new StringBuilder().append(payer).append(" paid $").append(amount/100.0).append(" for ");
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
		public int effectToPerson (String name)
		{
			int balance = 0;
			for (String p : eaters) {
				if (name.equals(p)) {
					balance -= split;
					break;
				}
			}
			if (name.equals(payer))
				balance += split * eaters.length;
			return balance;
		}
		public static int roundDiv (int dividend, int divisor)
		{
			return (dividend + (divisor / 2)) / divisor;
		}
	}

	private static class ChangeEmailTransaction extends Transaction {
		private final String name;
		private final String oldEmail;
		private final String newEmail;
		public ChangeEmailTransaction (long date, String name, String oldEmail, String newEmail)
		{
			super(date);
			this.name = name;
			this.oldEmail = oldEmail;
			this.newEmail = newEmail;
		}
		public void apply (PersistentState pstate)
		{
			Person p = pstate.people.get(name);
			if (p == null)
				throw new IllegalArgumentException(name + " does not exist");
			if (!p.email.equals(oldEmail))
				throw new IllegalArgumentException("expecting \"" + oldEmail +
						"\", but got " + name + ":\"" + p.email + "\"");
			p.email = newEmail;
		}
		public void undo (PersistentState pstate)
		{
			Person p = pstate.people.get(name);
			if (p == null)
				throw new IllegalArgumentException(name + " does not exist");
			if (!p.email.equals(newEmail))
				throw new IllegalArgumentException("expecting \"" + newEmail +
						"\", but got " + name + ":\"" + p.email + "\"");
			p.email = oldEmail;
		}
		public String save () {return date + "\tchemail\t" + name + "\t" + oldEmail + "\t" + newEmail;}
		public String description ()
		{
			return name + "'s new email: " + newEmail + " " + DateFormat.getDateInstance().format(new Date(date));
		}
		public int effectToPerson (String name) { return 0; }
	}

	public boolean hasHistory ()
	{
		return history.size() > 0;
	}

	public int historySize ()
	{
		return history.size();
	}

	public boolean hasUndoHistory ()
	{
		return undoHistory.size() > 0;
	}

	/* sortBy=1: by name
	 * sortBy=2: by balance ascending
	 * sortBy=3: frequent eaters goes first */
	public Iterable<Person> listPeople (int sortBy)
	{
		if (sortBy == 1) {
			return people.values();
		} else if (sortBy == 2) {
			ArrayList<Person> list = new ArrayList<Person>(people.values());
			Collections.sort(list, new Comparator<Person>() {
					public int compare (Person p1, Person p2) {
						return p1.balance - p2.balance;
					}
			});
			return list;
		} else if (sortBy == 3) {
			final HashMap<String, Double> freqs = new HashMap<String, Double>();
			for (String p : people.keySet()) freqs.put(p, 0.0);
			double score = 1.0;
			for (int i = history.size() - 1; i >= 0; i --) {
				if (history.get(i) instanceof LunchTransaction) {
					LunchTransaction t = (LunchTransaction)history.get(i);
					for (String p : t.eaters)
						freqs.put(p, freqs.get(p) + score);
					score *= 0.9;
				}
			}
			ArrayList<Person> list = new ArrayList<Person>(people.values());
			Collections.sort(list, new Comparator<Person>() {
					public int compare (Person p1, Person p2) {
						return -freqs.get(p1.name).compareTo(freqs.get(p2.name));
					}
			});
			return list;
		}
		assert false;
		return null;
	}

	public String[] listPeopleNames ()
	{
		return people.keySet().toArray(new String[people.size()]);
	}

	/* global history */
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

	/* personal history */
	public String showHistory (boolean reverse, String name)
	{
		StringBuilder sb = new StringBuilder();
		int balance = 0;
		for (Transaction trans : history) {
			int delta = trans.effectToPerson(name);
			if (delta == 0)
				continue;
			balance += delta;
			if (reverse) {
				sb.insert(0, trans.description() + "\n");
				sb.insert(0, "Balance: " + balance / 100.0 + "\n");
			} else {
				sb.append(trans.description() + "\n");
				sb.append("Balance: " + balance / 100.0 + "\n");
			}
		}
		return sb.toString();
	}

	/* selected group history */
	public String showHistory (boolean reverse, Set<String> selected)
	{
		StringBuilder sb = new StringBuilder();
		for (Transaction trans : history) {
			boolean hit = false;
			for (String person : selected)
				if (trans.effectToPerson(person) != 0) {
					hit = true;
					break;
				}
			if (hit == false)
				continue;
			if (reverse) {
				sb.insert(0, trans.description() + "\n");
			} else {
				sb.append(trans.description() + "\n");
			}
		}
		StringBuilder balance = new StringBuilder("Balance:\n");
		for (String person : selected) {
			Person p = people.get(person);
			balance.append(p.name).append(": ").append(p.balance / 100.0).append("\n");
		}
		if (reverse) {
			sb.insert(0, balance);
		} else {
			sb.append(balance);
		}
		return sb.toString();
	}

	public Person getPerson (String name)
	{
		return people.get(name);
	}

	private void apply (Transaction trans)
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

	public void performLunch (String payer, int amount, String remarks, String [] eaters)
	{
		apply(new LunchTransaction(0, payer, amount, remarks, eaters));
	}

	public void performTransfer (String from, String to, int amount, String remarks)
	{
		apply(new TransferTransaction(0, from, to, amount, remarks));
	}

	// how to handle conflict? not crach
	public void performAddPerson (String name, String email)
	{
		apply(new AddTransaction(0, name, email));
	}

	public void performChangeEmail (String name, String newEmail)
	{
		if (!people.containsKey(name))
			throw new RuntimeException("PersistentState.name person not found");
		ChangeEmailTransaction trans = new ChangeEmailTransaction(0, name, people.get(name).email, newEmail);
		apply(trans);
	}

	/* export and merge format
	   header: 'L0', number of unexported trans, CRC32 of whole log, exported trans.
	   header: 'Lz', same as above, but gzipped
	 */
	public String export (int numExp)
	{
		if (numExp <= 0 || numExp > history.size())
			throw new RuntimeException("numExp=" + numExp + ", history=" + history.size());
		int numUnexp = history.size() - numExp;

		try {
			StringWriter writer = new StringWriter();
			save(writer);
			byte [] trans = writer.toString().getBytes("UTF-8");
			CRC32 crcobj = new CRC32();
			crcobj.update(trans);
			long crc = crcobj.getValue();

			int ptr = 0, count = 0;
			while (count < numUnexp) {
				if (trans[ptr] == '\n') {
					count ++;
				}
				ptr ++;
			}
			// now, ptr points to the first byte to export.
			assert ptr < trans.length;
			byte [] data = new byte [2 + 2 + 4 + (trans.length - ptr)];
			data[0] = 'L';
			data[1] = '0';
			assert numUnexp < 65536;
			data[2] = (byte)(numUnexp >> 8);
			data[3] = (byte)(numUnexp);
			data[4] = (byte)(crc >> 24);
			data[5] = (byte)(crc >> 16);
			data[6] = (byte)(crc >> 8);
			data[7] = (byte)(crc);
			System.arraycopy(trans, ptr, data, 8, trans.length - ptr);

			// Should we compress?
			try {
				ByteArrayOutputStream zdata = new ByteArrayOutputStream();
				zdata.write('L');
				zdata.write('z');
				GZIPOutputStream zos = new GZIPOutputStream(zdata);
				zos.write(data, 2, data.length - 2);
				zos.close();
				if (zdata.size() < data.length)
					data = zdata.toByteArray();
			} catch (IOException x) {
				throw new RuntimeException(x);
			}
			return Base64.encodeToString(data, Base64.DEFAULT);
		} catch (Exception x) {
			throw new RuntimeException(x);
		}
	}

	public static class MergeResult {
		public final PersistentState newPS;
		public final String message;
		public MergeResult (PersistentState n, String m) {newPS = n; message = m;}
	}
	public MergeResult merge (String foreign)
	{
		byte [] bytes = null;
		try {
			bytes = Base64.decode(foreign, Base64.DEFAULT);
		} catch (Exception x) {
			return new MergeResult(null, "Invalid Data Format " + x);
		}
		if (bytes[0] == 'L' && bytes[1] == '0')
			;
		else if (bytes[0] == 'L' && bytes[1] == 'z') {
			try {
				ByteArrayInputStream zdata = new ByteArrayInputStream(bytes, 2, bytes.length - 2);
				GZIPInputStream zis = new GZIPInputStream(zdata);
				ByteArrayOutputStream pdata = new ByteArrayOutputStream();
				pdata.write('L');
				pdata.write('0');
				while (true) {
					int b = zis.read();
					if (b == -1) break;
					pdata.write(b);
				}
				bytes = pdata.toByteArray();
			} catch (IOException x) {
				throw new RuntimeException(x);
			}
		} else {
			return new MergeResult(null, "Invalid Data Format");
		}
		int numUnexp = ((bytes[2] & 0xff) << 8) | (bytes[3] & 0xff);
		if (numUnexp > history.size())
			return new MergeResult(null, "Need to export more transactions to merge");

		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < numUnexp; i ++)
			sb.append(history.get(i).save()).append("\n");
		try { sb.append(new String(bytes, 8, bytes.length - 8, "UTF-8")); } catch (UnsupportedEncodingException x) {}

		int crcExp = (bytes[4] << 24) | ((bytes[5] & 0xff) << 16) |
			((bytes[6] & 0xff) << 8) | (bytes[7] & 0xff);
		CRC32 crc = new CRC32();
		try { crc.update(sb.toString().getBytes("UTF-8")); } catch (UnsupportedEncodingException x) {}
		if ((int)crc.getValue() != crcExp) {
			Log.e("PersistentState", sb.toString());
			return new MergeResult(null, "Conflict or Corrupt data. Try again with more transactions");
		}

		PersistentState ps2;
		try {
			ps2 = load(sb.toString());
			sb = null;
		} catch (Exception x) {
			return new MergeResult(null, "Invalid Remote Log: " + x);
		}

		// Ensure strict increasing date
		for (int i = 0; i < history.size() - 1; i ++)
			if (history.get(i).date >= history.get(i+1).date)
				return new MergeResult(null, "this date goes backwards");
		for (int i = 0; i < ps2.history.size() - 1; i ++)
			if (ps2.history.get(i).date >= ps2.history.get(i+1).date)
				return new MergeResult(null, "remote date goes backwards");
		// Ensure no duplicate date
		TreeMap<Long, Transaction> map = new TreeMap<Long, Transaction>();
		ArrayList<Transaction> tmplist = new ArrayList<Transaction>(history);
		tmplist.addAll(ps2.history);
		for (Transaction t : tmplist) {
			Transaction exist = map.put(t.date, t);
			if (exist != null && !exist.equals(t))
				return new MergeResult(null, "date conflict");
		}
		tmplist = null;
		// Any new trans?
		if (map.size() == history.size())
			return new MergeResult(null, "Nothing new");

		PersistentState ps3;
		try {
			sb = new StringBuilder();
			for (Transaction t : map.values())
				sb.append(t.save()).append("\n");
			ps3 = load(sb.toString());
			sb = null;
		} catch (Exception x) {
			return new MergeResult(null, "Invalid Merged Log: " + x);
		}

		// all done. generate merge message
		sb = new StringBuilder();
		sb.append("New Transactions:\n");
		map.clear();
		for (Transaction t : history) map.put(t.date, t);
		for (Transaction t : ps3.history)
			if (!map.containsKey(t.date))
				sb.append(t.description()).append("\n");
		assert sb.length() > 0;

		return new MergeResult(ps3, sb.toString());
	}
}
