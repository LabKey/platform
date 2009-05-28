package org.labkey.api.query;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.exp.PropertyDescriptor;

/**
 * Used to change attributes on a column that was created elsewhere
 * User: jeckels
 * Date: May 27, 2009
 */
public interface PropertyColumnDecorator
{
    public void decorateColumn(ColumnInfo columnInfo, PropertyDescriptor pd);
}
