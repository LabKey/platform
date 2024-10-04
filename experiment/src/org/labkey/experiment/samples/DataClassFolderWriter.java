package org.labkey.experiment.samples;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.AbstractFolderContext;
import org.labkey.api.admin.FolderArchiveDataTypes;
import org.labkey.api.admin.FolderExportContext;
import org.labkey.api.admin.FolderWriter;
import org.labkey.api.admin.FolderWriterFactory;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.writer.VirtualFile;
import org.labkey.experiment.XarExporter;
import org.labkey.experiment.xar.XarExportSelection;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class DataClassFolderWriter extends AbstractExpFolderWriter
{
    public static final String DEFAULT_DIRECTORY = "data-classes";
    public static final String XAR_TYPES_NAME = "data_classes.xar";                 // the file which contains data class definitions
    public static final String XAR_TYPES_XML_NAME = XAR_TYPES_NAME + ".xml";
    public static final String DATA_CLASS_PREFIX = "DATA_CLASS_";
    public static final List<String> EXCLUDED_TYPES = List.of("MoleculeSet", "MolecularSpecies");

    private DataClassFolderWriter(){}

    @Override
    public void write(Container c, FolderExportContext ctx, VirtualFile vf) throws Exception
    {
        // We will divide the data class definitions from the runs into two separate XAR files, the reason is
        // during import we want all data to be imported via the query update service and any lineage will be wired up
        // by the runs XAR.
        XarExportSelection typesSelection = new XarExportSelection();
        XarExportSelection runsSelection = new XarExportSelection();
        Set<ExpDataClass> dataClasses = new HashSet<>();
        _exportPhiLevel = ctx.getPhiLevel();
        boolean exportTypes = false;
        boolean exportRuns = false;
        boolean exportDataClassData = ctx.getDataTypes().contains(FolderArchiveDataTypes.DATA_CLASS_DATA);
        VirtualFile xarDir = vf.getDir(DEFAULT_DIRECTORY);

        ExpExportContext exportContext = ctx.getContext(ExpExportContext.class);
        if (exportContext == null)
            throw new IllegalStateException("An instance of ExpExportContext is expected to be available from the FolderExportContext");

        // The design and data folder writers share the same write method. We need to determine
        // if the xar has previously been written to avoid potentially creating the xar twice
        // during the same export pass
        if (exportContext.isDataClassXarCreated())
            return;

        for (ExpDataClass dataClass : ExperimentService.get().getDataClasses(c, ctx.getUser(), false))
        {
            // ignore data classes that are filtered out
            if (EXCLUDED_TYPES.contains(dataClass.getName()))
                continue;

            dataClasses.add(dataClass);
            typesSelection.addDataClass(dataClass);
            exportTypes = true;

            // get the list of runs with the data we expect to export, these will be the sample derivation
            // protocol runs to track the lineage
            if (!dataClass.getDatas().isEmpty() && exportDataClassData)
            {
                List<ExpData> datasToExport = new ArrayList<>(dataClass.getDatas());

                // only want the sample derivation runs; other runs will get included in the experiment xar.
                Set<ExpRun> exportedRuns = ExperimentService.get().getRunsUsingDatas(datasToExport).stream().filter(run -> {
                    String lsid = run.getProtocol().getLSID();
                    return lsid.equals(ExperimentService.SAMPLE_DERIVATION_PROTOCOL_LSID) && isValidRunType(ctx, run);
                }).collect(Collectors.toSet());

                if (!exportedRuns.isEmpty())
                {
                    runsSelection.addRuns(exportedRuns);
                    exportRuns = true;
                }
            }
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

        // write the data class data as .tsv files
        if (exportDataClassData)
            writeDataClassDataFiles(dataClasses, ctx, xarDir);

        exportContext.setDataClassXarCreated(true);
    }

    /**
     * Sample derivation protocols involving data classes can be either to/from another data
     * class or also to/from a sample type. If it's the latter, we will let the sample writer handle it
     * since on import, data classes run before sample types.
     */
    private boolean isValidRunType(FolderExportContext ctx, ExpRun run)
    {
        return run.getMaterialOutputs().isEmpty() && run.getMaterialInputs().isEmpty();
    }

    private void writeDataClassDataFiles(Set<ExpDataClass> dataClasses, FolderExportContext ctx, VirtualFile dir) throws Exception
    {
        // write out the DataClass rows
        UserSchema userSchema = QueryService.get().getUserSchema(ctx.getUser(), ctx.getContainer(), ExpSchema.SCHEMA_EXP_DATA);
        if (userSchema != null)
        {
            for (ExpDataClass dataClass : dataClasses)
            {
                TableInfo tinfo = userSchema.getTable(dataClass.getName());
                if (tinfo != null)
                {
                    Collection<ColumnInfo> columns = getColumnsToExport(ctx, tinfo);

                    if (!columns.isEmpty())
                    {
                        SimpleFilter filter = SimpleFilter.createContainerFilter(ctx.getContainer());

                        // Sort by RowId so data get exported (and then imported) in the same order as created (default is the reverse order)
                        writeTsv(tinfo, columns, filter, new Sort(FieldKey.fromParts("RowId")), dir, DATA_CLASS_PREFIX + dataClass.getName());

                        writeAttachments(ctx.getContainer(), tinfo, dir);
                    }
                }
            }
        }
    }

    public static class DataClassDesignWriter extends DataClassFolderWriter
    {
        @Override
        public @Nullable String getDataType()
        {
            return FolderArchiveDataTypes.DATA_CLASS_DESIGNS;
        }

        public static class Factory implements FolderWriterFactory
        {
            @Override
            public FolderWriter create()
            {
                return new DataClassDesignWriter();
            }
        }
    }

    public static class DataClassDataWriter extends DataClassFolderWriter
    {
        @Override
        public @Nullable String getDataType()
        {
            return FolderArchiveDataTypes.DATA_CLASS_DATA;
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
                return new DataClassDataWriter();
            }
        }
    }
}
