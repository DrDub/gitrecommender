/*
 *   This file is part of gitrecommender
 *   Copyright (C) 2014 Pablo Duboue <pablo.duboue@gmail.com>
 * 
 *   gitrecommender is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as 
 *   published by the Free Software Foundation, either version 3 of 
 *   the License, or (at your option) any later version.
 *
 *   Meetdle is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *   
 *   You should have received a copy of the GNU Affero General Public 
 *   License along with Thoughtland.  If not, see <http://www.gnu.org/licenses/>.
 */
/*
 *   This file is part of gitrecommender
 *   Copyright (C) 2014 Pablo Duboue <pablo.duboue@gmail.com>
 * 
 *   gitrecommender is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as 
 *   published by the Free Software Foundation, either version 3 of 
 *   the License, or (at your option) any later version.
 *
 *   Meetdle is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *   
 *   You should have received a copy of the GNU Affero General Public 
 *   License along with Thoughtland.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.aprendizajengrande.gitrecommender;

import java.io.BufferedReader;
import java.io.File;
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
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.util.ToolRunner;
import org.apache.mahout.cf.taste.hadoop.item.RecommenderJob;

public class Recommend {

	public static void main(String[] args) throws Exception {

		if (args.length != 4) {
			System.err
					.println("Usage: <db dir> <hdfs folder for input> <hdfs folder for output> <output file>");
			System.exit(1);
		}

		Configuration conf = new Configuration();

		File dbDir = new File(args[0]);
		DB db = new DB(dbDir);

		File outputFile = new File(args[3]);

		String inputName = args[1] + "/ratings";
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
		pw.close();

		// compute recommendation in Hadoop
		ToolRunner.run(new Configuration(), new RecommenderJob(), new String[] {
				"--input", inputName, "--output", outputName,
				"--similarityClassname", "SIMILARITY_COSINE" });

		// read recommendations
		FSDataInputStream fsdis = output.getFileSystem(conf).open(actualOutput);
		BufferedReader br = new BufferedReader(new InputStreamReader(fsdis));
		String line = br.readLine();

		pw = new PrintWriter(new FileWriter(outputFile));
		List<String> files = db.files();
		List<String> authors = db.authors();
		while (line != null) {
			String[] parts = line.split("\\s+");
			String author = authors.get(Integer.parseInt(parts[0]));
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
