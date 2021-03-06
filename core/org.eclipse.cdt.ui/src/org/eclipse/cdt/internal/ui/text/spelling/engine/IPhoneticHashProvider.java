/*******************************************************************************
 * Copyright (c) 2000, 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Sergey Prigogin (Google)
 *******************************************************************************/

package org.eclipse.cdt.internal.ui.text.spelling.engine;

/**
 * Interface of hashers to compute the phonetic hash for a word.
 */
public interface IPhoneticHashProvider {

	/**
	 * Returns the phonetic hash for the word.
	 *
	 * @param word
	 *                  The word to get the phonetic hash for
	 * @return The phonetic hash for the word
	 */
	public String getHash(String word);

	/**
	 * Returns an array of characters to compute possible mutations.
	 *
	 * @return Array of possible mutator characters
	 */
	public char[] getMutators();
}
