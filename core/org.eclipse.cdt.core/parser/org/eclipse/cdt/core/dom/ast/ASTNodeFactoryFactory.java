/*******************************************************************************
 * Copyright (c) 2009 Wind River Systems, Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Markus Schorn - initial API and implementation
 *******************************************************************************/ 
package org.eclipse.cdt.core.dom.ast;

import org.eclipse.cdt.core.dom.ast.c.ICNodeFactory;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPNodeFactory;
import org.eclipse.cdt.internal.core.dom.parser.c.CNodeFactory;
import org.eclipse.cdt.internal.core.dom.parser.cpp.CPPNodeFactory;

/**
 * Provides access to the node factories.
 * 
 * @noextend This class is not intended to be subclassed by clients.
 * @noinstantiate This class is not intended to be instantiated by clients.
 * @since 5.2
 */
public class ASTNodeFactoryFactory {

	ASTNodeFactoryFactory() {}

	public static ICNodeFactory getDefaultCNodeFactory() {
		return CNodeFactory.getDefault();
	}

	public static ICPPNodeFactory getDefaultCPPNodeFactory() {
		return CPPNodeFactory.getDefault();
	}
}
