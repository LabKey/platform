/*
 * Copyright (c) 2018 LabKey Corporation
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
package org.labkey.list.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PairSerializer;
import org.labkey.api.util.Pair;

import java.util.HashMap;
import java.util.Map;

public class ListImportContext
{
    public static final String LIST_MERGE_OPTION = "mergeData";
    public static final String ALLOW_DOMAIN_UPDATES = "allowDomainUpdates";

    // example: "testData.xlsx" -> {"name", "testList"}
    @JsonSerialize(contentUsing = PairSerializer.class)
    private Map<String, Pair<String, String>> _inputDataMap;

    //generally, _useMerge == false means we truncate and replace the data
    private boolean _useMerge = false;

    private boolean _triggeredReload = false;

    private Map<String, String> _props = new HashMap<>();

    //For serialization
    protected ListImportContext() {}
    
    public ListImportContext(@Nullable Map<String, Pair<String, String>> inputDataMap, boolean useMerge, boolean isTriggeredReload)
    {
        if (inputDataMap != null && !inputDataMap.isEmpty())
            _inputDataMap = inputDataMap;

        _useMerge = useMerge;
        _triggeredReload = isTriggeredReload;
    }

    public ListImportContext(@Nullable Map<String, Pair<String, String>> inputDataMap, boolean useMerge)
    {
        if (inputDataMap != null && !inputDataMap.isEmpty())
            _inputDataMap = inputDataMap;

        _useMerge = useMerge;
    }

    public ListImportContext(@Nullable Map<String, Pair<String, String>> inputDataMap)
    {
        _inputDataMap = inputDataMap;
    }

    public Map<String, Pair<String, String>> getInputDataMap()
    {
        return _inputDataMap;
    }

    public boolean useMerge()
    {
        return _useMerge;
    }

    public boolean isTriggeredReload()
    {
        return _triggeredReload;
    }

    public Map<String, String> getProps()
    {
        return _props;
    }

    public void setProps(Map<String, String> props)
    {
        _props = props;
    }
}
