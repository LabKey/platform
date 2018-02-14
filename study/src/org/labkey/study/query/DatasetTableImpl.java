/*
 * Copyright (c) 2006-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.study.query;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.compliance.TableRules;
import org.labkey.api.compliance.TableRulesManager;
import org.labkey.api.data.AbstractForeignKey;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerFilterable;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.OORDisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.query.ExpRunTable;
import org.labkey.api.query.AliasManager;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.PdLookupForeignKey;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.UserIdQueryForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.DatasetTable;
import org.labkey.api.study.DataspaceContainerFilter;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.assay.AbstractAssayProvider;
import org.labkey.api.study.assay.AssayProtocolSchema;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.SpecimenForeignKey;
import org.labkey.api.util.ContainerContext;
import org.labkey.api.util.DemoMode;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.ActionURL;
import org.labkey.data.xml.TableType;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.DatasetController;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.ParticipantGroup;
import org.labkey.study.model.QCState;
import org.labkey.study.model.StudyManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Wraps a DatasetSchemaTableInfo and makes it Query-ized. Represents a single dataset's data */
public class DatasetTableImpl extends BaseStudyTable implements DatasetTable
{
    public static final String QCSTATE_ID_COLNAME = "QCState";
    public static final String QCSTATE_LABEL_COLNAME = "QCStateLabel";

    private final @NotNull DatasetDefinition _dsd;

    private TableInfo _fromTable;
    private ContainerFilterable _assayResultTable;

