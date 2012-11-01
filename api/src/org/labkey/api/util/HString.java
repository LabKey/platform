/*
 * Copyright (c) 2009-2012 LabKey Corporation
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
package org.labkey.api.util;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.view.ViewServlet;

import java.util.Locale;

/**
 * User: Matthew
 * Date: Feb 6, 2009
 * Time: 11:42:52 AM
 *
 * HString is short for HttpString (other suggestions FormString, TaintedSting, ValidatedString)
 *
 * HSting should act as much as possible like a regular java.lang.String.  It has two differences.
 * It implements Taintable, and if tainted it will try to hide its dirty data (depending on the subclass).
 *
 * The HString base class will html encode (PageFlowUtil.filter()) tainted data.  Subclasses may just return "".
 */

public class HString implements java.io.Serializable, Comparable<HString>, CharSequence, Taintable
{
    public static HString EMPTY = new HString("",false)
    {
        @Override
        public boolean isEmpty()
        {
            return true;
        }

        @Override
        public int length()
        {
            return 0;
        }
    };
    
    static final boolean _strict = false;
   
    protected String _source;
    protected String _safe;
    boolean _tainted = true;


    public HString(CharSequence s)
    {
        this(s, s instanceof Taintable && ((Taintable)s).isTainted());
    }

    
    public HString(CharSequence s, boolean tainted)
    {
        _source = null == s ? "" : s.toString();
        _tainted = tainted;
    }

    public HString(HString s, boolean tainted)
    {
        _source = null == s ? "" : s._source;
        _tainted = tainted;
    }

    protected HString(String s)
    {
        _source = s;
        _tainted = _strict;
    }

    
    protected HString(Object o, boolean tainted)
    {
        if (o instanceof HString)
            _source = ((HString)o).getSource();
        else if (o instanceof HStringBuilder)
            _source = ((HStringBuilder)o).getSource();
        else
            _source = o.toString();
        _tainted = tainted;
    }


    public HString(CharSequence... strs)
    {
        HStringBuilder sb = new HStringBuilder();
        for (CharSequence str : strs)
            sb.append(str);
        this._tainted = sb.isTainted();
        this._source = sb.getSource();
    }


    // you can override toString() to change the "safe" encoding for tainted strings
    public String toString()
    {
        if (!_tainted)
            return _source;
        if (null == _safe)
            _safe = PageFlowUtil.filter(_source);
        return _safe;
    }


    // only supports String and HString at the moment
    public static boolean eq(CharSequence a, CharSequence b)
    {
        if (a == null || b == null)
            return a == b;
        if (a instanceof HString)
            a = ((HString)a).getSource();
        if (b instanceof HString)
            b = ((HString)b).getSource();
        return a.equals(b);
    }

    public boolean equals(Object o)
    {
        // don't compare _tainted, we want to be as string like as possible
        if (o instanceof HString &&  _source.equals(((HString)o)._source))
            return true;
        return _source.equals(o);
    }


    public boolean isTainted()
    {
        return length() > 0 && _tainted;
    }


    public String getSource()
    {
        return _source;
    }


    public static String source(CharSequence s)
    {
        if (null == s)
            return null;
        if (s instanceof HString)
            return ((HString)s)._source;
        return s.toString();
    }


    private static boolean _tainted(Object s)
    {
        // if strict then assume all non-HString is tainted
        return s instanceof Taintable ? ((Taintable)s).isTainted() : _strict;
    }

    public static HString[] array(boolean tainted, String... strs)
    {
        HString[] h = new HString[strs.length];
        for (int i=0 ; i<strs.length ; i++)
            h[i] = new HString(strs[i],tainted);
        return h;
    }
    
    //
    //  StringUtils
    //


    public HString trimToEmpty()
    {
        return trim();
    }

    public int parseInt()
    {
        return Integer.parseInt(_source);
    }

    public long parseLong()
    {
        return Long.parseLong(_source);
    }


    public double parseDouble()
    {
        return Double.parseDouble(_source);
    }


    public HString[] split(char ch)
    {
        String[] a = StringUtils.split(_source, ch);
        HString[] h = new HString[a.length];
        for (int i=0 ; i<a.length ; i++)
            h[i] = new HString(a[i],_tainted);
        return h;
    }


