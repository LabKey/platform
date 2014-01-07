package org.labkey.api.ehr.dataentry;

import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.security.User;

/**
 * User: bimber
 * Date: 12/3/13
 * Time: 1:11 PM
 */
public interface DataEntryFormContext
{
    public TableInfo getTable(String schemaName, String queryName);

    public Container getContainer();

    public User getUser();
}
