/*
 * Copyright (c) 2009-2013 LabKey Corporation
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

/**
 * User: Matthew
 * Date: Feb 6, 2009
 * Time: 12:51:31 PM
 */
public class HStringBuilder implements java.io.Serializable, CharSequence, Appendable, Taintable
{
    private static final boolean _strict = HString._strict;
    
    private final StringBuilder _sb = new StringBuilder();
    private boolean _tainted = false;


    public HStringBuilder()
    {
    }

    public HStringBuilder(CharSequence s)
    {
        append(s);
    }

    public HString toHString()
    {
        return new HString(_sb.toString(), _tainted);    
    }

    private void _taint(Object o)
    {
        _tainted |= o instanceof Taintable && ((Taintable)o).isTainted();
    }

    private void _taint(CharSequence o)
    {
        _tainted |= o instanceof Taintable ? ((Taintable)o).isTainted() : _strict;
    }

    private void _taint(char[] seq)
    {
        _tainted = _strict;
    }

    public boolean isTainted()
    {
        return _tainted;
    }

    public String getSource()
    {
        return _sb.toString();
    }
    
    public String toString()
    {
        return toHString().toString();
    }

    public HStringBuilder append(HString s)
    {
        _tainted |= s.isTainted();
        _sb.append(s.getSource());
        return this;
    }

    //
    // delegate methods
    //

    public int length()
    {
        return _sb.length();
    }

    public int capacity()
    {
        return _sb.capacity();
    }

    public void ensureCapacity(int minimumCapacity)
    {
        _sb.ensureCapacity(minimumCapacity);
    }

    public void trimToSize()
    {
        _sb.trimToSize();
    }

    public void setLength(int newLength)
    {
        _sb.setLength(newLength);
    }

    public char charAt(int index)
    {
        return _sb.charAt(index);
    }

    public int codePointAt(int index)
    {
        return _sb.codePointAt(index);
    }

    public int codePointBefore(int index)
    {
        return _sb.codePointBefore(index);
    }

    public int codePointCount(int beginIndex, int endIndex)
    {
        return _sb.codePointCount(beginIndex, endIndex);
    }

    public int offsetByCodePoints(int index, int codePointOffset)
    {
        return _sb.offsetByCodePoints(index, codePointOffset);
    }

    public void getChars(int srcBegin, int srcEnd, char[] dst, int dstBegin)
    {
        _sb.getChars(srcBegin, srcEnd, dst, dstBegin);
    }

    public void setCharAt(int index, char ch)
    {
        _sb.setCharAt(index, ch);
    }

    public String substring(int start)
    {
        return _sb.substring(start);
    }

    public CharSequence subSequence(int start, int end)
    {
        return _sb.subSequence(start, end);
    }

    public String substring(int start, int end)
    {
        return _sb.substring(start, end);
    }

    public HStringBuilder append(Object obj)
    {
        _taint(obj);
        _sb.append(obj);
        return this;
    }

    public HStringBuilder append(String str)
    {
        _taint(str);
        _sb.append(str);
        return this;
    }

    public HStringBuilder append(StringBuffer sb)
    {
        _taint(sb);
        _sb.append(sb);
        return this;
    }

    public HStringBuilder append(CharSequence s)
    {
        _taint(s);
        _sb.append(s);
        return this;
    }

    public HStringBuilder append(CharSequence s, int start, int end)
    {
        _taint(s);
        _sb.append(s, start, end);
        return this;
    }

    public HStringBuilder append(char[] str)
    {
        _taint(str);
        _sb.append(str);
        return this;
    }

    public HStringBuilder append(char[] str, int offset, int len)
    {
        _taint(str);
        _sb.append(str, offset, len);
        return this;
    }

    public HStringBuilder append(boolean b)
    {
        _sb.append(b);
        return this;
    }

    public HStringBuilder append(char c)
    {
        _sb.append(c);
        return this;
    }

    public HStringBuilder append(int i)
    {
        _sb.append(i);
        return this;
    }

    public HStringBuilder append(long lng)
    {
        _sb.append(lng);
        return this;
    }

    public HStringBuilder append(float f)
    {
        _sb.append(f);
        return this;
    }

    public HStringBuilder append(double d)
    {
        _sb.append(d);
        return this;
    }

    public HStringBuilder appendCodePoint(int codePoint)
    {
        _sb.appendCodePoint(codePoint);
        return this;
    }

    public HStringBuilder delete(int start, int end)
    {
        _sb.delete(start, end);
        return this;
    }

    public HStringBuilder deleteCharAt(int index)
    {
        _sb.deleteCharAt(index);
        return this;
    }

    public HStringBuilder replace(int start, int end, String str)
    {
        _taint(str);
        _sb.replace(start, end, str);
        return this;
    }

    public HStringBuilder insert(int index, char[] str, int offset, int len)
    {
        _taint(str);
        _sb.insert(index, str, offset, len);
        return this;
    }

    public HStringBuilder insert(int offset, Object obj)
    {
        _taint(obj);
        _sb.insert(offset, obj);
        return this;
    }

    public HStringBuilder insert(int offset, String str)
    {
        _taint(str);
        _sb.insert(offset, str);
        return this;
    }

    public HStringBuilder insert(int offset, char[] str)
    {
        _taint(str);
        _sb.insert(offset, str);
        return this;
    }

    public HStringBuilder insert(int dstOffset, CharSequence s)
    {
        _taint(s);
        _sb.insert(dstOffset, s);
        return this;
    }

    public HStringBuilder insert(int dstOffset, CharSequence s, int start, int end)
    {
        _taint(s);
        _sb.insert(dstOffset, s, start, end);
        return this;
    }

    public HStringBuilder insert(int offset, boolean b)
    {
        _sb.insert(offset, b);
        return this;
    }

    public HStringBuilder insert(int offset, char c)
    {
        _sb.insert(offset, c);
        return this;
    }

    public HStringBuilder insert(int offset, int i)
    {
        _sb.insert(offset, i);
        return this;
    }

    public HStringBuilder insert(int offset, long l)
    {
        _sb.insert(offset, l);
        return this;
    }

    public HStringBuilder insert(int offset, float f)
    {
        _sb.insert(offset, f);
        return this;
    }

    public HStringBuilder insert(int offset, double d)
    {
        _sb.insert(offset, d);
        return this;
    }

    public int indexOf(String str)
    {
        return _sb.indexOf(str);
    }

    public int indexOf(String str, int fromIndex)
    {
        return _sb.indexOf(str, fromIndex);
    }

    public int lastIndexOf(String str)
    {
        return _sb.lastIndexOf(str);
    }

    public int lastIndexOf(String str, int fromIndex)
    {
        return _sb.lastIndexOf(str, fromIndex);
    }

    public HStringBuilder reverse()
    {
        _sb.reverse();
        return this;
    }
}
