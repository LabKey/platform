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
 * <code>ValidateInt</code> validates a decimal number parameter, and may
 * optionally check minimum and maximum values.
 */
public class ValidateDecimal implements ParamParser.ParamValidator
{
    private Double _min;
    private Double _max;
    private String _minParamName;
    private String _maxParamName;

    public double getMin()
    {
        return _min.doubleValue();
    }

    public void setMin(double min)
    {
        _min = min;
    }

    public double getMax()
    {
        return _max.doubleValue();
    }

    public void setMax(double max)
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
            double d = Double.parseDouble(value);
            if (_min != null && d < _min.doubleValue())
                parser.addError(name, "Value " + value + " is less than " + _min);
            if (_max != null && d < _max.doubleValue())
                parser.addError(name, "Value " + value + " is greater than " + _max);
            else if (!isValueBounded(d, _minParamName, parser, true))
                parser.addError(name, "The value of " + name + " must be at least the value of " + _minParamName);
            else if (!isValueBounded(d, _maxParamName, parser, false))
                parser.addError(name, "The value of " + name + " may be at most the value of " + _maxParamName);
        }
        catch (NumberFormatException e)
        {
            parser.addError(name, "Invalid decimal number '" + value + "'");
        }
    }

    private boolean isValueBounded(double d, String paramName, ParamParser parser, boolean less)
    {
        if (paramName == null)
            return true;

        String valueBounding = parser.getInputParameter(paramName);
        if (valueBounding == null)
            return true;

        try
        {
            double bounding = Double.parseDouble(valueBounding);
            if (less && d < bounding)
                return false;
            else if (!less && d > bounding)
                return false;
        }
        catch (NumberFormatException e)
        {
            // The bounding parameter has problems of its own.
        }
        return true;
    }
}