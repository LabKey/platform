package org.labkey.api.data;

import org.labkey.api.exp.api.ExpSampleSet;

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
