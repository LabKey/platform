package org.labkey.study.model;

import org.labkey.api.data.Container;

/**
 * User: brittp
 * Date: Feb 8, 2006
 * Time: 4:18:11 PM
 */
public class SampleRequestStatus extends AbstractStudyCachable<SampleRequestStatus>
{
    private int _rowId;
    private Container _container;
    private Integer _sortOrder;
    private String _label;
    private boolean _specimensLocked;
    private boolean _finalState;

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

    public boolean isFinalState()
    {
        return _finalState;
    }

    public void setFinalState(boolean finalState)
    {
        verifyMutability();
        _finalState = finalState;
    }

    public boolean isSpecimensLocked()
    {
        return _specimensLocked;
    }

    public void setSpecimensLocked(boolean specimensLocked)
    {
        verifyMutability();
        _specimensLocked = specimensLocked;
    }

    public boolean isSystemStatus()
    {
        return _sortOrder < 0;
    }
}
