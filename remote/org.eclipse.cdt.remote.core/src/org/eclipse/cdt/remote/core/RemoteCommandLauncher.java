/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Marc-Andre Laperle (Ericsson) - Fix MinGW and Cygwin build (Bug 438476)
 *******************************************************************************/
package org.eclipse.cdt.remote.core;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.Map;
import java.util.Properties;

import org.eclipse.cdt.core.CommandLauncher;
import org.eclipse.cdt.core.ICommandLauncher;
import org.eclipse.cdt.remote.internal.core.Activator;
import org.eclipse.cdt.remote.internal.core.messages.Messages;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.remote.core.IRemoteConnection;
import org.eclipse.remote.core.IRemoteConnectionType;
import org.eclipse.remote.core.IRemoteFileService;
import org.eclipse.remote.core.IRemoteProcess;
import org.eclipse.remote.core.IRemoteProcessBuilder;
import org.eclipse.remote.core.IRemoteProcessService;
import org.eclipse.remote.core.IRemoteResource;
import org.eclipse.remote.core.IRemoteServicesManager;
import org.eclipse.remote.core.RemoteProcessAdapter;

public class RemoteCommandLauncher implements ICommandLauncher {
	
	private static final String CYGWIN_PREFIX = "cygdrive"; //$NON-NLS-1$

	/**
	 * Convert a local (workspace) path into the remote equivalent. If the local path is not
	 * absolute, then do nothing.
	 * 
	 * e.g. Suppose the local path is /u/local_user/workspace/local_project/subdir1/subdir2
	 *      Suppose the remote project location is /home/remote_user/remote_project
	 *      Then the resulting path will be /home/remote_user/remote_project/subdir1/subdir2
	 * 
	 * @param localPath absolute local path in the workspace
	 * @param remote remote project
	 * @return remote path that is the equivalent of the local path
	 */
	public static String makeRemote(String local, IRemoteResource remote) {
		return makeRemote(new Path(local), remote).toString();
	}
	
	/**
	 * Convert a local (workspace) path into the remote equivalent. If the local path is not
	 * absolute, then do nothing.
	 * 
	 * e.g. Suppose the local path is /u/local_user/workspace/local_project/subdir1/subdir2
	 *      Suppose the remote project location is /home/remote_user/remote_project
	 *      Then the resulting path will be /home/remote_user/remote_project/subdir1/subdir2
	 * 
	 * @param localPath absolute local path in the workspace
	 * @param remote remote project
	 * @return remote path that is the equivalent of the local path
	 */
	public static IPath makeRemote(IPath localPath, IRemoteResource remote) {
		if (!localPath.isAbsolute()) {
			return localPath;
		}
		
		IPath remoteLocation = remote.getResource().getLocation();
		IPath remotePath = new Path(remote.getActiveLocationURI().getPath());
		
		// Device mismatch, we might be in the presence of Cygwin or MinGW
		if (remoteLocation.getDevice() != null && localPath.getDevice() == null) {
			boolean isCygwin = localPath.segment(0).equals(CYGWIN_PREFIX);
			remoteLocation = new Path(getPathString(remoteLocation, isCygwin));
			remotePath = new Path(getPathString(remotePath, isCygwin));
		}

		IPath relativePath = localPath.makeRelativeTo(remoteLocation);
		if (!relativePath.isEmpty()) {
			remotePath = remotePath.append(relativePath);
		}
		return remotePath;
	}
	
	private static String getPathString(IPath path, boolean isCygwin) {
		String s = path.toString();
		if (isCygwin) {
			s = s.replaceAll("^([a-zA-Z]):", "/" + CYGWIN_PREFIX + "/$1");  //$NON-NLS-1$//$NON-NLS-2$ //$NON-NLS-3$
		} else {
			s = s.replaceAll("^([a-zA-Z]):", "/$1"); //$NON-NLS-1$ //$NON-NLS-2$
		}

		return s;
	}
	
	private final ICommandLauncher fLocalLauncher = new CommandLauncher();
	private boolean fShowCommand;
	private String[] fCommandArgs;
	private IRemoteConnection fConnection;
	private IRemoteProcess fRemoteProcess;
	private final Properties fEnvironment = new Properties();

	/**
	 * The number of milliseconds to pause between polling.
	 */
	private static final long DELAY = 50L;

	/**
	 * Constructs a command array that will be passed to the process
	 */
	private String[] constructCommandArray(String command, String[] commandArgs, IRemoteResource remote) {
		String[] args = new String[1 + commandArgs.length];
		args[0] = makeRemote(command, remote);
		for (int i = 0; i < commandArgs.length; i++) {
			args[i + 1] = makeRemote(commandArgs[i], remote);
		}
		return args;
	}

