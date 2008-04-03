package org.labkey.query.data;

import org.labkey.api.data.*;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QueryUpdateForm;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.security.User;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.view.ActionURL;
import org.labkey.data.xml.TableType;
import org.labkey.query.controllers.dbuserschema.DbUserSchemaController;
import org.labkey.query.view.DbUserSchema;

import java.util.Arrays;

public class DbUserSchemaTable extends FilteredTable
{
    DbUserSchema _schema;
    TableType _metadata;
    SchemaTableInfo _schemaTableInfo;
    String _containerId;
    public DbUserSchemaTable(DbUserSchema schema, SchemaTableInfo table, TableType metadata)
    {
        super(table);
        _schema = schema;
        _metadata = metadata;
        wrapAllColumns(true);
        resetFks();
        if (metadata != null)
        {
            loadFromXML(schema, metadata, null);
        }
    }

    protected void resetFks()
    {
        for(ColumnInfo col : getColumns())
        {
            //if the column has an fk AND if the fk table comes from the same schema
            //reset the fk so that it creates the table through the schema, thus returning
            //a DbUserSchemaTable instead of a SchemaTableInfo. The former is public via Query
            //while the latter is not.
            if(null != col.getFk() && _schema.getDbSchema().getName().equalsIgnoreCase(col.getFkTableInfo().getSchema().getName()))
            {
                final String fkTableName = col.getFkTableInfo().getName();

                col.setFk(new LookupForeignKey(col.getFkTableInfo().getTitleColumn())
                {
                    public TableInfo getLookupTableInfo()
                    {
                        return _schema.getTable(fkTableName, null);
                    }
                });
            }
        }
    }

    public boolean hasPermission(User user, int perm)
    {
        if (!_schema.areTablesEditable())
            return false;
        ColumnInfo[] columns = getPkColumns();
        // Consider: allow multi-column keys
        if (columns.length != 1)
        {
            return false;
        }
        return _schema.getContainer().hasPermission(user, perm);
    }

    public void setContainer(String containerId)
    {
        if (containerId == null)
            return;
        ColumnInfo colContainer = getRealTable().getColumn("container");
        if (colContainer != null)
        {
            getColumn("container").setReadOnly(true);
            addCondition(colContainer, containerId);
            _containerId = containerId;
        }
    }

    public String getContainerId()
    {
        return _containerId;
    }

    public ActionURL delete(User user, ActionURL srcURL, QueryUpdateForm form) throws Exception
    {
        String[] ids = form.getRequest().getParameterValues(DataRegion.SELECT_CHECKBOX_NAME);
        ColumnInfo[] pk = getPkColumns();
        if (pk.length != 1)
            throw new IllegalStateException("Primary key must have 1 column in it, found: " + pk.length);


        SimpleFilter filter = new SimpleFilter();
        if (_containerId != null)
        {
            filter.addCondition("container", _containerId);
        }
        filter.addInClause(pk[0].getName(), Arrays.asList(ids));
        Table.delete(getRealTable(), filter);
        return srcURL;
    }

    public ActionURL urlFor(DbUserSchemaController.Action action)
    {
        ActionURL url = _schema.getContainer().urlFor(action);
        url.addParameter(QueryParam.schemaName.toString(), _schema.getSchemaName());
        url.addParameter(QueryParam.queryName.toString(), getName());
        return url;
    }

}
