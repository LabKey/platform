package org.labkey.api.exp.api;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

public class ResolveLsidsForm
{
    private boolean _singleSeedRequested = false;
    private List<String> _lsids;

    // serialization options
    private boolean _includeProperties = false;
    private boolean _includeInputsAndOutputs = false;
    private boolean _includeRunSteps = false;

    public List<String> getLsids()
    {
        return _lsids;
    }

    public void setLsids(List<String> lsids)
    {
        _lsids = lsids;
    }

    public void setLsid(String lsid)
    {
        _lsids = List.of(lsid);
        _singleSeedRequested = true;
    }

    @JsonIgnore
    public boolean isSingleSeedRequested()
    {
        return _singleSeedRequested;
    }

    public boolean isIncludeProperties()
    {
        return _includeProperties;
    }

    public void setIncludeProperties(boolean includeProperties)
    {
        _includeProperties = includeProperties;
    }

    public boolean isIncludeInputsAndOutputs()
    {
        return _includeInputsAndOutputs;
    }

    public void setIncludeInputsAndOutputs(boolean includeInputsAndOutputs)
    {
        _includeInputsAndOutputs = includeInputsAndOutputs;
    }

    public boolean isIncludeRunSteps()
    {
        return _includeRunSteps;
    }

    public void setIncludeRunSteps(boolean includeRunSteps)
    {
        _includeRunSteps = includeRunSteps;
    }
}
