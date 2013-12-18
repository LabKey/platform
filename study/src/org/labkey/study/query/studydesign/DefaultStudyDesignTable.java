package org.labkey.study.query.studydesign;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.DbSchema;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.UserIdForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by klum on 12/12/13.
 */
public class DefaultStudyDesignTable extends FilteredTable<UserSchema>
{
    protected List<FieldKey> _defaultVisibleColumns = new ArrayList<>();
    private Domain _domain;

    public DefaultStudyDesignTable(Domain domain, DbSchema dbSchema, UserSchema schema)
    {
        super(StorageProvisioner.createTableInfo(domain, dbSchema), schema);

        _domain = domain;
        wrapAllColumns(true);

        _defaultVisibleColumns.add(FieldKey.fromParts("Container"));
        _defaultVisibleColumns.add(FieldKey.fromParts("Created"));
        _defaultVisibleColumns.add(FieldKey.fromParts("CreatedBy"));

        // setup lookups for the standard fields
        ColumnInfo container = getColumn("Container");
        ContainerForeignKey.initColumn(container, schema);

        ColumnInfo created = getColumn("Created");
        created.setFormat("DateTime");

        ColumnInfo createdBy = getColumn(FieldKey.fromParts("CreatedBy"));
        createdBy.setLabel("Created By");
        UserIdForeignKey.initColumn(createdBy);

        ColumnInfo modified = getColumn("Modified");
        modified.setFormat("DateTime");

        ColumnInfo modifiedBy = getColumn(FieldKey.fromParts("ModifiedBy"));
        modifiedBy.setLabel("Modified By");
        UserIdForeignKey.initColumn(modifiedBy);

        initColumns();
    }

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return _defaultVisibleColumns;
    }

    protected void initColumns()
    {
        for (ColumnInfo col : getColumns())
            initColumn(col);
    }

    // Subclasses may override this to provide customizations to the column
    protected void initColumn(ColumnInfo col)
    {
    }

    @Nullable
    @Override
    public Domain getDomain()
    {
        return _domain;
    }

    @Nullable
    @Override
    public QueryUpdateService getUpdateService()
    {
        return new DefaultQueryUpdateService(this, this.getRealTable());
    }

    @Override
    public String getDescription()
    {
        return super.getDescription();
    }

    @Override
    public boolean hasPermission(UserPrincipal user, Class<? extends Permission> perm)
    {
        return getContainer().hasPermission(user, perm);
    }
}
