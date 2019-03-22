/*
 * Copyright (c) 2012-2013 LabKey Corporation
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
package org.labkey.api.query;

/**
 * User: bbimber
 * Date: 2/10/12
 * Time: 12:36 PM
 */
public class AggregateRowConfig
{
    private boolean _aggregateRowFirst;
    private boolean _aggregateRowLast;

    public AggregateRowConfig()
    {
        _aggregateRowFirst = false;
        _aggregateRowLast = true;
    }

    public AggregateRowConfig(boolean first, boolean last)
    {
        _aggregateRowFirst = first;
        _aggregateRowLast = last;
    }

    public boolean getAggregateRowFirst()
    {
        return _aggregateRowFirst;
    }

    public void setAggregateRowFirst(boolean first)
    {
        _aggregateRowFirst = first;
    }

    public boolean getAggregateRowLast()
    {
        return _aggregateRowLast;
    }

    public void setAggregateRowLast(boolean last)
    {
        _aggregateRowLast = last;
    }
}
