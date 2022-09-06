package org.labkey.experiment.samples;

import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.labkey.api.admin.BaseFolderWriter;
import org.labkey.api.admin.FolderArchiveDataTypes;
import org.labkey.api.admin.FolderExportContext;
import org.labkey.api.admin.FolderWriter;
import org.labkey.api.admin.FolderWriterFactory;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnHeaderType;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.MultiValuedDisplayColumn;
import org.labkey.api.data.MultiValuedForeignKey;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.PHI;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.ResultsFactory;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TSVGridWriter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.WrappedColumnInfo;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.XarExportContext;
import org.labkey.api.exp.api.ColumnExporter;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.query.ExpDataClassDataTable;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.study.SpecimenService;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.FileNameUniquifier;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.writer.VirtualFile;
import org.labkey.experiment.LSIDRelativizer;
import org.labkey.experiment.XarExporter;
import org.labkey.experiment.api.AliasInsertHelper;
import org.labkey.experiment.api.ExpDataClassAttachmentParent;
import org.labkey.experiment.xar.XarExportSelection;

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

public class SampleTypeAndDataClassFolderWriter extends BaseFolderWriter implements ColumnExporter
{
    public static final String DEFAULT_DIRECTORY = "sample-types";
    public static final String XAR_TYPES_NAME = "sample_types.xar";             // the file which contains the sample type and data class definitions
    public static final String XAR_RUNS_NAME = "runs.xar";                      // the file which contains the derivation runs for sample types and data classes
    public static final String XAR_TYPES_XML_NAME = XAR_TYPES_NAME + ".xml";
    public static final String XAR_RUNS_XML_NAME = XAR_RUNS_NAME + ".xml";
    public static final String SAMPLE_TYPE_PREFIX = "SAMPLE_TYPE_";
    public static final String SAMPLE_STATUS_PREFIX = "SAMPLE_STATUS_";
    public static final String DATA_CLASS_PREFIX = "DATA_CLASS_";
    private PHI _exportPhiLevel = PHI.NotPHI;
    private XarExportContext _xarCtx;
    private LSIDRelativizer.RelativizedLSIDs _relativizedLSIDs;

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
    public void write(Container c, FolderExportContext ctx, VirtualFile vf) throws Exception
    {
        // We will divide the sample type and data class definitions from the runs into two separate XAR files, the reason is
        // during import we want all data to be imported via the query update service and any lineage will be wired up
        // by the runs XAR.
        XarExportSelection typesSelection = new XarExportSelection();
        XarExportSelection runsSelection = new XarExportSelection();
        Set<ExpSampleType> sampleTypes = new HashSet<>();
        Set<ExpDataClass> dataClasses = new HashSet<>();
        List<ExpMaterial> materialsToExport = new ArrayList<>();
        List<ExpData> datasToExport = new ArrayList<>();
        _exportPhiLevel = ctx.getPhiLevel();
        boolean exportTypes = false;
        boolean exportRuns = false;
        _xarCtx = ctx.getContext(XarExportContext.class);

        Lsid sampleTypeLsid = new Lsid(ExperimentService.get().generateLSID(c, ExpSampleType.class, "export"));
        for (ExpSampleType sampleType : SampleTypeService.get().getSampleTypes(c, ctx.getUser(), true))
        {
            // ignore the magic sample type that is used for the specimen repository, it is managed by the specimen importer
            StudyService ss = StudyService.get();
            if (ss != null && ss.getStudy(c) != null && SpecimenService.SAMPLE_TYPE_NAME.equals(sampleType.getName()))
                continue;

            // ignore sample types that are filtered out
            if (_xarCtx != null && !_xarCtx.getIncludedSamples().containsKey(sampleType.getRowId()))
                continue;

            // filter out non sample type material sources
            Lsid lsid = new Lsid(sampleType.getLSID());

            if (sampleTypeLsid.getNamespacePrefix().equals(lsid.getNamespacePrefix()))
            {
                Set<Integer> includedSamples = _xarCtx != null ? _xarCtx.getIncludedSamples().get(sampleType.getRowId()) : null;
                sampleTypes.add(sampleType);
                typesSelection.addSampleType(sampleType);
                materialsToExport.addAll(sampleType.getSamples(c).stream()
                    .filter(m -> includedSamples == null || includedSamples.contains(m.getRowId()))
                    .toList());
                exportTypes = true;
            }
        }

        for (ExpDataClass dataClass : ExperimentService.get().getDataClasses(c, ctx.getUser(), false))
        {
            // ignore data classes that are filtered out
            if (_xarCtx != null && !_xarCtx.getIncludedDataClasses().containsKey(dataClass.getRowId()))
                continue;

            Set<Integer> includedDatas = _xarCtx != null ? _xarCtx.getIncludedDataClasses().get(dataClass.getRowId()) : null;
            dataClasses.add(dataClass);
            typesSelection.addDataClass(dataClass);
            datasToExport.addAll(dataClass.getDatas().stream()
                .filter(d -> includedDatas == null || includedDatas.contains(d.getRowId()))
                .toList());
            exportTypes = true;
        }

        // get the list of runs with the materials or data we expect to export, these will be the sample derivation
        // protocol runs to track the lineage
        Set<ExpRun> exportedRuns = new HashSet<>();
        if (!materialsToExport.isEmpty())
            exportedRuns.addAll(ExperimentService.get().getRunsUsingMaterials(materialsToExport));

        if (!datasToExport.isEmpty())
            exportedRuns.addAll(ExperimentService.get().getRunsUsingDatas(datasToExport));
        // only want the sample derivation runs; other runs will get included in the experiment xar.
        exportedRuns = exportedRuns.stream().filter(run -> {
            String lsid = run.getProtocol().getLSID();
            return lsid.equals(ExperimentService.SAMPLE_DERIVATION_PROTOCOL_LSID) ||
                    lsid.equals(ExperimentService.SAMPLE_ALIQUOT_PROTOCOL_LSID);
        }).collect(Collectors.toSet());

        if (!exportedRuns.isEmpty())
        {
            runsSelection.addRuns(exportedRuns);
            exportRuns = true;
        }
        VirtualFile xarDir = vf.getDir(DEFAULT_DIRECTORY);

        // UNDONE: The other exporters use FOLDER_RELATIVE, but it wants to use ${AutoFileLSID} replacements for DataClass LSIDs when exporting the TSV data.. see comment in ExportLsidDataColumn
        _relativizedLSIDs = new LSIDRelativizer.RelativizedLSIDs(LSIDRelativizer.FOLDER_RELATIVE);
        // create the XAR which contains the sample type and data class definitions
        if (exportTypes)
        {
            XarExporter exporter = new XarExporter(_relativizedLSIDs, typesSelection, ctx.getUser(), XAR_TYPES_XML_NAME, ctx.getLogger());
            try (OutputStream fOut = xarDir.getOutputStream(XAR_TYPES_NAME))
            {
                exporter.writeAsArchive(fOut);
            }
        }

        // create the XAR which contains any derivation protocol runs
        if (exportRuns)
        {
            XarExporter exporter = new XarExporter(_relativizedLSIDs, runsSelection, ctx.getUser(), XAR_RUNS_XML_NAME, ctx.getLogger());
            try (OutputStream fOut = xarDir.getOutputStream(XAR_RUNS_NAME))
            {
                exporter.writeAsArchive(fOut);
            }
        }

        // write the sample type data as .tsv files
        writeSampleTypeDataFiles(sampleTypes, ctx, xarDir);

        // write the data class data as .tsv files
        writeDataClassDataFiles(dataClasses, ctx, xarDir);
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

                    // filter only to the specific samples
                    if (_xarCtx != null && _xarCtx.getIncludedSamples().containsKey(sampleType.getRowId()))
                        filter.addInClause(FieldKey.fromParts("RowId"), _xarCtx.getIncludedSamples().get(sampleType.getRowId()));

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

    private ColumnExporter getColumnExporter(TableInfo tInfo, ColumnInfo col, FolderExportContext ctx)
    {
        ColumnExporter exporter = ExperimentService.get().getColumnExporter(tInfo, col, ctx) ;
        return exporter == null ? this : exporter;
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

                        // filter only to the specific data
                        if (_xarCtx != null && _xarCtx.getIncludedDataClasses().containsKey(dataClass.getRowId()))
                            filter.addInClause(FieldKey.fromParts("RowId"), _xarCtx.getIncludedDataClasses().get(dataClass.getRowId()));

                        // Sort by RowId so data get exported (and then imported) in the same order as created (default is the reverse order)
                        writeTsv(tinfo, columns, filter, new Sort(FieldKey.fromParts("RowId")), dir, DATA_CLASS_PREFIX + dataClass.getName());

                        writeAttachments(ctx.getContainer(), tinfo, dir);
                    }
                }
            }
        }
    }

    private void writeTsv(TableInfo tinfo, Collection<ColumnInfo> columns, SimpleFilter filter, Sort sort, VirtualFile dir, String baseName) throws IOException
    {
        ResultsFactory factory = ()->QueryService.get().select(tinfo, columns, filter, sort);
        try (TSVGridWriter tsvWriter = new TSVGridWriter(factory))
        {
            tsvWriter.setApplyFormats(false);
            tsvWriter.setColumnHeaderType(ColumnHeaderType.FieldKey);
            PrintWriter out = dir.getPrintWriter(baseName + ".tsv");
            tsvWriter.write(out);
        }
    }

    private ColumnInfo getAliquotedFromNameColumn(TableInfo tinfo)
    {
        ColumnInfo col = tinfo.getColumn(FieldKey.fromParts(ExpMaterialTable.Column.AliquotedFromLSID.name()));
        AliasedColumn aliquotedAlias = new AliasedColumn(tinfo, "AliquotedFrom", col);
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
        wrappedCol.setDisplayColumnFactory(ExportDataColumn::new);
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
    public boolean shouldExportColumn(ColumnInfo col)
    {
        // don't include sample state here so the sample type data and then all related
        // runs, storage info, etc. can be imported without sample state restrictions.
        // SampleState is exported and imported from a separate file.
        return !ExpMaterialTable.Column.SampleState.name().equalsIgnoreCase(col.getName());
    }

    @Override
    public Collection<ColumnInfo> getExportColumns(TableInfo tinfo, ColumnInfo col, FolderExportContext ctx)
    {
        if (ExpMaterialTable.Column.Flag.name().equalsIgnoreCase(col.getName()))
        {
            // substitute the comment value for the lsid lookup value
            FieldKey flagFieldKey = FieldKey.fromParts(ExpMaterialTable.Column.Flag.name(), "Comment");
            Map<FieldKey, ColumnInfo> select = QueryService.get().getColumns(tinfo, Collections.singletonList(flagFieldKey));
            return Collections.singletonList(new AliasedColumn(tinfo, ExpMaterialTable.Column.Flag.name(), select.get(flagFieldKey)));
        }
        else if (ExpMaterialTable.Column.Alias.name().equalsIgnoreCase(col.getName()))
        {
            MutableColumnInfo aliasCol = WrappedColumnInfo.wrap(col);

            if (tinfo.getSchema().getName().equalsIgnoreCase(SamplesSchema.SCHEMA_NAME))
                aliasCol.setDisplayColumnFactory(new SampleTypeAliasColumnFactory(aliasCol));
            else
                aliasCol.setDisplayColumnFactory(new DataClassAliasColumnFactory(aliasCol));

            return Collections.singletonList(aliasCol);
        }
        else if ("Components".equalsIgnoreCase(col.getName()) && tinfo.getName().equalsIgnoreCase("Molecule"))
        {
            MutableColumnInfo componentsCol = WrappedColumnInfo.wrap(col);

            componentsCol.setDisplayColumnFactory(colInfo -> new MoleculeComponentDataColumn(colInfo, ctx.getUser()));
            return Collections.singletonList(componentsCol);
        }
        else if (col.getFk() instanceof MultiValuedForeignKey)
        {
            MutableColumnInfo multiValueCol = WrappedColumnInfo.wrap(col);
            multiValueCol.setDisplayColumnFactory(colInfo -> new ExportMultiValuedLookupColumn(col));
            return Collections.singletonList(multiValueCol);
            // output json array???
            // skip multi-value columns
            // NOTE: This assumes that we are exporting the junction table and lookup target tables.  Is that ok?
            // NOTE: This needs to happen after the Alias column is handled since it has a MultiValuedForeignKey.
            // CONSIDER: Alternate strategy would be to export the lookup target values?
        }
        else if (ExpMaterialTable.Column.AliquotedFromLSID.name().equalsIgnoreCase(col.getName()))
        {
            // In order to reimport Aliquots, the AliquotedFrom field, which is the
            // name of the aliquot's parent, must be present.  We get the name from the LSID.
            return Collections.singletonList(getAliquotedFromNameColumn(tinfo));
        }
        else if (col.isKeyField() ||
                ExpMaterialTable.Column.LSID.name().equalsIgnoreCase(col.getName()) ||
                (col.isUserEditable() && !col.isHidden() && !col.isReadOnly()))
        {
            MutableColumnInfo wrappedCol = WrappedColumnInfo.wrap(col);
            // Relativize the LSID column or any column with LSID values (e.g. MoleculeSet.intendedMoleculeLsid)
            if ("lsidtype".equalsIgnoreCase(col.getSqlTypeName()) || (col.getName().toLowerCase().endsWith("lsid") && col.isStringType() && col.getScale() == 300))
            {
                wrappedCol.setDisplayColumnFactory(colInfo -> new ExportLsidDataColumn(colInfo, _relativizedLSIDs));
            }
            else
            {
                wrappedCol.setDisplayColumnFactory(ExportDataColumn::new);
            }
            List<ColumnInfo> columns = new ArrayList<>();
            columns.add( wrappedCol);

            // If the column is MV enabled, export the data in the indicator column as well
            if (col.isMvEnabled())
            {
                ColumnInfo mvIndicator = tinfo.getColumn(col.getMvColumnName());
                if (null == mvIndicator)
                    ExceptionUtil.logExceptionToMothership(null, new IllegalStateException("MV indicator column not found: " + tinfo.getName() + "|" + col.getMvColumnName()));
                else
                    columns.add(mvIndicator);
            }
            return columns;
        }
        return null;
    }

    private Collection<ColumnInfo> getColumnsToExport(FolderExportContext ctx, TableInfo tinfo)
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

            ColumnExporter columnExporter = getColumnExporter(tinfo, col, ctx);

            if ((basePropNames.contains(col.getName())
                    && !ExpMaterialTable.Column.Name.name().equalsIgnoreCase(col.getName())
                    && !ExpMaterialTable.Column.LSID.name().equalsIgnoreCase(col.getName()))
                    || !columnExporter.shouldExportColumn(col)
            )
            {
                continue;
            }

            Collection<ColumnInfo> exportColumns = columnExporter.getExportColumns(tinfo, col, ctx);
            if (exportColumns != null)
            {
                exportColumns.forEach(exportCol -> {
                    columns.put(exportCol.getFieldKey(), exportCol);
                });
            }

//            if (ExpMaterialTable.Column.Flag.name().equalsIgnoreCase(col.getName()))
//            {
//                // substitute the comment value for the lsid lookup value
//                FieldKey flagFieldKey = FieldKey.fromParts(ExpMaterialTable.Column.Flag.name(), "Comment");
//                Map<FieldKey, ColumnInfo> select = QueryService.get().getColumns(tinfo, Collections.singletonList(flagFieldKey));
//                ColumnInfo flagAlias = new AliasedColumn(tinfo, ExpMaterialTable.Column.Flag.name(), select.get(flagFieldKey));
//
//                columns.put(flagAlias.getFieldKey(), flagAlias);
//            }
//            else if (ExpMaterialTable.Column.Alias.name().equalsIgnoreCase(col.getName()))
//            {
//                MutableColumnInfo aliasCol = WrappedColumnInfo.wrap(col);
//
//                if (tinfo.getSchema().getName().equalsIgnoreCase(SamplesSchema.SCHEMA_NAME))
//                    aliasCol.setDisplayColumnFactory(new SampleTypeAliasColumnFactory(aliasCol));
//                else
//                    aliasCol.setDisplayColumnFactory(new DataClassAliasColumnFactory(aliasCol));
//
//                columns.put(aliasCol.getFieldKey(), aliasCol);
//            }
//            else if ("Components".equalsIgnoreCase(col.getName()) && tinfo.getName().equalsIgnoreCase("Molecule"))
//            {
//                MutableColumnInfo componentsCol = WrappedColumnInfo.wrap(col);
//
//                componentsCol.setDisplayColumnFactory(colInfo -> new MoleculeComponentDataColumn(colInfo, ctx.getUser()));
//                columns.put(componentsCol.getFieldKey(), componentsCol);
//            }
//            else if (col.getFk() instanceof MultiValuedForeignKey)
//            {
//                MutableColumnInfo multiValueCol = WrappedColumnInfo.wrap(col);
//                multiValueCol.setDisplayColumnFactory(colInfo -> new ExportMultiValuedLookupColumn(col));
//                columns.put(multiValueCol.getFieldKey(), multiValueCol);
//                // output json array???
//                // skip multi-value columns
//                // NOTE: This assumes that we are exporting the junction table and lookup target tables.  Is that ok?
//                // NOTE: This needs to happen after the Alias column is handled since it has a MultiValuedForeignKey.
//                // CONSIDER: Alternate strategy would be to export the lookup target values?
//                ctx.getLogger().info("Skipping multi-value column: " + col.getName());
//            }
//            else if (ExpMaterialTable.Column.AliquotedFromLSID.name().equalsIgnoreCase(col.getName()))
//            {
//                // In order to reimport Aliquots, the AliquotedFrom field, which is the
//                // name of the aliquot's parent, must be present.  We get the name from the LSID.
//                ColumnInfo wrappedCol = getAliquotedFromNameColumn(tinfo);
//                columns.put(col.getFieldKey(), wrappedCol);
//            }
//            else if (col.isKeyField() ||
//                    ExpMaterialTable.Column.LSID.name().equalsIgnoreCase(col.getName()) ||
//                    (col.isUserEditable() && !col.isHidden() && !col.isReadOnly()))
//            {
//                MutableColumnInfo wrappedCol = WrappedColumnInfo.wrap(col);
//                // Relativize the LSID column or any column with LSID values (e.g. MoleculeSet.intendedMoleculeLsid)
//                if ("lsidtype".equalsIgnoreCase(col.getSqlTypeName()) || (col.getName().toLowerCase().endsWith("lsid") && col.isStringType() && col.getScale() == 300))
//                {
//                    wrappedCol.setDisplayColumnFactory(colInfo -> new ExportLsidDataColumn(colInfo, _relativizedLSIDs));
//                }
//                else
//                {
//                    wrappedCol.setDisplayColumnFactory(ExportDataColumn::new);
//                }
//                columns.put(wrappedCol.getFieldKey(), wrappedCol);
//
//                // If the column is MV enabled, export the data in the indicator column as well
//                if (col.isMvEnabled())
//                {
//                    ColumnInfo mvIndicator = tinfo.getColumn(col.getMvColumnName());
//                    if (null == mvIndicator)
//                        ExceptionUtil.logExceptionToMothership(null, new IllegalStateException("MV indicator column not found: " + tinfo.getName() + "|" + col.getMvColumnName()));
//                    else
//                        columns.put(mvIndicator.getFieldKey(), mvIndicator);
//                }
//            }
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

    private static class MoleculeComponentDataColumn extends DataColumn
    {
        private static final Logger LOG = LogHelper.getLogger(MoleculeComponentDataColumn.class, "Data column used for exporting molecule components");
        private final User _user;
        private final ColumnInfo _lookupCol;
        public MoleculeComponentDataColumn(ColumnInfo col, User user)
        {
            super(col);
            _user = user;
            BaseColumnInfo lookupCol = null;

            // Retrieve the value column so it can be used when rendering json or tsv values.
            MultiValuedForeignKey mvfk = (MultiValuedForeignKey)col.getFk();
            ColumnInfo childKey = mvfk.createJunctionLookupColumn(col);
            if (childKey != null && childKey.getFk() != null)
            {
                ForeignKey childKeyFk = childKey.getFk();
                lookupCol = (BaseColumnInfo)childKeyFk.createLookupColumn(childKey, childKeyFk.getLookupColumnName());
                if (lookupCol == null)
                {
                    LOG.warn("Failed to create lookup column from '" + childKey.getName() + "' to '" + childKeyFk.getLookupSchemaKey() + "." + childKeyFk.getLookupTableName() + "." + childKeyFk.getLookupColumnName() + "'");
                }
                else
                {
                    // Remove the intermediate junction table from the FieldKey
                    lookupCol.setFieldKey(new FieldKey(col.getFieldKey(), lookupCol.getFieldKey().getName()));
                }
            }

            _lookupCol = lookupCol;
        }

        @Override
        public Object getValue(RenderContext ctx)
        {
            Object boundValue = getBoundColumn().getValue(ctx);
            ListDefinition listDef = ListService.get().getList(ctx.getContainer(), "MoleculeSequenceJunction");
            if (listDef == null)
                return null;
            TableInfo listTable = listDef.getTable(_user);
            if (listTable == null)
                return null;

            SQLFragment sql = new SQLFragment("SELECT L.stoichiometry, D.name, DC.name as DataClassName FROM ").append(listTable, "L")
                    .append(" JOIN ").append(ExperimentService.get().getTinfoData(), "D")
                    .append(" ON L.componentLsid = D.lsid")
                    .append(" JOIN ").append(ExperimentService.get().getTinfoDataClass(), "DC")
                    .append(" ON DC.rowId = D.classId")
                    .append(" WHERE L.moleculeLsid = ?")
                    .add(boundValue);
            SqlSelector selector = new SqlSelector(ExperimentService.get().getSchema(), sql);
            List<Map<String, Object>> ret = new ArrayList<>();
            selector.mapStream().forEach(map -> {
                Map<String, Object> row = new HashMap<>();
                row.put("type", map.get("DataClassName").equals("NucSequence") ? "nucleotide" : "protein");
                row.put("stoichiometry", map.get("stoichiometry"));
                row.put("name", map.get("name"));
                ret.add(row);
            });

//
//            SimpleFilter filter = SimpleFilter.createContainerFilter(ctx.getContainer());
//            filter.addCondition(FieldKey.fromParts("moleculeLsid"), boundValue);
//            TableSelector tSelector = new TableSelector(listTable, filter, null);
//            // select all values from the junction table where the molecule is the current molecule
//            // [{type: protein, name: PS-17, stoichiometry: 2}, {type: protein, name: PS-18, stoichiometry: 2}]
//            ret = new JSONArray( tSelector.getMapCollection());
            return new JSONArray(ret);
        }
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
        private final ColumnInfo _aliasColumn;

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

    private static class NameFromLsidDataColumn extends ExportDataColumn
    {
        private NameFromLsidDataColumn(ColumnInfo col) { super(col); }

        @Override
        public Object getValue(RenderContext ctx)
        {
            String lsid = (String) super.getValue(ctx);
            if (lsid == null)
                return null;

            String[] parts = lsid.split(":");
            if (parts.length == 0)
                return null;

            return parts[parts.length-1];
        }
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

    private static class ExportMultiValuedLookupColumn extends ExportDataColumn
    {
        private ExportMultiValuedLookupColumn(ColumnInfo col)
        {
            super(col);
        }

//        public ExportMultiValuedLookupColumn(DisplayColumn dc)
//        {
//            super(dc);
//        }
//
        @Override
        public Object getValue(RenderContext ctx)
        {
            return new JSONArray(new MultiValuedDisplayColumn(getBoundColumn().getRenderer(), true).getTsvFormattedValues(ctx));
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
