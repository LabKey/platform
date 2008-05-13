/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.api.pipeline.cmd;

import org.labkey.api.pipeline.ParamParser;

/**
 * <code>ValidateInt</code> validates a whole number parameter, and may
 * optionally check minimum and maximum values.
 */
public class ValidateNumber implements ParamParser.ParamValidator
{
    private Long _min;
    private Long _max;
    private String _minParamName;
    private String _maxParamName;

    public long getMin()
    {
        return _min.longValue();
    }

    public void setMin(long min)
    {
        _min = min;
    }

    public long getMax()
    {
        return _max.longValue();
    }

    public void setMax(long max)
    {
        _max = max;
    }

    public String getMinParamName()
    {
        return _minParamName;
    }

    public void setMinParamName(String minParamName)
    {
        _minParamName = minParamName;
    }

    public String getMaxParamName()
    {
        return _maxParamName;
    }

    public void setMaxParamName(String maxParamName)
    {
        _maxParamName = maxParamName;
    }

    public void validate(String name, String value, ParamParser parser)
    {
        try
        {
            long n = Long.parseLong(value);
            if (_min != null && n < _min.intValue())
                parser.addError(name, "Value " + value + " is less than " + _min);
            else if (_max != null && n < _max.intValue())
                parser.addError(name, "Value " + value + " is greater than " + _max);
            else if (!isValueBounded(n, _minParamName, parser, true))
                parser.addError(name, "The value of " + name + " must be at least the value of " + _minParamName);
            else if (!isValueBounded(n, _maxParamName, parser, false))
                parser.addError(name, "The value of " + name + " may be at most the value of " + _maxParamName);
        }
        catch (NumberFormatException e)
        {
            parser.addError(name, "Invalid whole number '" + value + "'"); 
        }
    }

    private boolean isValueBounded(long n, String paramName, ParamParser parser, boolean less)
    {
        if (paramName == null)
            return true;

        String valueBounding = parser.getInputParameter(paramName);
        if (valueBounding == null)
            return true;

        try
        {
            long bounding = Long.parseLong(valueBounding);
            if (less && n < bounding)
                return false;
            else if (!less && n > bounding)
                return false;
        }
        catch (NumberFormatException e)
        {
            // The bounding parameter has problems of its own.
        }
        return true;
    }
}
