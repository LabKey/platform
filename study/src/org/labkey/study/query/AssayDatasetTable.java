package org.labkey.study.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.assay.AbstractAssayProvider;
import org.labkey.api.assay.AssayProtocolSchema;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayService;
import org.labkey.api.assay.AssayTableMetadata;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.query.ExpRunTable;
import org.labkey.api.query.AliasManager;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QueryService;
import org.labkey.study.model.DatasetDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AssayDatasetTable extends DatasetTableImpl
{
    /**
     * The assay result LSID column is added to the dataset for assays that support it.
     * @see AssayTableMetadata#getResultLsidFieldKey()
     * @deprecated Use {@link DatasetTableImpl#SOURCE_ROW_LSID}
     */
    @Deprecated
    private static final String LEGACY_ASSAY_RESULT_LSID = "AssayResultLsid";

    private List<FieldKey> _defaultVisibleColumns = null;
    private TableInfo _assayResultTable;

    AssayDatasetTable(@NotNull StudyQuerySchema schema, ContainerFilter cf, @NotNull DatasetDefinition dsd)
    {
        super(schema, cf, dsd);

        TableInfo assayResultTable = getAssayResultTable();
        ExpObject publishSource = _dsd.resolvePublishSource();
        if (assayResultTable != null && publishSource instanceof ExpProtocol)
        {
            ExpProtocol protocol = (ExpProtocol) publishSource;

            AssayProvider provider = AssayService.get().getProvider(protocol);
            AssayTableMetadata tableMeta = provider.getTableMetadata(protocol);
            FieldKey assayResultLsid = tableMeta.getResultLsidFieldKey();

            for (final ColumnInfo columnInfo : assayResultTable.getColumns())
            {
                String name = columnInfo.getName();
                if (assayResultLsid != null && columnInfo.getFieldKey().equals(assayResultLsid))
                {
                    // add the assay result lsid column as "SourceRowLsid" so it won't collide with the dataset's LSID column
                    name = SOURCE_ROW_LSID;
                }

                if (!getColumnNameSet().contains(name))
                {
                    ExprColumn wrappedColumn = wrapPublishSourceColumn(columnInfo, name, (parent) -> getAssayResultAlias(parent));
                    addColumn(wrappedColumn);
                }
            }
        }
    }

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        if (_defaultVisibleColumns != null)
            return _defaultVisibleColumns;

        ExpObject publishSource = _dsd.resolvePublishSource();
        if (publishSource instanceof ExpProtocol)
        {
            ExpProtocol protocol = (ExpProtocol)publishSource;

            // compute default visible columns for assay dataset
            List<FieldKey> defaultVisibleCols = new ArrayList<>(super.getDefaultVisibleColumns());
            TableInfo assayResultTable = getAssayResultTable();
            if (null != assayResultTable)
            {
                for (FieldKey fieldKey : assayResultTable.getDefaultVisibleColumns())
                {
                    if (!defaultVisibleCols.contains(fieldKey) && !defaultVisibleCols.contains(FieldKey.fromParts(fieldKey.getName())))
                    {
                        defaultVisibleCols.add(fieldKey);
                    }
                }

                // Remove the target study column from the dataset version of the table - it's already scoped to the
                // relevant study so don't clutter the UI with it
                for (FieldKey fieldKey : defaultVisibleCols)
                {
                    if (AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME.equals(fieldKey.getName()))
                    {
                        defaultVisibleCols.remove(fieldKey);
                        break;
                    }
                }
                AssayProvider provider = AssayService.get().getProvider(protocol);
                if (null != provider)
                {
                    defaultVisibleCols.add(new FieldKey(provider.getTableMetadata(protocol).getRunFieldKeyFromResults(), ExpRunTable.Column.Name.toString()));
                    defaultVisibleCols.add(new FieldKey(provider.getTableMetadata(protocol).getRunFieldKeyFromResults(), ExpRunTable.Column.Comments.toString()));
                }
            }
            _defaultVisibleColumns = Collections.unmodifiableList(defaultVisibleCols);
            return _defaultVisibleColumns;
        }
        else
            return super.getDefaultVisibleColumns();
    }

    @Override
    protected ColumnInfo resolveColumn(String name)
    {
        var result = super.resolveColumn(name);
        if (result != null)
            return result;

        // backwards compatibility
        if (name.equalsIgnoreCase(LEGACY_ASSAY_RESULT_LSID))
            return super.resolveColumn(SOURCE_ROW_LSID);

        // Be backwards compatible with the old field keys for these properties.
        // We used to flatten all of the different domains/tables on the assay side into a row in the dataset,
        // so transform to do a lookup instead
        ExpObject source = _dsd.resolvePublishSource();
        FieldKey fieldKey = null;
        if (source instanceof ExpProtocol)
        {
            ExpProtocol protocol = (ExpProtocol)source;
            if (protocol != null)
            {
                // First, if it's Properties,
                if ("Properties".equalsIgnoreCase(name))
                {
                    // Hook up a column that joins back to this table so that the columns formerly under the Properties
                    // node when this was OntologyManager-backed can still be queried there
                    var wrapped = wrapColumn("Properties", getRealTable().getColumn("_key"));
                    wrapped.setIsUnselectable(true);
                    LookupForeignKey fk = new LookupForeignKey(getContainerFilter(), "_key", null)
                    {
                        @Override
                        public TableInfo getLookupTableInfo()
                        {
                            return getUserSchema().getTable(_dsd.getName(), getLookupContainerFilter());
                        }

                        @Override
                        public ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
                        {
                            return super.createLookupColumn(parent, displayField);
                        }
                    };
                    fk.setPrefixColumnCaption(false);
                    wrapped.setFk(fk);
                    return wrapped;
                }

                // Second, see the if the assay table can resolve the column
                TableInfo assayTable = getAssayResultTable();
                if (null != assayTable)
                {
                    result = getAssayResultTable().getColumn(name);
                    if (result != null)
                    {
                        return wrapPublishSourceColumn(result, result.getName(), this::getAssayResultAlias);
                    }
                }

                AssayProvider provider = AssayService.get().getProvider(protocol);
                FieldKey runFieldKey = provider == null ? null : provider.getTableMetadata(protocol).getRunFieldKeyFromResults();
                if (name.toLowerCase().startsWith("run"))
                {
                    String runProperty = name.substring("run".length()).trim();
                    if (runProperty.length() > 0 && runFieldKey != null)
                    {
                        fieldKey = new FieldKey(runFieldKey, runProperty);
                    }
                }
                else if (name.toLowerCase().startsWith("batch"))
                {
                    String batchPropertyName = name.substring("batch".length()).trim();
                    if (batchPropertyName.length() > 0 && runFieldKey != null)
                    {
                        fieldKey = new FieldKey(new FieldKey(runFieldKey, "Batch"), batchPropertyName);
                    }
                }
                else if (name.toLowerCase().startsWith("analyte"))
                {
                    String analytePropertyName = name.substring("analyte".length()).trim();
                    if (analytePropertyName.length() > 0)
                    {
                        fieldKey = FieldKey.fromParts("Analyte", analytePropertyName);
                        Map<FieldKey, ColumnInfo> columns = QueryService.get().getColumns(this, Collections.singleton(fieldKey));
                        result = columns.get(fieldKey);
                        if (result != null)
                        {
                            return result;
                        }
                        fieldKey = FieldKey.fromParts("Analyte", "Properties", analytePropertyName);
                    }
                }
                else if (!"SpecimenLsid".equalsIgnoreCase(name))
                {
                    // Try looking for it as a NAb specimen property
                    fieldKey = FieldKey.fromParts("SpecimenLsid", "Property", name);
                    Map<FieldKey, ColumnInfo> columns = QueryService.get().getColumns(this, Collections.singleton(fieldKey));
                    result = columns.get(fieldKey);
                    if (result != null)
                    {
                        return result;
                    }
                }
            }
        }

        if (fieldKey != null)
        {
            Map<FieldKey, ColumnInfo> columns = QueryService.get().getColumns(this, Collections.singleton(fieldKey));
            result = columns.get(fieldKey);
            if (null != result)
            {
                ((BaseColumnInfo)result).setFieldKey(new FieldKey(null,name));
                ((BaseColumnInfo)result).setAlias("_DataSetTableImpl_resolvefield$" + AliasManager.makeLegalName(name, getSqlDialect(), true, false));
            }
        }
        return result;
    }

    @Override
    protected @NotNull SQLFragment _getFromSQL(String alias, boolean includeParticipantVisit)
    {
        SQLFragment sqlf = super._getFromSQL(alias, includeParticipantVisit);

        // Join in Assay-side data to make it appear as if it's in the dataset table itself
        String assayResultAlias = getAssayResultAlias(alias);
        TableInfo assayResultTable = getAssayResultTable();
        // Check if assay design has been deleted
        if (assayResultTable != null)
        {
            sqlf.append(" LEFT OUTER JOIN ").append(assayResultTable.getFromSQL(assayResultAlias)).append("\n");
            sqlf.append(" ON ").append(assayResultAlias).append(".").append(assayResultTable.getPkColumnNames().get(0)).append(" = ");
            sqlf.append(alias).append(".").append(getSqlDialect().getColumnSelectName(_dsd.getKeyPropertyName()));
        }

        return getTransformedFromSQL(sqlf);
    }

    @Override
    public Map<FieldKey, ColumnInfo> getExtendedColumns(boolean includeHidden)
    {
        Map<FieldKey, ColumnInfo> columns = super.getExtendedColumns(includeHidden);

        TableInfo assayResultTable = getAssayResultTable();
        if (assayResultTable != null)
        {
            columns = new LinkedHashMap<>(columns);
            Map<FieldKey, ColumnInfo> assayColumns = assayResultTable.getExtendedColumns(includeHidden);
            // Add the assay column but only if it's not already on the dataset side, see issue 27787
            for (Map.Entry<FieldKey, ColumnInfo> entry : assayColumns.entrySet())
            {
                columns.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
        return columns;
    }

    private TableInfo getAssayResultTable()
    {
        if (_assayResultTable == null)
        {
            ExpObject source = _dsd.resolvePublishSource();
            if (!(source instanceof ExpProtocol))
            {
                return null;
            }
            ExpProtocol protocol = (ExpProtocol)source;
            AssayProvider provider = AssayService.get().getProvider(protocol);
            if (provider == null)
            {
                // Provider must have been in a module that's no longer available
                return null;
            }
            AssayProtocolSchema schema = provider.createProtocolSchema(_userSchema.getUser(), protocol.getContainer(), protocol, getContainer());
            _assayResultTable = schema.createDataTable(ContainerFilter.EVERYTHING, false);
        }
        return _assayResultTable;
    }

    private String getAssayResultAlias(String mainAlias)
    {
        return mainAlias + "_AR";
    }
}