	@Override
	public Process execute(IPath commandPath, String[] args, String[] env, IPath workingDirectory, IProgressMonitor monitor)
			throws CoreException {
		if (getProject() != null) {
			IRemoteResource remRes = (IRemoteResource) getProject().getAdapter(IRemoteResource.class);
			if (remRes != null) {
				URI uri = remRes.getActiveLocationURI();
				IRemoteServicesManager remoteServicesManager = Activator.getService(IRemoteServicesManager.class);
				IRemoteConnectionType connectionType = remoteServicesManager.getConnectionType(uri);
				if (connectionType != null) {
					fConnection = connectionType.getConnection(uri);
					if (fConnection != null) {
						parseEnvironment(env);
						fCommandArgs = constructCommandArray(commandPath.toString(), args, remRes);
						IRemoteProcessService processService = fConnection.getService(IRemoteProcessService.class);
						IRemoteProcessBuilder processBuilder = processService.getProcessBuilder(fCommandArgs);
						if (workingDirectory != null) {
							String remoteWorkingPath = makeRemote(workingDirectory.toString(), remRes);
							IRemoteFileService fileManager = fConnection.getService(IRemoteFileService.class);
			                IFileStore wd = fileManager.getResource(remoteWorkingPath);
							processBuilder.directory(wd);
						}
						Map<String, String> processEnv = processBuilder.environment();
						for (String key : fEnvironment.stringPropertyNames()) {
							processEnv.put(key, fEnvironment.getProperty(key));
						}
						try {
							fRemoteProcess = processBuilder.start();
							return new RemoteProcessAdapter(fRemoteProcess);
						} catch (IOException e) {
							fLocalLauncher.setErrorMessage(e.getMessage());
							return null;
						}
					}
				}
			}
		}
		return fLocalLauncher.execute(commandPath, args, env, workingDirectory, monitor);
	}

	@Override
	public String[] getCommandArgs() {
		return fCommandArgs;
	}

	@Override
	public String getCommandLine() {
		return getCommandLine(fCommandArgs);
	}

	protected String getCommandLine(String[] commandArgs) {
		return getCommandLineQuoted(commandArgs, false);
	}

	@SuppressWarnings("nls")
	private String getCommandLineQuoted(String[] commandArgs, boolean quote) {
		String nl = System.getProperty("line.separator", "\n");
		if (fConnection != null) {
			nl = fConnection.getProperty(IRemoteConnection.LINE_SEPARATOR_PROPERTY);
		}
		StringBuffer buf = new StringBuffer();
		if (commandArgs != null) {
			for (String commandArg : commandArgs) {
				if (quote && (commandArg.contains(" ") || commandArg.contains("\"") || commandArg.contains("\\"))) {
					commandArg = '"' + commandArg.replaceAll("\\\\", "\\\\\\\\").replaceAll("\"", "\\\\\"") + '"';
				}
				buf.append(commandArg);
				buf.append(' ');
			}
			buf.append(nl);
		}
		return buf.toString();
	}

	@Override
	public Properties getEnvironment() {
		return fEnvironment;
	}

	@Override
	public String getErrorMessage() {
		return fLocalLauncher.getErrorMessage();
	}

	@Override
	public IProject getProject() {
		return fLocalLauncher.getProject();
	}

	/**
	 * Parse array of "ENV=value" pairs to Properties.
	 */
	private void parseEnvironment(String[] env) {
		if (env != null) {
			fEnvironment.clear();
			for (String envStr : env) {
				// Split "ENV=value" and put in Properties
				int pos = envStr.indexOf('=');
				if (pos < 0) {
					pos = envStr.length();
				}
				String key = envStr.substring(0, pos);
				String value = envStr.substring(pos + 1);
				fEnvironment.put(key, value);
			}
		}
	}

	private void printCommandLine(OutputStream os) {
		if (os != null) {
			try {
				os.write(getCommandLineQuoted(getCommandArgs(), true).getBytes());
				os.flush();
			} catch (IOException e) {
				// ignore;
			}
		}
	}

	@Override
	public void setErrorMessage(String error) {
		fLocalLauncher.setErrorMessage(error);
	}

	@Override
	public void setProject(IProject project) {
		fLocalLauncher.setProject(project);
	}

	@Override
	public void showCommand(boolean show) {
		fLocalLauncher.showCommand(show);
		fShowCommand = show;
	}

	@Override
	public int waitAndRead(OutputStream out, OutputStream err) {
		if (fShowCommand) {
			printCommandLine(out);
		}

		if (fRemoteProcess == null) {
			return ILLEGAL_COMMAND;
		}

		RemoteProcessClosure closure = new RemoteProcessClosure(fRemoteProcess, out, err);
		closure.runBlocking();
		return OK;
	}

	@Override
	public int waitAndRead(OutputStream out, OutputStream err, IProgressMonitor monitor) {
		if (fShowCommand) {
			printCommandLine(out);
		}
		if (fRemoteProcess == null) {
			return ILLEGAL_COMMAND;
		}

		RemoteProcessClosure closure = new RemoteProcessClosure(fRemoteProcess, out, err);
		closure.runNonBlocking();
		while (!monitor.isCanceled() && closure.isAlive()) {
			try {
				Thread.sleep(DELAY);
			} catch (InterruptedException ie) {
				// ignore
			}
		}

		int state = OK;

		// Operation canceled by the user, terminate abnormally.
		if (monitor.isCanceled()) {
			closure.terminate();
			state = COMMAND_CANCELED;
			setErrorMessage(Messages.RemoteCommandLauncher_Command_canceled);
		}

		try {
			fRemoteProcess.waitFor();
		} catch (InterruptedException e) {
			// ignore
		}
		return state;
	}
}
