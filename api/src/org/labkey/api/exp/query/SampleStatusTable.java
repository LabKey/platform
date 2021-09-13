package org.labkey.api.exp.query;

import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.query.FilteredTable;

import java.util.Arrays;
import java.util.stream.Collectors;

public class SampleStatusTable extends FilteredTable<ExpSchema>
{
    public SampleStatusTable(ExpSchema expSchema, ContainerFilter cf)
    {
        super(CoreSchema.getInstance().getTableInfoDataStates(), expSchema, cf);
        if (cf != null)
            this.setContainerFilter(cf);
        SQLFragment sql = new SQLFragment(("(stateType IN ("));
        sql.append(Arrays.stream(ExpSchema.SampleStatusType.values()).map(type -> "'" + type.name() + "'").collect(Collectors.joining(",")));
        sql.append(") )");
        addCondition(sql);
        addWrapColumn(getRealTable().getColumn("RowId"));
        addWrapColumn(getRealTable().getColumn("Label"));
        addWrapColumn(getRealTable().getColumn("Description"));
        addWrapColumn(getRealTable().getColumn("Container"));
        addWrapColumn(getRealTable().getColumn("StateType"));
    }
}
