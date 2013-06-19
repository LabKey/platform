/*
 * Copyright (c) 2007-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.api.data;

import org.junit.Assert;
import org.junit.Test;

import java.text.FieldPosition;
import java.text.Format;
import java.text.ParseException;
import java.text.ParsePosition;
import java.util.HashMap;

/**
 * BooleanFormat implements java.text.Format for Boolean values. It allows one to specify a format
 * string, which controls the values used for true, false, and null.
 * <p>
 * The format string is expressed in the form <code>positive;negative;null</code>,
 * where "positive" is the string to display when true, "negative" is the string
 * to display when false, and "null" is the string to display when null.
 * <p>
 * This class also implements the parse side of java.text.Format, which allows you to parse
 * Booleans from a string containing one or more tokens. The list of known string to boolean
 * associations is stored in a static HashMap.
 * <p>
 * If all you need to do is parse, use the static <code>getInstance()</code> method to get
 * a reference to the single default instance. However, if you want to format Booleans using
 * a format string, create a new instance, passing the format string to the constructor.
 *
 * User: DaveS
 * Date: Oct 5, 2007
 * Time: 4:05:51 PM
 */
public class BooleanFormat extends Format
{
    public static final String DEFAULT_TRUE = "true";
    public static final String DEFAULT_FALSE = "false";
    public static final String DEFAULT_NULL = "";
    public static final String DEFAULT_PATTERN = DEFAULT_TRUE + ";" + DEFAULT_FALSE + ";" + DEFAULT_NULL;

    /**
     * Static map of common parse associations
     */
    private static HashMap<String, Boolean> _parseAssocs = new HashMap<>();

    static
    {
        _parseAssocs.put("true", Boolean.TRUE);
        _parseAssocs.put("false", Boolean.FALSE);
        _parseAssocs.put("1", Boolean.TRUE);
        _parseAssocs.put("0", Boolean.FALSE);
        _parseAssocs.put("yes", Boolean.TRUE);
        _parseAssocs.put("no", Boolean.FALSE);
        _parseAssocs.put("on", Boolean.TRUE);
        _parseAssocs.put("off", Boolean.FALSE);
        _parseAssocs.put("y", Boolean.TRUE);
        _parseAssocs.put("n", Boolean.FALSE);
        _parseAssocs.put("t", Boolean.TRUE);
        _parseAssocs.put("f", Boolean.FALSE);
    }

    //The current true, false, and null strings set by the format string
    protected String _true;
    protected String _false;
    protected String _null;

    private static BooleanFormat _definstance = new BooleanFormat();


    /**
     * Returns a singleton default instance which is most useful for parsing.
     * @return The singleton default instance
     */
    public static BooleanFormat getInstance()
    {
        return _definstance;
    }

    /**
     * Constructs the object using the default format pattern
     */
    public BooleanFormat()
    {
        setPattern(DEFAULT_PATTERN);
    }

    /**
     * Constructs the object using a particular format pattern
     * @param pattern The patter to set
     */
    public BooleanFormat(String pattern)
    {
        setPattern(pattern);
    }

    /**
     * Returns the format pattern for this instance
     * @return The format pattern
     */
    public String getPattern()
    {
        return _true + ";" + _false + ";" + _null;
    }

    /**
     * Sets the format pattern for the instance. This is kept protected so that the
     * instances are immutable, allowing for a global map keyed on the format pattern
     * @param pattern The pattern to set
     */
    protected void setPattern(String pattern)
    {
        //split into parts, can have just one part
        String[] parts = pattern.split(";");
        if(parts.length > 0)
            _true = parts[0].trim();
        else
            _true = DEFAULT_TRUE;
        if(parts.length > 1)
            _false = parts[1].trim();
        else
            _false = DEFAULT_FALSE;
        if(parts.length > 2)
            _null = parts[2].trim();
        else
            _null = DEFAULT_NULL;
    }

    /**
     * Formats a Boolean according to the instance's format pattern. This is most commonly
     * called from the base class <code>format(Object obj)</code> method.
     * @param obj           The object to format (must be Boolean or derived)
     * @param toAppendTo    String to append to
     * @param pos           Position to append at
     * @return              The string buffer passed in
     */
    public StringBuffer format(Object obj, StringBuffer toAppendTo, FieldPosition pos)
    {
        assert null != toAppendTo;

        if(null == obj)
            return toAppendTo.append(_null);

        //if the passed object is not a Boolean, don't try to format it
        //Fields are not necessary in this implementation, so pos is ignored
        if(obj instanceof Boolean)
            return toAppendTo.append(((Boolean)obj).booleanValue() ? _true : _false);
        else
            return toAppendTo;
    }

    /**
     * This method may be used to test if the <code>source</code> parameter should be
     * interpreted as a null value. It returns true if source is null, zero-length,
     * or equal (ignoring case) to "null".
     * <p>
     * This method exists because the java.text.Format architecture assumes
     * that the <code>parseObject()</code> method will always return a valid object
     * or generate a <code>ParseException</code>. Therefore, if the source object
     * can be interpreted as null, use this method first to test for that, and only
     * call <code>parseObject()</code> if this method returns false.
     *
     * @param source String to test
     * @return True if <code>source</code> should be interpreted as null.
     */
    public static boolean testForNull(String source)
    {
        return (null == source || source.length() == 0 || source.equalsIgnoreCase("null"));
    }

