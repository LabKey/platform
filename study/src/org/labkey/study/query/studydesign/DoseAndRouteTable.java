/*
 * Copyright (c) 2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
