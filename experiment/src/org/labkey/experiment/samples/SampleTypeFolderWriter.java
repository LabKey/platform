package org.labkey.experiment.samples;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.AbstractFolderContext;
import org.labkey.api.admin.FolderArchiveDataTypes;
import org.labkey.api.admin.FolderExportContext;
import org.labkey.api.admin.FolderWriter;
import org.labkey.api.admin.FolderWriterFactory;
import org.labkey.api.assay.TsvDataHandler;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.WrappedColumnInfo;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.study.SpecimenService;
import org.labkey.api.study.StudyService;
import org.labkey.api.writer.VirtualFile;
import org.labkey.experiment.XarExporter;
import org.labkey.experiment.xar.XarExportSelection;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class SampleTypeFolderWriter extends AbstractExpFolderWriter
{
    public static final String DEFAULT_DIRECTORY = "sample-types";
    public static final String XAR_TYPES_NAME = "sample_types.xar";             // the file which contains the sample type and data class definitions
    public static final String XAR_TYPES_XML_NAME = XAR_TYPES_NAME + ".xml";
    public static final String SAMPLE_TYPE_PREFIX = "SAMPLE_TYPE_";
    public static final String SAMPLE_STATUS_PREFIX = "SAMPLE_STATUS_";
    public static final List<String> EXCLUDED_TYPES = List.of("MixtureBatches");

    private SampleTypeFolderWriter(){}

    @Override
    public void write(Container c, FolderExportContext ctx, VirtualFile vf) throws Exception
    {
        // We will divide the sample type definitions from the runs into two separate XAR files, the reason is
        // during import we want all data to be imported via the query update service and any lineage will be wired up
        // by the runs XAR.
        XarExportSelection typesSelection = new XarExportSelection();
        XarExportSelection runsSelection = new XarExportSelection();
        Set<ExpSampleType> sampleTypes = new HashSet<>();
        List<ExpMaterial> materialsToExport = new ArrayList<>();
        _exportPhiLevel = ctx.getPhiLevel();
        boolean exportTypes = false;
        boolean exportRuns = false;
        boolean exportSampleTypeData = ctx.getDataTypes().contains(FolderArchiveDataTypes.SAMPLE_TYPE_DATA);
        VirtualFile xarDir = vf.getDir(DEFAULT_DIRECTORY);

        ExpExportContext exportContext = ctx.getContext(ExpExportContext.class);
        if (exportContext == null)
            throw new IllegalStateException("An instance of ExpExportContext is expected to be available from the FolderExportContext");

        // The design and data folder writers share the same write method. We need to determine
        // if the xar has previously been written to avoid potentially creating the xar twice
        // during the same export pass
        if (exportContext.isSampleXarCreated())
            return;

        Lsid sampleTypeLsid = new Lsid(ExperimentService.get().generateLSID(c, ExpSampleType.class, "export"));
        for (ExpSampleType sampleType : SampleTypeService.get().getSampleTypes(c, ctx.getUser(), true))
        {
            // ignore the magic sample type that is used for the specimen repository, it is managed by the specimen importer
            StudyService ss = StudyService.get();
            if (ss != null && ss.getStudy(c) != null && SpecimenService.SAMPLE_TYPE_NAME.equals(sampleType.getName()))
                continue;

            // ignore sample types that are filtered out
            if (EXCLUDED_TYPES.contains(sampleType.getName()))
                continue;

            // filter out non-sample type material sources
            Lsid lsid = new Lsid(sampleType.getLSID());

            if (sampleTypeLsid.getNamespacePrefix().equals(lsid.getNamespacePrefix()))
            {
                sampleTypes.add(sampleType);
                typesSelection.addSampleType(sampleType);
                materialsToExport.addAll(sampleType.getSamples(c));
                exportTypes = true;
            }
        }

        // get the list of runs with the materials or data we expect to export, these will be the sample derivation
        // protocol runs to track the lineage
        Set<ExpRun> exportedRuns = new HashSet<>();
        if (!materialsToExport.isEmpty() && exportSampleTypeData)
            exportedRuns.addAll(ExperimentService.get().getRunsUsingMaterials(materialsToExport));

        // only want the sample derivation runs; other runs will get included in the experiment xar.
        exportedRuns = exportedRuns.stream().filter(run -> {
            String lsid = run.getProtocol().getLSID();
            if (lsid.equals(ExperimentService.SAMPLE_DERIVATION_PROTOCOL_LSID))
                return isValidRunType(ctx, run);
            else
                return lsid.equals(ExperimentService.SAMPLE_ALIQUOT_PROTOCOL_LSID);
        }).collect(Collectors.toSet());

        if (!exportedRuns.isEmpty())
        {
            runsSelection.addRuns(exportedRuns);
            exportRuns = true;
        }

        // UNDONE: The other exporters use FOLDER_RELATIVE, but it wants to use ${AutoFileLSID} replacements for DataClass LSIDs when exporting the TSV data.. see comment in ExportLsidDataColumn
        _relativizedLSIDs = ctx.getRelativizedLSIDs();
        // create the XAR which contains the sample type and data class definitions
        if (exportTypes)
        {
            XarExporter exporter = new XarExporter(_relativizedLSIDs, typesSelection, ctx.getUser(), XAR_TYPES_XML_NAME, ctx.getLogger(), ctx.getContainer());
            try (OutputStream fOut = xarDir.getOutputStream(XAR_TYPES_NAME))
            {
                exporter.writeAsArchive(fOut);
            }
        }

        // create the XAR which contains any derivation protocol runs
        if (exportRuns)
        {
            XarExporter exporter = new XarExporter(_relativizedLSIDs, runsSelection, ctx.getUser(), XAR_RUNS_XML_NAME, ctx.getLogger(), ctx.getContainer());
            try (OutputStream fOut = xarDir.getOutputStream(XAR_RUNS_NAME))
            {
                exporter.writeAsArchive(fOut);
            }
        }

        // write the sample type data as .tsv files
        if (exportSampleTypeData)
            writeSampleTypeDataFiles(sampleTypes, ctx, xarDir);

        exportContext.setSampleXarCreated(true);
    }

    /**
     * Sample derivation protocols involving samples can be either to/from another sample
     * or to/from a data class. If it's the latter, don't include the run if data class data
     * is not included in the archive.
     */
    private boolean isValidRunType(FolderExportContext ctx, ExpRun run)
    {
        if (!run.getDataInputs().isEmpty() || !run.getDataOutputs().isEmpty())
        {
            return ctx.getDataTypes().contains(FolderArchiveDataTypes.DATA_CLASS_DATA);
        }
        return true;
    }

    private void writeSampleTypeDataFiles(Set<ExpSampleType> sampleTypes, FolderExportContext ctx, VirtualFile dir) throws Exception
    {
        // write out the sample rows
        UserSchema userSchema = QueryService.get().getUserSchema(ctx.getUser(), ctx.getContainer(), SamplesSchema.SCHEMA_NAME);
        if (userSchema != null)
        {
            for (ExpSampleType sampleType : sampleTypes)
            {
                TableInfo tinfo = userSchema.getTable(sampleType.getName());
                if (tinfo != null)
                {
                    SimpleFilter filter = SimpleFilter.createContainerFilter(ctx.getContainer());

                    // Sort by RowId so data get exported (and then imported) in the same order as created (default is the reverse order)
                    Sort sort = new Sort(FieldKey.fromParts("RowId"));

                    Collection<ColumnInfo> columns = getColumnsToExport(ctx, tinfo);

                    if (!columns.isEmpty())
                        writeTsv(tinfo, columns, filter, sort, dir, SAMPLE_TYPE_PREFIX + sampleType.getName());

                    writeTsv(tinfo, getStatusColumnsToExport(tinfo), filter, sort, dir, SAMPLE_STATUS_PREFIX + sampleType.getName());
                }
            }
        }
    }

    static ColumnInfo getAliquotedFromNameColumn(TableInfo tinfo)
    {
        ColumnInfo col = tinfo.getColumn(FieldKey.fromParts(ExpMaterialTable.Column.AliquotedFromLSID.name()));
        AliasedColumn aliquotedAlias = new AliasedColumn(tinfo, ExpMaterial.ALIQUOTED_FROM_INPUT, col);
        aliquotedAlias.setDisplayColumnFactory(NameFromLsidDataColumn::new);
        return aliquotedAlias;
    }

    private Collection<ColumnInfo> getStatusColumnsToExport(TableInfo tinfo)
    {
        List<ColumnInfo> columns = new ArrayList<>();

        // Name
        FieldKey nameFieldKey = FieldKey.fromParts(ExpMaterialTable.Column.Name.name());
        ColumnInfo col = tinfo.getColumn(nameFieldKey);
        MutableColumnInfo wrappedCol = WrappedColumnInfo.wrap(col);
        wrappedCol.setDisplayColumnFactory(TsvDataHandler.ExportDataColumn::new);
        columns.add(wrappedCol);

        // SampleState
        // substitute the Label value for the RowId lookup value
        FieldKey statusFieldKey = FieldKey.fromParts(ExpMaterialTable.Column.SampleState.name(), "Label");
        Map<FieldKey, ColumnInfo> select = QueryService.get().getColumns(tinfo, Collections.singletonList(statusFieldKey));
        ColumnInfo statusAlias = new AliasedColumn(tinfo, ExpMaterialTable.Column.SampleState.name(), select.get(statusFieldKey));

        columns.add(statusAlias);

        // In order to update status values for Aliquots, the AliquotedFrom field, which is the
        // name of the aliquot's parent, must be present.  We get the name from the LSID.
        columns.add(getAliquotedFromNameColumn(tinfo));

        return columns;
    }

    @Override
    public boolean shouldExcludeColumn(TableInfo tableInfo, ColumnInfo col, FolderExportContext context)
    {
        if (!super.shouldExcludeColumn(tableInfo, col, context))
        {
            // don't include sample state here so the sample type data and then all related
            // runs, storage info, etc. can be imported without sample state restrictions.
            // SampleState is exported and imported from a separate file.
            return ExpMaterialTable.Column.SampleState.name().equalsIgnoreCase(col.getName());
        }
        return true;
    }

    public static class SampleTypeDesignWriter extends SampleTypeFolderWriter
    {
        @Override
        public @Nullable String getDataType()
        {
            return FolderArchiveDataTypes.SAMPLE_TYPE_DESIGNS;
        }

        public static class Factory implements FolderWriterFactory
        {
            @Override
            public FolderWriter create()
            {
                return new SampleTypeDesignWriter();
            }
        }
    }

    public static class SampleTypeDataWriter extends SampleTypeFolderWriter
    {
        @Override
        public @Nullable String getDataType()
        {
            return FolderArchiveDataTypes.SAMPLE_TYPE_DATA;
        }

        @Override
        public boolean selectedByDefault(AbstractFolderContext.ExportType type, boolean forTemplate)
        {
            return super.selectedByDefault(type, forTemplate) && !forTemplate;
        }

        public static class Factory implements FolderWriterFactory
        {
            @Override
            public FolderWriter create()
            {
                return new SampleTypeDataWriter();
            }
        }
    }
}