    /**
     * Parses a string into a corresponding Boolean.
     *
     * @param source The string to parse
     * @return A parsed Boolean
     * @throws ParseException Thrown if the source cannot be parsed as a Boolean
     */
    public Boolean parseObject(String source) throws ParseException
    {
        return (Boolean)(super.parseObject(source));
    }

    /**
     * Parses a string into a corresponding Boolean. If the source string cannot be
     * interpreted as a Boolean, this method will set the pos error index and return
     * null. The base class will then throw a ParseException.
     *
     * @param source The string to parse
     * @param pos   The position at which to start
     * @return      A corresponding Boolean for source or null
     */
    public Boolean parseObject(String source, ParsePosition pos)
    {
        if(null == source || source.length() == 0 || !Character.isLetterOrDigit(source.codePointAt(0)))
        {
            pos.setErrorIndex(pos.getIndex());
            return null;
        }

        //start at the parse position and find the end of the token
        //(defined by the first non-letter-or-digit)
        int idxEnd = pos.getIndex();
        while(idxEnd < source.length() && Character.isLetterOrDigit(source.codePointAt(idxEnd)))
            ++idxEnd;

        //extract the substring and lower-case it
        String key = source.substring(pos.getIndex(), idxEnd).toLowerCase();

        //find it in the map
        Boolean ret = _parseAssocs.get(key);

        //if not found, check instance format string values
        if(null == ret)
        {
            if(_true.equalsIgnoreCase(key))
                ret = Boolean.TRUE;
            if(_false.equalsIgnoreCase(key))
                ret = Boolean.FALSE;
        }

        //update the parse position if found
        if(null != ret)
            pos.setIndex(idxEnd);
        else
            pos.setErrorIndex(pos.getIndex());
        
        return ret;
    }

    //JUnit Test Case
    public static class TestCase extends Assert
    {
        @Test
        public void testFormat()
        {
            //default
            BooleanFormat fmt = new BooleanFormat();
            assertTrue(fmt.format(Boolean.TRUE).equals(BooleanFormat.DEFAULT_TRUE));
            assertTrue(fmt.format(Boolean.FALSE).equals(BooleanFormat.DEFAULT_FALSE));
            assertTrue(fmt.format(null).equals(BooleanFormat.DEFAULT_NULL));

            //various format strings
            fmt = new BooleanFormat("Y;N;(null)");
            assertTrue(fmt.format(Boolean.TRUE).equals("Y"));
            assertTrue(fmt.format(Boolean.FALSE).equals("N"));
            assertTrue(fmt.format(null).equals("(null)"));

            fmt = new BooleanFormat("Y;N; ");
            assertTrue(fmt.format(Boolean.TRUE).equals("Y"));
            assertTrue(fmt.format(Boolean.FALSE).equals("N"));
            assertTrue(fmt.format(null).equals(""));

            fmt = new BooleanFormat("Yes;No");
            assertTrue(fmt.format(Boolean.TRUE).equals("Yes"));
            assertTrue(fmt.format(Boolean.FALSE).equals("No"));
            assertTrue(fmt.format(null).equals(BooleanFormat.DEFAULT_NULL));

            fmt = new BooleanFormat("Yea");
            assertTrue(fmt.format(Boolean.TRUE).equals("Yea"));
            assertTrue(fmt.format(Boolean.FALSE).equals(BooleanFormat.DEFAULT_FALSE));
            assertTrue(fmt.format(null).equals(BooleanFormat.DEFAULT_NULL));
        }

        @Test
        public void testParse() throws ParseException
        {
            BooleanFormat fmt = BooleanFormat.getInstance();
            assertTrue(((fmt.parseObject("true"))).booleanValue());
            assertTrue(((fmt.parseObject("True"))).booleanValue());
            assertTrue(((fmt.parseObject("y"))).booleanValue());
            assertTrue(((fmt.parseObject("Yes"))).booleanValue());
            assertTrue(((fmt.parseObject("1"))).booleanValue());

            assertFalse(((fmt.parseObject("false"))).booleanValue());
            assertFalse(((fmt.parseObject("FALSE"))).booleanValue());
            assertFalse(((fmt.parseObject("0"))).booleanValue());
            assertFalse(((fmt.parseObject("no"))).booleanValue());

            assertTrue(BooleanFormat.testForNull(null));
            assertTrue(BooleanFormat.testForNull(""));
            assertTrue(BooleanFormat.testForNull("null"));

            fmt = new BooleanFormat("X;Z");
            assertTrue(fmt.parseObject("X").booleanValue());
            assertFalse(fmt.parseObject("Z").booleanValue());

            try
            {
                fmt.parseObject("H");
                TestCase.fail("Parsing of 'H' should have generated an exception!");
            }
            catch(ParseException ignore) {}
        }
    }
} //class BooleanFormat
