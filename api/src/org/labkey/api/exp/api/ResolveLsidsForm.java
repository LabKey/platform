package org.labkey.api.exp.api;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

public class ResolveLsidsForm
{
    private boolean _includeProperties;
    private boolean _includeInputsAndOutputs;
    private boolean _includeRunSteps;
    private List<String> _lsids;
    private boolean _singleSeedRequested = false;

    public ResolveLsidsForm()
    {
        this(false, false, false);
    }

    public ResolveLsidsForm(boolean includeProperties, boolean includeInputsAndOutputs, boolean includeRunSteps)
    {
        _includeProperties = includeProperties;
        _includeInputsAndOutputs = includeInputsAndOutputs;
        _includeRunSteps = includeRunSteps;
    }

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
