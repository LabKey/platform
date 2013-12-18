package org.labkey.study.query;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.DatabaseTableType;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.UserIdQueryForeignKey;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.study.StudySchema;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * User: cnathe
 * Date: 12/13/13
*/
public class AssaySpecimenTable extends BaseStudyTable
{
    public AssaySpecimenTable(StudyQuerySchema schema)
    {
        super(schema, StudySchema.getInstance().getTableInfoAssaySpecimen());
        setName(StudyQuerySchema.ASSAY_SPECIMEN_TABLE_NAME);

        addWrapColumn(_rootTable.getColumn("RowId"));
        addWrapColumn(_rootTable.getColumn("AssayName"));
        addWrapColumn(_rootTable.getColumn("Description"));
        addWrapLocationColumn("LocationId", "LocationId");
        addWrapColumn(_rootTable.getColumn("Source"));
        addWrapTypeColumn("PrimaryTypeId", "PrimaryTypeId");
        addWrapTypeColumn("DerivativeTypeId", "DerivativeTypeId");
        addWrapColumn(_rootTable.getColumn("TubeType"));

        addContainerColumn();
        for (ColumnInfo baseColumn : _rootTable.getColumns())
        {
            String name = baseColumn.getName();
            if (name.equalsIgnoreCase("Created") || name.equalsIgnoreCase("Modified") || name.equalsIgnoreCase("CreatedBy") || name.equalsIgnoreCase("ModifiedBy"))
            {
                ColumnInfo column = addWrapColumn(baseColumn);
                if (name.equalsIgnoreCase("CreatedBy") || name.equalsIgnoreCase("ModifiedBy"))
                    UserIdQueryForeignKey.initColumn(schema.getUser(), schema.getContainer(), column, true);
                column.setHidden(true);
                column.setUserEditable(false);
                column.setShownInInsertView(false);
                column.setShownInUpdateView(false);
            }
        }
    }

    @Override
    public String getTitleColumn()
    {
        return "AssayName";
    }

    @Override
    public boolean hasPermission(UserPrincipal user, Class<? extends Permission> perm)
    {
        return getContainer().hasPermission(user, perm);
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        return new DefaultQueryUpdateService(this, this.getRealTable());
    }
}