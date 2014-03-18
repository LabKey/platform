package org.labkey.study.writer;

import org.labkey.api.data.TableInfo;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.query.StudyQuerySchema;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by klum on 3/17/14.
 */
public class StudyPropertiesWriter extends DefaultStudyDesignWriter
{
    public static final String SCHEMA_FILENAME = "study_metadata.xml";

    /**
     * Exports additional study related properties into the properties sub folder
     */
    public void writeExtendedStudyProperties(StudyImpl study, StudyExportContext ctx, VirtualFile dir) throws Exception
    {
        Set<TableInfo> studyTables = new HashSet<>();
        StudyQuerySchema schema = StudyQuerySchema.createSchema(StudyManager.getInstance().getStudy(ctx.getContainer()), ctx.getUser(), true);

        // export the personnel table extended properties
        studyTables.add(schema.getTable(StudyQuerySchema.PERSONNEL_TABLE_NAME));
        writeTableInfos(ctx, dir, studyTables, SCHEMA_FILENAME);

        studyTables.add(schema.getTable(StudyQuerySchema.OBJECTIVE_TABLE_NAME));
        writeTableData(ctx, dir, studyTables, null);
    }
}
