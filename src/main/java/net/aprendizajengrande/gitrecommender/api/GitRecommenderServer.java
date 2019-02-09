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

package net.aprendizajengrande.gitrecommender.api;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import net.aprendizajengrande.gitrecommender.Recommend;
import net.aprendizajengrande.gitrecommender.UpdateLog;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * Simple API for gitrecommender. This implementation is just intended for a
 * single user and small repos. A DB backend will be need for serious usage.
 * 
 * POST /recommend JSON object:
 * 
 * { "repository" : <repo URL>, "files" : [ "file paths" ] }
 * 
 * where a file URL contains the branch, for example:
 * 
 * https://github.com/fatiherikli/fil/blob/master/examples/hello.py
 * 
 * the file path is
 * 
 * "master/examples/hello.py"
 * 
 * so for the above repo is
 * 
 * { "repository": "https://github.com/fatiherikli/fil.git", "files" : [
 * "master/examples/hello.py", "gh-pages/index.html", "master/images/logo.png",
 * "master/workers/opal.js" ] }
 * 
 * the output of the API will be { "recommendation" : [ { "file" :
 * "blob/master/workers/javascript.js", "score" : 0.3 }, { "file" :
 * "blob/gh-pages/build/javascript.worker.js", "score" : 0.1 } ] }
 * 
 * or
 * 
 * { "error" : { "msg":"error message", ... } }
 * 
 * @author pablo
 * 
 */
public class GitRecommenderServer implements Servlet {

	public void destroy() {
	}

	public ServletConfig getServletConfig() {
		return null;
	}

	public String getServletInfo() {
		return "";
	}

	public void init(ServletConfig arg0) throws ServletException {
	}

	private void error(ServletResponse response, String msg) throws IOException {
		JSONObject obj = new JSONObject();
		JSONObject msgObj = new JSONObject();
		msgObj.put("msg", msg);
		obj.put("error", msgObj);
		System.err.println("Error: " + msg);
		response.setContentType("application/json");
		response.getWriter().println(obj.toString());
	}

	private void error(ServletResponse response, Exception e)
			throws IOException {
		error(response, e.toString());
	}

	public void service(ServletRequest request, ServletResponse response)
			throws ServletException, IOException {
		StringBuilder task = new StringBuilder();
		{
			InputStream is = request.getInputStream();
			BufferedReader br = new BufferedReader(new InputStreamReader(is));
			try {
				String line = br.readLine();
				while (line != null) {
					task.append(line).append('\n');
					line = br.readLine();
				}
			} catch (IOException e) {
				error(response, e);
				return;
			}
			br.close();
		}
		String repositoryStr;
		String[] filesStrArr;
		try {
			Object maybeObj = new JSONTokener(task.toString()).nextValue();
			if (!(maybeObj instanceof JSONObject)) {
				error(response, "Expected: JSON object");
				return;
			} else {
				JSONObject obj = (JSONObject) maybeObj;
				if (!obj.has("repository")) {
					error(response, "Expected: repository");
					return;
				}
				if (!obj.has("files")) {
					error(response, "Expected: files");
					return;
				}
				repositoryStr = obj.getString("repository");
				JSONArray filesArr = obj.getJSONArray("files");
				filesStrArr = new String[filesArr.length()];
				for (int i = 0; i < filesStrArr.length; i++) {
					filesStrArr[i] = filesArr.getString(i);
				}
			}
		} catch (JSONException e) {
			error(response, e);
			return;
		}

		System.err.println("Got: " + repositoryStr + " for "
				+ java.util.Arrays.asList(filesStrArr));

		synchronized (lock) {
			try {
				// check whether the repo already has a folder
				if (!repoToFolder.containsKey(repositoryStr)) {
					int id = repoToFolder.size();
					File repoDir = new File(String.valueOf(id));
					repoDir.mkdirs();
					File dbDir = new File(repoDir, "db");
					dbDir.mkdirs();
					File gitDir = new File(repoDir, "git");

					CloneCommand clone = Git.cloneRepository();
					clone.setBare(true);
					clone.setCloneAllBranches(true);
					clone.setDirectory(gitDir).setURI(repositoryStr);
					clone.call();

					setTimeStamp(repoDir, 0L);

					repoToFolder.put(repositoryStr, repoDir);
					save();
				}

				// check whether the repo has been updated today
				File repoDir = repoToFolder.get(repositoryStr);
				boolean updated = false;
				long timestamp = getTimeStamp(repoDir);
				updated = System.currentTimeMillis() - timestamp < 24 * 60 * 60 * 1000L;
				if (!updated) {
					File gitDir = new File(repoDir, "git");
					File dbDir = new File(repoDir, "db");

					// update repo
					FileRepositoryBuilder builder = new FileRepositoryBuilder();
					Repository repository = builder.setGitDir(gitDir)
							.readEnvironment().findGitDir().build();

					Git git = new Git(repository);
					git.pull();

					// call to UpdateLog
					UpdateLog.main(new String[] { gitDir.getAbsolutePath(),
							dbDir.getAbsolutePath() });

					setTimeStamp(repoDir);
				}

				// call to Recommend, get recos
				File dbDir = new File(repoDir, "db");
				File tmpInput = File.createTempFile("gitrecommender-input",
						"tmp");
				File tmpOutput = File.createTempFile("gitrecommender-output",
						"");
				File tmpOutputDir = new File(tmpOutput.getAbsolutePath()
						+ ".dir");
				tmpOutputDir.mkdirs();
				File outputFile = new File(repoDir, "recos");

				File taskFile = new File(repoDir, "task");
				PrintWriter pw = new PrintWriter(new FileWriter(taskFile));
				for (String fileStr : filesStrArr)
					pw.println(fileStr.replaceFirst("[^/]+/", ""));
				pw.close();

				deleteTemp();
				Recommend.main(new String[] { dbDir.getAbsolutePath(),
						tmpInput.toURI().toString(),
						tmpOutputDir.toURI().toString(),
						outputFile.getAbsolutePath(),
						taskFile.getAbsolutePath() });

				JSONObject result = new JSONObject();
				JSONArray recos = new JSONArray();

				// fill in recos
				BufferedReader br = new BufferedReader(new FileReader(
						outputFile));
				String line = br.readLine();
				while (line != null) {
					String[] parts = line.split("\\t");
					JSONObject entry = new JSONObject();
					entry.put("file", parts[1]);
					entry.put("score", Float.parseFloat(parts[2]));
					recos.put(entry);
					line = br.readLine();
				}
				br.close();

				result.put("recommendation", recos);

				System.err.println("Send: " + result.toString());

				response.setContentType("application/json");
				response.getWriter().println(result.toString());
			} catch (Exception e) {
				e.printStackTrace(response.getWriter());
			}
		}
	}

