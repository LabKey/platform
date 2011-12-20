package org.labkey.api.exp;

/**
 * User: cnathe
 * Date: Dec 16, 2011
 */
public class ExpQCFlag
{
    private int _runId;
    private String _flagType;
    private String _description;
    private String _comment;
    private boolean _enabled;
    private int _intKey1;
    private int _intKey2;

    public int getRunId()
    {
        return _runId;
    }

    public void setRunId(int runId)
    {
        _runId = runId;
    }

    public String getFlagType()
    {
        return _flagType;
    }

    public void setFlagType(String flagType)
    {
        _flagType = flagType;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public String getComment()
    {
        return _comment;
    }

    public void setComment(String comment)
    {
        _comment = comment;
    }

    public boolean isEnabled()
    {
        return _enabled;
    }

    public void setEnabled(boolean enabled)
    {
        _enabled = enabled;
    }

    public int getIntKey1()
    {
        return _intKey1;
    }

    public void setIntKey1(int intKey1)
    {
        _intKey1 = intKey1;
    }

    public int getIntKey2()
    {
        return _intKey2;
    }

    public void setIntKey2(int intKey2)
    {
        _intKey2 = intKey2;
    }
}