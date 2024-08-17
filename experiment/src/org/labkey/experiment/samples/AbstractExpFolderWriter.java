package org.labkey.experiment.samples;

import org.labkey.api.admin.BaseFolderWriter;
import org.labkey.api.admin.FolderExportContext;
import org.labkey.api.admin.LoggerGetter;
import org.labkey.api.assay.TsvDataHandler;
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
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TSVGridWriter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.WrappedColumnInfo;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ColumnExporter;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.query.ExpDataClassDataTable;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.exp.xar.LSIDRelativizer;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.FileNameUniquifier;
import org.labkey.api.util.FileUtil;
import org.labkey.api.writer.VirtualFile;
import org.labkey.experiment.api.AliasInsertHelper;
import org.labkey.experiment.api.ExpDataClassAttachmentParent;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.labkey.experiment.samples.SampleTypeFolderWriter.getAliquotedFromNameColumn;

public abstract class AbstractExpFolderWriter extends BaseFolderWriter implements ColumnExporter
{
    public static final String XAR_RUNS_NAME = "runs.xar";                      // the file which contains the derivation runs for sample types and data classes
    public static final String XAR_RUNS_XML_NAME = XAR_RUNS_NAME + ".xml";

    protected PHI _exportPhiLevel = PHI.NotPHI;
    protected LSIDRelativizer.RelativizedLSIDs _relativizedLSIDs;

    @Override
    public boolean show(Container c)
    {
        // need to always return true, so it can be used in a folder template
        return true;
    }

    @Override
    public void initialize(FolderExportContext ctx)
    {
        super.initialize(ctx);

        if (ctx.getContext(ExpExportContext.class) == null)
        {
            ctx.addContext(ExpExportContext.class, new ExpExportContext(ctx.getUser(), ctx.getContainer(), ctx.getDataTypes(), null, ctx.getLoggerGetter()));
        }
    }

    protected void writeTsv(TableInfo tinfo, Collection<ColumnInfo> columns, SimpleFilter filter, Sort sort, VirtualFile dir, String baseName) throws IOException
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

    Collection<ColumnInfo> getColumnsToExport(FolderExportContext ctx, TableInfo tinfo)
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

            if ((basePropNames.contains(col.getName())
                    && !ExpMaterialTable.Column.Name.name().equalsIgnoreCase(col.getName())
                    && !ExpMaterialTable.Column.LSID.name().equalsIgnoreCase(col.getName()))
                    || shouldExcludeColumn(tinfo, col, ctx)
            )
            {
                continue;
            }

            Collection<ColumnInfo> exportColumns = getExportColumns(tinfo, col, ctx);
            if (exportColumns != null)
            {
                exportColumns.forEach(exportCol -> {
                    columns.put(exportCol.getFieldKey(), exportCol);
                });
            }
        }
        return columns.values();
    }

    @Override
    public boolean shouldExcludeColumn(TableInfo tableInfo, ColumnInfo col, FolderExportContext context)
    {
        for (ColumnExporter exporter : ExperimentService.get().getColumnExporters())
        {
            if (exporter.shouldExcludeColumn(tableInfo, col, context))
                return true;
        }
        return false;
    }

    @Override
    public Collection<ColumnInfo> getExportColumns(TableInfo tinfo, ColumnInfo col, FolderExportContext ctx)
    {
        Collection<ColumnInfo> columns;
        for (ColumnExporter exporter : ExperimentService.get().getColumnExporters())
        {
            columns = exporter.getExportColumns(tinfo, col, ctx);
            if (columns != null)
                return columns;
        }

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
        else if (col.getFk() instanceof MultiValuedForeignKey)
        {
            // skip multi-value columns
            // NOTE: This assumes that we are exporting the junction table and lookup target tables.  Is that ok?
            // NOTE: This needs to happen after the Alias column is handled since it has a MultiValuedForeignKey.
            // CONSIDER: Alternate strategy would be to export the lookup target values?
            ctx.getLogger().info("Skipping multi-value column: " + col.getName());
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
            else if (PropertyType.FILE_LINK == col.getPropertyType())
            {
                wrappedCol.setDisplayColumnFactory(colInfo -> new TsvDataHandler.ExportFileLinkColumn(colInfo, ctx.getFileRootPath()));
            }
            else
            {
                wrappedCol.setDisplayColumnFactory(TsvDataHandler.ExportDataColumn::new);
            }
            columns = new ArrayList<>();
            columns.add(wrappedCol);

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

    protected boolean isExportable(ColumnInfo col)
    {
        return col.getPHI().isExportLevelAllowed(_exportPhiLevel);
    }

    protected void writeAttachments(Container c, TableInfo tinfo, VirtualFile dir) throws SQLException, IOException
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

    protected static class NameFromLsidDataColumn extends TsvDataHandler.ExportDataColumn
    {
        protected NameFromLsidDataColumn(ColumnInfo col) { super(col); }

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

    // similar to XarExporter.relativizeLSIDPropertyValue but for columns
    private static class ExportLsidDataColumn extends TsvDataHandler.ExportDataColumn
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

    /**
     * Export context to help manage both sample type and data class exports.
     */
    public static class ExpExportContext extends FolderExportContext
    {
        private boolean _sampleXarCreated;
        private boolean _dataClassXarCreated;

        public ExpExportContext(User user, Container c, Set<String> dataTypes, String format, LoggerGetter logger)
        {
            super(user, c, dataTypes, format, logger);
        }

        public boolean isSampleXarCreated()
        {
            return _sampleXarCreated;
        }

        public void setSampleXarCreated(boolean sampleXarCreated)
        {
            _sampleXarCreated = sampleXarCreated;
        }

        public boolean isDataClassXarCreated()
        {
            return _dataClassXarCreated;
        }

        public void setDataClassXarCreated(boolean dataClassXarCreated)
        {
            _dataClassXarCreated = dataClassXarCreated;
        }
    }
}
