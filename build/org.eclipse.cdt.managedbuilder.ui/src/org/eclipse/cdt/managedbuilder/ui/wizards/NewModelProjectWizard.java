/*******************************************************************************
 * Copyright (c) 2002, 2007 Rational Software Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * IBM Rational Software - Initial API and implementation
 *******************************************************************************/
package org.eclipse.cdt.managedbuilder.ui.wizards;


import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.cdt.core.CCorePlugin;
import org.eclipse.cdt.core.model.CoreModel;
import org.eclipse.cdt.core.settings.model.ICConfigurationDescription;
import org.eclipse.cdt.core.settings.model.ICProjectDescription;
import org.eclipse.cdt.managedbuilder.core.BuildException;
import org.eclipse.cdt.managedbuilder.core.IToolChain;
import org.eclipse.cdt.managedbuilder.ui.properties.ManagedBuilderUIPlugin;
import org.eclipse.cdt.managedbuilder.ui.properties.Messages;
import org.eclipse.cdt.ui.CUIPlugin;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceDescription;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExecutableExtension;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.ui.actions.WorkspaceModifyDelegatingOperation;
import org.eclipse.ui.wizards.newresource.BasicNewProjectResourceWizard;
import org.eclipse.ui.wizards.newresource.BasicNewResourceWizard;

public abstract class NewModelProjectWizard extends BasicNewResourceWizard implements IExecutableExtension  
{
	private static final String PREFIX= "CProjectWizard"; //$NON-NLS-1$
	private static final String OP_ERROR= "CProjectWizard.op_error"; //$NON-NLS-1$
	private static final String title= CUIPlugin.getResourceString(OP_ERROR + ".title"); //$NON-NLS-1$
	private static final String message= CUIPlugin.getResourceString(OP_ERROR + ".message"); //$NON-NLS-1$
	
	protected IConfigurationElement fConfigElement;
	protected CMainWizardPage fMainPage;
	protected CConfigWizardPage fConfigPage;
	
	protected IProject newProject;
	private String wz_title;
	private String wz_desc;
	
	public String lastProjectName = null;
	private ICWizardHandler savedHandler = null;
	private IToolChain[] savedToolChains = null;
	private boolean savedDefaults = false;

	protected List localPages = new ArrayList(); // replacing Wizard.pages since we have to delete them
	
	public NewModelProjectWizard() {
		this(Messages.getString("NewModelProjectWizard.0"),Messages.getString("NewModelProjectWizard.1")); //$NON-NLS-1$ //$NON-NLS-2$
	}

	public NewModelProjectWizard(String title, String desc) {
		super();
		setDialogSettings(CUIPlugin.getDefault().getDialogSettings());
		setNeedsProgressMonitor(true);
		wz_title = title;
		wz_desc = desc;
	}
	
	public void addPages() {
		fConfigPage = new CConfigWizardPage();
		fMainPage= new CMainWizardPage(CUIPlugin.getResourceString(PREFIX), fConfigPage);
		fMainPage.setTitle(wz_title);
		fMainPage.setDescription(wz_desc);
		addPage(fMainPage);
		addPage(fConfigPage);
		
		// support for custom wizard pages
		MBSCustomPageManager.init();
		MBSCustomPageManager.addStockPage(fMainPage, CMainWizardPage.PAGE_ID);
		MBSCustomPageManager.addStockPage(fConfigPage, CConfigWizardPage.PAGE_ID);
		
		// Set up custom page manager to current natures settings
		String[] natures = getNatures();
		if (natures == null || natures.length == 0)
			MBSCustomPageManager.addPageProperty(MBSCustomPageManager.PAGE_ID, MBSCustomPageManager.NATURE, null);
		else if (natures.length == 1)
			MBSCustomPageManager.addPageProperty(MBSCustomPageManager.PAGE_ID, MBSCustomPageManager.NATURE, natures[0]);
		else {
			Set x = new TreeSet();
			for (int i=0; i<natures.length; i++) x.add(natures[i]);
			MBSCustomPageManager.addPageProperty(MBSCustomPageManager.PAGE_ID, MBSCustomPageManager.NATURE, x);
		}
		
		// load all custom pages specified via extensions
		try	{
			MBSCustomPageManager.loadExtensions();
		} catch (BuildException e) { e.printStackTrace(); }
		
		IWizardPage[] customPages = MBSCustomPageManager.getCustomPages();
		if (customPages != null) {
			for (int k = 0; k < customPages.length; k++) {
				addPage(customPages[k]);
			}
		}
	}

	/**
	 * @return true if user has changed settings since project creation
	 */
	private boolean isChanged() {
		if (savedHandler != fMainPage.h_selected || !fMainPage.getProjectName().equals(lastProjectName))
			return true;
		IToolChain[] tcs = fMainPage.h_selected.getSelectedToolChains();
		if (savedToolChains.length != tcs.length) 
			return true;
		for (int i=0; i<savedToolChains.length; i++) {
			boolean found = false;
			for (int j=0; j<tcs.length; j++) {
				if (savedToolChains[i] == tcs[j]) {
					found = true; break;
				}
			}
			if (!found) return true;
		}
		return false;
	}
	
