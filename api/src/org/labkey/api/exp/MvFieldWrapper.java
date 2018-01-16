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
package org.labkey.api.exp;

import java.util.Set;

/**
 * For fields with associated missing value indicator, this wrapper holds both an actual value
 * (which may be null) and an MV indicator (which also may be null) for use during import (insert or update) operations.
 *
 * User: jgarms
 * Date: Jan 15, 2009
 */
public class MvFieldWrapper
{
    private Object value;
    private String mvIndicator;
    private final Set<String> _validMVIndicators;

    public MvFieldWrapper(Set<String> mvIndicators, Object value, String mvIndicator)
    {
        this(mvIndicators);
        setValue(value);
        setMvIndicator(mvIndicator);
    }

    public MvFieldWrapper(Set<String> mvIndicators)
    {
        _validMVIndicators = mvIndicators;
    }

    public Object getValue()
    {
        return value;
    }

    public void setValue(Object value)
    {
        this.value = value;
    }

    public String getMvIndicator()
    {
        return mvIndicator;
    }

    public void setMvIndicator(String mvIndicator)
    {
        if ("".equals(mvIndicator))
            mvIndicator = null;

        // Transform to canonical casing
        for (String validMVIndicator : _validMVIndicators)
        {
            if (validMVIndicator.equalsIgnoreCase(mvIndicator))
            {
                mvIndicator = validMVIndicator;
            }
        }

        this.mvIndicator = mvIndicator;
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName() + ": value=" + value + ", mvIndicator=" + mvIndicator;
    }

    public Object getOriginalValue()
    {
        return value != null ? value : mvIndicator;
    }

    public boolean isEmpty()
    {
        return value == null && mvIndicator == null;
    }

    @Override
    public boolean equals(Object o)
    {
        if (o instanceof MvFieldWrapper)
            return _equals(value, ((MvFieldWrapper)o).value) && _equals(mvIndicator, ((MvFieldWrapper)o).mvIndicator);
        return null==mvIndicator && _equals(value,o);
    }

    private boolean _equals(Object a, Object b)
    {
        if (null==a || null==b)
            return a==b;
        return a.equals(b);
    }
}
