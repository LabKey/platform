package org.labkey.survey.query;

import org.labkey.api.data.Container;
import org.labkey.api.data.DatabaseTableType;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: 12/7/12
 */
public class SurveyDesignTable extends FilteredTable
{
    public SurveyDesignTable(TableInfo table, Container container)
    {
        super(table, container);

        wrapAllColumns(true);

        List<FieldKey> defaultColumns = new ArrayList<FieldKey>(Arrays.asList(
                FieldKey.fromParts("Label"),
                FieldKey.fromParts("CreatedBy"),
                FieldKey.fromParts("Created"),
                FieldKey.fromParts("ModifiedBy"),
                FieldKey.fromParts("Modified")
        ));
        setDefaultVisibleColumns(defaultColumns);
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        TableInfo table = getRealTable();
        if (table != null && table.getTableType() == DatabaseTableType.TABLE)
            return new DefaultQueryUpdateService(this, table);
        return null;
    }

    @Override
    public boolean hasPermission(UserPrincipal user, Class<? extends Permission> perm)
    {
        return getContainer().hasPermission(user, perm);
    }
}
