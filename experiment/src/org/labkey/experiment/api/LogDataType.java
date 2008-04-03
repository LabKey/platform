package org.labkey.experiment.api;

import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.util.URLHelper;

public class LogDataType extends DataType
{
    public LogDataType()
    {
        super("Log");
    }
    public URLHelper getDetailsURL(ExpData dataObject)
    {
        return null;
    }
}
