package org.labkey.study.writer;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Results;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.query.StudyQuerySchema;
import org.labkey.study.xml.ExportDirType;
import org.labkey.study.xml.StudyDocument;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by klum on 1/21/14.
 */
public class AssayScheduleWriter extends DefaultStudyDesignWriter implements InternalStudyWriter
{
    private static final Logger LOG = Logger.getLogger(AssayScheduleWriter.class);
    private static final String DEFAULT_DIRECTORY = "assaySchedule";

    public static final String SELECTION_TEXT = "Assay Schedule";

    @Nullable
    @Override
    public String getSelectionText()
    {
        return SELECTION_TEXT;
    }

    @Override
    public void write(StudyImpl object, StudyExportContext ctx, VirtualFile root) throws Exception
    {
        StudyDocument.Study studyXml = ctx.getXml();

        ExportDirType dir = studyXml.addNewAssaySchedule();
        dir.setDir(DEFAULT_DIRECTORY);

        VirtualFile vf = root.getDir(DEFAULT_DIRECTORY);

        StudyQuerySchema schema = new StudyQuerySchema(StudyManager.getInstance().getStudy(ctx.getContainer()), ctx.getUser(), true);

        // add the assay schedule specific tables
        TableInfo assaySpecimenTable = schema.getTable(StudyQuerySchema.ASSAY_SPECIMEN_TABLE_NAME);

        writeTableData(ctx, vf, assaySpecimenTable, getDefaultColumns(assaySpecimenTable), null);
        writeAssaySpecimenVisitMap(ctx, vf);

        // export the study design tables (no need to export tableinfo's as these are non-extensible)
        Set<TableInfo> designTables = new HashSet<>();

        designTables.add(schema.getTable(StudyQuerySchema.STUDY_DESIGN_LABS_TABLE_NAME));
        designTables.add(schema.getTable(StudyQuerySchema.STUDY_DESIGN_SAMPLE_TYPES_TABLE_NAME));

        writeTableData(ctx, vf, designTables, null);
    }

    private void writeAssaySpecimenVisitMap(StudyExportContext ctx, VirtualFile vf) throws Exception
    {
        StudyQuerySchema schema = new StudyQuerySchema(StudyManager.getInstance().getStudy(ctx.getContainer()), ctx.getUser(), true);
        TableInfo tableInfo = schema.getTable(StudyQuerySchema.ASSAY_SPECIMEN_VISIT_TABLE_NAME);

        List<FieldKey> fields = new ArrayList<>();
        fields.addAll(tableInfo.getDefaultVisibleColumns());

        // we want to include the visit sequence number so we can resolve during import
        fields.add(FieldKey.fromParts("visitId", "sequenceNumMin"));

        Map<FieldKey, ColumnInfo> columns = QueryService.get().getColumns(tableInfo, fields);
        writeTableData(ctx, vf, tableInfo, new ArrayList<>(columns.values()), null);
    }
}
