package org.labkey.list.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PairSerializer;
import org.labkey.api.util.Pair;

import java.util.Map;

public class ListImportContext
{
    // example: "testData.xlsx" -> {"name", "testList"}
    @JsonSerialize(contentUsing = PairSerializer.class)
    private Map<String, Pair<String, String>> _inputDataMap;

    //generally, _useMerge == false means we truncate and replace the data
    private boolean _useMerge = false;

    private boolean _triggeredReload = false;

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
}