	public IProject getProject(boolean defaults) {
		if (newProject != null && isChanged()) 
			clearProject(); 
		if (newProject == null)	{
			savedDefaults = defaults;
			savedHandler = fMainPage.h_selected;
			savedToolChains = fMainPage.h_selected.getSelectedToolChains();
			lastProjectName = fMainPage.getProjectName();
			// start creation process
			invokeRunnable(getRunnable(defaults)); 
		} 
		return newProject;
	}

	/**
	 * Remove created project either after error
	 * or if user returned back from config page. 
	 */
	private void clearProject() {
		if (lastProjectName == null) return;
		try {
			ResourcesPlugin.getWorkspace().getRoot().getProject(lastProjectName).delete(true, true, null);
		} catch (CoreException ignore) {}
		newProject = null;
		lastProjectName = null;
	}
	
	private boolean invokeRunnable(IRunnableWithProgress runnable) {
		IRunnableWithProgress op= new WorkspaceModifyDelegatingOperation(runnable);
		try {
			getContainer().run(true, true, op);
		} catch (InvocationTargetException e) {
			CUIPlugin.errorDialog(getShell(), title, message, e.getTargetException(), false);
			clearProject();
			return false;
		} catch  (InterruptedException e) {
			return false;
		}
		return true;
	}

	public boolean performFinish() {
		// needs delete only if project was 
		// created before and still valid
		boolean needsDelete = (newProject != null && savedDefaults && !isChanged());
		// create project if it is not created yet
		if (getProject(fMainPage.isCurrent()) == null) return false;
		// process custom pages
		doCustom();
		// project is created with all possible configs
		// to let user modify all of them. Resulting
		// project should contain only selected cfgs.
		if (needsDelete) deleteExtraConfigs();
		
		BasicNewProjectResourceWizard.updatePerspective(fConfigElement);
		selectAndReveal(newProject);
		return true;
	}
	
    public boolean performCancel() {
    	clearProject();
        return true;
    }


	private void deleteExtraConfigs() {
		if (fMainPage.isCurrent()) return; // nothing to delete
		ICProjectDescription prjd = CoreModel.getDefault().getProjectDescription(newProject, true);
		if (prjd == null) return;
		ICConfigurationDescription[] all = prjd.getConfigurations();
		if (all == null) return;
		CfgHolder[] req = fConfigPage.getCfgItems(false);
		boolean modified = false;
		for (int i=0; i<all.length; i++) {
			boolean found = false;
			for (int j=0; j<req.length; j++) {
				if (all[i].getName().equals(req[j].name)) {
					found = true; break;
				}
			}
			if (!found) {
				modified = true;
				prjd.removeConfiguration(all[i]);
			}
		}
		if (modified) try {
			CoreModel.getDefault().setProjectDescription(newProject, prjd);
		} catch (CoreException e) {}
	}
	
	private void doCustom() {
		IRunnableWithProgress[] operations = MBSCustomPageManager.getOperations();
		if(operations != null)
			for(int k = 0; k < operations.length; k++)
				try {
					getContainer().run(false, true, operations[k]);
				} catch (InvocationTargetException e) {
					ManagedBuilderUIPlugin.log(e);
				} catch (InterruptedException e) {
					ManagedBuilderUIPlugin.log(e);
				}
	}
	
	public void setInitializationData(IConfigurationElement config, String propertyName, Object data) throws CoreException {
		fConfigElement= config;
	}

	private IRunnableWithProgress getRunnable(boolean _defaults) {
		final boolean defaults = _defaults;
		return new IRunnableWithProgress() {
			public void run(IProgressMonitor imonitor) throws InvocationTargetException, InterruptedException {
				getShell().getDisplay().syncExec(new Runnable() {
					public void run() { 
						try {
							newProject = createIProject(lastProjectName, fMainPage.getProjectLocation());
							if (newProject != null) 
								fMainPage.h_selected.createProject(newProject, fConfigPage.getCfgItems(defaults));
						} catch (CoreException e) {	ManagedBuilderUIPlugin.log(e); }
					}
				});
			}
		};
	}

	/**
	 * 
	 */	
	public IProject createIProject(final String name, final IPath location) throws CoreException{
		if (newProject != null)	return newProject;

		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		IWorkspaceRoot root = workspace.getRoot();
		final IProject newProjectHandle = root.getProject(name);
		
		if (!newProjectHandle.exists()) {
			IWorkspaceDescription workspaceDesc = workspace.getDescription();
			workspaceDesc.setAutoBuilding(false);
			workspace.setDescription(workspaceDesc);
			IProjectDescription description = workspace.newProjectDescription(newProjectHandle.getName());
			if(location != null)
				description.setLocation(location);
			newProject = CCorePlugin.getDefault().createCDTProject(description, newProjectHandle, new NullProgressMonitor());
		} else {
			IWorkspaceRunnable runnable = new IWorkspaceRunnable() {
				public void run(IProgressMonitor monitor) throws CoreException {
					newProjectHandle.refreshLocal(IResource.DEPTH_INFINITE, monitor);
				}
			};
			NullProgressMonitor monitor = new NullProgressMonitor();
			workspace.run(runnable, root, IWorkspace.AVOID_UPDATE, monitor);
			newProject = newProjectHandle;
		}
        
		// Open the project if we have to
		if (!newProject.isOpen()) {
			newProject.open(new NullProgressMonitor());
		}
		return continueCreation(newProject);	
	}
	
	protected abstract IProject continueCreation(IProject prj); 
	protected abstract String[] getNatures();
}
