/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
package org.labkey.study.writer;

import org.labkey.api.admin.ImportContext;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.study.importer.SpecimenImporter;
import org.labkey.study.xml.StudyDocument;

import java.util.Collection;

/**
 * User: cnathe
 * Date: 2/11/13
 */
public class LocationSpecimenWriter extends StandardSpecimenWriter
{
    @Override
    protected SQLFragment generateSql(ImportContext<StudyDocument.Study> ctx, TableInfo tinfo, Collection<SpecimenImporter.ImportableColumn> columns)
    {
        SqlDialect d = tinfo.getSqlDialect();

        // the generated SQL for the study.Location table needs to take into consideration the "maskClinic" setting on the export context
        SQLFragment sql = new SQLFragment().append("SELECT ");
        String comma = "";

        for (SpecimenImporter.ImportableColumn column : columns)
        {
            sql.append(comma);

            // when masking, use generic label for clinics and remove the LabwareLabCode, Description, and Address fields for clinics
            if (ctx.isMaskClinic() && column.getDbColumnName().toLowerCase().equals("label"))
                sql.append("CASE WHEN Clinic = " + d.getBooleanTRUE() + " THEN ").appendStringLiteral("Clinic").append(" ELSE Label END AS Label");
            else if (ctx.isMaskClinic() && column.isMaskOnExport())
                sql.append(getMaskClinicSql(d, column.getDbColumnName()));
            else
                sql.append(column.getDbColumnName());

            comma = ", ";
        }

        sql.append(" FROM ");
        sql.append(tinfo, "ti");
        sql.append(" WHERE Container = ? ORDER BY ExternalId");

        sql.add(ctx.getContainer());
        return sql;
    }

    private SQLFragment getMaskClinicSql(SqlDialect d, String colName)
    {
        SQLFragment sql = new SQLFragment();
        sql.append("CASE WHEN Clinic = ");
        sql.append(d.getBooleanTRUE());
        sql.append(" THEN NULL ELSE ");
        sql.append(colName);
        sql.append(" END AS ");
        sql.append(colName);
        return sql;
    }
}
