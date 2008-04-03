package org.labkey.study.model;

import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.util.MemTracker;
import org.labkey.study.requirements.DefaultActor;
import org.labkey.study.StudySchema;

/**
 * User: brittp
 * Date: Feb 8, 2006
 * Time: 4:18:20 PM
 */
public class SampleRequestActor extends DefaultActor<SampleRequestActor>
        implements StudyCachable<SampleRequestActor>, Cloneable
{
    private int _rowId;
    private Integer _sortOrder;
    private Container _container;
    private String _label;
    private boolean _perSite;

    private boolean _mutable = true;

    public SampleRequestActor()
    {
        MemTracker.put(this);
    }

    protected void verifyMutability()
    {
        if (!_mutable)
            throw new IllegalStateException("Cached objects are immutable; createMutable must be called first.");
    }

    public boolean isMutable()
    {
        return _mutable;
    }

    protected void unlock()
    {
        _mutable = true;
    }

    public void lock()
    {
        _mutable = false;
    }


    public SampleRequestActor createMutable()
    {
        try
        {
            SampleRequestActor obj = (SampleRequestActor) clone();
            obj.unlock();
            return obj;
        }
        catch (CloneNotSupportedException e)
        {
            throw new RuntimeException(e);
        }
    }


    public Container getContainer()
    {
        return _container;
    }

    public void setContainer(Container container)
    {
        verifyMutability();
        _container = container;
    }

    public String getLabel()
    {
        return _label;
    }

    public void setLabel(String label)
    {
        verifyMutability();
        _label = label;
    }

    public Object getPrimaryKey()
    {
        return getRowId();
    }

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        verifyMutability();
        _rowId = rowId;
    }

    public Integer getSortOrder()
    {
        return _sortOrder;
    }

    public void setSortOrder(Integer sortOrder)
    {
        verifyMutability();
        _sortOrder = sortOrder;
    }

    public boolean isPerSite()
    {
        return _perSite;
    }

    public void setPerSite(boolean perSite)
    {
        verifyMutability();
        _perSite = perSite;
    }

    public String getGroupName()
    {
        return getLabel();
    }


    protected TableInfo getTableInfo()
    {
        return StudySchema.getInstance().getTableInfoSampleRequestActor();
    }
}
