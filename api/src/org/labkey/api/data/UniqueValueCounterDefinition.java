/*
 * Copyright (c) 2019 LabKey Corporation
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
package org.labkey.api.data;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class UniqueValueCounterDefinition implements CounterDefinition
{
    private final String _counterName;
    private final List<String> _pairedColumnNames;
    private final Set<String> _attachedColumnNames;

    public UniqueValueCounterDefinition(String counterName, List<String> pairedColumnNames, Set<String> attachedColumnnames)
    {
        _counterName = counterName;
        _pairedColumnNames = pairedColumnNames.stream().map(String::toLowerCase).collect(Collectors.toList());          // lower case
        _attachedColumnNames = attachedColumnnames.stream().map(String::toLowerCase).collect(Collectors.toSet());       // lower case
    }

    @Override
    public String getCounterName()
    {
        return _counterName;
    }

    @Override
    public List<String> getPairedColumnNames()
    {
        return _pairedColumnNames;
    }

    @Override
    public Set<String> getAttachedColumnNames()
    {
        return _attachedColumnNames;
    }

    @Override
    public String getDbSequenceName(String prefix, List<String> pairedValues)
    {
        assert pairedValues.size() == _pairedColumnNames.size();
        return prefix + ":" + getCounterName() + ":" + String.join(":", pairedValues);
    }
}