    public static HString join(HString[] a, char ch)
    {
        HStringBuilder sb = new HStringBuilder();
        for (int i=0 ; i<a.length ; i++)
        {
            if (i>0)
                sb.append(ch);
            if (null != a[i])
                sb.append(a[i]);
        }
        return sb.toHString();
    }


    //
    //  for ConvertHelper
    //


    public static class Converter implements org.apache.commons.beanutils.Converter
    {
		private static ConvertHelper.DateFriendlyStringConverter _impl = new ConvertHelper.DateFriendlyStringConverter();
		
        public Object convert(Class type, Object value)
        {
            if (value == null)
                return HString.EMPTY;
            if (value instanceof HString)
                return value;
            // Converter is always strict
            String s = (String)_impl.convert(String.class, value);
            validateChars(s);
            return new HString(s, true);
        }
    }


    //
    // delegation
    //

    public int length()
    {
        return _source.length();
    }

    public boolean isEmpty()
    {
        return _source.length() == 0;
    }

    public char charAt(int index)
    {
        return _source.charAt(index);
    }

//    public int codePointAt(int index)
//    {
//        return toString().codePointAt(index);
//    }

//    public int codePointBefore(int index)
//    {
//        return toString().codePointBefore(index);
//    }

//    public int codePointCount(int beginIndex, int endIndex)
//    {
//        return toString().codePointCount(beginIndex, endIndex);
//    }

//    public int offsetByCodePoints(int index, int codePointOffset)
//    {
//        return toString().offsetByCodePoints(index, codePointOffset);
//    }

//    void getChars(char[] dst, int dstBegin)
//    {
//        toString().getChars(0, length(), dst, dstBegin);
//    }

//    public void getChars(int srcBegin, int srcEnd, char[] dst, int dstBegin)
//    {
//        toString().getChars(srcBegin, srcEnd, dst, dstBegin);
//    }

//    @Deprecated
//    public void getBytes(int srcBegin, int srcEnd, byte[] dst, int dstBegin)
//    {
//        toString().getBytes(srcBegin, srcEnd, dst, dstBegin);
//    }

//    public byte[] getBytes(String charsetName)
//            throws UnsupportedEncodingException
//    {
//        return _source.getBytes(charsetName);
//    }

//    public byte[] getBytes(Charset charset)
//    {
//        return toString().getBytes(charset);
//    }

//    public byte[] getBytes()
//    {
//        return toString().getBytes();
//    }

    public boolean equalsIgnoreCase(String anotherString)
    {
        return _source.equalsIgnoreCase(anotherString);
    }

    public boolean equalsIgnoreCase(HString anotherString)
    {
        return _source.equalsIgnoreCase(anotherString._source);
    }

    public int compareTo(HString str)
    {
        return _source.compareTo(str._source);
    }

    public int compareToIgnoreCase(HString str)
    {
        return _source.compareToIgnoreCase(str._source);
    }

    public boolean regionMatches(int toffset, String other, int ooffset, int len)
    {
        return _source.regionMatches(toffset, other, ooffset, len);
    }

    public boolean regionMatches(boolean ignoreCase, int toffset, String other, int ooffset, int len)
    {
        return _source.regionMatches(ignoreCase, toffset, other, ooffset, len);
    }

    public boolean startsWith(String prefix, int toffset)
    {
        return _source.startsWith(prefix, toffset);
    }

    public boolean startsWith(String prefix)
    {
        return _source.startsWith(prefix);
    }

    public boolean endsWith(String suffix)
    {
        return _source.endsWith(suffix);
    }

    public int hashCode()
    {
        return _source.hashCode();
    }

    public int indexOf(int ch)
    {
        return _source.indexOf(ch);
    }

    public int indexOf(int ch, int fromIndex)
    {
        return _source.indexOf(ch, fromIndex);
    }

    public int lastIndexOf(int ch)
    {
        return _source.lastIndexOf(ch);
    }

    public int lastIndexOf(int ch, int fromIndex)
    {
        return _source.lastIndexOf(ch, fromIndex);
    }

    public int indexOf(String str)
    {
        return _source.indexOf(str);
    }

    public int indexOf(String str, int fromIndex)
    {
        return _source.indexOf(str, fromIndex);
    }

    public int lastIndexOf(String str)
    {
        return _source.lastIndexOf(str);
    }

    public int lastIndexOf(String str, int fromIndex)
    {
        return _source.lastIndexOf(str, fromIndex);
    }

    public HString substring(int beginIndex)
    {
        return new HString(_source.substring(beginIndex));
    }

