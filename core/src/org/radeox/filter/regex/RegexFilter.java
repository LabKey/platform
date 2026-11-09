/*
 * This file is part of "SnipSnap Radeox Rendering Engine".
 *
 * Copyright (c) 2002 Stephan J. Schmidt, Matthias L. Jugel
 * All Rights Reserved.
 *
 * Please visit http://radeox.org/ for updates and contact.
 *
 * --LICENSE NOTICE--
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 * --LICENSE NOTICE--
 */

package org.radeox.filter.regex;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.junit.Test;
import org.radeox.filter.FilterSupport;
import org.radeox.filter.context.FilterContext;
import org.radeox.regex.Compiler;
import org.radeox.regex.Matcher;
import org.radeox.regex.Pattern;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/*
 * Class that stores regular expressions, can be subclassed
 * for special Filters
 *
 * @author stephan
 * @team sonicteam
 * @version $Id: RegexFilter.java,v 1.11 2004/04/15 13:56:14 stephan Exp $
 */

public abstract class RegexFilter extends FilterSupport
{
    private static final Logger log = LogManager.getLogger(RegexFilter.class);

    /**
     * Regular expression pattern for matching quoted strings.
     * Matches text enclosed in double quotes while ensuring proper escaped quote handling.
     */
    public static final String QUOTE_REGEX = "\"([^\"\\\\]*(?:\\.[^\"\\\\]*)*)\"";
    protected List<Pattern> pattern = new ArrayList<>();
    protected List<String> substitute = new ArrayList<>();

    public final static boolean SINGLELINE = false;
    public final static boolean MULTILINE = true;

    public RegexFilter()
    {
        super();
    }

    /**
     * create a new regular expression that takes input as multiple lines
     */
    public RegexFilter(String regex, String substitute)
    {
        this();
        addRegex(regex, substitute);
    }

    /**
     * create a new regular expression and set
     */
    public RegexFilter(String regex, String substitute, boolean multiline)
    {
        addRegex(regex, substitute, multiline);
    }

    public void clearRegex()
    {
        pattern.clear();
        substitute.clear();
    }

    public void addRegex(String regex, String substitute)
    {
        addRegex(regex, substitute, MULTILINE);
    }

    public void addRegex(String regex, String substitute, boolean multiline)
    {
        try
        {
            Compiler compiler = Compiler.create();
            compiler.setMultiline(multiline);
            this.pattern.add(compiler.compile(regex));
            // Pattern.DOTALL
            this.substitute.add(substitute);
        }
        catch (Exception e)
        {
            log.warn("bad pattern: {} -> {}", regex, substitute, e);
        }
    }

    @Override
    public abstract String filter(String input, FilterContext context);

    public static class TestCase
    {
        Pattern pattern = Compiler.create().compile(QUOTE_REGEX);

        @Test
        public void testBasicQuotedString()
        {
            assertTrue(Matcher.create("\"Hello World\"", pattern).matches());
            assertFalse(Matcher.create("Hello World", pattern).matches());
            assertFalse(Matcher.create("\"Unclosed quote", pattern).matches());
        }

        @Test
        public void testWithWhiteSpaceCharacters()
        {
            assertTrue(Matcher.create("\"Hello\nWorld\"", pattern).matches());
            assertTrue(Matcher.create("\"Hello\tWorld\"", pattern).matches());
            assertTrue(Matcher.create("\"Hello\n\rWorld\"", pattern).matches());
        }

        @Test
        public void testEscapedQuotes()
        {
            assertFalse(Matcher.create("\"String with \\\"escaped quotes\\\"\"", pattern).matches());
            assertFalse(Matcher.create("\"He said: \\\"Hello\\\"\"", pattern).matches());
            assertFalse(Matcher.create("\"Hello\\World\"", pattern).matches());
        }

        @Test
        public void testEscapedCharacters()
        {
            assertFalse(Matcher.create("\"Line1\\nLine2\"", pattern).matches());
            assertFalse(Matcher.create("\"Tab\\there\"", pattern).matches());
            assertFalse(Matcher.create("\"Backslash\\\\test\"", pattern).matches());
        }

        @Test
        public void testInvalidPatterns()
        {
            assertFalse(Matcher.create("\"Missing end", pattern).matches());
            assertFalse(Matcher.create("Missing start\"", pattern).matches());
            assertFalse(Matcher.create("\"Nested \"quotes\"\"", pattern).matches());
        }
    }
}
