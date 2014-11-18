/*
 *   This file is part of gitrecommender
 *   Copyright (C) 2014 Pablo Duboue <pablo.duboue@gmail.com>
 * 
 *   gitrecommender is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as 
 *   published by the Free Software Foundation, either version 3 of 
 *   the License, or (at your option) any later version.
 *
 *   gitrecommender is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *   
 *   You should have received a copy of the GNU General Public License 
 *   along with gitrecommender.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.aprendizajengrande.gitrecommender;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;

public class ExtractLog {

	public static void main(String[] args) throws Exception {
		
		FileRepositoryBuilder builder = new FileRepositoryBuilder();
		System.out.println("Git dir: " + args[0]);
		Repository repository = builder.setGitDir(new File(args[0]))
				.readEnvironment() // scan environment GIT_* variables
				.findGitDir() // scan up the file system tree
				.build();

		Git git = new Git(repository);
		Iterable<RevCommit> log = git.log().call();

		RevCommit previous = null;
		String previousAuthor = null;

		// author -> file -> counts
		Map<String, Map<String, Counter>> counts = new HashMap<>();
		Map<String, Counter> commitCounts = new HashMap<>(); // author -> counts
		Map<String, Integer> authorIds = new HashMap<>();
		Map<String, Integer> fileIds = new HashMap<>();
		List<String> authors = new ArrayList<>();
		List<String> files = new ArrayList<>();

		int commitNum = 0;

		for (RevCommit commit : log) {
			commitNum++;
			if (commitNum % 1000 == 0) {
				System.out.println(commitNum);

				// compute affinity for files as % of commits that touch that
				// file
				PrintWriter pw = new PrintWriter(args[1] + "." + commitNum
						+ ".dat");
				for (String author : commitCounts.keySet()) {
					double totalCommits = commitCounts.get(author).intValue();
					for (Map.Entry<String, Counter> c : counts.get(author)
							.entrySet()) {
						pw.println(authorIds.get(author)
								+ "\t"
								+ fileIds.get(c.getKey())
								+ "\t"
								+ ((c.getValue().intValue() / totalCommits) * 10000)
								+ "\t" + c.getValue().intValue());
					}
				}
				pw.close();

				pw = new PrintWriter(args[1] + "." + commitNum + ".users");
				int id = 1;
				for (String author : authors) {
					pw.println(id + "\t" + author);
					id++;
				}
				pw.close();

				pw = new PrintWriter(args[1] + "." + commitNum + ".files");
				id = 1;
				for (String file : files) {
					pw.println(id + "\t" + file);
					id++;
				}
				pw.close();
			}
			// System.out.println("Author: " +
			// commit.getAuthorIdent().getName());
			String author = commit.getAuthorIdent().getName();
			if (!counts.containsKey(author)) {
				counts.put(author, new HashMap<String, Counter>());
				commitCounts.put(author, new Counter(1));
				authorIds.put(author, authorIds.size() + 1);
				authors.add(author);
			} else {
				commitCounts.get(author).inc();
			}

			if (previous != null) {
				AbstractTreeIterator oldTreeParser = prepareTreeParser(
						repository, previous);
				AbstractTreeIterator newTreeParser = prepareTreeParser(
						repository, commit);
				// then the procelain diff-command returns a list of diff
				// entries
				List<DiffEntry> diff = git.diff().setOldTree(oldTreeParser)
						.setNewTree(newTreeParser).call();
				for (DiffEntry entry : diff) {
					// System.out.println("\tFile: " + entry.getNewPath());
					String file = entry.getNewPath();
					if (!fileIds.containsKey(file)) {
						fileIds.put(file, fileIds.size() + 1);
						files.add(file);
					}
					if (!counts.get(previousAuthor).containsKey(file)) {
						counts.get(previousAuthor).put(file, new Counter(1));
					} else {
						counts.get(previousAuthor).get(file).inc();
					}
				}
				// diff.dispose();
				// previous.dispose();
			}
			previous = commit;
			previousAuthor = author;
		}

		// compute affinity for files as % of commits that touch that file
		PrintWriter pw = new PrintWriter(args[1] + ".dat");
		for (String author : commitCounts.keySet()) {
			double totalCommits = commitCounts.get(author).intValue();
			for (Map.Entry<String, Counter> c : counts.get(author).entrySet()) {
				pw.println(authorIds.get(author) + "\t"
						+ fileIds.get(c.getKey()) + "\t"
						+ ((c.getValue().intValue() / totalCommits) * 10000)
						+ "\t" + c.getValue().intValue());
			}
		}
		pw.close();

		pw = new PrintWriter(args[1] + ".users");
		int id = 1;
		for (String author : authors) {
			pw.println(id + "\t" + author);
			id++;
		}
		pw.close();

		pw = new PrintWriter(args[1] + ".files");
		id = 1;
		for (String file : files) {
			pw.println(id + "\t" + file);
			id++;
		}
		pw.close();

		repository.close();
	}

	private static AbstractTreeIterator prepareTreeParser(
			Repository repository, RevCommit commit) throws IOException,
			MissingObjectException, IncorrectObjectTypeException {
		// from the commit we can build the tree which allows us to construct
		// the TreeParser
		RevWalk walk = new RevWalk(repository);
		RevTree tree = walk.parseTree(commit.getTree().getId());
		CanonicalTreeParser result = new CanonicalTreeParser();
		ObjectReader reader = repository.newObjectReader();
		try {
			result.reset(reader, tree.getId());
		} finally {
			reader.release();
		}
		walk.dispose();
		return result;
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
}