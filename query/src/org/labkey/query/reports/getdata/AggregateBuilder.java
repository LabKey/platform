/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
package org.labkey.query.reports.getdata;

import org.labkey.api.stats.SummaryStatisticRegistry;
import org.labkey.api.data.Aggregate;
import org.labkey.api.query.FieldKey;
import org.labkey.api.services.ServiceRegistry;

/**
 * JSON deserialization target, responsible for creating the Aggregate object understood by Query.,
 *
 * User: jeckels
 * Date: 5/21/13
 */
public class AggregateBuilder
{
    private FieldKey _fieldKey;
    private String _type;
    private String _label;
    private boolean _distinct = false;

    public void setFieldKey(String[] fieldKey)
    {
        _fieldKey = FieldKey.fromParts(fieldKey);
    }

    public void setType(String type)
    {
        _type = type;
    }

    public void setLabel(String label)
    {
        _label = label;
    }

    public void setDistinct(boolean distinct)
    {
        _distinct = distinct;
    }

    public Aggregate create()
    {
        // lookup the Aggregate.Type from the String value
        SummaryStatisticRegistry registry = ServiceRegistry.get().getService(SummaryStatisticRegistry.class);
        Aggregate.Type type = registry != null ? registry.getByName(_type) : null;
        if (type == null)
            throw new IllegalArgumentException("Invalid aggregate type: '" + _type + "'.");

        return new Aggregate(_fieldKey, type, _label, _distinct);
    }
}
