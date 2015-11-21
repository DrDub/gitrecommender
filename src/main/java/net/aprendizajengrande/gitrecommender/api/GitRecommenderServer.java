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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
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
 * "blob/master/workers/opal.js" ] }
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
		response.setContentType("application/json");
		response.getWriter().println(obj.toString());
	}

	private void error(ServletResponse response, Exception e)
			throws IOException {
		error(response, e.toString());
	}

	public void service(ServletRequest request, ServletResponse response)
			throws ServletException, IOException {
		InputStream is = request.getInputStream();
		BufferedReader br = new BufferedReader(new InputStreamReader(is));

		StringBuilder task = new StringBuilder();
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
		String repositoryStr;
		String[] filesStrArr;
		try {
			Object maybeObj = new JSONTokener(task.toString()).nextValue();
			if (maybeObj instanceof JSONObject) {
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

		synchronized (lock) {
			try {
				// check whether the repo already has a folder
				if (!repoToFolder.containsKey(repositoryStr)) {
					// TODO create it
					save();
				}

				boolean updated = false;
				// TODO check whether the repo has been updated today
				if (!updated) {
					// TODO call to UpdateLog
					save();
				}

				// TODO call to Recommend, get recos

				JSONObject result = new JSONObject();
				JSONArray recos = new JSONArray();

				// TODO fill in recos

				result.put("recommendation", recos);

				response.setContentType("application/json");
				response.getWriter().println(result.toString());
			} catch (Exception e) {
				e.printStackTrace(response.getWriter());
			}
		}
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
