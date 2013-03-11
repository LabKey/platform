package org.labkey.api.module;

import org.apache.commons.lang3.ArrayUtils;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DatabaseTableType;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;

import java.beans.PropertyChangeEvent;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 3/4/13
 * Time: 10:45 AM
 */
public class SimpleModuleContainerListener implements ContainerManager.ContainerListener
{
    private Module _owner;

    public SimpleModuleContainerListener(Module owner)
    {
        _owner = owner;
    }

    public void containerCreated(Container c, User user)
    {
    }

    public void containerDeleted(Container c, User user)
    {
        for (final String schemaName : _owner.getSchemaNames())
        {
            purgeSchema(schemaName, c, user);
        }
    }

    @Override
    public void containerMoved(Container c, Container oldParent, User user)
    {
    }

    protected void purgeSchema(String schemaName, Container c, User user)
    {
        UserSchema schema = QueryService.get().getUserSchema(user, c, schemaName);
        if (schema == null)
        {
            return;
        }

        try
        {
            List<TableInfo> sorted = schema.getSortedTables();
            Collections.reverse(sorted);
            for (TableInfo table : sorted)
            {
                ColumnInfo containerCol = null;
                for (ColumnInfo column : table.getColumns())
                {
                    if ("container".equalsIgnoreCase(column.getName()))
                    {
                        containerCol = column;
                        break;
                    }
                }

                if (containerCol != null)
                    purgeTable(table, c, user);
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    protected void purgeTable(TableInfo table, Container c, User u)
            throws SQLException
    {
        if (table instanceof FilteredTable)
        {
            SimpleFilter filter = new SimpleFilter("Container", c);
            TableInfo realTable = ((FilteredTable)table).getRealTable();
            if (realTable.getTableType() == DatabaseTableType.TABLE)
            {
                Table.delete(realTable, filter);
            }
        }

        Domain domain = table.getDomain();
        if (domain != null)
        {
            SQLFragment objectIds = domain.getDomainKind().sqlObjectIdsInDomain(domain);

            Integer[] ids = new SqlSelector(table.getSchema(), objectIds).getArray(int.class);
            OntologyManager.deleteOntologyObjects(c, true, ArrayUtils.toPrimitive(ids));
        }
    }

    public void propertyChange(PropertyChangeEvent evt)
    {
    }
}
