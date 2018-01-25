package org.labkey.list.model;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.Pair;

import java.util.Map;

public class ListImportContext
{
    // example: "testData.xlsx" -> {"name", "testList"}
    private Map<String, Pair<String, String>> _inputDataMap;

    //generally, _useMerge == false means we truncate and replace the data
    private boolean _useMerge = false;

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
}
