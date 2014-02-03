package org.labkey.api.ehr.dataentry;

import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.module.Module;

/**
 * User: bimber
 * Date: 2/1/14
 * Time: 11:19 AM
 */
public class SingleQueryFormProvider
{
    private Module _owner;
    private String _schemaName;
    private String _queryName;
    private SingleQueryFormSection _section;

    public SingleQueryFormProvider(Module owner, String schemaName, String queryName, SingleQueryFormSection section)
    {
        _owner = owner;
        _schemaName = schemaName;
        _queryName = queryName;
        _section = section;
    }

    public boolean isAvailable(Container c, TableInfo ti)
    {
        if (!c.getActiveModules().contains(_owner))
            return false;

        return (_schemaName.equals(ti.getPublicSchemaName()) && _queryName.equals(ti.getName()));
    }

    public SingleQueryFormSection getSection()
    {
        return _section;
    }
}
