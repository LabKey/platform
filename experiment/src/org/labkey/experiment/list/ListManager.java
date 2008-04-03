package org.labkey.experiment.list;

import org.labkey.api.data.TableInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.Table;
import org.labkey.api.data.DbSchema;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.security.User;

import java.sql.SQLException;

public class ListManager
{
    static private ListManager instance;
    public static final String LIST_AUDIT_EVENT = "ListAuditEvent";

    synchronized static public ListManager get()
    {
        if (instance == null)
            instance = new ListManager();
        return instance;
    }

    public DbSchema getSchema()
    {
        return ExperimentService.get().getSchema();
    }

    public TableInfo getTinfoList()
    {
        return getSchema().getTable("list");
    }

    public TableInfo getTinfoIndexInteger()
    {
        return getSchema().getTable("indexInteger");
    }

    public TableInfo getTinfoIndexVarchar()
    {
        return getSchema().getTable("indexVarchar");
    }

    public ListDef[] getLists(Container container) throws SQLException
    {
        ListDef.Key key = new ListDef.Key(container);
        return key.select();
    }

    public ListDef[] getAllLists() throws SQLException
    {
        return Table.select(getTinfoList(), Table.ALL_COLUMNS, null, null, ListDef.class);
    }

    public ListDef getList(int id)
    {
        return Table.selectObject(getTinfoList(), id, ListDef.class);
    }
    
    public ListDef insert(User user, ListDef def) throws SQLException
    {
        return Table.insert(user, getTinfoList(), def);
    }

    public ListDef update(User user, ListDef def) throws SQLException
    {
        return Table.update(user, getTinfoList(), def, def.getRowId(), null);
    }
}
