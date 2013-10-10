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

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

public class Updater implements Runnable {

	private static final File basedir = GitUpdater.gitrepo;
	private static final File gitDir = new File(basedir, ".git/");
	
	public void run() {
		System.out.println("-------------------------");
		System.out.println(gitDir.getAbsolutePath());
		File updateFile = new File(basedir, "UPDATE");
		Git git = null;
		
		try {
			if (!gitDir.exists()) {
				git = Git.cloneRepository()
						.setDirectory(basedir)
						.setURI(GitUpdater.remote)
						.setProgressMonitor(buildProgressMonitor())
						.call();
				
				System.out.println("Repository cloned");
			} else {
				updateFile.createNewFile();
				
				FileRepositoryBuilder builder = new FileRepositoryBuilder();
				Repository repo = builder.setGitDir(gitDir)
				  .readEnvironment() // scan environment GIT_* variables
				  .findGitDir() // scan up the file system tree
				  .build();
				
				git = new Git(repo);
			
				PullResult result = git.pull()
										.setProgressMonitor(buildProgressMonitor())
										.call();
				
				if (!result.isSuccessful() || result.getMergeResult().getMergeStatus().equals(MergeStatus.MERGED)) {
					System.out.println("Update Failed");
					FileUtils.deleteDirectory(basedir);
					basedir.mkdir();
					System.out.println("Re-cloning repository");
					
					git = Git.cloneRepository()
							.setDirectory(basedir)
							.setURI(GitUpdater.remote)
							.setProgressMonitor(buildProgressMonitor())
							.call();
					
					System.out.println("Repository cloned");
				}
				
				System.out.println("State: " + result.getMergeResult().getMergeStatus());
			}
			
			File configdir = new File("config");
			
			if (configdir.exists()) {
			    FileUtils.copyDirectory(configdir, new File(basedir, "config"));
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			git.getRepository().close();
		}
		
		updateFile.delete();
		System.out.println("-------------------------");
	}

	private ProgressMonitor buildProgressMonitor() {
		return new ProgressMonitor() {
			private int curIndent = -1;
			
			@Override
			public void start(int totalTasks) {
			}

			@Override
			public void beginTask(String title, int totalWork) {
				if (!title.startsWith("remote:")) curIndent++;
				String message = title + "(" + totalWork + ")";
				for (int i = 0; i < curIndent; i++) {
					message = "\t" + message;
				}
				System.out.println(message);
			}

			@Override
			public void update(int completed) {
			}

			@Override
			public void endTask() {
				String message = "Task ended";
				for (int i = 0; i < curIndent; i++) {
					message = "\t" + message;
				}
				System.out.println(message);
				curIndent--;
			}

			@Override
			public boolean isCancelled() {
				return false;
			}
			
		};
	}

}