    public DatasetTableImpl(@NotNull final StudyQuerySchema schema, @NotNull DatasetDefinition dsd)
    {
        super(schema, dsd.getTableInfo(schema.getUser(), schema.getMustCheckPermissions(), true));

        TimepointType timepointType = dsd.getStudy().getTimepointType();

        String nameLabel = dsd.getName();
        if (!dsd.getLabel().equalsIgnoreCase(dsd.getName()))
            nameLabel += " (" + dsd.getLabel() + ")";
        String keyDescription = dsd.getKeyTypeDescription();
        setDescription("Contains up to one row of " + nameLabel + " data for each " + keyDescription + (keyDescription.contains("/") ? " combination." : "."));
        _dsd = dsd;
        _title = dsd.getLabel();

        HashMap<String,DomainProperty> properties = new HashMap<>();
        Domain dd = _dsd.getDomain();
        if (dd != null)
        {
            for (DomainProperty dp : dd.getProperties())
                properties.put(dp.getPropertyURI(), dp);
        }

        List<FieldKey> defaultVisibleCols = new ArrayList<>();

        HashSet<String> standardURIs = new HashSet<>();
        for (PropertyDescriptor pd : DatasetDefinition.getStandardPropertiesSet())
            standardURIs.add(pd.getPropertyURI());

        ActionURL updateURL = new ActionURL(DatasetController.UpdateAction.class, dsd.getContainer());
        updateURL.addParameter("datasetId", dsd.getDatasetId());
        setUpdateURL(new DetailsURL(updateURL, Collections.singletonMap("lsid", "lsid")));

        ActionURL insertURL = new ActionURL(DatasetController.InsertAction.class, getContainer());
        insertURL.addParameter(DatasetDefinition.DATASETKEY, dsd.getDatasetId());
        setInsertURL(new DetailsURL(insertURL));

        ActionURL gridURL = new ActionURL(StudyController.DatasetAction.class, dsd.getContainer());
        gridURL.addParameter(DatasetDefinition.DATASETKEY, dsd.getDatasetId());
        setGridURL(new DetailsURL(gridURL));

//        ActionURL importURL = new ActionURL(StudyController.ShowImportDatasetAction.class, dsd.getContainer());
        ActionURL importURL = new ActionURL(StudyController.ImportAction.class, dsd.getContainer());
        importURL.addParameter(DatasetDefinition.DATASETKEY, dsd.getDatasetId());
        setImportURL(new DetailsURL(importURL));

        ActionURL deleteRowsURL = new ActionURL(StudyController.DeleteDatasetRowsAction.class, dsd.getContainer());
        setDeleteURL(new DetailsURL(deleteRowsURL));

        String subjectColName = StudyService.get().getSubjectColumnName(dsd.getContainer());
        for (ColumnInfo baseColumn : getRealTable().getColumns())
        {
            String name = baseColumn.getName();
            if (subjectColName.equalsIgnoreCase(name))
            {
                ColumnInfo column = new AliasedColumn(this, subjectColName, baseColumn);
                column.setInputType("text");
                // TODO, need a way for a lookup to have a "text" input
                column.setDisplayColumnFactory(colInfo -> {
                    DataColumn dataColumn = new DataColumn(colInfo, false);
                    dataColumn.setInputType("text");
                    return dataColumn;
                });

                column.setFk(new ParticipantForeignKey());
                if (null == column.getURL())
                    column.setURL(column.getFk().getURL(column));

                if (DemoMode.isDemoMode(schema.getContainer(), schema.getUser()))
                {
                    column.setDisplayColumnFactory(PtidObfuscatingDisplayColumn::new);
                }

                addColumn(column);
                if (isVisibleByDefault(column))
                    defaultVisibleCols.add(FieldKey.fromParts(column.getName()));
            }
            else if (getRealTable().getColumn(name + OORDisplayColumnFactory.OOR_INDICATOR_COLUMN_SUFFIX) != null)
            {
                // If this is the value column that goes with an OORIndicator, add the special OOR options
                OORDisplayColumnFactory.addOORColumns(this, baseColumn, getRealTable().getColumn(name +
                        OORDisplayColumnFactory.OOR_INDICATOR_COLUMN_SUFFIX));
                if (isVisibleByDefault(baseColumn))
                    defaultVisibleCols.add(FieldKey.fromParts(name));
            }
            else if (name.toLowerCase().endsWith(OORDisplayColumnFactory.OOR_INDICATOR_COLUMN_SUFFIX.toLowerCase()) &&
                    getRealTable().getColumn(name.substring(0, name.length() - OORDisplayColumnFactory.OOR_INDICATOR_COLUMN_SUFFIX.length())) != null)
            {
                // If this is an OORIndicator and there's a matching value column in the same table, don't add this column
            }
            else if (name.equalsIgnoreCase("Created") || name.equalsIgnoreCase("Modified") ||
                    name.equalsIgnoreCase("CreatedBy") || name.equalsIgnoreCase("ModifiedBy")
            )
            {
                ColumnInfo c = addWrapColumn(baseColumn);
                if (name.equalsIgnoreCase("CreatedBy") || name.equalsIgnoreCase("ModifiedBy"))
                    UserIdQueryForeignKey.initColumn(schema.getUser(), schema.getContainer(), c, true);
                c.setUserEditable(false);
                c.setShownInInsertView(false);
                c.setShownInUpdateView(false);
            }
            else if (name.equalsIgnoreCase("SequenceNum"))
            {
                ColumnInfo c = addWrapColumn(baseColumn);
                if (!timepointType.isVisitBased())
                {
                    c.setHidden(true);
                    c.setShownInInsertView(false);
                    c.setShownInDetailsView(false);
                    c.setShownInUpdateView(false);

                    // set a format to prevent scientific notation, since the sequencenum values
                    // for date-based studies can be quite large (e.g., 20091014).
                    c.setFormat("#");
                }
                //Don't add to default visible cols...
            }
            else if (name.equalsIgnoreCase("VisitRowId")||name.equalsIgnoreCase("Dataset"))
            {
                addWrapColumn(baseColumn).setHidden(baseColumn.isHidden());
            }
            else if (name.equalsIgnoreCase(QCSTATE_ID_COLNAME))
            {
                ColumnInfo qcStateColumn = new AliasedColumn(this, QCSTATE_ID_COLNAME, baseColumn);
                qcStateColumn.setFk(new QueryForeignKey(QueryService.get().getUserSchema(schema.getUser(), getContainer(), "core"), getContainer(), "QCState","RowId", "Label"));
                qcStateColumn.setDisplayColumnFactory(QCStateDisplayColumn::new);

                qcStateColumn.setDimension(false);

                addColumn(qcStateColumn);
                // Hide the QCState column if the study doesn't have QC states defined. Otherwise, don't hide it
                // but don't include it in the default set of columns either
                if (!StudyManager.getInstance().showQCStates(_userSchema.getContainer()))
                    qcStateColumn.setHidden(true);
            }
            else if ("ParticipantSequenceNum".equalsIgnoreCase(name))
            {
                // Add a copy of the ParticipantSequenceNum column without the FK so we can get the value easily when materializing to temp tables:
                addWrapColumn(baseColumn).setHidden(true);
                ColumnInfo pvColumn = new AliasedColumn(this, StudyService.get().getSubjectVisitColumnName(dsd.getContainer()), baseColumn);//addWrapColumn(baseColumn);
                pvColumn.setFk(new ParticipantVisitForeignKey("ParticipantSequenceNum"));
                pvColumn.setIsUnselectable(true);
                pvColumn.setUserEditable(false);
                pvColumn.setShownInInsertView(false);
                pvColumn.setShownInUpdateView(false);
                pvColumn.setDimension(false);
                addColumn(pvColumn);
                if (timepointType.isVisitBased())
                    defaultVisibleCols.add(new FieldKey(pvColumn.getFieldKey(), "Visit"));
            }
            else
            {
                ColumnInfo col = addWrapColumn(baseColumn);

                // When copying a column, the hidden bit is not propagated, so we need to do it manually
                if (baseColumn.isHidden())
                    col.setHidden(true);
                // "Date" is not in default column-list, but shouldn't automatically be hidden either
                if (!isVisibleByDefault(baseColumn) && !"Date".equalsIgnoreCase(baseColumn.getName()))
                    col.setHidden(true);

                String propertyURI = col.getPropertyURI();
                if (null != propertyURI && !standardURIs.contains(propertyURI))
                {
                    // this is expensive
                    //PropertyDescriptor pd = OntologyManager.getPropertyDescriptor(propertyURI, schema.getContainer());
                    DomainProperty dp = properties.get(propertyURI);
                    PropertyDescriptor pd = (null==dp) ? null : dp.getPropertyDescriptor();

                    // Issue 28671: Dataset with Lookup & MV Indicator enabled on field displays missing value as foreign key upon export
                    // MvIndicator has the same propertyURI as the value column, but should not copy the value column's foreign key
                    if (!col.isMvIndicatorColumn() && null != dp && (pd.getLookupQuery() != null || pd.getConceptURI() != null))
                        col.setFk(new PdLookupForeignKey(schema.getUser(), pd, schema.getContainer()));


                    if (pd != null && pd.getPropertyType() == PropertyType.MULTI_LINE)
                    {
                        col.setDisplayColumnFactory(colInfo -> {
                            DataColumn dc = new DataColumn(colInfo);
                            dc.setPreserveNewlines(true);
                            return dc;
                        });
                    }
                }
                if (isVisibleByDefault(col))
                    defaultVisibleCols.add(FieldKey.fromParts(col.getName()));

                // Add a magic lookup to an "GlobalUniqueId" columns that targets the SpecimenDetails query based on the
                // GlobalUniqueId value instead of the real PK, which is RowId (even if FK was already set (#25489))
                if (AbstractAssayProvider.SPECIMENID_PROPERTY_NAME.equalsIgnoreCase(col.getName()) && col.getJdbcType() == JdbcType.VARCHAR)
                {
                    col.setFk(new LookupForeignKey(SpecimenDetailTable.GLOBAL_UNIQUE_ID_COLUMN_NAME)
                    {
                        @Override
                        public TableInfo getLookupTableInfo()
                        {
                            TableInfo table = _userSchema.getTable(StudyQuerySchema.SPECIMEN_DETAIL_TABLE_NAME);
                            if (table instanceof SpecimenDetailTable)       // Could be a UnionTable, which should already have right containers
                                ((SpecimenDetailTable)table).addCondition(new SimpleFilter(FieldKey.fromParts("Container"), _userSchema.getContainer().getId()));
                            return table;
                        }
                    });
                }
            }
        }

        // TODO move "LSID" column handling to the loop above for consistency? (MAB)
        ColumnInfo lsidColumn = getColumn("LSID");
        lsidColumn.setHidden(true);
        lsidColumn.setKeyField(true);
        lsidColumn.setShownInInsertView(false);
        lsidColumn.setShownInUpdateView(false);
        getColumn("SourceLSID").setHidden(true);

        ColumnInfo dsRowIdColumn = getColumn("dsrowid");

        // Importing old study may not have this column
        if (dsRowIdColumn != null)
        {
            dsRowIdColumn.setHidden(true);
            dsRowIdColumn.setKeyField(false);
            dsRowIdColumn.setShownInInsertView(false);
            dsRowIdColumn.setShownInUpdateView(false);
            getColumn("dsrowid").setHidden(true);
        }

        if (null != _userSchema.getStudy() && !_userSchema.getStudy().isDataspaceStudy() && null == getColumn("container"))
            addContainerColumn(true);

        ColumnInfo autoJoinColumn = new AliasedColumn(this, "DataSets", _rootTable.getColumn("ParticipantId"));
        autoJoinColumn.setDescription("Contains lookups to each Dataset that can be joined by the " + _dsd.getLabel() + " Dataset's '" + _dsd.getKeyTypeDescription() + "' combination.");
        autoJoinColumn.setKeyField(false);
        autoJoinColumn.setIsUnselectable(true);
        autoJoinColumn.setUserEditable(false);
        autoJoinColumn.setCalculated(true);
        autoJoinColumn.setLabel("DataSets");
        final FieldKey sequenceNumFieldKey = new FieldKey(null, "SequenceNum");
        final FieldKey keyFieldKey = new FieldKey(null, "_Key");
        AbstractForeignKey autoJoinFk = new AbstractForeignKey()
        {
            @Override
            public ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
            {
                if (displayField == null)
                    return null;

                DatasetAutoJoinTable table = new DatasetAutoJoinTable(schema, DatasetTableImpl.this.getDatasetDefinition(), parent, getRemappedField(sequenceNumFieldKey), getRemappedField(keyFieldKey));
                return table.getColumn(displayField);
            }

            @Override
            public TableInfo getLookupTableInfo()
            {
                return new DatasetAutoJoinTable(schema, DatasetTableImpl.this.getDatasetDefinition(), null, null, null);
            }

            @Override
            public StringExpression getURL(ColumnInfo parent)
            {
                return null;
            }
        };
        autoJoinFk.addSuggested(sequenceNumFieldKey);
        autoJoinFk.addSuggested(keyFieldKey);
        autoJoinColumn.setFk(autoJoinFk);
        addColumn(autoJoinColumn);

        setDefaultVisibleColumns(defaultVisibleCols);

        // columns from the ParticipantVisit table

        TableInfo participantVisit = StudySchema.getInstance().getTableInfoParticipantVisit();
        if (timepointType == TimepointType.DATE)
        {
            ColumnInfo dayColumn = new AliasedColumn(this, "Day", participantVisit.getColumn("Day"));
            dayColumn.setUserEditable(false);
            dayColumn.setDimension(false);
            dayColumn.setMeasure(false);
            addColumn(dayColumn);
        }

        // TODO move "VisitRowId" to resolveColumn()?  Do we need to expose this? (MAB)
        ColumnInfo visitRowId = new AliasedColumn(this, "VisitRowId", participantVisit.getColumn("VisitRowId"));
        visitRowId.setName("VisitRowId");
        visitRowId.setHidden(true);
        visitRowId.setUserEditable(false);
        visitRowId.setShownInInsertView(false);
        visitRowId.setShownInUpdateView(false);
        visitRowId.setMeasure(false);
        addColumn(visitRowId);

        if (_dsd.isAssayData())
        {
            TableInfo assayResultTable = getAssayResultTable();
            if (assayResultTable != null)
            {
                for (final ColumnInfo columnInfo : assayResultTable.getColumns())
                {
                    if (!getColumnNameSet().contains(columnInfo.getName()))
                    {
                        ExprColumn wrappedColumn = wrapAssayColumn(columnInfo);
                        addColumn(wrappedColumn);
                    }
                }
            }
        }

        addFolderColumn();
    }

