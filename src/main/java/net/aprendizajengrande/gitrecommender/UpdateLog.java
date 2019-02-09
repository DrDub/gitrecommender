/*
 *   This file is part of gitrecommender
 *   Copyright (C) 2014-2019 Pablo Duboue <pablo.duboue@gmail.com>
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import net.aprendizajengrande.gitrecommender.db.DB;

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

public class UpdateLog {

	public static void main(String[] args) throws Exception {

		if (args.length == 0) {
			System.err.println("Usage: UpdateLog <git dir> <db dir>");
			System.exit(-1);
		}

		File gitDir = new File(args[0]);
		File dbDir = new File(args[1]);

		final DB db = new DB(dbDir);

		System.out.println("Git dir: " + gitDir);
		System.out.println("DB dir: " + dbDir);

		FileRepositoryBuilder builder = new FileRepositoryBuilder();
		// scan environment GIT_* variables
		final Repository repository = builder.setGitDir(gitDir).readEnvironment()
				.findGitDir() // scan up the file system tree
				.build();

		final Git git = new Git(repository);
		Iterable<RevCommit> log = git.log().call();

		// go through all commits and process them in reverse order
		final List<RevCommit> allCommits = new ArrayList<RevCommit>();

		int newCommits = 0;
		boolean seen = false;
		for (RevCommit commit : log) {
			String name = commit.name();
			if (db.lastCommit().equals(name)) {
				seen = true;
			}
			if (!seen)
				newCommits++;

			allCommits.add(commit);
		}
		System.out.println("Found " + allCommits.size() + " commits ("
				+ newCommits + " new).");

		int commitNum = 0;
		List<Integer> files = new ArrayList<Integer>();
                long start = System.currentTimeMillis();
                
		int cpus = Runtime.getRuntime().availableProcessors();
		ExecutorService threadPool = Executors.newFixedThreadPool(cpus);

                final AtomicInteger runningTasks = new AtomicInteger(0);
                
		for (int i = newCommits - 1; i >= 0; i--) {
                    commitNum++;
                    
                    if (commitNum % 1000 == 0) {
                        while(runningTasks.get() > 0){
				Thread.sleep(100);
				continue;
			}
                        long end = System.currentTimeMillis();
                        System.out.println("Processed " + commitNum + " commits out of " + newCommits + " in " + ( (end - start) / 1000 ) + " secs.");
                        if (commitNum % 10000 == 0) {
                            synchronized(db){
                                db.save();
                            }
                        }
                    }
                    final int commitIdx = i;
                    
                    runningTasks.incrementAndGet();
                    threadPool.submit(new Runnable() {
                            public void run() {
                                try {
                                    RevCommit commit = allCommits.get(commitIdx);

                                    List<String> fileNames = new ArrayList<String>(); // TODO: move to threadlocal

                                    if (commitIdx < allCommits.size() - 1) {
                                        AbstractTreeIterator oldTreeParser = prepareTreeParser(
                                                                                               repository, allCommits.get(commitIdx + 1));
                                        AbstractTreeIterator newTreeParser = prepareTreeParser(
                                                                                               repository, commit);
                                        // then the procelain diff-command returns a list of diff entries
                                        List<DiffEntry> diff = git.diff().setOldTree(oldTreeParser)
                                            .setNewTree(newTreeParser).call();
                                        for (DiffEntry entry : diff) {
                                            // System.out.println("\tFile: " + entry.getNewPath());
                                            String file = entry.getNewPath();
                                            fileNames.add(file);
                                        }
                                    }
                                    synchronized(db){
                                        String author = commit.getAuthorIdent().getName();
                                        int authorId = db.idAuthor(author);
                                        List<Integer> files = new ArrayList<Integer>(fileNames.size()); // TODO: move to threadlocal
                                        for(String file : fileNames)
                                            files.add(db.idFile(file));
                                        
                                        db.observeCommit(commit.name(), authorId, files);
                                    }
                                } catch (Exception exc) {
                                    exc.printStackTrace();
                                }

                                runningTasks.decrementAndGet();
                            }
			});

		}

                while(runningTasks.get() > 0){
                    Thread.sleep(100);
                    continue;
                }
                synchronized(db){
                    db.save();
                }
		threadPool.shutdown();
		repository.close();
                long end = System.currentTimeMillis();
                System.out.println("Import took: " + ( (end - start) / 1000 ) + " secs.");
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
}
