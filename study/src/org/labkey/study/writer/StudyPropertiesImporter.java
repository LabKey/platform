package org.labkey.study.writer;

import org.labkey.api.admin.ImportException;
import org.labkey.api.data.TableInfo;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.importer.DefaultStudyDesignImporter;
import org.labkey.study.importer.StudyImportContext;
import org.labkey.study.model.StudyManager;
import org.labkey.study.query.StudyQuerySchema;
import org.labkey.study.xml.ExportDirType;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by klum on 3/17/14.
 */
public class StudyPropertiesImporter extends DefaultStudyDesignImporter
{
    /**
     * Exports additional study related properties into the properties sub folder
     */
    public void importExtendedStudyProperties(StudyImportContext ctx, VirtualFile root) throws Exception
    {
        ExportDirType dirType = ctx.getXml().getProperties();

        if (dirType != null)
        {
            VirtualFile vf = root.getDir(dirType.getDir());
            if (vf != null)
            {
                // import any custom study design table properties
                importTableinfo(ctx, vf, StudyPropertiesWriter.SCHEMA_FILENAME);

                // import the objectve and personnel tables
                StudyQuerySchema schema = StudyQuerySchema.createSchema(StudyManager.getInstance().getStudy(ctx.getContainer()), ctx.getUser(), true);
                List<TableInfo> studyPropertyTables = new ArrayList<>();

                studyPropertyTables.add(schema.getTable(StudyQuerySchema.OBJECTIVE_TABLE_NAME));
                studyPropertyTables.add(schema.getTable(StudyQuerySchema.PERSONNEL_TABLE_NAME));

                for (TableInfo table : studyPropertyTables)
                {
                    deleteData(ctx, table);
                    importTableData(ctx, vf, table, null, null);
                }
            }
            else
                throw new ImportException("Unable to open the folder at : " + dirType.getDir());
        }
    }
}
