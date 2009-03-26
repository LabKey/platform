/*
 * Copyright (c) 2009 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.apache.commons.beanutils.ConversionException;

import java.util.regex.Pattern;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Feb 9, 2009
 * Time: 10:40:19 AM
 */
public class IdentifierString extends HString
{
     Pattern idPattern = Pattern.compile("[\\p{Alpha}_][\\p{Alnum}_]*");

    public IdentifierString(String s)
    {
        _source = s;
        _tainted = _source.length() != 0 && !idPattern.matcher(s).matches();
    }

	public IdentifierString(HString s)
	{
		this(null == s ? "" : s._source);
	}

	public IdentifierString(String s, boolean t)
	{
		_source = s;
		_tainted = _source.length() != 0 && t;
	}

    public IdentifierString(Container c)
    {
        _source = c.getId();
        _tainted = false;
    }

	public String toString()
    {
        return isTainted() ? "" : _source;
    }

    @Override
    public boolean equals(Object o)
    {
        return (o instanceof IdentifierString) && _source.equalsIgnoreCase(((IdentifierString)o)._source);
    }

    @Override
    public int compareTo(HString str)
    {
        return super.compareToIgnoreCase(str);
    }


    public static class Converter implements org.apache.commons.beanutils.Converter
    {
        public Object convert(Class type, Object value)
        {
            if (value == null)
                return null;
            if (value instanceof IdentifierString)
                return value;
            if (value instanceof HString)
                value = ((HString)value).getSource();
            IdentifierString g = new IdentifierString(String.valueOf(value));
            if (g.isTainted())
                throw new ConversionException("Invalid identifier");
            return g;
        }
    }
}