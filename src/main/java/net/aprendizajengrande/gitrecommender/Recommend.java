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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

import net.aprendizajengrande.gitrecommender.db.DB;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.util.ToolRunner;
import org.apache.mahout.cf.taste.hadoop.item.RecommenderJob;

public class Recommend {

	public static void main(String[] args) throws Exception {

		if (args.length != 4 && args.length != 5) {
			System.err
					.println("Usage: <db dir> <hdfs folder for input> <hdfs folder for output> <output file> <dyn files?>");
			System.exit(1);
		}

		Configuration conf = new Configuration();
		// see
		// http://stackoverflow.com/questions/17265002/hadoop-no-filesystem-for-scheme-file
		conf.set("fs.hdfs.impl",
				org.apache.hadoop.hdfs.DistributedFileSystem.class.getName());
		conf.set("fs.file.impl",
				org.apache.hadoop.fs.LocalFileSystem.class.getName());

		FileSystem hdfs = FileSystem.get(conf);

		// delete leftover temp folder from previous run
		Path temp = new Path("temp");
		if (hdfs.exists(temp))
			hdfs.delete(temp, true);

		File dbDir = new File(args[0]);
		DB db = new DB(dbDir);

		File outputFile = new File(args[3]);

		String inputName = args[1];
		String outputName = args[2] + "/recos";

		Path input = new Path(inputName);
		Path output = new Path(outputName);
		Path actualOutput = new Path(outputName + "/part-r-00000");

		// populate ratings file
		FSDataOutputStream fsdos = input.getFileSystem(conf).create(input);
		PrintWriter pw = new PrintWriter(new OutputStreamWriter(fsdos));

		// compute affinity for files as % of commits that touch that file
		int[] authorCommitCounts = db.commitsPerAuthor();
		Map<Integer, Integer> counts[] = db.counts();

		for (int author = 0; author < authorCommitCounts.length; author++) {
			for (Map.Entry<Integer, Integer> c : counts[author].entrySet()) {
				pw.println(author
						+ "\t"
						+ c.getKey()
						+ "\t"
						+ ((c.getValue() / (authorCommitCounts[author] * 1.0)) * 10000.0)
						+ "\t" + c.getValue().intValue());
			}
		}

		if (args.length == 5) {
			BufferedReader br = new BufferedReader(new FileReader(args[4]));
			String line = br.readLine();
			int extraAuthor = authorCommitCounts.length;
			int pos = 0;
			while (line != null) {
				Integer fileId = db.idFileOrNull(line);
				if (fileId != null) {
					pw.println(extraAuthor + "\t" + fileId + "\t100.0\t"
							+ pos);
					pos++;
				}
				line = br.readLine();
			}
			br.close();
		}

		pw.close();

		// compute recommendation in Hadoop
		String[] options = new String[] { "--input", inputName, "--output",
				outputName, "--similarityClassname", "SIMILARITY_COSINE",
				"--minPrefsPerUser", "0", "--booleanData", "1" };
		if (args.length == 5) {
			String inputNameExtraId = inputName + ".extraId";
			Path inputExtraId = new Path(inputNameExtraId);
			fsdos = input.getFileSystem(conf).create(inputExtraId);
			pw = new PrintWriter(new OutputStreamWriter(fsdos));
			pw.println(authorCommitCounts.length);
			pw.close();
			String[] newOptions = new String[options.length + 2];
			System.arraycopy(options, 0, newOptions, 0, options.length);
			newOptions[options.length] = "--usersFile";
			newOptions[options.length + 1] = inputNameExtraId;
			options = newOptions;
		}

		ToolRunner.run(new Configuration(), new RecommenderJob(), options);

		// read recommendations
		FSDataInputStream fsdis = output.getFileSystem(conf).open(actualOutput);
		BufferedReader br = new BufferedReader(new InputStreamReader(fsdis));
		String line = br.readLine();

		pw = new PrintWriter(new FileWriter(outputFile));
		List<String> files = db.files();
		List<String> authors = db.authors();
		while (line != null) {
			String[] parts = line.split("\\s+");
			int authorId = Integer.parseInt(parts[0]);
			String author;
			if (args.length == 5) {
				if (authorId < authorCommitCounts.length) {
					line = br.readLine();
					continue;
				}
				author = "extra";
			} else {
				author = authors.get(authorId);
			}
			parts = parts[1].substring(1, parts[1].length() - 1).split(",");
			for (String pair : parts) {
				String[] pairsPart = pair.split(":");
				pw.println(author + "\t"
						+ files.get(Integer.parseInt(pairsPart[0])) + "\t"
						+ pairsPart[1]);
			}
			line = br.readLine();
		}
		pw.close();
	}

}
