/**********************************************************************
 * Copyright (c) 2002,2003 Rational Software Corporation and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Common Public License v0.5
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v05.html
 * 
 * Contributors: 
 * IBM Rational Software - Initial API and implementation
***********************************************************************/
package org.eclipse.cdt.core.parser;

import java.io.Reader;
import java.util.Map;
import java.util.List;

import org.eclipse.cdt.core.parser.ast.IASTFactory;
import org.eclipse.cdt.internal.core.parser.NullSourceElementRequestor;
import org.eclipse.cdt.internal.core.parser.Parser;
import org.eclipse.cdt.internal.core.parser.Preprocessor;
import org.eclipse.cdt.internal.core.parser.Scanner;
import org.eclipse.cdt.internal.core.parser.ast.full.FullParseASTFactory;
import org.eclipse.cdt.internal.core.parser.ast.quick.QuickParseASTFactory;

/**
 * @author jcamelon
 *
 */
public class ParserFactory {

	public static IASTFactory createASTFactory( ParserMode mode )
	{
		if( mode == ParserMode.QUICK_PARSE )
			return new QuickParseASTFactory(); 
		else
			return new FullParseASTFactory(); 
	}
	
	public static IParser createParser( IScanner scanner, IParserCallback callback, ParserMode mode )
	{
		ParserMode ourMode = ( (mode == null )? ParserMode.COMPLETE_PARSE : mode ); 
		IParserCallback ourCallback = (( callback == null) ? new NullSourceElementRequestor() : callback );   
		return new Parser( scanner, ourCallback, ourMode );
	}
	
	public static IScanner createScanner( Reader input, String fileName, Map defns, List inclusions, ParserMode mode ) 
	{
		ParserMode ourMode = ( (mode == null )? ParserMode.COMPLETE_PARSE : mode ); 
		IScanner s = new Scanner( input, fileName, defns );
		s.setMode( ourMode ); 
		s.overwriteIncludePath(inclusions);
		return s; 
	}
	
	public static IPreprocessor createPreprocesor( Reader input, String fileName, Map defns, List inclusions, ParserMode mode )
	{
		ParserMode ourMode = ( (mode == null )? ParserMode.COMPLETE_PARSE : mode ); 
		IPreprocessor s = new Preprocessor( input, fileName, defns );
		s.setMode( ourMode );
		s.overwriteIncludePath(inclusions); 
		return s; 
	}
}
