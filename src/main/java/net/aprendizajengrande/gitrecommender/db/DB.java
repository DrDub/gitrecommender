package net.aprendizajengrande.gitrecommender.db;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

// files, user, commits counts
// persists to disk in text files
// good to move to a relational DB
// this class is not thread-safe
public class DB {

	// author-id -> file-id -> counts
	private Map<Integer, Map<Integer, Counter>> counts = new HashMap<>();

	// author-id -> counts
	private Map<Integer, Counter> commitCounts = new HashMap<>();

	// id-to-string mappings
	private Map<String, Integer> authorIds = new HashMap<>();
	private Map<String, Integer> fileIds = new HashMap<>();
	private List<String> authors = new ArrayList<>();
	private List<String> files = new ArrayList<>();

	private String lastCommit = "NOSUCHCOMMIT";

	private File dataDir;

	public DB(File dataDir) throws IOException {
		this.dataDir = dataDir;
		load();
	}

	private void load() throws IOException {
		File countsFile = new File(dataDir, "counts.tsv");
		if (!countsFile.exists())
			return; // empty DB

		BufferedReader br;
		String line;

		br = new BufferedReader(new FileReader(new File(dataDir, "counts.tsv")));
		line = br.readLine();
		Map<Integer, Counter> current = null;
		while (line != null) {
			String[] parts = line.split("\t");
			if (parts.length == 3)
				current.put(Integer.parseInt(parts[1]),
						new Counter(Integer.parseInt(parts[2])));
			else {
				int currentId = Integer.parseInt(parts[0]);
				current = new HashMap<>();
				counts.put(currentId, current);
			}
			line = br.readLine();
		}
		br.close();

		br = new BufferedReader(new FileReader(new File(dataDir,
				"commit-counts.tsv")));
		line = br.readLine();
		while (line != null) {
			String[] parts = line.split("\t");
			commitCounts.put(Integer.parseInt(parts[0]),
					new Counter(Integer.parseInt(parts[1])));
			line = br.readLine();
		}
		br.close();

		br = new BufferedReader(new FileReader(new File(dataDir, "files.txt")));
		line = br.readLine();
		while (line != null) {
			int id = files.size();
			files.add(line);
			fileIds.put(line, id);
			line = br.readLine();
		}
		br.close();

		br = new BufferedReader(
				new FileReader(new File(dataDir, "authors.txt")));
		line = br.readLine();
		while (line != null) {
			int id = authors.size();
			authors.add(line);
			authorIds.put(line, id);
			line = br.readLine();
		}
		br.close();

		br = new BufferedReader(new FileReader(new File(dataDir,
				"last-commit.txt")));
		lastCommit = br.readLine();
		br.close();
	}

	public void save() throws IOException {
		PrintWriter pw;

		pw = new PrintWriter(new FileWriter(new File(dataDir, "counts.tsv")));
		for (Entry<Integer, Map<Integer, Counter>> e : counts.entrySet()) {
			pw.println(e.getKey());
			for (Entry<Integer, Counter> ee : e.getValue().entrySet())
				pw.println("\t" + ee.getKey() + "\t" + ee.getValue().intValue());
		}
		pw.close();

		pw = new PrintWriter(new FileWriter(new File(dataDir,
				"commit-counts.tsv")));
		for (Entry<Integer, Counter> e : commitCounts.entrySet()) {
			pw.println(e.getKey() + "\t" + e.getValue().intValue());
		}
		pw.close();

		pw = new PrintWriter(new FileWriter(new File(dataDir, "files.txt")));
		for (String file : files) {
			pw.println(file);
		}
		pw.close();

		pw = new PrintWriter(new FileWriter(new File(dataDir, "authors.txt")));
		for (String author : authors) {
			pw.println(author);
		}
		pw.close();

		pw = new PrintWriter(new FileWriter(
				new File(dataDir, "last-commit.txt")));
		pw.println(lastCommit);
		pw.close();
	}

	public int idAuthor(String author) {
		Integer result = authorIds.get(author);
		if (result == null) {
			result = authors.size();
			authors.add(author);
			authorIds.put(author, result);
			commitCounts.put(result, new Counter());
			counts.put(result, new HashMap<Integer, Counter>());
		}
		return result;
	}

	public int idFile(String file) {
		Integer result = fileIds.get(file);
		if (result == null) {
			result = files.size();
			files.add(file);
			fileIds.put(file, result);
		}
		return result;
	}

	public void observeCommit(String commit, int author, List<Integer> files) {
		this.lastCommit = commit;
		commitCounts.get(author).inc();
		Map<Integer, Counter> theseCounts = counts.get(author);
		for (int file : files) {
			Counter counter = theseCounts.get(file);
			if (counter == null) {
				counter = new Counter();
				theseCounts.put(file, counter);
			}
			counter.inc();
		}
	}

	public List<String> authors() {
		return Collections.unmodifiableList(authors);
	}

	public List<String> files() {
		return Collections.unmodifiableList(files);
	}

	public int[] commitsPerAuthor() {
		int[] result = new int[authors.size()];
		for (int author = 0; author < result.length; author++)
			result[author] = commitCounts.get(author).intValue();
		return result;
	}

	public Map<Integer, Integer>[] counts() {
		@SuppressWarnings("unchecked")
		Map<Integer, Integer>[] result = new Map[authors.size()];
		for (int author = 0; author < result.length; author++) {
			Map<Integer, Integer> map = new HashMap<>();
			for (Entry<Integer, Counter> e : counts.get(author).entrySet())
				map.put(e.getKey(), e.getValue().intValue());
			result[author] = map;
		}
		return result;
	}

	public String lastCommit() {
		return lastCommit;
	}

	public static class Counter {
		private int count;

		public Counter() {
			this(0);
		}

		public Counter(int count) {
			this.count = count;
		}

		public void inc() {
			count++;
		}

		public int intValue() {
			return count;
		}
	}

	// transform a DB into input files for Mahout
	public static void main(String[] args) throws Exception {
		DB db = new DB(new File(args[0]));

		// compute affinity for files as % of commits that touch that file
		PrintWriter pw = new PrintWriter(args[1] + ".ratings");
		for (int author : db.commitCounts.keySet()) {
			double totalCommits = db.commitCounts.get(author).intValue();
			for (Map.Entry<Integer, Counter> c : db.counts.get(author)
					.entrySet()) {
				pw.println(author + "\t" + c.getKey() + "\t"
						+ ((c.getValue().intValue() / totalCommits) * 10000)
						+ "\t" + c.getValue().intValue());
			}
		}
		pw.close();

		pw = new PrintWriter(args[1] + ".users");
		int id = 1;
		for (String author : db.authors) {
			pw.println(id + "\t" + author);
			id++;
		}
		pw.close();

		pw = new PrintWriter(args[1] + ".files");
		id = 1;
		for (String file : db.files) {
			pw.println(id + "\t" + file);
			id++;
		}
		pw.close();
	}
}