    public HString substring(int beginIndex, int endIndex)
    {
        return new HString(_source.substring(beginIndex, endIndex));
    }

    public CharSequence subSequence(int beginIndex, int endIndex)
    {
        return _source.subSequence(beginIndex, endIndex);
    }

    public HString concat(CharSequence str)
    {
        return new HString(this, str);
    }

    public HString replace(char oldChar, char newChar)
    {
        return new HString(_source.replace(oldChar, newChar));
    }

    public boolean matches(String regex)
    {
        return _source.matches(regex);
    }

    public boolean contains(CharSequence s)
    {
        return _source.contains(s);
    }

    public HString replaceFirst(String regex, String replacement)
    {
        return new HString(_source.replaceFirst(regex, replacement), _tainted || _tainted(replacement));
    }

    public HString replaceAll(String regex, HString replacement)
    {
        String r = _source.replaceAll(regex, replacement.getSource());
        return new HString(r, _tainted || _tainted(replacement));
    }

    public HString replace(CharSequence target, CharSequence replacement)
    {
        String r = _source.replace(target, replacement);
        return new HString(r, _tainted || _tainted(replacement));
    }

    public HString[] split(String regex, int limit)
    {
        String[] a = _source.split(regex, limit);
        return array(_tainted, a);
    }

    public HString[] split(String regex)
    {
        String[] a = _source.split(regex);
        return array(_tainted, a);
    }

    public HString toLowerCase(Locale locale)
    {
        return new HString(_source.toLowerCase(locale), _tainted);
    }

    public HString toLowerCase()
    {
        return new HString(_source.toLowerCase(), _tainted);
    }

    public HString toUpperCase(Locale locale)
    {
        return new HString(_source.toUpperCase(locale), _tainted);
    }

    public HString toUpperCase()
    {
        return new HString(_source.toUpperCase(), _tainted);
    }

    public HString trim()
    {
        String s = _source.trim();
        if (s.length() == 0)
            return EMPTY;
        if (s.length() == _source.length())
            return this;
        return new HString(s, _tainted);
    }

//    public char[] toCharArray()
//    {
//        return toString().toCharArray();
//    }

    //
    // static
    //

    public static HString valueOf(Integer i)
    {
        return null == i ? EMPTY : new HString(String.valueOf(i),false);
    }

    public static HString valueOf(int i)
    {
        return new HString(String.valueOf(i),false);
    }

    public static HString valueOf(double d)
    {
        return new HString(String.valueOf(d),false);
    }

    public static HString valueOf(boolean b)
    {
        return new HString(String.valueOf(b),false);
    }

    public static HString format(String format, Object... args)
    {
        String r = String.format(format, args);
        boolean tainted = false;
        for (Object o : args)
            tainted |= _tainted(o);
        return new HString(r,tainted);
    }

    public static HString format(Locale l, String format, Object... args)
    {
        String r = String.format(l, format, args);
        boolean tainted = false;
        for (Object o : args)
            tainted |= _tainted(o);
        return new HString(r,tainted);
    }


    protected static void validateChars(CharSequence s)
    {
        if (!ViewServlet.validChars(s))
            throw new ConversionException("Invalid characters in string");
    }


    public static class TestCase extends Assert
    {
        @Test
        public void testConverter()
        {
            HString t = (HString)new Converter().convert(HString.class, "<script>alert('8(')</script>");
            assertTrue(t.isTainted());
            assertFalse(t.toString().contains("<script"));
            HStringBuilder sb = new HStringBuilder();
            assertFalse(sb.isTainted());
            sb.append("hi there");
            assertFalse(sb.isTainted());
            sb.append(t);
            assertTrue(sb.isTainted());
            assertFalse(sb.toString().contains("<script"));
            assertFalse(PageFlowUtil.filter(t).isTainted());
        }

        @Test
        public void test()
        {
            // HString
            HString t = new HString("<script>alert('8(')</script>", true);
            assertTrue(t.isTainted());
            assertFalse(t.toString().contains("<script"));
            HStringBuilder sb = new HStringBuilder();
            assertFalse(sb.isTainted());
            sb.append("hi there");
            assertFalse(sb.isTainted());
            sb.append(t);
            assertTrue(sb.isTainted());
            assertFalse(sb.toString().contains("<script"));
            assertFalse(PageFlowUtil.filter(t).isTainted());
        }
    }
}


