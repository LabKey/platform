package org.labkey.experiment.samples;

import org.labkey.api.admin.BaseFolderWriter;
import org.labkey.api.admin.FolderArchiveDataTypes;
import org.labkey.api.admin.FolderWriter;
import org.labkey.api.admin.FolderWriterFactory;
import org.labkey.api.admin.ImportContext;
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
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.writer.VirtualFile;
import org.labkey.experiment.LSIDRelativizer;
import org.labkey.experiment.XarExporter;
import org.labkey.experiment.api.AliasInsertHelper;
import org.labkey.experiment.api.ExperimentServiceImpl;
import org.labkey.experiment.xar.XarExportSelection;
import org.labkey.folder.xml.FolderDocument;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SampleTypeFolderWriter extends BaseFolderWriter
{
    private static final String DEFAULT_DIRECTORY = "sample-types";
    private static final String XAR_FILE_NAME = "sample_types.xar";
    private static final String XAR_XML_FILE_NAME = XAR_FILE_NAME + ".xml";

    private SampleTypeFolderWriter()
    {
    }

    @Override
    public String getDataType()
    {
        return FolderArchiveDataTypes.SAMPLE_TYPES;
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

        // add any sample derivation runs
        List<ExpRun> runs = ExperimentService.get().getExpRuns(ctx.getContainer(), null, null).stream()
                .filter(run -> run.getProtocol().getLSID().equals(ExperimentService.SAMPLE_DERIVATION_PROTOCOL_LSID))
                .collect(Collectors.toList());

        Set<ExpRun> exportedRuns = new HashSet<>();
        for (ExpRun run : runs)
        {
            if (exportRun(run, sampleTypes))
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
                            PrintWriter out = xarDir.getPrintWriter(sampleType.getName() + ".tsv");
                            tsvWriter.write(out);
                        }
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
    private boolean exportRun(ExpRun run, Set<ExpSampleType> sampleTypes)
    {
        for (ExpMaterial material : run.getMaterialOutputs())
        {
            if (sampleTypes.contains(material.getSampleType()))
            {
                return true;
            }
        }

        for (ExpMaterial material : run.getMaterialInputs().keySet())
        {
            if (sampleTypes.contains(material.getSampleType()))
            {
                return true;
            }
        }
        return false;
    }

    private Collection<ColumnInfo> getColumnsToExport(TableInfo tinfo)
    {
        Set<ColumnInfo> columns = new LinkedHashSet<>();
        Set<PropertyStorageSpec> baseProps = tinfo.getDomainKind().getBaseProperties(tinfo.getDomain());
        Set<String> basePropNames = baseProps.stream()
                .map(PropertyStorageSpec::getName)
                .collect(Collectors.toCollection(CaseInsensitiveHashSet::new));

        for (ColumnInfo col : tinfo.getColumns())
        {
            if (basePropNames.contains(col.getName()))
                continue;

            if (ExpMaterialTable.Column.Flag.name().equalsIgnoreCase(col.getName()))
            {
                // substitute the comment value for the lsid lookup value
                FieldKey flagFieldKey = FieldKey.fromParts(ExpMaterialTable.Column.Flag.name(), "Comment");
                Map<FieldKey, ColumnInfo> select = QueryService.get().getColumns(tinfo, Collections.singletonList(flagFieldKey));
                ColumnInfo flagAlias = new AliasedColumn(tinfo, ExpMaterialTable.Column.Flag.name(), select.get(flagFieldKey));

                columns.add(flagAlias);
            }
            else if (ExpMaterialTable.Column.Alias.name().equalsIgnoreCase(col.getName()))
            {
                MutableColumnInfo aliasCol = WrappedColumnInfo.wrap(col);

                aliasCol.setDisplayColumnFactory(new DisplayColumnFactory()
                {
                    @Override
                    public DisplayColumn createRenderer(ColumnInfo colInfo)
                    {
                        return new DataColumn(aliasCol)
                        {
                            @Override
                            public Object getValue(RenderContext ctx)
                            {
                                Object val = super.getValue(ctx);

                                if (val != null)
                                {
                                    Collection<String> aliases = AliasInsertHelper.getAliases(String.valueOf(val));
                                    if (!aliases.isEmpty())
                                        return String.join(",", aliases);
                                }
                                return "";
                            }
                        };
                    }
                });
                columns.add(aliasCol);
            }
            else if ((col.isUserEditable() && !col.isHidden() && !col.isReadOnly()) || col.isKeyField())
            {
                MutableColumnInfo wrappedCol = WrappedColumnInfo.wrap(col);
                wrappedCol.setDisplayColumnFactory(colInfo -> new ExportDataColumn(colInfo));
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
            }
        }
        return columns;
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
            return new SampleTypeFolderWriter();
        }
    }
}
