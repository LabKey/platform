/*
 * Copyright (c) 2009-2016 LabKey Corporation
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewServlet;


/**
 * User: matthewb
 * Date: Nov 3, 2009
 * Time: 12:22:55 PM
 */
public class ReturnURLString
{
    private final String _source;

    public static ReturnURLString EMPTY = new ReturnURLString("");

    
    public ReturnURLString(CharSequence s)
    {
        _source = null == s ? null : String.valueOf(s);
    }


    public boolean isEmpty()
    {
        if (StringUtils.isEmpty(_source))
            return true;
        return null == getURLHelper();
    }


    public String getSource()
    {
        return _source;
    }

    @Override @NotNull
    public String toString()
    {
        try
        {
            if (StringUtils.isEmpty(_source))
                return "";
            new ActionURL(getSource());
            return _source;
        }
        catch (Exception x)
        {
            return "";
        }
    }


    @Nullable
    public ActionURL getActionURL()
    {
        try
        {
            if (StringUtils.isEmpty(_source))
                return null;
            return new ActionURL(_source);
        }
        catch (Exception x)
        {
            return null;
        }
    }


    @Nullable
    public URLHelper getURLHelper()
    {
        try
        {
            if (StringUtils.isEmpty(_source))
                return null;
            return new URLHelper(this._source);
        }
        catch (Exception x)
        {
            return null;
        }
    }


    public static class Converter implements org.apache.commons.beanutils.Converter
    {
		private static ConvertHelper.DateFriendlyStringConverter _impl = new ConvertHelper.DateFriendlyStringConverter();

        public Object convert(Class type, Object value)
        {
            if (value == null)
                return ReturnURLString.EMPTY;
            if (value instanceof ReturnURLString)
                return value;
            CharSequence seq;
            if (value instanceof CharSequence)
                seq = (CharSequence)value;
            else
                seq = (String)_impl.convert(String.class, value);

            if (!ViewServlet.validChars(seq))
                throw new ConversionException("Invalid characters in string");

            return new ReturnURLString(seq);
        }
    }
}
