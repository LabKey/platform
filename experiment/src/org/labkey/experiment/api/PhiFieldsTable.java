package org.labkey.experiment.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.exp.query.ExpSchema;

public class PhiFieldsTable extends BaseFieldsTable
{
    public PhiFieldsTable(@NotNull ExpSchema userSchema, @Nullable ContainerFilter containerFilter)
    {
        super("PHIFields", userSchema, containerFilter);
        setDescription("Shows one row for each PHI-annotated field in the selected folder(s). Rows are shown in " +
            "a folder or project only if the user has administrator permissions in that folder.");

        MutableColumnInfo phi = addColumn("PHI", JdbcType.VARCHAR);
        phi.setDescription("PHI Annotation");

        addCondition(new SimpleFilter(phi.getFieldKey(), "NotPHI", CompareType.NEQ));
    }

    @Override
    protected void addColumnSQL(SQLFragment sql)
    {
    }
}
