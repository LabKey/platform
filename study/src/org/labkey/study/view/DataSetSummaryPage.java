package org.labkey.study.view;

import org.labkey.api.jsp.JspBase;
import org.labkey.api.data.Container;
import org.labkey.study.model.DataSetDefinition;

/**
 * User: brittp
 * Date: Jan 9, 2006
 * Time: 10:53:39 AM
 */
public abstract class DataSetSummaryPage extends BaseStudyPage
{
    private DataSetDefinition _dataSet;

    public void init(Container container, DataSetDefinition dataSet)
    {
        super.init(container);
        _dataSet = dataSet;
    }

    public DataSetDefinition getDataSetDefinition()
    {
        return _dataSet;
    }
}
