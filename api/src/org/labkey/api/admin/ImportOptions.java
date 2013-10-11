package org.labkey.api.admin;

import org.labkey.api.data.Container;

/**
 * User: klum
 * Date: 10/10/13
 */
public class ImportOptions
{
    private boolean _skipQueryValidation;
    private String _containerId;

    public ImportOptions(String containerId)
    {
        _containerId = containerId;
    }

    public boolean isSkipQueryValidation()
    {
        return _skipQueryValidation;
    }

    public void setSkipQueryValidation(boolean skipQueryValidation)
    {
        _skipQueryValidation = skipQueryValidation;
    }

    public String getContainerId()
    {
        return _containerId;
    }

    public void setContainerId(String containerId)
    {
        _containerId = containerId;
    }
}
