/*
 * Copyright (c) 2013 LabKey Corporation
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

import org.labkey.api.data.Aggregate;
import org.labkey.api.query.FieldKey;

/**
 * JSON deserialization target, responsible for creating the Aggregate object understood by Query.,
 *
 * User: jeckels
 * Date: 5/21/13
 */
public class AggregateBuilder
{
    private FieldKey _fieldKey;
    private Aggregate.Type _type;
    private String _label;
    private boolean _distinct = false;

    public void setFieldKey(String[] fieldKey)
    {
        _fieldKey = FieldKey.fromParts(fieldKey);
    }

    public void setType(Aggregate.Type type)
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
        return new Aggregate(_fieldKey, _type, _label, _distinct);
    }
}