	private void deleteTemp() throws IOException {
		File temp = new File("temp");
		if (temp.exists()) {
			// renaming it to avoid error-prone recursive deletion
			temp.renameTo(new File(File.createTempFile("gitrecommender-tmp",
					"", new File(".")).getAbsolutePath()
					+ ".dir"));
		}
	}

	private void setTimeStamp(File repoDir, long i) throws IOException {
		PrintWriter pw = new PrintWriter(new FileWriter(new File(repoDir,
				"timestamp")));
		pw.println(i);
		pw.close();
	}

	private void setTimeStamp(File repoDir) throws IOException {
		setTimeStamp(repoDir, System.currentTimeMillis());
	}

	private long getTimeStamp(File repoDir) throws IOException {
		File timestampFile = new File(repoDir, "timestamp");
		if (!timestampFile.exists())
			return 0L;
		BufferedReader br = new BufferedReader(new FileReader(timestampFile));
		String line = br.readLine();
		br.close();
		if (line == null)
			return 0L;
		return Long.parseLong(line);
	}

	private static Object lock = new Object();

	private static Map<String, File> repoToFolder = new HashMap<String, File>();

	private static File apiDB = new File("api-db.ser");

	@SuppressWarnings("unchecked")
	private static void load() throws IOException, ClassNotFoundException {
		if (apiDB.exists()) {
			ObjectInputStream ois = new ObjectInputStream(new FileInputStream(
					apiDB));
			repoToFolder = (Map<String, File>) ois.readObject();
			ois.close();
		}
	}

	private static void save() throws IOException {
		ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(
				apiDB));
		oos.writeObject(repoToFolder);
		oos.close();
	}

	public static void main(String[] args) throws Exception {
		if (args.length != 1) {
			System.err.println("Usage: GitRecommenderServer <port number>");
			System.exit(-1);
		}

		load();
		Server server = new Server(Integer.valueOf(args[0]));
		ServletHolder holder = new ServletHolder(new GitRecommenderServer());
		ServletHandler context = new ServletHandler();
		context.addServletWithMapping(holder, "/recommend");
		server.setHandler(context);
		server.start();
		server.join();
	}
}
