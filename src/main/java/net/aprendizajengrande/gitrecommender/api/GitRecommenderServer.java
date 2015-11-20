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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.json.JSONArray;
import org.json.JSONObject;

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

	private static Object lock = new Object();

	public void service(ServletRequest request, ServletResponse response)
			throws ServletException, IOException {
		InputStream is = request.getInputStream();
		BufferedReader br = new BufferedReader(new InputStreamReader(is));

		StringBuilder doc = new StringBuilder();
		String line = br.readLine();
		while (line != null) {
			doc.append(line).append('\n');
			line = br.readLine();
		}

		synchronized (lock) {
			try {
				JSONObject result = new JSONObject();
				JSONArray recos = new JSONArray();
				
				//TODO fill in recos
				
				result.put("recommendation", recos);

				response.getWriter().println(result.toString());
			} catch (Exception e) {
				e.printStackTrace(response.getWriter());
			}
		}
	}

	public static void main(String[] args) throws Exception {
		Server server = new Server(Integer.valueOf(args[0]));
		ServletHolder holder = new ServletHolder(new GitRecommenderServer());
		ServletHandler context = new ServletHandler();
		context.addServletWithMapping(holder, "/");
		server.setHandler(context);
		server.start();
		server.join();
	}
}
