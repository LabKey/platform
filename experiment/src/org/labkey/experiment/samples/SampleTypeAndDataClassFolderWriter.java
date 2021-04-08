package org.labkey.experiment.samples;

import org.labkey.api.admin.BaseFolderWriter;
import org.labkey.api.admin.FolderArchiveDataTypes;
import org.labkey.api.admin.FolderWriter;
import org.labkey.api.admin.FolderWriterFactory;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnHeaderType;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.MultiValuedForeignKey;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.PHI;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.ResultsFactory;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TSVGridWriter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.WrappedColumnInfo;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.XarExportContext;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.query.ExpDataClassDataTable;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.study.SpecimenService;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.FileNameUniquifier;
import org.labkey.api.util.FileUtil;
import org.labkey.api.writer.VirtualFile;
import org.labkey.experiment.LSIDRelativizer;
import org.labkey.experiment.XarExporter;
import org.labkey.experiment.api.AliasInsertHelper;
import org.labkey.experiment.api.ExpDataClassAttachmentParent;
import org.labkey.experiment.xar.XarExportSelection;
import org.labkey.folder.xml.FolderDocument;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SampleTypeAndDataClassFolderWriter extends BaseFolderWriter
{
    public static final String DEFAULT_DIRECTORY = "sample-types";
    public static final String XAR_TYPES_NAME = "sample_types.xar";             // the file which contains the sample type and data class definitions
    public static final String XAR_RUNS_NAME = "runs.xar";                      // the file which contains the derivation runs for sample types and data classes
    private static final String XAR_TYPES_XML_NAME = XAR_TYPES_NAME + ".xml";
    private static final String XAR_RUNS_XML_NAME = XAR_RUNS_NAME + ".xml";
    public static final String SAMPLE_TYPE_PREFIX = "SAMPLE_TYPE_";
    public static final String DATA_CLASS_PREFIX = "DATA_CLASS_";
    private PHI _exportPhiLevel = PHI.NotPHI;
    private XarExportContext _xarCtx;

    private SampleTypeAndDataClassFolderWriter()
    {
    }

    @Override
    public String getDataType()
    {
        return FolderArchiveDataTypes.SAMPLE_TYPES_AND_DATA_CLASSES;
    }

    @Override
    public boolean show(Container c)
    {
        // need to always return true so it can be used in a folder template
        return true;
    }

    @Override
    public void write(Container object, ImportContext<FolderDocument.Folder> ctx, VirtualFile vf) throws Exception
    {
        // We will divide the sample type and data class definitions from the runs into two separate XAR files, the reason is
        // during import we want all data to be imported via the query update service and any lineage will be wired up
        // by the runs XAR.
        XarExportSelection typesSelection = new XarExportSelection();
        XarExportSelection runsSelection = new XarExportSelection();
        Set<ExpSampleType> sampleTypes = new HashSet<>();
        Set<ExpDataClass> dataClasses = new HashSet<>();
        _exportPhiLevel = ctx.getPhiLevel();
        boolean exportTypes = false;
        boolean exportRuns = false;
        _xarCtx = ctx.getContext(XarExportContext.class);

        Lsid sampleTypeLsid = new Lsid(ExperimentService.get().generateLSID(ctx.getContainer(), ExpSampleType.class, "export"));
        for (ExpSampleType sampleType : SampleTypeService.get().getSampleTypes(ctx.getContainer(), ctx.getUser(), true))
        {
            // ignore the magic sample type that is used for the specimen repository, it is managed by the specimen importer
            if (StudyService.get().getStudy(ctx.getContainer()) != null && SpecimenService.SAMPLE_TYPE_NAME.equals(sampleType.getName()))
                continue;

            // ignore sample types that are filtered out
            if (_xarCtx != null && !_xarCtx.getIncludedSamples().containsKey(sampleType.getRowId()))
                continue;

            // filter out non sample type material sources
            Lsid lsid = new Lsid(sampleType.getLSID());

            if (sampleTypeLsid.getNamespacePrefix().equals(lsid.getNamespacePrefix()))
            {
                sampleTypes.add(sampleType);
                typesSelection.addSampleType(sampleType);
                exportTypes = true;
            }
        }

        for (ExpDataClass dataClass : ExperimentService.get().getDataClasses(ctx.getContainer(), ctx.getUser(), false))
        {
            // ignore data classes that are filtered out
            if (_xarCtx != null && !_xarCtx.getIncludedDataClasses().containsKey(dataClass.getRowId()))
                continue;

            dataClasses.add(dataClass);
            typesSelection.addDataClass(dataClass);
            exportTypes = true;
        }

        // add any sample derivation or aliquot runs
        List<ExpRun> runs = ExperimentService.get().getExpRuns(ctx.getContainer(), null, null).stream()
                .filter(run -> run.getProtocol().getLSID().equals(ExperimentService.SAMPLE_DERIVATION_PROTOCOL_LSID)
                || run.getProtocol().getLSID().equals(ExperimentService.SAMPLE_ALIQUOT_PROTOCOL_LSID))
                .collect(Collectors.toList());

        Set<ExpRun> exportedRuns = new HashSet<>();
        for (ExpRun run : runs)
        {
            if (exportRun(run, sampleTypes, dataClasses))
                exportedRuns.add(run);
        }

        if (!exportedRuns.isEmpty())
        {
            runsSelection.addRuns(exportedRuns);
            exportRuns = true;
        }
        VirtualFile xarDir = vf.getDir(DEFAULT_DIRECTORY);

        // UNDONE: The other exporters use FOLDER_RELATIVE, but it wants to use ${AutoFileLSID} replacements for DataClass LSIDs when exporting the TSV data.. see comment in ExportLsidDataColumn
        LSIDRelativizer.RelativizedLSIDs relativizedLSIDs = new LSIDRelativizer.RelativizedLSIDs(LSIDRelativizer.FOLDER_RELATIVE);
        // create the XAR which contains the sample type and data class definitions
        if (exportTypes)
        {
            XarExporter exporter = new XarExporter(relativizedLSIDs, typesSelection, ctx.getUser(), XAR_TYPES_XML_NAME, ctx.getLogger());
            try (OutputStream fOut = xarDir.getOutputStream(XAR_TYPES_NAME))
            {
                exporter.writeAsArchive(fOut);
            }
        }

        // create the XAR which contains any derivation protocol runs
        if (exportRuns)
        {
            XarExporter exporter = new XarExporter(relativizedLSIDs, runsSelection, ctx.getUser(), XAR_RUNS_XML_NAME, ctx.getLogger());
            try (OutputStream fOut = xarDir.getOutputStream(XAR_RUNS_NAME))
            {
                exporter.writeAsArchive(fOut);
            }
        }

        // write the sample type data as .tsv files
        writeSampleTypeDataFiles(sampleTypes, ctx, xarDir, relativizedLSIDs);

        // write the data class data as .tsv files
        writeDataClassDataFiles(dataClasses, ctx, xarDir, relativizedLSIDs);
    }

    private void writeSampleTypeDataFiles(Set<ExpSampleType> sampleTypes, ImportContext<FolderDocument.Folder> ctx, VirtualFile dir, LSIDRelativizer.RelativizedLSIDs relativizedLSIDs) throws Exception
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
                    Collection<ColumnInfo> columns = getColumnsToExport(ctx, tinfo, relativizedLSIDs);

                    if (!columns.isEmpty())
                    {
                        SimpleFilter filter = SimpleFilter.createContainerFilter(ctx.getContainer());

                        // filter only to the specific samples
                        if (_xarCtx != null && _xarCtx.getIncludedSamples().containsKey(sampleType.getRowId()))
                            filter.addInClause(FieldKey.fromParts("RowId"), _xarCtx.getIncludedSamples().get(sampleType.getRowId()));

                        ResultsFactory factory = ()->QueryService.get().select(tinfo, columns, filter, null);
                        try (TSVGridWriter tsvWriter = new TSVGridWriter(factory))
                        {
                            tsvWriter.setApplyFormats(false);
                            tsvWriter.setColumnHeaderType(ColumnHeaderType.FieldKey);
                            PrintWriter out = dir.getPrintWriter(SAMPLE_TYPE_PREFIX + sampleType.getName() + ".tsv");
                            tsvWriter.write(out);
                        }
                    }
                }
            }
        }
    }

    private void writeDataClassDataFiles(Set<ExpDataClass> dataClasses, ImportContext<FolderDocument.Folder> ctx, VirtualFile dir, LSIDRelativizer.RelativizedLSIDs relativizedLSIDs) throws Exception
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
                    Collection<ColumnInfo> columns = getColumnsToExport(ctx, tinfo, relativizedLSIDs);

                    if (!columns.isEmpty())
                    {
                        SimpleFilter filter = SimpleFilter.createContainerFilter(ctx.getContainer());

                        // filter only to the specific samples
                        if (_xarCtx != null && _xarCtx.getIncludedDataClasses().containsKey(dataClass.getRowId()))
                            filter.addInClause(FieldKey.fromParts("RowId"), _xarCtx.getIncludedDataClasses().get(dataClass.getRowId()));

                        ResultsFactory factory = ()->QueryService.get().select(tinfo, columns, filter, null);
                        try (TSVGridWriter tsvWriter = new TSVGridWriter(factory))
                        {
                            tsvWriter.setApplyFormats(false);
                            tsvWriter.setColumnHeaderType(ColumnHeaderType.FieldKey);
                            PrintWriter out = dir.getPrintWriter(DATA_CLASS_PREFIX + dataClass.getName() + ".tsv");
                            tsvWriter.write(out);
                        }

                        writeAttachments(ctx.getContainer(), tinfo, dir);
                    }
                }
            }
        }
    }

    /**
     * Checks whether the material inputs or outputs for the run are samples
     * from the set of specified sample types. Will return true if either the
     * inputs or outputs are from the set of sample types.
     */
    private boolean exportRun(ExpRun run, Set<ExpSampleType> sampleTypes, Set<ExpDataClass> dataClasses)
    {
        if (run.getMaterialInputs().keySet().stream().anyMatch(mat -> sampleTypes.contains(mat.getSampleType())))
        {
            return true;
        }

        if (run.getMaterialOutputs().stream().anyMatch(mat -> sampleTypes.contains(mat.getSampleType())))
        {
            return true;
        }

        if (run.getDataInputs().keySet().stream().anyMatch(data -> dataClasses.contains(data.getDataClass(null))))
        {
            return true;
        }

        return run.getDataOutputs().stream().anyMatch(data -> dataClasses.contains(data.getDataClass(null)));
    }

    private Collection<ColumnInfo> getColumnsToExport(ImportContext<FolderDocument.Folder> ctx, TableInfo tinfo, LSIDRelativizer.RelativizedLSIDs relativizedLSIDs)
    {
        Map<FieldKey, ColumnInfo> columns = new LinkedHashMap<>();
        Set<PropertyStorageSpec> baseProps = tinfo.getDomainKind().getBaseProperties(tinfo.getDomain());
        Set<String> basePropNames = baseProps.stream()
                .map(PropertyStorageSpec::getName)
                .collect(Collectors.toCollection(CaseInsensitiveHashSet::new));

        for (ColumnInfo col : tinfo.getColumns())
        {
            if (!isExportable(col))
                continue;

            if (basePropNames.contains(col.getName())
                    && !ExpMaterialTable.Column.Name.name().equalsIgnoreCase(col.getName())
                    && !ExpMaterialTable.Column.LSID.name().equalsIgnoreCase(col.getName()))
            {
                continue;
            }

            if (ExpMaterialTable.Column.Flag.name().equalsIgnoreCase(col.getName()))
            {
                // substitute the comment value for the lsid lookup value
                FieldKey flagFieldKey = FieldKey.fromParts(ExpMaterialTable.Column.Flag.name(), "Comment");
                Map<FieldKey, ColumnInfo> select = QueryService.get().getColumns(tinfo, Collections.singletonList(flagFieldKey));
                ColumnInfo flagAlias = new AliasedColumn(tinfo, ExpMaterialTable.Column.Flag.name(), select.get(flagFieldKey));

                columns.put(flagAlias.getFieldKey(), flagAlias);
            }
            else if (ExpMaterialTable.Column.Alias.name().equalsIgnoreCase(col.getName()))
            {
                MutableColumnInfo aliasCol = WrappedColumnInfo.wrap(col);

                if (tinfo.getSchema().getName().equalsIgnoreCase(SamplesSchema.SCHEMA_NAME))
                    aliasCol.setDisplayColumnFactory(new SampleTypeAliasColumnFactory(aliasCol));
                else
                    aliasCol.setDisplayColumnFactory(new DataClassAliasColumnFactory(aliasCol));

                columns.put(aliasCol.getFieldKey(), aliasCol);
            }
            else if (col.getFk() instanceof MultiValuedForeignKey)
            {
                // skip multi-value columns
                // NOTE: This assumes that we are exporting the junction table and lookup target tables.  Is that ok?
                // NOTE: This needs to happen after the Alias column is handled since it has a MultiValuedForeignKey.
                // CONSIDER: Alternate strategy would be to export the lookup target values?
                ctx.getLogger().info("Skipping multi-value column: " + col.getName());
            }
            else if (col.isKeyField() ||
                    ExpMaterialTable.Column.LSID.name().equalsIgnoreCase(col.getName()) ||
                    (col.isUserEditable() && !col.isHidden() && !col.isReadOnly()))
            {
                MutableColumnInfo wrappedCol = WrappedColumnInfo.wrap(col);
                // Relativize the LSID column or any column with LSID values (e.g. MoleculeSet.intendedMoleculeLsid)
                if ("lsidtype".equalsIgnoreCase(col.getSqlTypeName()) || (col.getName().toLowerCase().endsWith("lsid") && col.isStringType() && col.getScale() == 300))
                {
                    wrappedCol.setDisplayColumnFactory(colInfo -> new ExportLsidDataColumn(colInfo, relativizedLSIDs));
                }
                else
                {
                    wrappedCol.setDisplayColumnFactory(ExportDataColumn::new);
                }
                columns.put(wrappedCol.getFieldKey(), wrappedCol);

                // If the column is MV enabled, export the data in the indicator column as well
                if (col.isMvEnabled())
                {
                    ColumnInfo mvIndicator = tinfo.getColumn(col.getMvColumnName());
                    if (null == mvIndicator)
                        ExceptionUtil.logExceptionToMothership(null, new IllegalStateException("MV indicator column not found: " + tinfo.getName() + "|" + col.getMvColumnName()));
                    else
                        columns.put(mvIndicator.getFieldKey(), mvIndicator);
                }
            }
        }
        return columns.values();
    }

    private void writeAttachments(Container c, TableInfo tinfo, VirtualFile dir) throws SQLException, IOException
    {
        List<ColumnInfo> attachmentCols = new ArrayList<>();
        for (DomainProperty prop : tinfo.getDomain().getProperties())
        {
            if (prop.getPropertyDescriptor().getPropertyType() == PropertyType.ATTACHMENT)
            {
                ColumnInfo col = tinfo.getColumn(prop.getName());

                if (isExportable(col))
                    attachmentCols.add(col);
            }
        }

        if (!attachmentCols.isEmpty())
        {
            VirtualFile tableDir = dir.getDir(tinfo.getName());
            Map<String, FileNameUniquifier> uniquifiers = new HashMap<>();
            List<ColumnInfo> selectColumns = new ArrayList<>();

            selectColumns.add(tinfo.getColumn(FieldKey.fromParts(ExpDataClassDataTable.Column.LSID)));
            selectColumns.addAll(attachmentCols);

            for (ColumnInfo col : attachmentCols)
                uniquifiers.put(col.getName(), new FileNameUniquifier());

            try (ResultSet rs = QueryService.get().select(tinfo, selectColumns, null, null))
            {
                while (rs.next())
                {
                    Lsid lsid = Lsid.parse(rs.getString(1));
                    AttachmentParent attachmentParent = new ExpDataClassAttachmentParent(c, lsid);
                    int attachmentCol = 2;

                    for (ColumnInfo col : attachmentCols)
                    {
                        String filename = rs.getString(attachmentCol++);

                        // Item might not have an attachment in this column
                        if (filename == null)
                            continue;

                        String columnName = col.getName();
                        VirtualFile columnDir = tableDir.getDir(columnName);
                        FileNameUniquifier uniquifier = uniquifiers.get(columnName);

                        try (InputStream is = AttachmentService.get().getInputStream(attachmentParent, filename); OutputStream os = columnDir.getOutputStream(uniquifier.uniquify(filename)))
                        {
                            FileUtil.copyData(is, os);
                        }
                    }
                }
            }
        }
    }

    private boolean isExportable(ColumnInfo col)
    {
        return col.getPHI().isExportLevelAllowed(_exportPhiLevel);
    }

    private static class SampleTypeAliasColumnFactory extends AbstractAliasColumnFactory
    {
        public SampleTypeAliasColumnFactory(ColumnInfo aliasColumn)
        {
            super(aliasColumn);
        }

        @Override
        Collection<String> getAliases(String lsid)
        {
            return AliasInsertHelper.getAliases(lsid);
        }
    }

    private static class DataClassAliasColumnFactory extends AbstractAliasColumnFactory
    {
        public DataClassAliasColumnFactory(ColumnInfo aliasColumn)
        {
            super(aliasColumn);
        }

        @Override
        Collection<String> getAliases(String lsid)
        {
            SQLFragment sql = new SQLFragment("SELECT AL.name FROM ").append(ExperimentService.get().getTinfoAlias(), "AL")
                    .append(" JOIN ").append(ExperimentService.get().getTinfoDataAliasMap(), "DM")
                    .append(" ON AL.rowId = DM.alias")
                    .append(" WHERE DM.lsid = ?")
                    .add(lsid);

            return new SqlSelector(ExperimentService.get().getSchema(), sql).getCollection(String.class);
        }
    }

    private abstract static class AbstractAliasColumnFactory implements DisplayColumnFactory
    {
        private ColumnInfo _aliasColumn;

        public AbstractAliasColumnFactory(ColumnInfo aliasColumn)
        {
            _aliasColumn = aliasColumn;
        }

        @Override
        public DisplayColumn createRenderer(ColumnInfo colInfo)
        {
            return new DataColumn(_aliasColumn)
            {
                @Override
                public Object getValue(RenderContext ctx)
                {
                    Object val = super.getValue(ctx);

                    if (val != null)
                    {
                        Collection<String> aliases = getAliases(String.valueOf(val));
                        if (!aliases.isEmpty())
                            return String.join(",", aliases);
                    }
                    return "";
                }
            };
        }

        abstract Collection<String> getAliases(String lsid);
    }

    private static class ExportDataColumn extends DataColumn
    {
        private ExportDataColumn(ColumnInfo col)
        {
            super(col);
        }

        @Override
        public Object getDisplayValue(RenderContext ctx)
        {
            return getValue(ctx);
        }
    }

    // similar to XarExporter.relativizeLSIDPropertyValue but for columns
    private static class ExportLsidDataColumn extends ExportDataColumn
    {
        private final LSIDRelativizer.RelativizedLSIDs relativizedLSIDs;

        private ExportLsidDataColumn(ColumnInfo col, LSIDRelativizer.RelativizedLSIDs relativizedLSIDs)
        {
            super(col);
            this.relativizedLSIDs = relativizedLSIDs;
        }

        @Override
        public Object getValue(RenderContext ctx)
        {
            Object o = super.getValue(ctx);
            if (o instanceof String)
            {
                String s = (String)o;
                assert Lsid.isLsid(s);
                // UNDONE: This always generates the ${AutoFileLSID} when using FOLDER_RELATIVE!! why do we do that!?!
                String lsid = relativizedLSIDs.relativize(s);
                return lsid;
            }

            return o;
        }
    }

    public static class Factory implements FolderWriterFactory
    {
        @Override
        public FolderWriter create()
        {
            return new SampleTypeAndDataClassFolderWriter();
        }
    }
}
