package org.labkey.query.view;

import org.labkey.api.data.*;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.security.ACL;
import org.labkey.api.view.DataView;
import org.labkey.query.controllers.dbuserschema.DbUserSchemaController;
import org.labkey.query.data.DbUserSchemaTable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DbUserSchemaView extends QueryView
{
    public DbUserSchemaView(DbUserSchema schema, QuerySettings settings)
    {
        super(schema, settings);
    }


    protected void populateButtonBar(DataView view, ButtonBar bar, boolean exportAsWebPage)
    {
        TableInfo table = getTable();
        if (table instanceof DbUserSchemaTable)
        {
            DbUserSchemaTable dbUserSchemaTable = (DbUserSchemaTable) table;
            if (table.hasPermission(getUser(), ACL.PERM_INSERT))
            {
                bar.add(new ActionButton("Insert New", dbUserSchemaTable.urlFor(DbUserSchemaController.Action.insert)));
            }
        }
        super.populateButtonBar(view, bar, exportAsWebPage);
    }

    public List<DisplayColumn> getDisplayColumns()
    {
        List<DisplayColumn> ret = new ArrayList<DisplayColumn>(super.getDisplayColumns());
        TableInfo table = getTable();
        if (table instanceof DbUserSchemaTable)
        {
            DbUserSchemaTable dbUserSchemaTable = (DbUserSchemaTable) table;
            if (table.hasPermission(getUser(), ACL.PERM_UPDATE) && !isPrintView() && !isExportView())
            {
                DetailsURL durl = new DetailsURL(dbUserSchemaTable.urlFor(DbUserSchemaController.Action.update), Collections.singletonMap("pk", dbUserSchemaTable.getPkColumns().get(0).getName()));
                ret.add(0, new UrlColumn(durl.getURL(Table.createColumnMap(getTable(), null)), "edit"));

            }
        }
        return ret;
    }
}
