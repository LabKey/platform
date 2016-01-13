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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.view.ActionURL;

/**
 * User: matthewb
 * Date: Nov 3, 2009
 * Time: 12:22:55 PM
 */
public class ReturnURLString extends HString
{
    public static ReturnURLString EMPTY = new ReturnURLString("",false)
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

    
    public ReturnURLString(CharSequence s)
    {
        super(s);
    }


    public ReturnURLString(CharSequence s, boolean tainted)
    {
        super(s, tainted);
    }


    @Override
    public String getSource()
    {
        return super.getSource();
    }

    @Override
    public String toString()
    {
        if (!isTainted())
            return _source;
        if (null == _safe)
            _safe = PageFlowUtil.filter(_source);
        return _safe;
    }


    @Nullable
    public ActionURL getActionURL()
    {
        try
        {
            return new ActionURL(this);
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
            return new URLHelper(this);
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
            validateChars(seq);

            // TODO more validation for crazy char encoding

            return new ReturnURLString(seq, true);
        }
    }
}
