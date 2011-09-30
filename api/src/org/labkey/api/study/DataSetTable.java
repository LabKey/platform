package org.labkey.api.study;

import org.labkey.api.data.TableInfo;

/**
 * User: brittp
 * Date: Sep 28, 2011 5:03:09 PM
 */
public interface DataSetTable extends TableInfo
{
    DataSet getDataSet();
}
