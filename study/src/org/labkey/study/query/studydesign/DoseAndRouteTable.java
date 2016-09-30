package org.labkey.study.query.studydesign;

import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.query.ExprColumn;
import org.labkey.study.StudySchema;
import org.labkey.study.query.StudyQuerySchema;

/**
 * Created by klum on 9/23/2016.
 */
public class DoseAndRouteTable extends StudyDesignLookupBaseTable
{
    public DoseAndRouteTable(StudyQuerySchema schema, ContainerFilter filter)
    {
        super(schema, StudySchema.getInstance().getTableInfoDoseAndRoute(), filter);
        setName("DoseAndRoute");

        // add an expr column for label to ba a concatenation of dose and route (need to keep the label generation in sync with code in DoseAndRoute.parseLabel)
        String doseField = ExprColumn.STR_TABLE_ALIAS + ".dose";
        String routeField = ExprColumn.STR_TABLE_ALIAS + ".route";
        SQLFragment sql = new SQLFragment("CONCAT(CASE WHEN ").append(doseField).append(" IS NOT NULL THEN ").append(doseField).append(" ELSE '' END,")
            .append("' : ',")
            .append("CASE WHEN ").append(routeField).append(" IS NOT NULL THEN ").append(routeField).append(" ELSE '' END)");

        ExprColumn labelCol = new ExprColumn(this, "Label", sql, JdbcType.VARCHAR);
        labelCol.setReadOnly(true);
        addColumn(labelCol);
    }
}
