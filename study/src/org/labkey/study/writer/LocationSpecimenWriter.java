package org.labkey.study.writer;

import org.labkey.api.admin.ImportContext;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
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
        // the generated SQL for the study.Location table needs to take into consideration the "maskClinic" setting on the export context
        SQLFragment sql = new SQLFragment().append("SELECT ");
        String comma = "";

        for (SpecimenImporter.ImportableColumn column : columns)
        {
            sql.append(comma);

            // when masking, use generic label for clinics
            if (ctx.isMaskClinic() && column.getDbColumnName().toLowerCase().equals("label"))
                sql.append("CASE WHEN Clinic = true THEN ").appendStringLiteral("Clinic").append(" ELSE Label END AS Label");
            // when masking, remove the LabwareLabCode for clinics
            else if (ctx.isMaskClinic() && column.getDbColumnName().toLowerCase().equals("labwarelabcode"))
                sql.append("CASE WHEN Clinic = true THEN NULL ELSE LabwareLabCode END AS LabwareLabCode");
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
}
