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
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.Results;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TSVGridWriter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.WrappedColumnInfo;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.PropertyType;
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
import org.labkey.experiment.api.ExperimentServiceImpl;
import org.labkey.experiment.xar.XarExportSelection;
import org.labkey.folder.xml.FolderDocument;

import java.io.FileNotFoundException;
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
    private static final String DEFAULT_DIRECTORY = "sample-types";
    private static final String XAR_FILE_NAME = "sample_types.xar";
    private static final String XAR_XML_FILE_NAME = XAR_FILE_NAME + ".xml";
    public static final String SAMPLE_TYPE_PREFIX = "SAMPLE_TYPE_";
    public static final String DATA_CLASS_PREFIX = "DATA_CLASS_";

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
        XarExportSelection selection = new XarExportSelection();
        Set<ExpSampleType> sampleTypes = new HashSet<>();
        Set<ExpDataClass> dataClasses = new HashSet<>();
        boolean exportXar = false;

        Lsid sampleTypeLsid = new Lsid(ExperimentService.get().generateLSID(ctx.getContainer(), ExpSampleType.class, "export"));
        for (ExpSampleType sampleType : SampleTypeService.get().getSampleTypes(ctx.getContainer(), ctx.getUser(), true))
        {
            // ignore the magic sample type that is used for the specimen repository, it is managed by the specimen importer
            if (StudyService.get().getStudy(ctx.getContainer()) != null && SpecimenService.SAMPLE_TYPE_NAME.equals(sampleType.getName()))
                continue;

            // filter out non sample type material sources
            Lsid lsid = new Lsid(sampleType.getLSID());

            if (sampleTypeLsid.getNamespacePrefix().equals(lsid.getNamespacePrefix()))
            {
                sampleTypes.add(sampleType);
                selection.addSampleType(sampleType);
                exportXar = true;
            }
        }

        for (ExpDataClass dataClass : ExperimentService.get().getDataClasses(ctx.getContainer(), ctx.getUser(), false))
        {
            dataClasses.add(dataClass);
            selection.addDataClass(dataClass);
        }

        // add any sample derivation runs
        List<ExpRun> runs = ExperimentService.get().getExpRuns(ctx.getContainer(), null, null).stream()
                .filter(run -> run.getProtocol().getLSID().equals(ExperimentService.SAMPLE_DERIVATION_PROTOCOL_LSID))
                .collect(Collectors.toList());

        Set<ExpRun> exportedRuns = new HashSet<>();
        for (ExpRun run : runs)
        {
            if (exportRun(run, sampleTypes, dataClasses))
                exportedRuns.add(run);
        }

        if (!exportedRuns.isEmpty())
        {
            selection.addRuns(exportedRuns);
            exportXar = true;
        }
        VirtualFile xarDir = vf.getDir(DEFAULT_DIRECTORY);

        if (exportXar)
        {
            XarExporter exporter = new XarExporter(LSIDRelativizer.FOLDER_RELATIVE, selection, ctx.getUser(), XAR_XML_FILE_NAME, ctx.getLogger());

            try (OutputStream fOut = xarDir.getOutputStream(XAR_FILE_NAME))
            {
                exporter.writeAsArchive(fOut);
            }
        }

        // write the sample type data as .tsv files
        writeSampleTypeDataFiles(sampleTypes, ctx, xarDir);

        // write the data class data as .tsv files
        writeDataClassDataFiles(dataClasses, ctx, xarDir);
    }

    private void writeSampleTypeDataFiles(Set<ExpSampleType> sampleTypes, ImportContext<FolderDocument.Folder> ctx, VirtualFile dir) throws Exception
    {
        // write out the sample rows that aren't participating in the derivation protocol
        UserSchema userSchema = QueryService.get().getUserSchema(ctx.getUser(), ctx.getContainer(), SamplesSchema.SCHEMA_NAME);
        if (userSchema != null)
        {
            for (ExpSampleType sampleType : sampleTypes)
            {
                TableInfo tinfo = userSchema.getTable(sampleType.getName());
                if (tinfo != null)
                {
                    Collection<ColumnInfo> columns = getColumnsToExport(tinfo);

                    if (!columns.isEmpty())
                    {
                        // query to get all of the samples that were involved in a derivation protocol, we want to
                        // omit these from the tsv since the XAR importer will currently wire up those rows
                        SQLFragment sql = new SQLFragment("SELECT DISTINCT(materialId) FROM ").append(ExperimentService.get().getTinfoMaterialInput(), "mi")
                                .append(" JOIN ").append(ExperimentService.get().getTinfoMaterial(), "mat")
                                .append(" ON mi.materialId = mat.rowId")
                                .append(" JOIN ").append(ExperimentServiceImpl.get().getSchema().getTable("Object"), "o")
                                .append(" ON mat.objectId = o.objectId")
                                .append(" WHERE o.ownerObjectId = ?")
                                .add(sampleType.getObjectId());

                        Collection<Integer> derivedIds = new SqlSelector(ExperimentService.get().getSchema(), sql).getCollection(Integer.class);
                        SimpleFilter filter = new SimpleFilter();
                        if (!derivedIds.isEmpty())
                            filter.addCondition(FieldKey.fromParts("RowId"), derivedIds, CompareType.NOT_IN);
                        Results rs = QueryService.get().select(tinfo, columns, filter, null);
                        try (TSVGridWriter tsvWriter = new TSVGridWriter(rs))
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

    private void writeDataClassDataFiles(Set<ExpDataClass> dataClasses, ImportContext<FolderDocument.Folder> ctx, VirtualFile dir) throws Exception
    {
        // write out the sample rows that aren't participating in the derivation protocol
        UserSchema userSchema = QueryService.get().getUserSchema(ctx.getUser(), ctx.getContainer(), ExpSchema.SCHEMA_EXP_DATA);
        if (userSchema != null)
        {
            for (ExpDataClass dataClass : dataClasses)
            {
                TableInfo tinfo = userSchema.getTable(dataClass.getName());
                if (tinfo != null)
                {
                    Collection<ColumnInfo> columns = getColumnsToExport(tinfo);

                    if (!columns.isEmpty())
                    {
                        // query to get all of the data rows that were involved in a derivation protocol, we want to
                        // omit these from the tsv since the XAR importer will currently wire up those rows
                        SQLFragment sql = new SQLFragment("SELECT DISTINCT(dataId) FROM ").append(ExperimentService.get().getTinfoDataInput(), "di")
                                .append(" JOIN ").append(ExperimentService.get().getTinfoData(), "dat")
                                .append(" ON di.dataId = dat.rowId")
                                .append(" WHERE dat.cpastype = ?")
                                .add(dataClass.getLSID());

                        Collection<Integer> derivedIds = new SqlSelector(ExperimentService.get().getSchema(), sql).getCollection(Integer.class);
                        SimpleFilter filter = new SimpleFilter();
                        if (!derivedIds.isEmpty())
                            filter.addCondition(FieldKey.fromParts("RowId"), derivedIds, CompareType.NOT_IN);
                        Results rs = QueryService.get().select(tinfo, columns, filter, null);
                        try (TSVGridWriter tsvWriter = new TSVGridWriter(rs))
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

    private Collection<ColumnInfo> getColumnsToExport(TableInfo tinfo)
    {
        Map<FieldKey, ColumnInfo> columns = new LinkedHashMap<>();
        Set<PropertyStorageSpec> baseProps = tinfo.getDomainKind().getBaseProperties(tinfo.getDomain());
        Set<String> basePropNames = baseProps.stream()
                .map(PropertyStorageSpec::getName)
                .collect(Collectors.toCollection(CaseInsensitiveHashSet::new));

        for (ColumnInfo col : tinfo.getColumns())
        {
            if (basePropNames.contains(col.getName()) && !ExpMaterialTable.Column.Name.name().equalsIgnoreCase(col.getName()))
                continue;

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
            else if ((col.isUserEditable() && !col.isHidden() && !col.isReadOnly()) || col.isKeyField())
            {
                MutableColumnInfo wrappedCol = WrappedColumnInfo.wrap(col);
                wrappedCol.setDisplayColumnFactory(colInfo -> new ExportDataColumn(colInfo));
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
        List<DomainProperty> attachmentProps = tinfo.getDomain().getProperties().stream()
                .filter(dp -> dp.getPropertyDescriptor().getPropertyType() == PropertyType.ATTACHMENT)
                .collect(Collectors.toList());

        if (!attachmentProps.isEmpty())
        {
            VirtualFile tableDir = dir.getDir(tinfo.getName());
            Map<String, FileNameUniquifier> uniquifiers = new HashMap<>();
            List<ColumnInfo> selectColumns = new ArrayList<>();

            selectColumns.add(tinfo.getColumn(FieldKey.fromParts(ExpDataClassDataTable.Column.LSID)));
            for (DomainProperty prop : attachmentProps)
            {
                uniquifiers.put(prop.getName(), new FileNameUniquifier());
                selectColumns.add(tinfo.getColumn(FieldKey.fromParts(prop.getName())));
            }

            try (ResultSet rs = QueryService.get().select(tinfo, selectColumns, null, null))
            {
                while (rs.next())
                {
                    Lsid lsid = Lsid.parse(rs.getString(1));
                    AttachmentParent attachmentParent = new ExpDataClassAttachmentParent(c, lsid);
                    int attachmentCol = 2;

                    for (DomainProperty prop : attachmentProps)
                    {
                        String filename = rs.getString(attachmentCol++);

                        // Item might not have an attachment in this column
                        if (filename == null)
                            continue;

                        String columnName = prop.getName();
                        VirtualFile columnDir = tableDir.getDir(columnName);
                        FileNameUniquifier uniquifier = uniquifiers.get(columnName);

                        try (InputStream is = AttachmentService.get().getInputStream(attachmentParent, filename); OutputStream os = columnDir.getOutputStream(uniquifier.uniquify(filename)))
                        {
                            FileUtil.copyData(is, os);
                        }
                        catch (FileNotFoundException e)
                        {
                            // Shouldn't happen... but just skip this file in production if it does
                            assert false;
                        }
                    }
                }
            }
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

    public static class Factory implements FolderWriterFactory
    {
        @Override
        public FolderWriter create()
        {
            return new SampleTypeAndDataClassFolderWriter();
        }
    }
}
