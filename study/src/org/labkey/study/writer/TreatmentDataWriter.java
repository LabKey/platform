package org.labkey.study.writer;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.Results;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableInfoWriter;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.writer.VirtualFile;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;
import org.labkey.data.xml.TablesType;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.query.StudyQuerySchema;
import org.labkey.study.xml.ExportDirType;
import org.labkey.study.xml.StudyDocument;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by klum on 1/21/14.
 */
public class TreatmentDataWriter extends DefaultStudyDesignWriter implements InternalStudyWriter
{
    private static final Logger LOG = Logger.getLogger(TreatmentDataWriter.class);
    private static final String DEFAULT_DIRECTORY = "treatments";
    private static final String SCHEMA_FILENAME = "treatments_metadata.xml";


    public static final String SELECTION_TEXT = "Treatment Data";

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

        ExportDirType dir = studyXml.addNewTreatmentData();
        dir.setDir(DEFAULT_DIRECTORY);

        VirtualFile vf = root.getDir(DEFAULT_DIRECTORY);

        // write the treatment tableInfos to the manifest
        Set<TableInfo> treatmentTables = new HashSet<>();
        StudyQuerySchema schema = new StudyQuerySchema(StudyManager.getInstance().getStudy(ctx.getContainer()), ctx.getUser(), true);

        // add the treatment specific tables
        treatmentTables.add(schema.getTable(StudyQuerySchema.PRODUCT_TABLE_NAME));
        treatmentTables.add(schema.getTable(StudyQuerySchema.PRODUCT_ANTIGEN_TABLE_NAME));
        treatmentTables.add(schema.getTable(StudyQuerySchema.TREATMENT_TABLE_NAME));
        treatmentTables.add(schema.getTable(StudyQuerySchema.TREATMENT_PRODUCT_MAP_TABLE_NAME));

        //writeTableInfos(ctx, vf, treatmentTables);
        writeTableData(ctx, vf, treatmentTables, null);

        // for the TreatmentVisitMap table, export the visit sequence num & cohort label instead of the ID
        writeTreatmentVisitMap(ctx, vf);

        // export the study design tables (no need to export tableinfo's as these are non-extensible)
        Set<TableInfo> designTables = new HashSet<>();

        // study designs also can have data stored at both the project and folder level although for the
        // initial implementation we will only export/import from/to current folder
        // ContainerFilter containerFilter = new ContainerFilter.CurrentPlusProject(ctx.getUser());

        designTables.add(schema.getTable(StudyQuerySchema.STUDY_DESIGN_GENES_TABLE_NAME));
        designTables.add(schema.getTable(StudyQuerySchema.STUDY_DESIGN_ROUTES_TABLE_NAME));
        designTables.add(schema.getTable(StudyQuerySchema.STUDY_DESIGN_IMMUNOGEN_TYPES_TABLE_NAME));
        designTables.add(schema.getTable(StudyQuerySchema.STUDY_DESIGN_SUB_TYPES_TABLE_NAME));

        writeTableData(ctx, vf, designTables, null);
    }

    private void writeTableInfos(StudyExportContext ctx, VirtualFile vf, Set<TableInfo> tables) throws IOException
    {
        // Create dataset metadata file
        TablesDocument tablesDoc = TablesDocument.Factory.newInstance();
        TablesType tablesXml = tablesDoc.addNewTables();

        for (TableInfo tinfo : tables)
        {
            TableType tableXml = tablesXml.addNewTable();

            TableInfoWriter writer = new TreatementTableWriter(ctx.getContainer(), tinfo, tinfo.getColumns());
            writer.writeTable(tableXml);
        }
        vf.saveXmlBean(SCHEMA_FILENAME, tablesDoc);
    }

    private void writeTreatmentVisitMap(StudyExportContext ctx, VirtualFile vf) throws Exception
    {
        StudyQuerySchema schema = new StudyQuerySchema(StudyManager.getInstance().getStudy(ctx.getContainer()), ctx.getUser(), true);
        TableInfo tableInfo = schema.getTable(StudyQuerySchema.TREATMENT_VISIT_MAP_TABLE_NAME);

        List<FieldKey> fields = new ArrayList<>();
        fields.addAll(tableInfo.getDefaultVisibleColumns());

        // we want to include the cohort label and visit sequence number so we can resolve during import
        fields.add(FieldKey.fromParts("cohortId", "label"));
        fields.add(FieldKey.fromParts("visitId", "sequenceNumMin"));

        Map<FieldKey, ColumnInfo> columns = QueryService.get().getColumns(tableInfo, fields);
        Results rs = QueryService.get().select(tableInfo, columns.values(), null, null);

        writeResultsToTSV(rs, vf, getFileName(tableInfo));
    }

    private static class TreatementTableWriter extends TableInfoWriter
    {
        public TreatementTableWriter(Container c, TableInfo tinfo, Collection<ColumnInfo> columns)
        {
            super(c, tinfo, columns);
        }
    }
}
