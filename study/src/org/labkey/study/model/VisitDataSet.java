package org.labkey.study.model;

import org.labkey.api.data.Container;

/**
 * User: brittp
 * Date: Jan 7, 2006
 * Time: 3:53:35 PM
 */
public class VisitDataSet
{
    private int _dataSetId;
    private int _visitId;
    private boolean _isRequired = false;
    private Container _container;

    public VisitDataSet()
    {
    }

    public VisitDataSet(Container container, int dataSetId, int visitId, boolean isRequired)
    {
        _dataSetId = dataSetId;
        _visitId = visitId;
        _isRequired = isRequired;
        _container = container;
    }

    public boolean isRequired()
    {
        return _isRequired;
    }

    public int getVisitRowId()
    {
        return _visitId;
    }

    public int getDataSetId()
    {
        return _dataSetId;
    }

    public Container getContainer()
    {
        return _container;
    }
}
