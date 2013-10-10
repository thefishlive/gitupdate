/**
 * gitupdater 0.1-SNAPSHOT
 * Copyright (C) 2013 James Fitzpatrick <james_fitzpatrick@outlook.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package io.github.thefishlive.updater;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.FileUtils;

public class GitUpdater {

	public static File gitrepo;
	public static String remote;
	public static int port;
	
	public static void main(String[] args) {
		loadConfig();
		
		HttpServer server = new HttpServer();
		server.run();
	}
	
	public static void loadConfig() {
		File config = new File("config.cfg");
		
		if (!config.exists()) {
			try {
				InputStream stream = GitUpdater.class.getResourceAsStream("config.cfg");
				
				if (stream != null) {
					FileUtils.copyInputStreamToFile(stream, config);
				} else {
					config.createNewFile();
				}
				
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		String line = "";
		
		try {
			BufferedReader reader = new BufferedReader(new FileReader(config));
			while((line = reader.readLine()) != null) {
				if (line.startsWith("gitrepo:")) {
					gitrepo = new File(line.substring("gitrepo:".length()).trim());
					if (!gitrepo.exists()) {
						gitrepo.mkdirs();
					}
				} else if (line.startsWith("remote:")) {
					remote = line.substring("remote:".length()).trim();
				} else if (line.startsWith("port:")) {
					port = Integer.parseInt(line.substring("port:".length()).trim());
				}
			}
			reader.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	
}
