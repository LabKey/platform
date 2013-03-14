package org.labkey.di.pipeline;

import org.labkey.api.data.Container;
import org.labkey.api.data.Entity;
import java.util.Date;

/**
 * Created with IntelliJ IDEA.
 * User: matthewb
 * Date: 2013-03-13
 * Time: 4:52 PM
 */
public class TransformConfiguration extends Entity
{
    int rowId = -1;
    String transformId;
    boolean enabled = false;
    boolean verboseLogging = false;
    Date lastChecked = null;

    public TransformConfiguration()
    {
    }

    public TransformConfiguration(ETLDescriptor etl, Container container)
    {
        setTransformId(etl.getTransformId());
        setContainer(container.getId());
    }

    public int getRowId()
    {
        return rowId;
    }

    public void setRowId(int rowId)
    {
        this.rowId = rowId;
    }

    public String getTransformId()
    {
        return transformId;
    }

    public void setTransformId(String transformId)
    {
        this.transformId = transformId;
    }

    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    public boolean isVerboseLogging()
    {
        return verboseLogging;
    }

    public void setVerboseLogging(boolean verboseLogging)
    {
        this.verboseLogging = verboseLogging;
    }

    public Date getLastChecked()
    {
        return lastChecked;
    }

    public void setLastChecked(Date lastChecked)
    {
        this.lastChecked = lastChecked;
    }
}