    @Override
    public boolean supportTableRules()
    {
        return true;
    }

    @Override
    public Set<FieldKey> getPHIDataLoggingColumns()
    {
        String subjectColName = StudyService.get().getSubjectColumnName(getContainer());
        Set<FieldKey> loggingColumns = new HashSet<>(1);
        for (ColumnInfo col : getColumns())
        {
            if (StringUtils.equalsIgnoreCase(subjectColName, col.getName()))
            {
                loggingColumns.add(col.getFieldKey());
                break;
            }
        }

        return loggingColumns;
    }

    @Override
    public String getPHILoggingComment()
    {
        return "PHI accessed in dataset. Data shows " + StudyService.get().getSubjectColumnName(getContainer())+ ".";
    }

    List<FieldKey> _assayDefaultVisibleColumns = null;

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        if (!_dsd.isAssayData())
            return super.getDefaultVisibleColumns();
        else if (null != _assayDefaultVisibleColumns)
            return _assayDefaultVisibleColumns;

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
            ExpProtocol protocol = _dsd.getAssayProtocol();
            AssayProvider provider = AssayService.get().getProvider(protocol);
            if (null != provider)
            {
                defaultVisibleCols.add(new FieldKey(provider.getTableMetadata(protocol).getRunFieldKeyFromResults(), ExpRunTable.Column.Name.toString()));
                defaultVisibleCols.add(new FieldKey(provider.getTableMetadata(protocol).getRunFieldKeyFromResults(), ExpRunTable.Column.Comments.toString()));
            }
        }
        _assayDefaultVisibleColumns = Collections.unmodifiableList(defaultVisibleCols);
        return _assayDefaultVisibleColumns;
    }


    @Override
    public boolean hasContainerColumn()
    {
        return null != _rootTable.getColumn("container");
    }

    @Override
    public String getContainerFilterColumn()
    {
        if (null == _rootTable.getColumn("container"))
                return null;

        return "Container";
    }

    @Override
    protected ColumnInfo addFolderColumn()
    {
        // Workaround to prevent IllegalArgumentException for assay tables
        if (getColumn("Folder") == null)
        {
            ColumnInfo ci = _rootTable.getColumn("Container");
            if (null == ci)
            {
                ci = getColumn("Container");
            }
            ColumnInfo folder = new AliasedColumn(this, "Folder", ci);
            ContainerForeignKey.initColumn(folder,getUserSchema());
            folder.setHidden(true);
            addColumn(folder);
        }
        return getColumn("Folder");
    }

    /** Wrap a column in our underlying assay data table with one that puts it in the dataset table */
    private ExprColumn wrapAssayColumn(final ColumnInfo columnInfo)
    {
        ExprColumn wrappedColumn = new ExprColumn(this, columnInfo.getName(), columnInfo.getValueSql(ExprColumn.STR_TABLE_ALIAS + "_AR"), columnInfo.getJdbcType())
        {
            @Override
            public void declareJoins(String parentAlias, Map<String, SQLFragment> map)
            {
                super.declareJoins(parentAlias, map);
                columnInfo.declareJoins(getAssayResultAlias(parentAlias), map);
            }
        };
        wrappedColumn.copyAttributesFrom(columnInfo);

        // When copying a column, the hidden bit is not propagated, so we need to do it manually
        if (columnInfo.isHidden())
            wrappedColumn.setHidden(true);

        ForeignKey fk = wrappedColumn.getFk();
        if (fk != null && fk instanceof SpecimenForeignKey)
            ((SpecimenForeignKey) fk).setTargetStudyOverride(_dsd.getContainer());
        return wrappedColumn;
    }

    @Override
    public Dataset getDataset()
    {
        return _dsd;
    }

    @Override
    protected ColumnInfo resolveColumn(String name)
    {
        ColumnInfo result = super.resolveColumn(name);
        if (result != null)
        {
            return result;
        }

        // Resolve 'ParticipantSequenceKey' to 'ParticipantSequenceNum' for compatibility with versions <12.2.
        if ("ParticipantSequenceKey".equalsIgnoreCase(name))
            return getColumn("ParticipantSequenceNum");

        FieldKey fieldKey = null;

        // Be backwards compatible with the old field keys for these properties.
        // We used to flatten all of the different domains/tables on the assay side into a row in the dataset,
        // so transform to do a lookup instead
        ExpProtocol protocol = _dsd.getAssayProtocol();
        if (protocol != null)
        {
            // First, if it's Properties,
            if ("Properties".equalsIgnoreCase(name))
            {
                // Hook up a column that joins back to this table so that the columns formerly under the Properties
                // node when this was OntologyManager-backed can still be queried there
                result = wrapColumn("Properties", getRealTable().getColumn("_key"));
                result.setIsUnselectable(true);
                LookupForeignKey fk = new LookupForeignKey("_key")
                {
                    @Override
                    public TableInfo getLookupTableInfo()
                    {
                        return new DatasetTableImpl(getUserSchema(), _dsd);
                    }

                    @Override
                    public ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
                    {
                        return super.createLookupColumn(parent, displayField);
                    }
                };
                fk.setPrefixColumnCaption(false);
                result.setFk(fk);
                return result;
            }

            // Second, see the if the assay table can resolve the column
            TableInfo assayTable = getAssayResultTable();
            if (null != assayTable)
            {
                result = getAssayResultTable().getColumn(name);
                if (result != null)
                {
                    return wrapAssayColumn(result);
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
        if (fieldKey == null && !"Properties".equalsIgnoreCase((name)) && !"SpecimenLsid".equalsIgnoreCase(name) && !"Analyte".equalsIgnoreCase(name))
        {
            fieldKey = FieldKey.fromParts("Properties", name);
        }

        if (fieldKey != null)
        {
            Map<FieldKey, ColumnInfo> columns = QueryService.get().getColumns(this, Collections.singleton(fieldKey));
            result = columns.get(fieldKey);
            if (null != result)
            {
                result.setFieldKey(new FieldKey(null,name));
                result.setAlias("_DataSetTableImpl_resolvefield$" + AliasManager.makeLegalName(name, getSqlDialect(), true, false));
            }
        }
        return result;
    }

    @Override
    public Domain getDomain()
    {
        return _dsd.getDomain();
    }

    private String getAssayResultAlias(String mainAlias)
    {
        return mainAlias + "_AR";
    }


    protected void _setContainerFilter(@NotNull ContainerFilter filter)
    {
        checkLocked();

        if (filter instanceof DataspaceContainerFilter)
            filter = ((DataspaceContainerFilter)filter).getCanOptimizeDatasetContainerFilter();

        super._setContainerFilter(filter);
    }

    @NotNull
    @Override
    public ContainerFilter getDefaultContainerFilter()
    {
        ContainerFilter f = super.getDefaultContainerFilter();
        if (f instanceof DataspaceContainerFilter)
            f = ((DataspaceContainerFilter)f).getCanOptimizeDatasetContainerFilter();
        return f;
    }


    @NotNull
    private SQLFragment _getFromSQL(String alias, boolean includeParticipantVisit)
    {
        ParticipantGroup group = getUserSchema().getSessionParticipantGroup();
        DatasetDefinition.DataSharing sharing = ((DatasetDefinition)getDataset()).getDataSharingEnum();

        final SQLFragment sqlf;

        if (!includeParticipantVisit && !_dsd.isAssayData() && sharing == DatasetDefinition.DataSharing.NONE && group == null)
        {
            sqlf = super.getFromSQL(alias, true);
        }
        else
        {
            String innerAlias = "__" +  alias;
            SqlDialect d = getSchema().getSqlDialect();
            sqlf = new SQLFragment();
            sqlf.appendComment("<DatasetTableImpl>", d);

            if (!includeParticipantVisit)
            {
                sqlf.append("(SELECT * FROM ");
                sqlf.append(super.getFromSQL(innerAlias, true));
            }
            else
            {
                TableInfo participantVisit = StudySchema.getInstance().getTableInfoParticipantVisit();
                sqlf.append("(SELECT ").append(innerAlias).append(".*, PV.VisitRowId");
                if (_userSchema.getStudy().getTimepointType() == TimepointType.DATE)
                    sqlf.append(", PV.Day");
                SQLFragment from = getRealTable().getFromSQL(innerAlias);
                sqlf.append("\n FROM ").append(from).append(" LEFT OUTER JOIN ").append(participantVisit.getFromSQL("PV"))
                        .append("\n" + "    ON ").append(innerAlias).append(".ParticipantId = PV.ParticipantId AND ")
                        .append(innerAlias).append(".SequenceNum = PV.SequenceNum");

                if (null != getRealTable().getColumn("container"))
                {
                    sqlf.append(" AND PV.Container = ").append(innerAlias).append(".Container");
                }
                else
                {
                    sqlf.append(" AND PV.Container = '").append(getContainer().getId()).append("'");
                }
            }

            boolean hasWhere = false;

            // Datasets mostly ignore container filters because they usually belong to a single container.
            // In the dataspace case, they are unfiltered (no container filter).
            // We actually need to handle the container filter in the "dataset with shared data" case
            if (_dsd.isShared())
            {
                ContainerFilter cf = getContainerFilter();

                if (null != cf.getIds(getContainer()))
                {
                    TableInfo tiParticipant = _userSchema.getDbSchema().getTable("Participant");
                    ColumnInfo ciContainer = tiParticipant.getColumn("Container");
                    SimpleFilter.FilterClause f = super.getContainerFilterClause(cf, ciContainer.getFieldKey());
                    SQLFragment sqlCF = f.toSQLFragment(Collections.singletonMap(ciContainer.getFieldKey(), ciContainer), getSchema().getSqlDialect());
                    if (((DatasetDefinition) getDataset()).getDataSharingEnum() == DatasetDefinition.DataSharing.PTID)
                    {
                        sqlf.append(" WHERE ").append(innerAlias).append(".ParticipantId IN (SELECT ParticipantId FROM study.Participant WHERE ").append(sqlCF).append(")");
                    }
                    else
                    {
                        // TODO: I'd like to pass in innerAlias to toSQLFragment(), but I can't so I'm prepending and hoping...
                        if (!StringUtils.startsWithIgnoreCase(sqlCF.getRawSQL(), "container"))
                            throw new IllegalStateException("problem generating dataset SQL");
                        sqlf.append(" WHERE ").append(innerAlias).append(".").append(sqlCF);
                    }

                    hasWhere = true;
                }
            }

            // Add the session participant group filter
            if (group != null)
            {
                SQLFragment frag;
                if (group.isSession() || group.isNew())
                {
                    // Unsaved session group doesn't persist in participant group map table (yet) so we need to
                    // expand into a "ParticipantId IN ..." filter.
                    // CONSIDER: Use a temp table for large participant group lists
                    FieldKey participantFieldKey = FieldKey.fromParts("ParticipantId");
                    ColumnInfo participantCol = getColumn(participantFieldKey);
                    SimpleFilter.InClause clause = new SimpleFilter.InClause(participantFieldKey, group.getParticipantSet());
                    SQLFragment temp = clause.toSQLFragment(Collections.singletonMap(participantFieldKey, participantCol), getSqlDialect());

                    // TODO: I'd like to pass in innerAlias to toSQLFragment(), but I can't so I'm string replacing and hoping...
                    // don't use String.replaceAll(), $ is a special character (in 2nd parameter) and will throw
                    String sql = StringUtils.replace(temp.getRawSQL(), "ParticipantId", innerAlias + ".ParticipantId");
                    frag = new SQLFragment(sql, temp.getParams());
                }
                else
                {
                    // Filter using ParticipantGroupMap
                    // NOTE: Unlike the Participant table's ParticipantCategoryColumn as used by the normal
                    // participant group filter "ParticipantId/<category> = <group>" filter, we don't join on
                    // ParticipantGroupMap.Container to support project-level shared datasets.
                    frag = new SQLFragment();
                    frag.append(innerAlias).append(".ParticipantId IN (SELECT ParticipantId FROM study.ParticipantGroupMap WHERE GroupId=?)");
                    frag.add(group.getRowId());
                }

                sqlf.append("\n").append(hasWhere ? "AND " : "WHERE ").append(frag);
            }

            sqlf.append(") AS ").append(alias);

            if (_dsd.isAssayData())
            {
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
            }
            sqlf.appendComment("</DatasetTableImpl>", d);
        }

        return getTransformedFromSQL(sqlf);
    }


    @NotNull
    @Override
    public SQLFragment getFromSQL(String alias)
    {
        return _getFromSQL(alias, true);
    }


    @NotNull
    @Override
    public SQLFragment getFromSQL(String alias, Set<FieldKey> cols)
    {
        boolean includePV = false;
        if (cols.contains(new FieldKey(null, "VisitRowId")))
            includePV = true;
        else if (_userSchema.getStudy().getTimepointType() == TimepointType.DATE && cols.contains(new FieldKey(null, "Day")))
            includePV = true;
        return _getFromSQL(alias, includePV);
    }


    private TableInfo getAssayResultTable()
    {
        if (_assayResultTable == null)
        {
            ExpProtocol protocol = _dsd.getAssayProtocol();
            if (protocol == null)
            {
                return null;
            }
            AssayProvider provider = AssayService.get().getProvider(protocol);
            if (provider == null)
            {
                // Provider must have been in a module that's no longer available
                return null;
            }
            AssayProtocolSchema schema = provider.createProtocolSchema(_userSchema.getUser(), protocol.getContainer(), protocol, getContainer());
            _assayResultTable = schema.createDataTable(false);
            if (_assayResultTable != null)
            {
                _assayResultTable.setContainerFilter(ContainerFilter.EVERYTHING);
            }
        }
        return _assayResultTable;
    }

    @Override
    public ContainerContext getContainerContext()
    {
        if (_dsd.isShared())
            return new ContainerContext.FieldKeyContext(new FieldKey(null,"Folder"));
        else
            return _dsd.getContainer();
    }

    @Override
    public void overlayMetadata(String tableName, UserSchema schema, Collection<QueryException> errors)
    {
        checkLocked();
        if (isMetadataOverrideable())
        {
            // First apply all the metadata from study.StudyData so that it doesn't have to be duplicated for
            // every dataset
            // Then include the specific overrides for this dataset
            overlayMetadataIfExists(StudyQuerySchema.STUDY_DATA_TABLE_NAME, schema, errors);

            if (null == _dsd.getLabel() || _dsd.getLabel().equalsIgnoreCase(_dsd.getName()))
            {   // Name and label are same (or label null so we don't care about it)
                if (!_dsd.getName().equalsIgnoreCase(tableName))
                {
                    overlayMetadataIfExists(_dsd.getName(), schema, errors);
                }
                overlayMetadataIfExists(tableName, schema, errors);
            }
            else
            {   // Name and label different; try name, then label only if name not found
                Collection<TableType> metadata = QueryService.get().findMetadataOverride(schema, _dsd.getName(), false, false, errors, null);
                if (null != metadata)
                {
                    overlayMetadata(metadata, schema, errors);
                }
                else
                {
                    overlayMetadataIfExists(_dsd.getLabel(), schema, errors);
                }
                if (!tableName.equalsIgnoreCase(_dsd.getName()) && !tableName.equalsIgnoreCase(_dsd.getLabel()))
                {
                    // TableName different than both name and label, so overlay it if found
                    overlayMetadataIfExists(tableName, schema, errors);
                }
            }
        }
    }

    private void overlayMetadataIfExists(String tableName, UserSchema schema, Collection<QueryException> errors)
    {
        Collection<TableType> metadata = QueryService.get().findMetadataOverride(schema, tableName, false, false, errors, null);
        if (null != metadata)
            overlayMetadata(metadata, schema, errors);
    }

    private class QCStateDisplayColumn extends DataColumn
    {
        private Map<Integer, QCState> _qcStateCache;
        public QCStateDisplayColumn(ColumnInfo col)
        {
            super(col);
        }

        @Override @NotNull
        public String getFormattedValue(RenderContext ctx)
        {
            Object value = getValue(ctx);
            StringBuilder formattedValue = new StringBuilder(super.getFormattedValue(ctx));
            if (value != null && value instanceof Integer)
            {
                QCState state = getStateCache(ctx).get(value);
                if (state != null && state.getDescription() != null)
                    formattedValue.append(PageFlowUtil.helpPopup("QC State " + state.getLabel(), state.getDescription()));
            }
            return formattedValue.toString();
        }

        private Map<Integer, QCState> getStateCache(RenderContext ctx)
        {
            if (_qcStateCache == null)
            {
                _qcStateCache = new HashMap<>();
                for (QCState state : StudyManager.getInstance().getQCStates(ctx.getContainer()))
                    _qcStateCache.put(state.getRowId(), state);
            }
            return _qcStateCache;
        }
    }

    private static final Set<String> defaultHiddenCols = new CaseInsensitiveHashSet("Container", "VisitRowId", "SequenceNum", "Created", "CreatedBy", "ModifiedBy", "Modified", "lsid", "SourceLsid", "DSRowID");
    private boolean isVisibleByDefault(ColumnInfo col)
    {
        // If this is a server-managed key, or an assay-backed dataset, don't include the key column in the default
        // set of visible columns
        if ((_dsd.getKeyManagementType() != Dataset.KeyManagementType.None || _dsd.isAssayData()) &&
                col.getName().equals(_dsd.getKeyPropertyName()))
            return false;
        // for backwards compatibility "Date" is not in default visible columns for visit-based study
        if ("Date".equalsIgnoreCase(col.getName()) && _dsd.getStudy().getTimepointType() == TimepointType.VISIT)
        {
            // if there is a property desceriptor, treat like a regular column, otherwise not visible
            if (null == _dsd.getDomain() || null == _dsd.getDomain().getPropertyByName("Date"))
                return false;
        }
        return (!col.isHidden() && !col.isUnselectable() && !defaultHiddenCols.contains(col.getName()));
    }


    protected TableInfo getFromTable()
    {
        if (_fromTable == null)
        {
            _fromTable = _dsd.getTableInfo(_userSchema.getUser(), _userSchema.getMustCheckPermissions(), true);
        }
        return _fromTable;
    }

    public DatasetDefinition getDatasetDefinition()
    {
        return _dsd;
    }

    /**
     * In order to discourage the user from selecting data from deeply nested datasets, we hide
     * the "ParticipantID", "ParticipantVisit", and "Datasets" columns when the user could just as easily find
     * the same data further up the tree.
     */
    public void hideParticipantLookups()
    {
        ColumnInfo col = getColumn(StudyService.get().getSubjectColumnName(_dsd.getContainer()));
        if (col != null)
            col.setHidden(true);

        col = getColumn(StudyService.get().getSubjectVisitColumnName(_dsd.getContainer()));
        if (col != null)
            col.setHidden(true);

        col = getColumn("DataSets");
        if (col != null)
            col.setHidden(true);
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        User user = _userSchema.getUser();
        Dataset def = getDatasetDefinition();
        if (!user.hasRootAdminPermission() && !def.canWrite(user))
            return null;
        return new DatasetUpdateService(this);
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        // OK to edit these in Dataspace project and in any folder
        Dataset def = getDatasetDefinition();
        if (ReadPermission.class.isAssignableFrom(perm))
            return def.canRead(user);
        if (InsertPermission.class.isAssignableFrom(perm) || UpdatePermission.class.isAssignableFrom(perm) || DeletePermission.class.isAssignableFrom(perm))
            return def.canWrite(user);
        return def.getPolicy().hasPermission(user, perm);
    }

    @Override
    public Container getContainer()
    {
        if (null != _dsd)
            return _dsd.getContainer();
        // NOTE _dsd can be null within FilteredTable constructor
        return getUserSchema().getContainer();
    }

    @Override
    public boolean supportsContainerFilter()
    {
        return _dsd.getStudy().getShareDatasetDefinitions();
    }

    @Override
    public void setContainerFilter(@NotNull ContainerFilter filter)
    {
        super.setContainerFilter(filter);
    }

    @Override
    public Map<FieldKey, ColumnInfo> getExtendedColumns(boolean includeHidden)
    {
        Map<FieldKey, ColumnInfo> columns = super.getExtendedColumns(includeHidden);

        if (_dsd.isAssayData())
        {
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
        }

        return columns;
    }


    // TODO see BaseStudyTable.addWrapParticipantColumn(), do we need both?
    class ParticipantForeignKey extends LookupForeignKey
    {
        private ParticipantTable _tableInfo;

        ParticipantForeignKey()
        {
            super(StudyService.get().getSubjectColumnName(_userSchema.getContainer()));
            // 19918: GROUP BY columns in custom query no longer retain ForeignKey configuration
            if (_dsd.isShared())
                addJoin(new FieldKey(null,"Folder"),"Container",false);
            // Perf improvement - stash the table name so it can be accessed without needing to create the whole TableInfo
            _tableName = StudyService.get().getSubjectTableName(_userSchema.getContainer());
        }

        @Override
        public TableInfo getLookupTableInfo()
        {
            if (_tableInfo == null)
            {
                // Ideally we could just ask the schema for the ParticipantTable (e.g., _schema.getTable(...)),
                // but we need to pass arguments to ParticipantTable constructor to hide datasets.
                _tableInfo = new ParticipantTable(_userSchema, true);
                _tableInfo.setIgnoreSessionParticipantGroup();
                _tableInfo.overlayMetadata(StudyService.get().getSubjectTableName(_userSchema.getContainer()), _userSchema, new ArrayList<>());
                _tableInfo.afterConstruct();
            }
            return _tableInfo;
        }

        @Override
        public StringExpression getURL(ColumnInfo parent)
        {
            return super.getURL(parent, true);
        }
    }

    class ParticipantVisitForeignKey extends LookupForeignKey
    {
        private ParticipantVisitTable _tableInfo;

        ParticipantVisitForeignKey(String pkColumnName)
        {
            super(pkColumnName);

            // 20546: row duplication for dataspace project w/ same ptid in multiple containers
            if (_dsd.isShared())
                addJoin(new FieldKey(null,"Folder"),"Container",false);
        }

        @Override
        public TableInfo getLookupTableInfo()
        {
            if (_tableInfo == null)
            {
                // Ideally we could just ask the schema for the ParticipantTable (e.g., _schema.getTable(...)),
                // but we need to pass arguments to ParticipantTable constructor to hide datasets.
                _tableInfo = new ParticipantVisitTable(_userSchema, true);
                _tableInfo.setIgnoreSessionParticipantGroup();
                _tableInfo.afterConstruct();
            }
            return _tableInfo;
        }
    }


    @NotNull
    @Override
    public List<ColumnInfo> getAlternateKeyColumns()
    {
        ColumnInfo c;
        List<ColumnInfo> cols = new ArrayList<>();

        FieldKey fk = getContainerFieldKey();
        if (null != fk)
        {
            c = getColumn(fk);
            if (null != c)
                cols.add(getColumn(getContainerFieldKey()));
        }

        c = getColumn(getDataset().getStudy().getSubjectColumnName());
        assert null != c;
        cols.add(c);
        if (Dataset.KeyType.SUBJECT != getDataset().getKeyType())
        {
            c = getColumn("sequencenum");
            assert null != c;
            cols.add(c);

            if (getDataset().getKeyType() != Dataset.KeyType.SUBJECT_VISIT)
            {
                c = getColumn(getDataset().getKeyPropertyName());
                if (null != c)
                    cols.add(c);
            }
        }
        return cols;
    }}
