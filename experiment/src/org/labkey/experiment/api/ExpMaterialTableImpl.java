/*
 * Copyright (c) 2008-2026 LabKey Corporation
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

package org.labkey.experiment.api;

import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.util.Precision;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.labkey.api.assay.plate.AssayPlateMetadataService;
import org.labkey.api.audit.AuditHandler;
import org.labkey.api.cache.BlockingCache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.compliance.TableRules;
import org.labkey.api.compliance.TableRulesManager;
import org.labkey.api.data.ColumnHeaderType;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DatabaseIdentifier;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.ImportAliasable;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MaterializedQueryHelper;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.PHI;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UnionContainerFilter;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.LoggingDataIterator;
import org.labkey.api.dataiterator.MapDataIterator;
import org.labkey.api.dataiterator.SimpleTranslator;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.MvColumn;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.exp.api.NameExpressionOptionService;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.DefaultPropertyValidator;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.DomainUtil;
import org.labkey.api.exp.property.IPropertyValidator;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.query.ExpDataTable;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.exp.query.ExpSampleTypeTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.gwt.client.AuditBehaviorType;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.model.PropertyValidatorType;
import org.labkey.api.inventory.InventoryService;
import org.labkey.api.ontology.Quantity;
import org.labkey.api.ontology.Unit;
import org.labkey.api.qc.SampleStatusService;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUrls;
import org.labkey.api.query.RowIdForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.column.BuiltInColumnTypes;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.MediaReadPermission;
import org.labkey.api.security.permissions.MoveEntitiesPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.test.TestWhen;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.GUID;
import org.labkey.api.util.HeartBeat;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.data.xml.TableType;
import org.labkey.experiment.ExpDataIterators;
import org.labkey.experiment.ExpDataIterators.AliasDataIteratorBuilder;
import org.labkey.experiment.controllers.exp.ExperimentController;
import org.labkey.experiment.lineage.LineageMethod;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.labkey.api.audit.AuditHandler.PROVIDED_DATA_PREFIX;
import static org.labkey.api.data.ColumnRenderPropertiesImpl.NON_NEGATIVE_NUMBER_CONCEPT_URI;
import static org.labkey.api.exp.api.SampleTypeDomainKind.ALIQUOT_COUNT_LABEL;
import static org.labkey.api.exp.api.SampleTypeDomainKind.ALIQUOT_VOLUME_LABEL;
import static org.labkey.api.exp.api.SampleTypeDomainKind.AVAILABLE_ALIQUOT_COUNT_LABEL;
import static org.labkey.api.exp.api.SampleTypeDomainKind.AVAILABLE_ALIQUOT_VOLUME_LABEL;
import static org.labkey.api.exp.api.SampleTypeDomainKind.SAMPLE_TYPE_FILE_DIRECTORY_NAME;
import static org.labkey.api.exp.query.ExpMaterialTable.Column.*;
import static org.labkey.api.util.StringExpressionFactory.AbstractStringExpression.NullValueBehavior.NullResult;
import static org.labkey.experiment.api.SampleTypeServiceImpl.SampleChangeType.merge;
import static org.labkey.experiment.api.SampleTypeServiceImpl.SampleChangeType.schema;

public class ExpMaterialTableImpl extends ExpRunItemTableImpl<ExpMaterialTable.Column> implements ExpMaterialTable
{
    private static final Logger _log = LogManager.getLogger(ExpMaterialTableImpl.class);
    ExpSampleTypeImpl _ss;
    Set<String> _uniqueIdFields;
    boolean _supportTableRules = true;

    private static final Set<String> MATERIAL_ALT_KEYS;
    private static final List<IPropertyValidator> AMOUNT_RANGE_VALIDATORS = new ArrayList<>();
    static {
        MATERIAL_ALT_KEYS = CaseInsensitiveHashSet.of(MaterialSourceId.name(), Name.name());

        Lsid rangeValidatorLsid = DefaultPropertyValidator.createValidatorURI(PropertyValidatorType.Range);
        IPropertyValidator amountValidator = PropertyService.get().createValidator(rangeValidatorLsid.toString());
        amountValidator.setName("SampleAmountNonNegative");
        amountValidator.setExpressionValue("~gte=0");
        amountValidator.setErrorMessage("Amounts must be non-negative.");
        amountValidator.setColumnNameProvidedData(PROVIDED_DATA_PREFIX + StoredAmount.name());
        AMOUNT_RANGE_VALIDATORS.add(amountValidator);
    }

    private static Boolean _incrementalUpdateDisabled = null;

    public ExpMaterialTableImpl(UserSchema schema, ContainerFilter cf, @Nullable ExpSampleType sampleType)
    {
        super(ExpSchema.TableType.Materials.name(), ExperimentServiceImpl.get().getTinfoMaterial(), schema, cf);
        setDetailsURL(new DetailsURL(new ActionURL(ExperimentController.ShowMaterialAction.class, schema.getContainer()), Collections.singletonMap("rowId", "rowId"), NullResult));
        setPublicSchemaName(ExpSchema.SCHEMA_NAME);
        addAllowablePermission(InsertPermission.class);
        addAllowablePermission(UpdatePermission.class);
        addAllowablePermission(MoveEntitiesPermission.class);
        setAllowedInsertOption(QueryUpdateService.InsertOption.MERGE);
        setSampleType(sampleType);
    }

    public Set<String> getUniqueIdFields()
    {
        if (_uniqueIdFields == null)
        {
            _uniqueIdFields = new CaseInsensitiveHashSet();
            _uniqueIdFields.addAll(getColumns().stream().filter(ColumnInfo::isUniqueIdField).map(ColumnInfo::getName).collect(Collectors.toSet()));
        }
        return _uniqueIdFields;
    }

    @Override
    protected ColumnInfo resolveColumn(String name)
    {
        ColumnInfo result = super.resolveColumn(name);
        if (result == null)
        {
            if (CpasType.name().equalsIgnoreCase(name))
                result = createColumn(SampleSet.name(), SampleSet);
            else if (Property.name().equalsIgnoreCase(name))
                result = createPropertyColumn(Property.name());
            else if (QueryableInputs.name().equalsIgnoreCase(name))
                result = createColumn(QueryableInputs.name(), QueryableInputs);
        }
        return result;
    }

    @Override
    public ColumnInfo getExpObjectColumn()
    {
        var ret = wrapColumn("ExpMaterialTableImpl_object_", _rootTable.getColumn(ObjectId.name()));
        ret.setConceptURI(BuiltInColumnTypes.EXPOBJECTID_CONCEPT_URI);
        return ret;
    }

    @Override
    public AuditHandler getAuditHandler(AuditBehaviorType auditBehaviorType)
    {
        if (getUserSchema().getName().equalsIgnoreCase(SamplesSchema.SCHEMA_NAME))
        {
            // Special case sample auditing to help build a useful timeline view
            return SampleTypeServiceImpl.get();
        }

        return super.getAuditHandler(auditBehaviorType);
    }

    @Override
    public MutableColumnInfo createColumn(String alias, Column column)
    {
        switch (column)
        {
            case Folder ->
            {
                return wrapColumn(alias, _rootTable.getColumn("Container"));
            }
            case LSID ->
            {
                var columnInfo = wrapColumn(alias, _rootTable.getColumn(LSID.name()));
                columnInfo.setHidden(true);
                columnInfo.setReadOnly(true);
                columnInfo.setUserEditable(false);
                columnInfo.setShownInInsertView(false);
                columnInfo.setShownInDetailsView(false);
                columnInfo.setShownInUpdateView(false);
                return columnInfo;
            }
            case MaterialSourceId ->
            {
                var columnInfo = wrapColumn(alias, _rootTable.getColumn(MaterialSourceId.name()));
                columnInfo.setFk(new LookupForeignKey(getLookupContainerFilter(), null, null, null, null, "RowId", "Name")
                {
                    @Override
                    public TableInfo getLookupTableInfo()
                    {
                        ExpSampleTypeTable sampleTypeTable = ExperimentService.get().createSampleTypeTable(ExpSchema.TableType.SampleSets.toString(), _userSchema, getLookupContainerFilter());
                        sampleTypeTable.populate();
                        return sampleTypeTable;
                    }

                    @Override
                    public StringExpression getURL(ColumnInfo parent)
                    {
                        return super.getURL(parent, true);
                    }
                });
                columnInfo.setUserEditable(false);
                columnInfo.setReadOnly(true);
                columnInfo.setHidden(true);
                return columnInfo;
            }
            case RootMaterialRowId ->
            {
                var columnInfo = wrapColumn(alias, _rootTable.getColumn(RootMaterialRowId.name()));
                columnInfo.setFk(getExpSchema().getMaterialForeignKey(getLookupContainerFilter(), RowId.name()));
                columnInfo.setLabel("Root Material");
                columnInfo.setHidden(true);
                columnInfo.setReadOnly(true);
                columnInfo.setShownInInsertView(false);
                columnInfo.setShownInDetailsView(false);
                columnInfo.setShownInUpdateView(false);
                columnInfo.setUserEditable(false);

                // NK: Here we mark the column as not required AND nullable which is the opposite of the database where
                // a NOT NULL constraint is in place. This is done to avoid the RequiredValidator check upon updating a row.
                // See ExpMaterialValidatorIterator.
                columnInfo.setRequired(false);
                columnInfo.setNullable(true);

                return columnInfo;
            }
            case AliquotedFromLSID ->
            {
                var columnInfo = wrapColumn(alias, _rootTable.getColumn(AliquotedFromLSID.name()));
                columnInfo.setSqlTypeName("lsidtype");
                columnInfo.setFk(getExpSchema().getMaterialForeignKey(getLookupContainerFilter(), LSID.name()));
                columnInfo.setLabel("Aliquoted From Parent");
                columnInfo.setHidden(true);
                columnInfo.setReadOnly(true);
                columnInfo.setUserEditable(false);
                columnInfo.setShownInInsertView(false);
                columnInfo.setShownInDetailsView(false);
                columnInfo.setShownInUpdateView(false);
                return columnInfo;
            }
            case IsAliquot ->
            {
                String rootMaterialRowIdField = ExprColumn.STR_TABLE_ALIAS + "." + RootMaterialRowId.name();
                String rowIdField = ExprColumn.STR_TABLE_ALIAS + "." + RowId.name();
                ExprColumn columnInfo = new ExprColumn(this, IsAliquot.fieldKey(), new SQLFragment(
                        "(CASE WHEN (" + rootMaterialRowIdField + " = " + rowIdField + ") THEN ").append(getSqlDialect().getBooleanFALSE())
                        .append(" WHEN ").append(rowIdField).append(" IS NOT NULL THEN ").append(getSqlDialect().getBooleanTRUE()) // Issue 52745
                        .append(" ELSE NULL END)"), JdbcType.BOOLEAN);
                columnInfo.setLabel("Is Aliquot");
                columnInfo.setDescription("Identifies if the material is a sample or an aliquot");
                columnInfo.setUserEditable(false);
                columnInfo.setReadOnly(true);
                columnInfo.setHidden(false);
                return columnInfo;
            }
            case Name ->
            {
                var nameCol = wrapColumn(alias, _rootTable.getColumn(column.toString()));
                // shut off this field in insert and update views if user specified names are not allowed
                if (!NameExpressionOptionService.get().getAllowUserSpecificNamesValue(getContainer()))
                {
                    nameCol.setShownInInsertView(false);
                    nameCol.setShownInUpdateView(false);
                }
                return nameCol;
            }
            case RawAmount ->
            {
                var columnInfo = wrapColumn(alias, _rootTable.getColumn(StoredAmount.name()));
                columnInfo.setDisplayColumnFactory(colInfo -> new SampleTypeAmountPrecisionDisplayColumn(colInfo, null));
                columnInfo.setConceptURI(NON_NEGATIVE_NUMBER_CONCEPT_URI);
                columnInfo.setDescription("The amount of this sample, in the base unit for the sample type's display unit (if defined), currently on hand.");
                columnInfo.setHidden(true);
                columnInfo.setReadOnly(true);
                columnInfo.setShownInDetailsView(false);
                columnInfo.setShownInInsertView(false);
                columnInfo.setShownInUpdateView(false);
                columnInfo.setUserEditable(false);
                columnInfo.setValidators(AMOUNT_RANGE_VALIDATORS);
                return columnInfo;
            }
            case StoredAmount ->
            {
                String label = StoredAmount.label();
                Set<String> importAliases = Set.of(label, "Stored Amount");
                Unit typeUnit = getSampleTypeUnit();
                if (typeUnit != null)
                {
                    SampleTypeAmountDisplayColumn columnInfo = new SampleTypeAmountDisplayColumn(this, StoredAmount.name(), Units.name(), label, importAliases, typeUnit);
                    columnInfo.setDisplayColumnFactory(colInfo -> new SampleTypeAmountPrecisionDisplayColumn(colInfo, typeUnit));
                    columnInfo.setDescription("The amount of this sample, in the display unit for the sample type, currently on hand.");
                    columnInfo.setShownInUpdateView(true);
                    columnInfo.setShownInInsertView(true);
                    columnInfo.setUserEditable(true);
                    columnInfo.setCalculated(false);
                    columnInfo.setConceptURI(NON_NEGATIVE_NUMBER_CONCEPT_URI);
                    columnInfo.setValidators(AMOUNT_RANGE_VALIDATORS);
                    return columnInfo;
                }
                else
                {
                    var columnInfo = wrapColumn(alias, _rootTable.getColumn(StoredAmount.name()));
                    columnInfo.setDisplayColumnFactory(colInfo -> new SampleTypeAmountPrecisionDisplayColumn(colInfo, null));
                    columnInfo.setLabel(label);
                    columnInfo.setImportAliasesSet(importAliases);
                    columnInfo.setDescription("The amount of this sample currently on hand.");
                    columnInfo.setConceptURI(NON_NEGATIVE_NUMBER_CONCEPT_URI);
                    columnInfo.setValidators(AMOUNT_RANGE_VALIDATORS);
                    return columnInfo;
                }
            }
            case RawUnits ->
            {
                var columnInfo = wrapColumn(alias, _rootTable.getColumn(Units.name()));
                columnInfo.setDescription("The units associated with the Stored Amount for this sample.");
                columnInfo.setHidden(true);
                columnInfo.setReadOnly(true);
                columnInfo.setShownInDetailsView(false);
                columnInfo.setShownInInsertView(false);
                columnInfo.setShownInUpdateView(false);
                columnInfo.setUserEditable(false);
                return columnInfo;
            }
            case Units ->
            {
                ForeignKey fk = new LookupForeignKey("Value", "Value")
                {
                    @Override
                    public @Nullable TableInfo getLookupTableInfo()
                    {
                        return getExpSchema().getTable(ExpSchema.MEASUREMENT_UNITS_TABLE);
                    }
                };

                Unit typeUnit = getSampleTypeUnit();
                if (typeUnit != null)
                {
                    SampleTypeUnitDisplayColumn columnInfo = new SampleTypeUnitDisplayColumn(this, Units.name(), typeUnit);
                    columnInfo.setFk(fk);
                    columnInfo.setDescription("The sample type display units associated with the Amount for this sample.");
                    columnInfo.setShownInUpdateView(true);
                    columnInfo.setShownInInsertView(true);
                    columnInfo.setUserEditable(true);
                    columnInfo.setCalculated(false);
                    return columnInfo;
                }
                else
                {
                    var columnInfo = wrapColumn(alias, _rootTable.getColumn(Units.name()));
                    columnInfo.setFk(fk);
                    columnInfo.setDescription("The units associated with the Stored Amount for this sample.");
                    return columnInfo;
                }
            }
            case Description ->
            {
                return wrapColumn(alias, _rootTable.getColumn(Description.name()));
            }
            case SampleSet ->
            {
                var columnInfo = wrapColumn(alias, _rootTable.getColumn("CpasType"));
                columnInfo.setFk(new QueryForeignKey(_userSchema, getContainerFilter(), ExpSchema.SCHEMA_NAME, getContainer(), null, ExpSchema.TableType.SampleSets.name(), "lsid", null)
                {
                    @Override
                    protected ContainerFilter getLookupContainerFilter()
                    {
                        // Be sure that we can resolve the sample type if it's defined in a separate container.
                        // Same as CurrentPlusProjectAndShared but includes SampleSet's container as well.
                        // Issue 37982: Sample Type: Link to precursor sample type does not resolve correctly if sample has
                        // parents in current sample type and a sample type in the parent container
                        Set<Container> containers = new HashSet<>();
                        if (null != getSampleType())
                            containers.add(getSampleType().getContainer());
                        containers.add(getContainer());
                        if (getContainer().getProject() != null)
                            containers.add(getContainer().getProject());
                        containers.add(ContainerManager.getSharedContainer());
                        ContainerFilter cf = new ContainerFilter.CurrentPlusExtras(_userSchema.getContainer(), _userSchema.getUser(), containers);

                        if (null != _containerFilter && _containerFilter.getType() != ContainerFilter.Type.Current)
                            cf = new UnionContainerFilter(_containerFilter, cf);
                        return cf;
                    }
                });
                columnInfo.setReadOnly(true);
                columnInfo.setUserEditable(false);
                columnInfo.setShownInInsertView(false);
                return columnInfo;
            }
            case SourceProtocolLSID ->
            {
                // NOTE: This column is incorrectly named "Protocol", but we are keeping it for backwards compatibility to avoid breaking queries in hvtnFlow module
                ExprColumn columnInfo = new ExprColumn(this, ExpDataTable.Column.Protocol.toString(), new SQLFragment(
                        "(SELECT ProtocolLSID FROM " + ExperimentServiceImpl.get().getTinfoProtocolApplication() + " pa " +
                                " WHERE pa.RowId = " + ExprColumn.STR_TABLE_ALIAS + ".SourceApplicationId)"), JdbcType.VARCHAR);
                columnInfo.setSqlTypeName("lsidtype");
                columnInfo.setFk(getExpSchema().getProtocolForeignKey(getContainerFilter(), "LSID"));
                columnInfo.setLabel("Source Protocol");
                columnInfo.setDescription("Contains a reference to the protocol for the protocol application that created this sample");
                columnInfo.setUserEditable(false);
                columnInfo.setReadOnly(true);
                columnInfo.setHidden(true);
                return columnInfo;
            }
            case SourceProtocolApplication ->
            {
                var columnInfo = wrapColumn(alias, _rootTable.getColumn("SourceApplicationId"));
                columnInfo.setFk(getExpSchema().getProtocolApplicationForeignKey(getContainerFilter()));
                columnInfo.setUserEditable(false);
                columnInfo.setReadOnly(true);
                columnInfo.setHidden(true);
                columnInfo.setAutoIncrement(false);
                return columnInfo;
            }
            case SourceApplicationInput ->
            {
                var col = createEdgeColumn(alias, SourceProtocolApplication, ExpSchema.TableType.MaterialInputs);
                col.setDescription("Contains a reference to the MaterialInput row between this ExpMaterial and it's SourceProtocolApplication");
                col.setHidden(true);
                return col;
            }
            case RunApplication ->
            {
                SQLFragment sql = new SQLFragment("(SELECT pa.rowId FROM ")
                        .append(ExperimentService.get().getTinfoProtocolApplication(), "pa")
                        .append(" WHERE pa.runId = ").append(ExprColumn.STR_TABLE_ALIAS).append(".runId")
                        .append(" AND pa.cpasType = ").appendValue(ExpProtocol.ApplicationType.ExperimentRunOutput)
                        .append(")");

                var col = new ExprColumn(this, alias, sql, JdbcType.INTEGER);
                col.setFk(getExpSchema().getProtocolApplicationForeignKey(getContainerFilter()));
                col.setDescription("Contains a reference to the ExperimentRunOutput protocol application of the run that created this sample");
                col.setUserEditable(false);
                col.setReadOnly(true);
                col.setHidden(true);
                return col;
            }
            case RunApplicationOutput ->
            {
                var col = createEdgeColumn(alias, RunApplication, ExpSchema.TableType.MaterialInputs);
                col.setDescription("Contains a reference to the MaterialInput row between this ExpMaterial and it's RunOutputApplication");
                return col;
            }
            case Run ->
            {
                var ret = wrapColumn(alias, _rootTable.getColumn("RunId"));
                ret.setFk(getExpSchema().getRunIdForeignKey(getContainerFilter()));
                ret.setReadOnly(true);
                ret.setShownInInsertView(false);
                ret.setShownInUpdateView(false);
                return ret;
            }
            case RowId ->
            {
                var ret = wrapColumn(alias, _rootTable.getColumn("RowId"));
                // When no sorts are added by views, QueryServiceImpl.createDefaultSort() adds the primary key's default sort direction
                ret.setSortDirection(Sort.SortDirection.DESC);
                ret.setFk(new RowIdForeignKey(ret));
                ret.setUserEditable(false);
                ret.setHidden(true);
                ret.setShownInInsertView(false);
                ret.setHasDbSequence(true);
                ret.setIsRootDbSequence(true);
                return ret;
            }
            case Property ->
            {
                return createPropertyColumn(alias);
            }
            case Flag ->
            {
                return createFlagColumn(alias);
            }
            case Created ->
            {
                return wrapColumn(alias, _rootTable.getColumn("Created"));
            }
            case CreatedBy ->
            {
                return createUserColumn(alias, _rootTable.getColumn("CreatedBy"));
            }
            case Modified ->
            {
                return wrapColumn(alias, _rootTable.getColumn("Modified"));
            }
            case ModifiedBy ->
            {
                return createUserColumn(alias, _rootTable.getColumn("ModifiedBy"));
            }
            case Alias ->
            {
                return createAliasColumn(alias, ExperimentService.get()::getTinfoMaterialAliasMap);
            }
            case Inputs ->
            {
                return createLineageColumn(this, alias, true, false);
            }
            case QueryableInputs ->
            {
                return createLineageColumn(this, alias, true, true);
            }
            case Outputs ->
            {
                return createLineageColumn(this, alias, false, false);
            }
            case Properties ->
            {
                return createPropertiesColumn(alias);
            }
            case SampleState ->
            {
                boolean statusEnabled = isStatusEnabled(getContainer());
                var ret = wrapColumn(alias, _rootTable.getColumn(column.name()));
                ret.setLabel("Status");
                ret.setHidden(!statusEnabled);
                ret.setShownInDetailsView(statusEnabled);
                ret.setShownInInsertView(statusEnabled);
                ret.setShownInUpdateView(statusEnabled);
                ret.setRemapMissingBehavior(SimpleTranslator.RemapMissingBehavior.Error);
                ret.setFk(new QueryForeignKey.Builder(getUserSchema(), getSampleStatusLookupContainerFilter())
                        .schema(getExpSchema()).table(ExpSchema.TableType.SampleStatus).display("Label"));
                return ret;
            }
            case AliquotCount ->
            {
                var ret = wrapColumn(alias, _rootTable.getColumn(AliquotCount.name()));
                ret.setLabel(ALIQUOT_COUNT_LABEL);
                return ret;
            }
            case RawAliquotVolume ->
            {
                var ret = wrapColumn(alias, _rootTable.getColumn(AliquotVolume.name()));
                ret.setLabel(RawAliquotVolume.label());
                ret.setHidden(true);
                ret.setShownInDetailsView(false);
                ret.setShownInInsertView(false);
                ret.setShownInUpdateView(false);
                return ret;
            }
            case AliquotVolume ->
            {
                Unit typeUnit = getSampleTypeUnit();
                if (typeUnit != null)
                {
                    SampleTypeAmountDisplayColumn columnInfo = new SampleTypeAmountDisplayColumn(this, Column.AliquotVolume.name(), AliquotUnit.name(), ALIQUOT_VOLUME_LABEL, Collections.emptySet(), typeUnit);
                    columnInfo.setDisplayColumnFactory(colInfo -> new SampleTypeAmountPrecisionDisplayColumn(colInfo, typeUnit));
                    columnInfo.setDescription("The total amount of this sample's aliquots, in the display unit for the sample type, currently on hand.");
                    return columnInfo;
                }
                else
                {
                    var ret = wrapColumn(alias, _rootTable.getColumn(AliquotVolume.name()));
                    ret.setLabel(ALIQUOT_VOLUME_LABEL);
                    return ret;
                }
            }
            case RawAvailableAliquotVolume ->
            {
                var ret = wrapColumn(alias, _rootTable.getColumn(AvailableAliquotVolume.name()));
                ret.setLabel(RawAvailableAliquotVolume.label());
                ret.setHidden(true);
                ret.setShownInDetailsView(false);
                ret.setShownInInsertView(false);
                ret.setShownInUpdateView(false);
                return ret;
            }
            case AvailableAliquotVolume ->
            {
                Unit typeUnit = getSampleTypeUnit();
                if (typeUnit != null)
                {
                    SampleTypeAmountDisplayColumn columnInfo = new SampleTypeAmountDisplayColumn(this, Column.AvailableAliquotVolume.name(), AliquotUnit.name(), AVAILABLE_ALIQUOT_VOLUME_LABEL, Collections.emptySet(), typeUnit);
                    columnInfo.setDisplayColumnFactory(colInfo -> new SampleTypeAmountPrecisionDisplayColumn(colInfo, typeUnit));
                    columnInfo.setDescription("The total amount of this sample's available aliquots currently on hand, in the display unit for the sample type.");
                    return columnInfo;
                }
                else
                {
                    var ret = wrapColumn(alias, _rootTable.getColumn(AvailableAliquotVolume.name()));
                    ret.setLabel(AVAILABLE_ALIQUOT_VOLUME_LABEL);
                    return ret;
                }
            }
            case AvailableAliquotCount ->
            {
                var ret = wrapColumn(alias, _rootTable.getColumn(AvailableAliquotCount.name()));
                ret.setLabel(AVAILABLE_ALIQUOT_COUNT_LABEL);
                return ret;
            }
            case RawAliquotUnit ->
            {
                var ret = wrapColumn(alias, _rootTable.getColumn(AliquotUnit.name()));
                ret.setLabel(RawAliquotUnit.label());
                ret.setHidden(true);
                ret.setShownInDetailsView(false);
                ret.setShownInInsertView(false);
                ret.setShownInUpdateView(false);
                return ret;
            }
            case AliquotUnit ->
            {
                ForeignKey fk = new LookupForeignKey("Value", "Value")
                {
                    @Override
                    public @Nullable TableInfo getLookupTableInfo()
                    {
                        return getExpSchema().getTable(ExpSchema.MEASUREMENT_UNITS_TABLE);
                    }
                };

                Unit typeUnit = getSampleTypeUnit();
                if (typeUnit != null)
                {
                    SampleTypeUnitDisplayColumn columnInfo = new SampleTypeUnitDisplayColumn(this, AliquotUnit.name(), typeUnit);
                    columnInfo.setFk(fk);
                    columnInfo.setDescription("The sample type display units associated with the AliquotAmount for this sample.");
                    return columnInfo;
                }
                else
                {
                    var ret = wrapColumn(alias, _rootTable.getColumn(AliquotUnit.name()));
                    ret.setShownInDetailsView(false);
                    return ret;
                }
            }
            case MaterialExpDate ->
            {
                var ret = wrapColumn(alias, _rootTable.getColumn(MaterialExpDate.name()));
                ret.setLabel("Expiration Date");
                ret.setShownInDetailsView(true);
                ret.setShownInInsertView(true);
                ret.setShownInUpdateView(true);
                return ret;
            }
            case LastIndexed ->
            {
                var sql = new SQLFragment("(SELECT LastIndexed FROM ")
                        .append(ExperimentServiceImpl.get().getTinfoMaterialIndexed(), "mi")
                        .append(" WHERE mi.MaterialId = ").append(ExprColumn.STR_TABLE_ALIAS).append(".RowId)");
                var ret = new ExprColumn(this, LastIndexed.name(), sql, JdbcType.TIMESTAMP);
                ret.setDescription("Date when the material was last full-text search indexed in the system");
                ret.setHidden(true);
                ret.setReadOnly(true);
                ret.setUserEditable(false);
                return ret;
            }
            default -> throw new IllegalArgumentException("Unknown column " + column);
        }
    }

    @Override
    public MutableColumnInfo createPropertyColumn(String alias)
    {
        var ret = wrapColumn(alias, _rootTable.getColumn(RowId.name()));

        if (_ss != null && _ss.getTinfo() != null)
            ret.setFk(new QueryForeignKey.Builder(getUserSchema(), getLookupContainerFilter()).table(_ss.getTinfo()).key(RowId.name()).build());

        ret.setIsUnselectable(true);
        ret.setDescription("A holder for any custom fields associated with this sample");
        ret.setHidden(true);
        return ret;
    }

    private static boolean isStatusEnabled(Container c)
    {
        return SampleStatusService.get().supportsSampleStatus() && !SampleStatusService.get().getAllProjectStates(c).isEmpty();
    }

    private Unit getSampleTypeUnit()
    {
        Unit typeUnit = null;
        if (_ss != null && _ss.getMetricUnit() != null)
            typeUnit = Unit.fromName(_ss.getMetricUnit());
        return typeUnit;
    }

    private void setSampleType(@Nullable ExpSampleType st)
    {
        checkLocked();
        if (_ss != null)
        {
            throw new IllegalStateException("Cannot unset sample type");
        }
        if (st != null && !(st instanceof ExpSampleTypeImpl))
        {
            throw new IllegalArgumentException("Expected sample type to be an instance of " + ExpSampleTypeImpl.class.getName() + " but was a " + st.getClass().getName());
        }
        _ss = (ExpSampleTypeImpl) st;
        if (_ss != null)
        {
            setPublicSchemaName(SamplesSchema.SCHEMA_NAME);
            setName(st.getName());

            String description = _ss.getDescription();
            if (StringUtils.isEmpty(description))
                description = "Contains one row per sample in the " + _ss.getName() + " sample type";
            setDescription(description);

            if (canUserAccessPhi())
            {
                ActionURL url = PageFlowUtil.urlProvider(ExperimentUrls.class).getImportSamplesURL(getContainer(), _ss.getName());
                setImportURL(new DetailsURL(url));
            }
        }
    }

    public ExpSampleType getSampleType()
    {
        return _ss;
    }

    @Override
    protected void populateColumns()
    {
        var st = getSampleType();
        var rowIdCol = addColumn(RowId);
        addColumn(MaterialSourceId);
        addColumn(SourceProtocolApplication);
        addColumn(SourceApplicationInput);
        addColumn(RunApplication);
        addColumn(RunApplicationOutput);
        addColumn(SourceProtocolLSID);

        List<FieldKey> defaultCols = new ArrayList<>();
        var nameCol = addColumn(Name);
        defaultCols.add(Name.fieldKey());
        if (st != null && st.hasNameAsIdCol())
        {
            // Show the Name field but don't mark is as required when using name expressions
            if (st.hasNameExpression())
            {
                var nameExpression = st.getNameExpression();
                nameCol.setNameExpression(nameExpression);
                nameCol.setNullable(true);
                String nameExpressionPreview = getExpNameExpressionPreview(getUserSchema().getSchemaName(), st.getName(), getUserSchema().getUser());
                String desc = appendNameExpressionDescription(nameCol.getDescription(), nameExpression, nameExpressionPreview);
                nameCol.setDescription(desc);
            }
            else
            {
                nameCol.setNullable(false);
            }
        }
        else
        {
            nameCol.setReadOnly(true);
            nameCol.setShownInInsertView(false);
        }

        addColumn(Alias);
        addColumn(Description);
        addColumn(SampleSet);
        addColumn(MaterialExpDate);
        defaultCols.add(MaterialExpDate.fieldKey());
        addContainerColumn(Folder, null);
        if (getContainer().hasProductFolders())
            defaultCols.add(Folder.fieldKey());
        addColumn(Run);
        defaultCols.add(Run.fieldKey());
        addColumn(LSID);
        addColumn(RootMaterialRowId);
        addColumn(AliquotedFromLSID);
        addColumn(IsAliquot);
        addColumn(Created);
        addColumn(CreatedBy);
        addColumn(Modified);
        addColumn(ModifiedBy);
        if (st == null)
            defaultCols.add(SampleSet.fieldKey());
        addColumn(Flag);
        addColumn(SampleState);
        if (isStatusEnabled(getContainer()))
            defaultCols.add(SampleState.fieldKey());

        // TODO is this a real Domain???
        if (st != null && !"urn:lsid:labkey.com:SampleSource:Default".equals(st.getDomain().getTypeURI()))
        {
            defaultCols.add(Flag.fieldKey());
            addSampleTypeColumns(st, defaultCols);

            setName(_ss.getName());

            ActionURL gridUrl = new ActionURL(ExperimentController.ShowSampleTypeAction.class, getContainer());
            gridUrl.addParameter("rowId", st.getRowId());
            setGridURL(new DetailsURL(gridUrl));
        }

        List<FieldKey> calculatedFieldKeys = DomainUtil.getCalculatedFieldsForDefaultView(this);
        defaultCols.addAll(calculatedFieldKeys);

        addColumn(AliquotCount);
        addColumn(AliquotVolume);
        addColumn(AliquotUnit);
        addColumn(AvailableAliquotCount);
        addColumn(AvailableAliquotVolume);

        addColumn(StoredAmount);
        defaultCols.add(StoredAmount.fieldKey());

        addColumn(Units);
        defaultCols.add(Units.fieldKey());

        addColumn(RawAmount).getRenderer().addQueryFieldKeys(new HashSet<>(Set.of(StoredAmount.fieldKey())));
        addColumn(RawUnits).getRenderer().addQueryFieldKeys(new HashSet<>(Set.of(Units.fieldKey())));
        addColumn(RawAliquotVolume).getRenderer().addQueryFieldKeys(new HashSet<>(Set.of(AliquotVolume.fieldKey())));
        addColumn(RawAvailableAliquotVolume).getRenderer().addQueryFieldKeys(new HashSet<>(Set.of(AvailableAliquotVolume.fieldKey())));
        addColumn(RawAliquotUnit).getRenderer().addQueryFieldKeys(new HashSet<>(Set.of(AliquotUnit.fieldKey())));

        if (InventoryService.get() != null && (st == null || !st.isMedia()))
        {
            List<FieldKey> inventoryCols = InventoryService.get().addInventoryStatusColumns(st == null ? null : st.getMetricUnit(), this, getContainer(), _userSchema.getUser());
            // GH Issue 1035: Don't include StorageTerminalLocation in the default view
            defaultCols.addAll(inventoryCols.stream().filter(fk -> !fk.equals(InventoryService.InventoryStatusColumn.StorageTerminalLocation.fieldKey())).toList());
        }

        SQLFragment sql;
        UserSchema plateUserSchema;
        // Issue 53194: this would be the case for linked to study samples. The contextual role is set up from the study dataset
        // for the source sample, we want to allow the plate schema to inherit any contextual roles to allow querying
        // against tables in that schema.
        if (_userSchema instanceof UserSchema.HasContextualRoles samplesSchema && !samplesSchema.getContextualRoles().isEmpty())
            plateUserSchema = AssayPlateMetadataService.get().getPlateSchema(_userSchema, samplesSchema.getContextualRoles());
        else
            plateUserSchema = QueryService.get().getUserSchema(_userSchema.getUser(), _userSchema.getContainer(), "plate");

        if (plateUserSchema != null && plateUserSchema.getTable("Well") != null)
        {
            String rowIdField = ExprColumn.STR_TABLE_ALIAS + "." + RowId.name();
            SQLFragment existsSubquery = new SQLFragment()
                    .append("SELECT 1 FROM ")
                    .append(plateUserSchema.getTable("Well"), "well")
                    .append(" WHERE well.sampleid = ").append(rowIdField);

            sql = new SQLFragment()
                    .append("CASE WHEN EXISTS (")
                    .append(existsSubquery)
                    .append(") THEN 'Plated' ")
                    .append("WHEN ").append(ExprColumn.STR_TABLE_ALIAS).append(".RowId").append(" IS NOT NULL THEN 'Not Plated' ")// Issue 52745
                    .append("ELSE NULL END");
        }
        else
        {
            sql = new SQLFragment("(SELECT NULL)");
        }
        var col = new ExprColumn(this, IsPlated.name(), sql, JdbcType.VARCHAR);
        col.setDescription("Whether the sample that has been plated, if plating is supported.");
        col.setUserEditable(false);
        col.setReadOnly(true);
        col.setShownInDetailsView(false);
        col.setShownInInsertView(false);
        col.setShownInUpdateView(false);
        if (plateUserSchema != null)
           col.setURL(DetailsURL.fromString("plate-isPlated.api?sampleId=${" + RowId.name() + "}"));
        addColumn(col);

        addVocabularyDomains();
        addColumn(LastIndexed);
        addColumn(Properties);

        var colInputs = addColumn(Inputs);
        addMethod(Inputs.name(), new LineageMethod(colInputs, true), Set.of(colInputs.getFieldKey()));

        var colOutputs = addColumn(Outputs);
        addMethod(Outputs.name(), new LineageMethod(colOutputs, false), Set.of(colOutputs.getFieldKey()));

        addExpObjectMethod();

        ActionURL detailsUrl = new ActionURL(ExperimentController.ShowMaterialAction.class, getContainer());
        DetailsURL url = new DetailsURL(detailsUrl, Collections.singletonMap("rowId", "RowId"), NullResult);
        nameCol.setURL(url);
        rowIdCol.setURL(url);
        setDetailsURL(url);

        if (canUserAccessPhi())
        {
            ActionURL updateActionURL = PageFlowUtil.urlProvider(ExperimentUrls.class).getUpdateMaterialQueryRowAction(getContainer(), this);
            setUpdateURL(new DetailsURL(updateActionURL, Collections.singletonMap("RowId", "RowId")));

            ActionURL insertActionURL = PageFlowUtil.urlProvider(ExperimentUrls.class).getInsertMaterialQueryRowAction(getContainer(), this);
            setInsertURL(new DetailsURL(insertActionURL));
        }
        else
        {
            setImportURL(LINK_DISABLER);
            setInsertURL(LINK_DISABLER);
            setUpdateURL(LINK_DISABLER);
        }

        setTitleColumn(Name.toString());

        setDefaultVisibleColumns(defaultCols);

        MutableColumnInfo lineageLookup = ClosureQueryHelper.createAncestorLookupColumnInfo("Ancestors", this, _rootTable.getColumn(RowId.name()), _ss, true);
        addColumn(lineageLookup);
    }

    private ContainerFilter getSampleStatusLookupContainerFilter()
    {
        // The default lookup container filter is Current. However, we want to have the default be CurrentPlusProjectAndShared
        // for the sample status lookup since in the app project context we want to share status definitions across
        // a given project instead of creating duplicate statuses in each subfolder project.
        ContainerFilter.Type type = QueryService.get().getContainerFilterTypeForLookups(getContainer());
        type = type == null ? ContainerFilter.Type.CurrentPlusProjectAndShared : type;
        return type.create(getUserSchema());
    }

    @Override
    public Domain getDomain()
    {
        return getDomain(false);
    }

    @Override
    public Domain getDomain(boolean forUpdate)
    {
        return _ss == null ? null : _ss.getDomain(forUpdate);
    }

    public static String appendNameExpressionDescription(String currentDescription, String nameExpression, String nameExpressionPreview)
    {
        if (nameExpression == null)
            return currentDescription;

        StringBuilder sb = new StringBuilder();
        if (currentDescription != null && !currentDescription.isEmpty())
        {
            sb.append(currentDescription);
            if (!currentDescription.endsWith("."))
                sb.append(".");
            sb.append("\n");
        }

        sb.append("\nIf not provided, a unique name will be generated from the expression:\n");
        sb.append(nameExpression);
        sb.append(".");
        if (!StringUtils.isEmpty(nameExpressionPreview))
        {
            sb.append("\nExample of name that will be generated from the current pattern: \n");
            sb.append(nameExpressionPreview);
        }

        return sb.toString();
    }

    private void addSampleTypeColumns(ExpSampleType st, List<FieldKey> visibleColumns)
    {
        TableInfo dbTable = ((ExpSampleTypeImpl)st).getTinfo();
        if (null == dbTable)
            return;

        UserSchema schema = getUserSchema();
        Domain domain = st.getDomain();
        ColumnInfo rowIdColumn = getColumn(RowId);
        ColumnInfo nameColumn = getColumn(Name);

        visibleColumns.remove(Run.fieldKey());

        // When not using name expressions, mark the ID columns as required.
        // NOTE: If not explicitly set, the first domain property will be chosen as the ID column.
        final List<DomainProperty> idCols = st.hasNameExpression() ? Collections.emptyList() : st.getIdCols();

        Set<FieldKey> mvColumns = domain.getProperties().stream()
                .filter(ImportAliasable::isMvEnabled)
                .map(dp -> FieldKey.fromParts(dp.getPropertyDescriptor().getMvIndicatorStorageColumnName()))
                .collect(Collectors.toSet());

        for (ColumnInfo dbColumn : dbTable.getColumns())
        {
            // Don't include PHI columns in the full-text search index
            // CONSIDER: Can we move this to a base class? Maybe in .addColumn()
            if (schema.getUser().isSearchUser() && !dbColumn.getPHI().isLevelAllowed(PHI.NotPHI))
                continue;

            if (
                rowIdColumn.getFieldKey().equals(dbColumn.getFieldKey()) ||
                nameColumn.getFieldKey().equals(dbColumn.getFieldKey())
            )
            {
                continue;
            }

            var wrapped = wrapColumnFromJoinedTable(dbColumn.getName(), dbColumn);

            // TODO missing values? comments? flags?
            DomainProperty dp = domain.getPropertyByURI(dbColumn.getPropertyURI());
            var propColumn = copyColumnFromJoinedTable(null==dp ? dbColumn.getName() : dp.getName(), wrapped);
            if (propColumn.getName().equalsIgnoreCase("genid"))
            {
                propColumn.setHidden(true);
                propColumn.setUserEditable(false);
                propColumn.setShownInDetailsView(false);
                propColumn.setShownInInsertView(false);
                propColumn.setShownInUpdateView(false);
            }
            if (null != dp)
            {
                PropertyColumn.copyAttributes(schema.getUser(), propColumn, dp.getPropertyDescriptor(), schema.getContainer(),
                    SamplesSchema.SCHEMA_SAMPLES, st.getName(), RowId.fieldKey(), null, getLookupContainerFilter());

                if (idCols.contains(dp))
                {
                    propColumn.setNullable(false);
                    propColumn.setDisplayColumnFactory(c -> new DataColumn(c)
                    {
                        @Override
                        protected boolean isDisabledInput(RenderContext ctx)
                        {
                            return !super.isDisabledInput() && ctx.getMode() != DataRegion.MODE_INSERT;
                        }
                    });
                }

                // Issue 38341: domain designer advanced settings 'show in default view' setting is not respected
                if (!propColumn.isHidden())
                {
                    visibleColumns.add(propColumn.getFieldKey());
                }

                if (propColumn.isMvEnabled())
                {
                    // The column in the physical table has a "_MVIndicator" suffix, but we want to expose
                    // it with an "MVIndicator" suffix (no underscore)
                    var mvColumn = new AliasedColumn(this, dp.getName() + MvColumn.MV_INDICATOR_SUFFIX,
                            StorageProvisioner.get().getMvIndicatorColumn(dbTable, dp.getPropertyDescriptor(), "No MV column found for '" + dp.getName() + "' in sample type '" + getName() + "'"));
                    mvColumn.setLabel(dp.getLabel() != null ? dp.getLabel() : dp.getName() + " MV Indicator");
                    mvColumn.setSqlTypeName("VARCHAR");
                    mvColumn.setPropertyURI(dp.getPropertyURI());
                    mvColumn.setNullable(true);
                    mvColumn.setUserEditable(false);
                    mvColumn.setHidden(true);
                    mvColumn.setMvIndicatorColumn(true);

                    addColumn(mvColumn);
                    propColumn.setMvColumnName(FieldKey.fromParts(dp.getName() + MvColumn.MV_INDICATOR_SUFFIX));
                }
            }

            if (!mvColumns.contains(propColumn.getFieldKey()))
                addColumn(propColumn);

        }

        setDefaultVisibleColumns(visibleColumns);
    }

    // These are mostly fields that are wrapped by fields with different names (see createColumn())
    // we could handle each case separately, but this is easier
    static final Set<FieldKey> wrappedFieldKeys = Set.of(
            new FieldKey(null, "objectid"),
            new FieldKey(null, "RowId"),
            new FieldKey(null, "LSID"),                 // Flag
            new FieldKey(null, "SourceApplicationId"),  // SourceProtocolApplication
            new FieldKey(null, "runId"),                // Run, RunApplication
            new FieldKey(null, "CpasType"));            // SampleSet
    static final Set<FieldKey> ALL_COLUMNS = Set.of();

    private @NotNull Set<FieldKey> computeInnerSelectedColumns(Set<FieldKey> selectedColumns)
    {
        if (null == selectedColumns)
            return ALL_COLUMNS;
        selectedColumns = new TreeSet<>(selectedColumns);
        if (selectedColumns.contains(new FieldKey(null, StoredAmount)))
            selectedColumns.add(new FieldKey(null, Units));
        if (selectedColumns.contains(new FieldKey(null, ExpMaterial.ALIQUOTED_FROM_INPUT)))
            selectedColumns.add(new FieldKey(null, AliquotedFromLSID.name()));
        if (selectedColumns.contains(new FieldKey(null, IsAliquot.name())))
            selectedColumns.add(new FieldKey(null, RootMaterialRowId.name()));
        if (selectedColumns.contains(new FieldKey(null, AliquotVolume.name())) || selectedColumns.contains(new FieldKey(null, AvailableAliquotVolume.name())))
            selectedColumns.add(new FieldKey(null, AliquotUnit.name()));
        selectedColumns.addAll(wrappedFieldKeys);
        if (null != getFilter())
            selectedColumns.addAll(getFilter().getAllFieldKeys());
        return selectedColumns;
    }

    @NotNull
    @Override
    public SQLFragment getFromSQL(String alias)
    {
        return getFromSQL(alias, null);
    }

    @Override
    public SQLFragment getFromSQLExpanded(String alias, Set<FieldKey> selectedColumns)
    {
        SQLFragment sql = new SQLFragment("(");

        // SELECT FROM
        // NOTE: We want to avoid caching in paths where the table is actively being updated (e.g., loadRows).
        // Unfortunately, we don't _really_ know when this is, but if we are in a transaction, that's a good guess.
        // Also, we may use RemapCache for material lookup outside a transaction.
        boolean onlyMaterialColums = false;
        if (null != selectedColumns && !selectedColumns.isEmpty())
            onlyMaterialColums = selectedColumns.stream().allMatch(fk -> fk.getName().equalsIgnoreCase("Folder") || null != _rootTable.getColumn(fk));

        boolean usedMaterialized = false;
        if (!onlyMaterialColums && null != _ss && null != _ss.getTinfo() && !getExpSchema().getDbSchema().getScope().isTransactionActive())
        {
            SQLFragment materializedSql = getMaterializedSQL();
            if (materializedSql != null)
            {
                sql.append(materializedSql);
                usedMaterialized = true;
            }
        }

        if (!usedMaterialized)
            sql.append(getJoinSQL(selectedColumns));

        // WHERE
        SQLFragment filterFrag = getFilter().getSQLFragment(_rootTable, null);
        sql.append("\n").append(filterFrag);
        if (_ss != null && !usedMaterialized)
        {
            if (!filterFrag.isEmpty())
                sql.append(" AND ");
            else
                sql.append(" WHERE ");
            sql.append("CpasType = ").appendValue(_ss.getLSID());
        }
        sql.append(") ").appendIdentifier(alias);

        return getTransformedFromSQL(sql);
    }

    @Override
    public void setSupportTableRules(boolean b)
    {
        this._supportTableRules = b;
    }

    @Override
    public boolean supportTableRules()
    {
        return _supportTableRules;
    }

    @Override
    protected @NotNull TableRules findTableRules()
    {
        Container definitionContainer = getUserSchema().getContainer();
        if (null != _ss)
            definitionContainer = _ss.getContainer();
        return TableRulesManager.get().getTableRules(definitionContainer, getUserSchema().getUser(), getUserSchema().getContainer());
    }

    static class InvalidationCounters
    {
        public final AtomicLong update, insert, delete, rollup;
        public final AtomicReference<Timestamp> pendingUpdateSince = new AtomicReference<>();

        InvalidationCounters()
        {
            long l = System.currentTimeMillis();
            update = new AtomicLong(l);
            insert = new AtomicLong(l);
            delete = new AtomicLong(l);
            rollup = new AtomicLong(l);
        }

        void recordPendingUpdate(@NotNull Timestamp changedSince)
        {
            pendingUpdateSince.accumulateAndGet(changedSince, (cur, next) -> cur == null || next.before(cur) ? next : cur);
        }

        @Nullable Timestamp drainPendingUpdate()
        {
            return pendingUpdateSince.getAndSet(null);
        }
    }

    static final BlockingCache<String,_MaterializedQueryHelper> _materializedQueries = CacheManager.getBlockingStringKeyCache(CacheManager.UNLIMITED, CacheManager.HOUR, "materialized sample types", null);
    static final Map<String, InvalidationCounters> _invalidationCounters = Collections.synchronizedMap(new HashMap<>());
    static final AtomicBoolean initializedListeners = new AtomicBoolean(false);

    public static void refreshMaterializedView(final String lsid, SampleTypeServiceImpl.SampleChangeType reason)
    {
        refreshMaterializedView(lsid, reason, null);
    }

    /**
     * @param changedSince a watermark (from the database clock, captured before the update's writes) at or after which
     *                     the changed {@code exp.material} rows were modified; only meaningful for update. null means
     *                     the caller could not capture a watermark, forcing a full re-sync on the next read.
     */
    public static void refreshMaterializedView(final String lsid, SampleTypeServiceImpl.SampleChangeType reason, @Nullable Timestamp changedSince)
    {
        var scope = ExperimentServiceImpl.getExpSchema().getScope();
        var runnable = new RefreshMaterializedViewRunnable(lsid, reason, changedSince);
        scope.addCommitTask(runnable, DbScope.CommitTaskOption.POSTCOMMIT);
    }

    /**
     * POSTCOMMIT task that turns a committed data change into an invalidation: a schema change (or any update/merge that
     * could not capture a watermark) drops the cached MQH so the next read rebuilds the whole view; otherwise it bumps the
     * matching per-LSID counter(s), and for {@code update}/{@code merge} also records the change watermark for a targeted
     * incremental update.
     */
    private record RefreshMaterializedViewRunnable(
        String lsid,
        SampleTypeServiceImpl.SampleChangeType reason,
        @Nullable Timestamp changedSince
    ) implements Runnable
    {
        @Override
        public void run()
        {
            if (reason == schema)
            {
                /* NOTE: MaterializedQueryHelper can detect data changes and refresh the materialized view using the provided SQL.
                 * It does not handle schema changes where the SQL itself needs to be updated.  In this case, we remove the
                 * MQH from the cache to force the SQL to be regenerated.
                 */
                _materializedQueries.remove(lsid);
                return;
            }

            var counters = getInvalidateCounters(lsid);
            switch (reason)
            {
                case insert -> counters.insert.incrementAndGet();
                case rollup -> counters.rollup.incrementAndGet();
                case delete -> counters.delete.incrementAndGet();
                case update, merge -> {
                    // An update or merge did not capture a watermark, so it cannot be targeted incrementally; drop the
                    // cached MQH so the next read rebuilds the whole view. Clear any pending watermark since the
                    // rebuild supersedes it.
                    if (changedSince == null)
                    {
                        counters.pendingUpdateSince.set(null);
                        _materializedQueries.remove(lsid);
                        return;
                    }

                    counters.recordPendingUpdate(changedSince);
                    counters.update.incrementAndGet();

                    if (reason == merge)
                        counters.insert.incrementAndGet();
                }
                default -> throw new IllegalStateException("Unexpected value: " + reason);
            }
        }

        @Override
        public @NotNull String toString()
        {
            return "RefreshMaterializedViewRunnable{lsid=" + lsid + ", reason=" + reason + ", changedSince=" + changedSince + "}";
        }
    }

    private static InvalidationCounters getInvalidateCounters(String lsid)
    {
        if (!initializedListeners.getAndSet(true))
            CacheManager.addListener(_invalidationCounters::clear);

        return _invalidationCounters.computeIfAbsent(lsid, (_) -> new InvalidationCounters());
    }

    /** Look up (or build and cache) the {@link _MaterializedQueryHelper} for this table's sample type. Caller must ensure {@code _ss != null}. */
    private _MaterializedQueryHelper getOrCreateMQH()
    {
        return _materializedQueries.get(_ss.getLSID(), null, (unusedKey, unusedArg) ->
        {
            /* NOTE: MaterializedQueryHelper does have a pattern to help with detecting schema changes.
             * Previously it has been used on non-provisioned tables.  It might be helpful to have a pattern,
             * even if just to help with race-conditions.
             *
             * Maybe have a callback to generate the SQL dynamically and verify that the SQL is unchanged.
             */
            List<ColumnInfo> updateColumns = new ArrayList<>();
            SQLFragment viewSql = getJoinSQL(null, updateColumns).append(" WHERE CpasType = ").appendValue(_ss.getLSID());
            MaterializedQueryHelper.Builder builder = new _MaterializedQueryHelper.Builder(_ss.getLSID(), "", getExpSchema().getDbSchema().getScope(), viewSql)
                .updateColumns(updateColumns)
                .unlogged(true)
                .addIndex("CREATE UNIQUE INDEX uq_${NAME}_rowid ON temp.${NAME} (rowid)")
                .addIndex("CREATE UNIQUE INDEX uq_${NAME}_lsid ON temp.${NAME} (lsid)")
                .addIndex("CREATE INDEX idx_${NAME}_container ON temp.${NAME} (container)")
                .addIndex("CREATE INDEX idx_${NAME}_root ON temp.${NAME} (rootmaterialrowid)");

            if (isIncrementalUpdateDisabled())
                builder.addInvalidCheck(() -> String.valueOf(getInvalidateCounters(_ss.getLSID()).update.get()));

            return (_MaterializedQueryHelper) builder.build();
        });
    }

    /* SELECT and JOIN, does not include WHERE, same as getJoinSQL() */
    private @Nullable SQLFragment getMaterializedSQL()
    {
        if (null == _ss)
            return getJoinSQL(null);

        // Skip the materialized path during shutdown; temp-table cleanup can drop it mid-read (42P01), so use the live join.
        if (ContextListener.isShuttingDown())
            return null;

        var mqh = getOrCreateMQH();

        SQLFragment tempRef = mqh.tryGetFromSqlIfLoaded("_cached_view_");
        if (tempRef == null)
        {
            mqh.materializeAsync();
            return null;
        }

        return new SQLFragment("SELECT * FROM ").append(tempRef);
    }

    /**
     * Incremental update is dependent upon the web server and database server time being consistent.
     * If they differ, then it can lead to stale materialized results which can lead to data integrity issues.
     */
    private static boolean isIncrementalUpdateDisabled()
    {
        // Disable if web server and database time differ
        if (_incrementalUpdateDisabled == null)
        {
            DbScope scope = DbScope.getLabKeyScope();
            SqlDialect.ServerDatabaseTimeDifference difference = SqlDialect.getServerDatabaseTimeDifference(scope);
            _incrementalUpdateDisabled = difference.exceedsWarningThreshold();

            if (_incrementalUpdateDisabled)
                _log.warn("Incremental update disabled for samples. Web and database server time differ by {} seconds which exceeds the threshold of {} seconds. You may experience degraded sample query performance.", difference.getSeconds(), SqlDialect.TIME_DIFFERENCE_WARNING_SECONDS);
        }

        return _incrementalUpdateDisabled;
    }

    /**
     * MaterializedQueryHelper has a built-in mechanism for tracking when a temp table needs to be recomputed.
     * It does not help with incremental updates (except for providing the upsert() method).
     * _MaterializedQueryHelper and _Materialized copy the pattern using class Invalidator.
     */
    static class _MaterializedQueryHelper extends MaterializedQueryHelper
    {
        final String _lsid;
        final List<ColumnInfo> _updateColumns;

        static class Builder extends MaterializedQueryHelper.Builder
        {
            String _lsid;
            List<ColumnInfo> _updateColumns = List.of();

            public Builder(String lsid, String prefix, DbScope scope, SQLFragment select)
            {
                super(prefix, scope, select);
                this._lsid = lsid;
            }

            public Builder updateColumns(List<ColumnInfo> updateColumns)
            {
                this._updateColumns = updateColumns;
                return this;
            }

            @Override
            public _MaterializedQueryHelper build()
            {
                return new _MaterializedQueryHelper(_lsid, _updateColumns, _prefix, _scope, _select, _uptodate, _supplier, _indexes, _max, _isSelectInto, _unlogged);
            }
        }

        _MaterializedQueryHelper(
            String lsid,
            List<ColumnInfo> updateColumns,
            String prefix,
            DbScope scope,
            SQLFragment select,
            @Nullable SQLFragment uptodate,
            Supplier<String> supplier,
            @Nullable Collection<String> indexes,
            long maxTimeToCache,
            boolean isSelectIntoSql,
            boolean unlogged
        )
        {
            super(prefix, scope, select, uptodate, supplier, indexes, maxTimeToCache, isSelectIntoSql, unlogged);
            this._lsid = lsid;
            this._updateColumns = updateColumns;
        }

        @Override
        protected Materialized createMaterialized(String txCacheKey)
        {
            DbSchema temp = DbSchema.getTemp();
            String name = _prefix + "_" + GUID.makeHash();
            _Materialized materialized = new _Materialized(this, name, txCacheKey, HeartBeat.currentTimeMillis(), "\"" + temp.getName() + "\".\"" + name + "\"");
            initMaterialized(materialized);
            return materialized;
        }

        @Override
        protected void incrementalUpdateBeforeSelect(Materialized m)
        {
            _Materialized materialized = (_Materialized) m;

            boolean lockAcquired = false;
            try
            {
                lockAcquired = materialized.getLock().tryLock(1, TimeUnit.MINUTES);
                if (Materialized.LoadingState.ERROR == materialized._loadingState.get())
                    throw materialized._loadException;

                runIncremental("delete", materialized.incrementalDeleteCheck, this::executeIncrementalDelete);
                runIncremental("update", materialized.incrementalUpdateCheck, this::executeIncrementalUpdate);
                runIncremental("rollup", materialized.incrementalRollupCheck, this::executeIncrementalRollup);
                runIncremental("insert", materialized.incrementalInsertCheck, this::executeIncrementalInsert);
            }
            catch (RuntimeException|InterruptedException ex)
            {
                RuntimeException rex = UnexpectedException.wrap(ex);
                materialized.setError(rex);
                // The only time I'd expect an error is due to a schema change race-condition, but that can happen in any code path.

                // Ensure that next refresh starts clean
                _materializedQueries.remove(_lsid);
                getInvalidateCounters(_lsid).update.incrementAndGet();
                throw rex;
            }
            finally
            {
                if (lockAcquired)
                    materialized.getLock().unlock();
            }
        }

        /**
         * Apply one incremental step, advancing its invalidation snapshot only after the work has committed.
         * The counter is captured (via {@code current()}) before the work runs and accepted (via {@code markValidAs()})
         * afterward, so a concurrent reader's non-destructive {@code peekValid()} reports stale for the entire
         * duration of the update. That keeps reads on the live (unmaterialized) query until the temp table actually
         * reflects the change, instead of briefly serving a stale materialized view. Must be called while holding the
         * materialized's loading lock so only one updater runs at a time.
         */
        private void runIncremental(String kind, MaterializedQueryHelper.SupplierInvalidator check, Runnable work)
        {
            if (check.peekValid())
                return;
            String token = check.current();
            traced("incremental." + kind, getMaterializationName(), work);
            check.markValidAs(token);
        }

        @Override
        protected String getMaterializationName()
        {
            return StringUtils.defaultIfEmpty(Lsid.parse(_lsid).getObjectId(), _lsid);
        }

        void upsertWithRetry(SQLFragment sql)
        {
            // not actually read-only, but we don't want to start an explicit transaction
            _scope.executeWithRetryReadOnly((_) -> upsert(sql));
        }

        void executeIncrementalInsert()
        {
            SQLFragment incremental = new SQLFragment("INSERT INTO temp.${NAME}\n")
                    .append("SELECT * FROM (")
                    .append(getViewSourceSql()).append(") viewsource_\n")
                    .append("WHERE rowid > (SELECT COALESCE(MAX(rowid),0) FROM temp.${NAME})");
            upsertWithRetry(incremental);
        }

        void executeIncrementalDelete()
        {
            var d = CoreSchema.getInstance().getSchema().getSqlDialect();
            // POSTGRES bug??? the obvious query is _very_ slow O(n^2)
            // DELETE FROM temp.${NAME} WHERE rowid NOT IN (SELECT rowid FROM exp.material WHERE cpastype = <<_lsid>>)
            SQLFragment incremental = new SQLFragment()
                    .append("WITH deleted AS (SELECT rowid FROM temp.${NAME} EXCEPT SELECT rowid FROM exp.material WHERE cpastype = ").appendValue(_lsid,d).append(")\n")
                    .append("DELETE FROM temp.${NAME} WHERE rowid IN (SELECT rowid from deleted)\n");
            upsertWithRetry(incremental);
        }

        void executeIncrementalRollup()
        {
            var d = CoreSchema.getInstance().getSchema().getSqlDialect();
            SQLFragment incremental = new SQLFragment();
            if (d.isPostgreSQL())
            {
                incremental
                    .append("UPDATE temp.${NAME} AS st\n")
                    .append("SET aliquotcount = expm.aliquotcount, availablealiquotcount = expm.availablealiquotcount, aliquotvolume = expm.aliquotvolume, availablealiquotvolume = expm.availablealiquotvolume, aliquotunit = expm.aliquotunit\n")
                    .append("FROM exp.Material AS expm\n")
                    .append("WHERE expm.rowid = st.rowid AND expm.cpastype = ").appendValue(_lsid,d).append(" AND (\n")
                    .append("    st.aliquotcount IS DISTINCT FROM expm.aliquotcount OR ")
                    .append("    st.availablealiquotcount IS DISTINCT FROM expm.availablealiquotcount OR ")
                    .append("    st.aliquotvolume IS DISTINCT FROM expm.aliquotvolume OR ")
                    .append("    st.availablealiquotvolume IS DISTINCT FROM expm.availablealiquotvolume OR ")
                    .append("    st.aliquotunit IS DISTINCT FROM expm.aliquotunit")
                    .append(")");
            }
            else
            {
                // SQL Server 2022 supports IS DISTINCT FROM
                incremental
                    .append("UPDATE st\n")
                    .append("SET aliquotcount = expm.aliquotcount, availablealiquotcount = expm.availablealiquotcount, aliquotvolume = expm.aliquotvolume, availablealiquotvolume = expm.availablealiquotvolume, aliquotunit = expm.aliquotunit\n")
                    .append("FROM temp.${NAME} st, exp.Material expm\n")
                    .append("WHERE expm.rowid = st.rowid AND expm.cpastype = ").appendValue(_lsid,d).append(" AND (\n")
                    .append("    COALESCE(st.aliquotcount,-2147483648) <> COALESCE(expm.aliquotcount,-2147483648) OR ")
                    .append("    COALESCE(st.availablealiquotcount,-2147483648) <> COALESCE(expm.availablealiquotcount,-2147483648) OR ")
                    .append("    COALESCE(st.aliquotvolume,-2147483648) <> COALESCE(expm.aliquotvolume,-2147483648) OR ")
                    .append("    COALESCE(st.availablealiquotvolume,-2147483648) <> COALESCE(expm.availablealiquotvolume,-2147483648) OR ")
                    .append("    COALESCE(st.aliquotunit,'-') <> COALESCE(expm.aliquotunit,'-')")
                    .append(")");
            }
            upsertWithRetry(incremental);
        }

        void executeIncrementalUpdate()
        {
            InvalidationCounters counters = getInvalidateCounters(_lsid);
            Timestamp since = counters.drainPendingUpdate();
            if (since == null || isIncrementalUpdateDisabled())
                return;

            try
            {
                upsertWithRetry(buildIncrementalUpdateSql(since));
            }
            catch (RuntimeException ex)
            {
                // Restore the drained watermark so nothing is lost; the caller's handler will drop the MQH and rebuild cleanly.
                counters.recordPendingUpdate(since);
                throw ex;
            }
        }

        SQLFragment buildIncrementalUpdateSql(@NotNull Timestamp changedSince)
        {
            SqlDialect d = CoreSchema.getInstance().getSchema().getSqlDialect();
            // SQL Server's datetime type lacks microsecond precision
            Timestamp comparison = d.isSqlServer() ? new Timestamp(changedSince.getTime() - 500) : changedSince;

            SQLFragment sql = new SQLFragment("WITH wModified AS (\n")
                    .append("SELECT rowId FROM exp.material expm WHERE expm.modified >= ?\n").add(comparison)
                    .append(")\n");

            SQLFragment src = new SQLFragment()
                    .append(getViewSourceSql()).append(" AND m.rowId IN (SELECT rowId FROM wModified)\n")
                    .append("UNION\n")
                    .append(getViewSourceSql()).append(" AND m.rootmaterialrowid IN (SELECT rowId FROM wModified)\n");

            if (d.isPostgreSQL())
            {
                sql.append("UPDATE temp.${NAME} AS st\nSET ");
                appendSetFromSrc(sql);
                sql.append("\nFROM (").append(src).append("\n) src\n").append("WHERE st.rowid = src.rowid");
            }
            else
            {
                sql.append("UPDATE st\nSET ");
                appendSetFromSrc(sql);
                sql.append("\nFROM temp.${NAME} st INNER JOIN (").append(src).append("\n) src ON st.rowid = src.rowid");
            }

            return sql;
        }

        private void appendSetFromSrc(SQLFragment sql)
        {
            String comma = "";
            for (ColumnInfo col : _updateColumns)
            {
                DatabaseIdentifier identifier = col.getSelectIdentifier();
                sql.append(comma).appendIdentifier(identifier).append(" = src.").appendIdentifier(identifier);
                comma = ", ";
            }
        }
    }

    static class _Materialized extends MaterializedQueryHelper.Materialized
    {
        final MaterializedQueryHelper.SupplierInvalidator incrementalInsertCheck;
        final MaterializedQueryHelper.SupplierInvalidator incrementalRollupCheck;
        final MaterializedQueryHelper.SupplierInvalidator incrementalDeleteCheck;
        final MaterializedQueryHelper.SupplierInvalidator incrementalUpdateCheck;

        _Materialized(_MaterializedQueryHelper mqh, String tableName, String cacheKey, long created, String sql)
        {
            super(mqh, tableName, cacheKey, created, sql);
            final InvalidationCounters counters = getInvalidateCounters(mqh._lsid);
            incrementalInsertCheck = new MaterializedQueryHelper.SupplierInvalidator(() -> String.valueOf(counters.insert.get()));
            incrementalRollupCheck = new MaterializedQueryHelper.SupplierInvalidator(() -> String.valueOf(counters.rollup.get()));
            incrementalDeleteCheck = new MaterializedQueryHelper.SupplierInvalidator(() -> String.valueOf(counters.delete.get()));
            incrementalUpdateCheck = new MaterializedQueryHelper.SupplierInvalidator(() -> String.valueOf(counters.update.get()));
        }

        @Override
        public void reset()
        {
            super.reset();
            long now = HeartBeat.currentTimeMillis();
            incrementalInsertCheck.stillValid(now);
            incrementalRollupCheck.stillValid(now);
            incrementalDeleteCheck.stillValid(now);
            incrementalUpdateCheck.stillValid(now);
        }

        /**
         * Returns true if any incremental counter has changed since the last reset, indicating that
         * {@code incrementalUpdateBeforeSelect()} would perform synchronous DB work.
         * Uses non-destructive {@code peekValid()} so the background task can still apply the updates.
         */
        @Override
        public boolean needsSynchronousWork()
        {
            if (super.needsSynchronousWork()) return true;
            return !incrementalInsertCheck.peekValid()
                || !incrementalDeleteCheck.peekValid()
                || !incrementalUpdateCheck.peekValid()
                || !incrementalRollupCheck.peekValid();
        }

        Lock getLock()
        {
            return _loadingLock;
        }
    }

    /** Immutable join-key columns that an incremental UPDATE never re-derives (excluded from the re-assign list). */
    static final Set<FieldKey> IMMUTABLE_UPDATE_COLUMNS = Set.of(RowId.fieldKey(), LSID.fieldKey(), CpasType.fieldKey(), RootMaterialRowId.fieldKey());

    /* SELECT and JOIN, does not include WHERE */
    private SQLFragment getJoinSQL(Set<FieldKey> selectedColumns)
    {
        return getJoinSQL(selectedColumns, null);
    }

    private SQLFragment getJoinSQL(Set<FieldKey> selectedColumns, @Nullable List<ColumnInfo> outUpdateColumns)
    {
        TableInfo provisioned = null == _ss ? null : _ss.getTinfo();
        Set<String> provisionedCols = new CaseInsensitiveHashSet(provisioned != null ? provisioned.getColumnNameSet() : Collections.emptySet());
        provisionedCols.remove(RowId.name());
        provisionedCols.remove(Name.name());
        boolean hasProvisionedColumns = containsProvisionedColumns(selectedColumns, provisionedCols);
        boolean hasSampleColumns = false;
        boolean hasAliquotColumns = false;

        List<ColumnInfo> materialCols = _rootTable.getColumns();
        selectedColumns = computeInnerSelectedColumns(selectedColumns);

        SQLFragment sql = new SQLFragment();
        sql.appendComment("<ExpMaterialTableImpl.getJoinSQL(" + (null == _ss ? "" : _ss.getName()) + ")>", getSqlDialect());
        sql.append("SELECT ");
        String comma = "";
        for (ColumnInfo materialCol : materialCols)
        {
            // don't need to generate SQL for columns that aren't selected
            if (ALL_COLUMNS == selectedColumns || selectedColumns.contains(materialCol.getFieldKey()))
            {
                sql.append(comma).append("m.").appendIdentifier(materialCol.getSelectIdentifier());
                comma = ", ";
                if (null != outUpdateColumns && !IMMUTABLE_UPDATE_COLUMNS.contains(materialCol.getFieldKey()))
                    outUpdateColumns.add(materialCol);
            }
        }

        if (null != provisioned && hasProvisionedColumns)
        {
            for (ColumnInfo propertyColumn : provisioned.getColumns())
            {
                // don't select twice
                if (
                    RowId.name().equalsIgnoreCase(propertyColumn.getColumnName()) ||
                    Name.name().equalsIgnoreCase(propertyColumn.getColumnName())
                )
                {
                    continue;
                }

                // don't need to generate SQL for columns that aren't selected
                if (ALL_COLUMNS == selectedColumns || selectedColumns.contains(propertyColumn.getFieldKey()) || propertyColumn.isMvIndicatorColumn())
                {
                    boolean rootField = StringUtils.isEmpty(propertyColumn.getDerivationDataScope())
                            || ExpSchema.DerivationDataScopeType.ParentOnly.name().equalsIgnoreCase(propertyColumn.getDerivationDataScope());
                    String alias;
                    if ("genid".equalsIgnoreCase(propertyColumn.getColumnName()) || propertyColumn.isUniqueIdField())
                    {
                        alias = "m_aliquot";
                        hasAliquotColumns = true;
                    }
                    else if (rootField)
                    {
                        alias = "m_sample";
                        hasSampleColumns = true;
                    }
                    else
                    {
                        alias = "m_aliquot";
                        hasAliquotColumns = true;
                    }

                    sql.append(comma).append(propertyColumn.getValueSql(alias)).append(" AS ").appendIdentifier(propertyColumn.getSelectIdentifier());
                    comma = ", ";

                    // provisioned columns are never immutable join keys, so always re-derivable on update
                    if (null != outUpdateColumns)
                        outUpdateColumns.add(propertyColumn);
                }
            }
        }

        sql.append("\nFROM ");
        sql.append(_rootTable, "m");
        if (hasSampleColumns)
            sql.append(" INNER JOIN ").append(provisioned, "m_sample").append(" ON m.RootMaterialRowId = m_sample.RowId");
        if (hasAliquotColumns)
            sql.append(" INNER JOIN ").append(provisioned, "m_aliquot").append(" ON m.RowId = m_aliquot.RowId");

        sql.appendComment("</ExpMaterialTableImpl.getJoinSQL()>", getSqlDialect());
        return sql;
    }

    private static class SampleTypeAmountDisplayColumn extends ExprColumn
    {
        public SampleTypeAmountDisplayColumn(TableInfo parent, String amountFieldName, String unitFieldName, String label, Set<String> importAliases, Unit typeUnit)
        {
            super(parent, FieldKey.fromParts(amountFieldName), new SQLFragment(
                            "(CASE WHEN ").append(ExprColumn.STR_TABLE_ALIAS + ".").append(unitFieldName)
                            .append(" = ? AND ").append(ExprColumn.STR_TABLE_ALIAS + ".").append(amountFieldName)
                            .append(" IS NOT NULL THEN CAST(").append(ExprColumn.STR_TABLE_ALIAS + ".").append(amountFieldName)
                            .append(" / ? AS ")
                            .append(parent.getSqlDialect().isPostgreSQL() ? "DECIMAL" : "DOUBLE PRECISION")
                            .append(") ELSE ").append(ExprColumn.STR_TABLE_ALIAS + ".").append(amountFieldName)
                            .append(" END)")
                            .add(typeUnit.getBase().toString())
                            .add(typeUnit.getValue()),
                    JdbcType.DOUBLE);

            setLabel(label);
            setImportAliasesSet(importAliases);
        }
    }

    private static class SampleTypeUnitDisplayColumn extends ExprColumn
    {
        public SampleTypeUnitDisplayColumn(TableInfo parent, String unitFieldName, Unit typeUnit)
        {
            super(parent, FieldKey.fromParts(unitFieldName), new SQLFragment(
                            "(CASE WHEN ").append(ExprColumn.STR_TABLE_ALIAS + ".").append(unitFieldName)
                            .append(" = ? THEN ? ELSE ").append(ExprColumn.STR_TABLE_ALIAS + ".").append(unitFieldName)
                            .append(" END)")
                            .add(typeUnit.getBase().toString())
                            .add(typeUnit.toString()),
                    JdbcType.VARCHAR);
        }
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        return new SampleTypeUpdateServiceDI(this, _ss);
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        if (_ss == null)
        {
            // Allow read and delete for exp.Materials.
            // Don't allow insert/update on exp.Materials without a sample type.
            if (perm == DeletePermission.class || perm == ReadPermission.class)
                return getContainer().hasPermission(user, perm);
            return false;
        }

        if (_ss.isMedia() && perm == ReadPermission.class)
            return getContainer().hasPermission(user, MediaReadPermission.class);

        return super.hasPermission(user, perm);
    }

    @NotNull
    @Override
    public List<IndexDefinition> getUniqueIndices()
    {
        // Rewrite the "idx_material_ak" unique index over "Folder", "SampleSet", "Name" to just "Name"
        // Issue 25397: Don't include the "idx_material_ak" index if the "Name" column hasn't been added to the table.
        // Some FKs to ExpMaterialTable don't include the "Name" column (e.g., NabBaseTable.Specimen)
        String indexName = "idx_material_ak";
        List<IndexDefinition> ret = new ArrayList<>(super.getUniqueIndices());
        if (getColumn("Name") != null)
            ret.add(new IndexDefinition(indexName, IndexType.Unique, Arrays.asList(getColumn("Name")), null));
        else
            ret.removeIf(def -> def.name().equals(indexName));
        return Collections.unmodifiableList(ret);
    }

    //
    // UpdatableTableInfo
    //

    @Override
    public @Nullable Long getOwnerObjectId()
    {
        return OntologyManager.ensureObject(_ss.getContainer(), _ss.getLSID(), (Long) null);
    }

    @Nullable
    @Override
    public CaseInsensitiveHashMap<String> remapSchemaColumns()
    {
        CaseInsensitiveHashMap<String> m = new CaseInsensitiveHashMap<>();

        if (null != getRealTable().getColumn("container") && null != getColumn("folder"))
        {
            m.put("container", "folder");
        }

        for (ColumnInfo col : getColumns())
        {
            if (col.getMvColumnName() != null)
                m.put(col.getName() + "_" + MvColumn.MV_INDICATOR_SUFFIX, col.getMvColumnName().getName());
        }

        return m;
    }

    @Override
    public Set<String> getAltMergeKeys(DataIteratorContext context)
    {
        return MATERIAL_ALT_KEYS;
    }

    @Override
    public @Nullable Set<String> getExistingRecordKeyColumnNames(DataIteratorContext context, Map<String, Integer> colNameMap)
    {
        Set<String> keyColumnNames = new CaseInsensitiveHashSet();

        if (context.getInsertOption().allowUpdate)
        {
            if (context.getInsertOption().updateOnly)
            {
                if (colNameMap.containsKey(RowId.name()))
                    keyColumnNames.add(RowId.name());
                else if (colNameMap.containsKey(Name.name()))
                {
                    keyColumnNames.add(MaterialSourceId.name());
                    keyColumnNames.add(Name.name());
                }
                else
                    throw new IllegalArgumentException("Either RowId or Name is required for update.");
            }
            else
            {
                Set<String> altMergeKeys = getAltMergeKeys(context);
                if (altMergeKeys != null)
                    keyColumnNames.addAll(altMergeKeys);
            }
        }

        return keyColumnNames.isEmpty() ? null : keyColumnNames;
    }

    @Override
    public @Nullable Set<String> getExistingRecordSharedKeyColumnNames()
    {
        return CaseInsensitiveHashSet.of(MaterialSourceId.name());
    }

    @Override
    @NotNull
    public List<Set<String>> getAdditionalRequiredInsertColumns()
    {
        if (getSampleType() == null)
            return Collections.emptyList();

        try
        {
            return getRequiredParentImportFields(getSampleType().getRequiredImportAliases());
        }
        catch (IOException e)
        {
            return Collections.emptyList();
        }
    }

    @Override
    public DataIteratorBuilder persistRows(DataIteratorBuilder data, DataIteratorContext context)
    {
        TableInfo propertiesTable = _ss.getTinfo();

        // The specimens sample type doesn't have a properties table
        if (propertiesTable == null)
            return data;

        try
        {
            final Container container = getContainer();
            final User user = getUserSchema().getUser();
            ExperimentServiceImpl expService = ExperimentServiceImpl.get();
            SearchService.TaskIndexingQueue queue = SearchService.get().defaultTask().getQueue(container, SearchService.PRIORITY.modified);

            DataIteratorBuilder dib = new ExpDataIterators.PersistDataIteratorBuilder(data, this, propertiesTable, _ss, container, user, _ss.getImportAliasesIncludingAliquot())
                .setFileLinkDirectory(SAMPLE_TYPE_FILE_DIRECTORY_NAME)
                .setIndexFunction(searchIndexDataKeys -> propertiesTable.getSchema().getScope().addCommitTask(() -> {
                        List<String> lsids = searchIndexDataKeys.lsids();
                        List<Long> orderedRowIds = searchIndexDataKeys.orderedRowIds();

                        // Issue 51263: order by RowId to reduce deadlock
                        ListUtils.partition(orderedRowIds, 100).forEach(sublist ->
                                queue.addRunnable((q) ->
                                {
                                    for (ExpMaterialImpl expMaterial : expService.getExpMaterials(sublist))
                                        expMaterial.index(q, this);
                                })
                        );

                        ListUtils.partition(lsids, 100).forEach(sublist ->
                                queue.addRunnable((q) ->
                                {
                                    for (ExpMaterialImpl expMaterial : expService.getExpMaterialsByLsid(sublist))
                                        expMaterial.index(q, this);
                                })
                        );
                    }, DbScope.CommitTaskOption.POSTCOMMIT)
                );

            dib = LoggingDataIterator.wrap(dib);
            return LoggingDataIterator.wrap(new AliasDataIteratorBuilder(dib, container, user, expService.getTinfoMaterialAliasMap(), _ss, true));
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    @NotNull
    public AuditBehaviorType getDefaultAuditBehavior()
    {
        return AuditBehaviorType.DETAILED;
    }

    static final Set<String> excludeFromDetailedAuditField;
    static
    {
        var set = new CaseInsensitiveHashSet();
        set.addAll(TableInfo.defaultExcludedDetailedUpdateAuditFields);
        set.addAll(ExpDataIterators.MATERIAL_NOT_FOR_UPDATE);
        // We don't want the inventory columns to show up in the sample timeline audit record;
        // they are captured in their own audit record.
        set.addAll(InventoryService.InventoryStatusColumn.names());
        excludeFromDetailedAuditField = Collections.unmodifiableSet(set);
    }

    @Override
    public @NotNull Set<String> getExcludedDetailedUpdateAuditFields()
    {
        // uniqueId fields don't change in reality, so exclude them from the audit updates
        Set<String> excluded = new CaseInsensitiveHashSet();
        excluded.addAll(this.getUniqueIdFields());
        excluded.addAll(excludeFromDetailedAuditField);
        return excluded;
    }

    @Override
    public List<Pair<String, String>> getImportTemplates(ViewContext ctx)
    {
        // respect any metadata overrides
        if (getRawImportTemplates() != null)
            return super.getImportTemplates(ctx);

        List<Pair<String, String>> templates = new ArrayList<>();
        ActionURL url = PageFlowUtil.urlProvider(QueryUrls.class).urlCreateExcelTemplate(ctx.getContainer(), getPublicSchemaName(), getName());
        url.addParameter("headerType", ColumnHeaderType.ImportField.name());
        try
        {
            if (getSampleType() != null && !getSampleType().getImportAliases().isEmpty())
            {
                for (String aliasKey : getSampleType().getImportAliases().keySet())
                    url.addParameter("includeColumn", aliasKey);
            }
        }
        catch (IOException ignored)
        {}
        templates.add(Pair.of("Download Template", url.toString()));
        return templates;
    }

    @Override
    public void overlayMetadata(String tableName, UserSchema schema, Collection<QueryException> errors)
    {
        if (SamplesSchema.SCHEMA_NAME.equals(schema.getName()))
        {
            Collection<TableType> metadata = QueryService.get().findMetadataOverride(schema, SamplesSchema.SCHEMA_METADATA_NAME, false, false, errors, null);
            if (null != metadata)
            {
                overlayMetadata(metadata, schema, errors);
            }
        }
        super.overlayMetadata(tableName, schema, errors);
    }

    static class SampleTypeAmountPrecisionDisplayColumn extends DataColumn
    {
        private final Unit typeUnit;
        private final boolean applySampleTypePrecision;

        public SampleTypeAmountPrecisionDisplayColumn(ColumnInfo col, Unit typeUnit)
        {
            super(col, false);
            this.typeUnit = typeUnit;
            this.applySampleTypePrecision = col.getFormat() == null; // only apply if no custom format is set by user
        }

        @Override
        public Object getDisplayValue(RenderContext ctx)
        {
            Object value = super.getDisplayValue(ctx);
            if (this.applySampleTypePrecision && value != null)
            {
                int scale = this.typeUnit == null ? Quantity.DEFAULT_PRECISION_SCALE : this.typeUnit.getPrecisionScale();
                value = Precision.round(Double.valueOf(value.toString()), scale);
            }
            return value;
        }
    }

    @TestWhen(TestWhen.When.BVT)
    public static class IncrementalUpdateTestCase extends Assert
    {
        private static User _user;
        private static Container _c;

        @BeforeClass
        public static void setup()
        {
            JunitUtil.deleteTestContainer();

            _c = JunitUtil.getTestContainer();
            _user = TestContext.get().getUser();
        }

        @AfterClass
        public static void tearDown()
        {
            JunitUtil.deleteTestContainer();
        }

        @Test
        public void testTargetedSingleRowUpdate() throws Exception
        {
            ExpSampleType st = createSampleType("IncrUpdTargeted");
            int root = insertRoots(st, "R1").getFirst();
            int aliquot = insertAliquots(st, "R1", 1).getFirst();

            ExpMaterialTableImpl table = getSamplesTable(st);
            assertCacheMatchesFreshDerivation(table, st.getLSID()); // baseline materialization

            // Targeted update of one aliquot's aliquot-scoped property.
            updateRows(st, List.of(CaseInsensitiveHashMap.of(RowId.name(), aliquot, "aliquotProp", "changed")));
            assertCacheMatchesFreshDerivation(table, st.getLSID());

            // And a targeted update of the root's own (non-derived-onto-aliquot) name-adjacent material column path.
            updateRows(st, List.of(CaseInsensitiveHashMap.of(RowId.name(), root, "rootProp", "rootChanged0")));
            assertCacheMatchesFreshDerivation(table, st.getLSID());
        }

        @Test
        public void testBatchUpdate() throws Exception
        {
            ExpSampleType st = createSampleType("IncrUpdBatch");
            List<Integer> roots = insertRoots(st, "R1", "R2", "R3", "R4", "R5");

            ExpMaterialTableImpl table = getSamplesTable(st);
            assertCacheMatchesFreshDerivation(table, st.getLSID());

            List<Map<String, Object>> updates = new ArrayList<>();
            for (int i = 0; i < roots.size(); i++)
                updates.add(CaseInsensitiveHashMap.of(RowId.name(), roots.get(i), "rootProp", "batch" + i));
            updateRows(st, updates);
            assertCacheMatchesFreshDerivation(table, st.getLSID());
        }

        @Test
        public void testRootFanoutUpdate() throws Exception
        {
            ExpSampleType st = createSampleType("IncrUpdFanout");
            int root = insertRoots(st, "R1").getFirst();
            insertAliquots(st, "R1", 25);

            ExpMaterialTableImpl table = getSamplesTable(st);
            assertCacheMatchesFreshDerivation(table, st.getLSID());

            // Editing the root's ParentOnly property must re-derive m_sample.* for every aliquot beneath it.
            updateRows(st, List.of(CaseInsensitiveHashMap.of(RowId.name(), root, "rootProp", "fannedOut")));
            assertCacheMatchesFreshDerivation(table, st.getLSID());
        }

        @Test
        public void testFullResync() throws Exception
        {
            ExpSampleType st = createSampleType("IncrUpdFull");
            List<Integer> roots = insertRoots(st, "R1", "R2", "R3");
            insertAliquots(st, "R1", 3);

            ExpMaterialTableImpl table = getSamplesTable(st);
            assertCacheMatchesFreshDerivation(table, st.getLSID());

            // Make a real change, then force the full rebuild path (as a no-watermark update/merge would: drop the MQH)
            // before reading. The next read must rebuild the whole view from scratch.
            updateRows(st, List.of(CaseInsensitiveHashMap.of(RowId.name(), roots.getFirst(), "rootProp", "fullResynced")));
            _materializedQueries.remove(st.getLSID());
            assertCacheMatchesFreshDerivation(table, st.getLSID());
        }

        @Test
        public void testModifiedBoundaryNotSkipped() throws Exception
        {
            // The watermark predicate uses >= so a row modified at exactly the watermark is never skipped.
            ExpSampleType st = createSampleType("IncrUpdBoundary");
            int root = insertRoots(st, "R1").getFirst();

            ExpMaterialTableImpl table = getSamplesTable(st);
            assertCacheMatchesFreshDerivation(table, st.getLSID());

            // Drive the targeted update with a watermark equal to the row's own Modified, simulating same-tick capture.
            updateRows(st, List.of(CaseInsensitiveHashMap.of(RowId.name(), root, "rootProp", "boundary")));
            Timestamp rowModified = new SqlSelector(ExperimentServiceImpl.getExpSchema().getScope(),
                    new SQLFragment("SELECT modified FROM exp.material WHERE rowid = ?").add(root)).getObject(Timestamp.class);
            InvalidationCounters counters = getInvalidateCounters(st.getLSID());
            counters.pendingUpdateSince.set(rowModified);
            counters.update.incrementAndGet();
            assertCacheMatchesFreshDerivation(table, st.getLSID());
        }

        @Test
        public void testSequentialUpdates() throws Exception
        {
            // Two updates in sequence: each read must apply its own watermark and leave the cache consistent.
            ExpSampleType st = createSampleType("IncrUpdSequential");
            int root = insertRoots(st, "R1").getFirst();
            insertAliquots(st, "R1", 2);

            ExpMaterialTableImpl table = getSamplesTable(st);
            assertCacheMatchesFreshDerivation(table, st.getLSID());

            updateRows(st, List.of(CaseInsensitiveHashMap.of(RowId.name(), root, "rootProp", "first")));
            assertCacheMatchesFreshDerivation(table, st.getLSID());

            updateRows(st, List.of(CaseInsensitiveHashMap.of(RowId.name(), root, "rootProp", "second")));
            assertCacheMatchesFreshDerivation(table, st.getLSID());
        }

        @Test
        public void testMergeInsertAndUpdate() throws Exception
        {
            // A single merge that both updates existing rows and inserts new ones must leave the cache consistent: the
            // merge signals both the update path (existing rows, watermark-targeted) and the insert path (new rows).
            ExpSampleType st = createSampleType("IncrUpdMerge");
            insertRoots(st, "R1", "R2");
            insertAliquots(st, "R1", 2);

            ExpMaterialTableImpl table = getSamplesTable(st);
            assertCacheMatchesFreshDerivation(table, st.getLSID());

            // R1/R2 already exist (matched by name -> updated); R3/R4 are new (-> inserted), all in one merge.
            mergeRows(st, List.of(
                    CaseInsensitiveHashMap.of("name", "R1", "rootProp", "mergedR1"),
                    CaseInsensitiveHashMap.of("name", "R2", "rootProp", "mergedR2"),
                    CaseInsensitiveHashMap.of("name", "R3", "rootProp", "mergedR3"),
                    CaseInsensitiveHashMap.of("name", "R4", "rootProp", "mergedR4")));
            assertCacheMatchesFreshDerivation(table, st.getLSID());
        }

        private ExpSampleType createSampleType(String name) throws Exception
        {
            List<GWTPropertyDescriptor> props = new ArrayList<>();
            props.add(new GWTPropertyDescriptor("name", "string"));
            GWTPropertyDescriptor rootProp = new GWTPropertyDescriptor("rootProp", "string");
            rootProp.setDerivationDataScope(ExpSchema.DerivationDataScopeType.ParentOnly.name()); // -> m_sample join
            props.add(rootProp);
            GWTPropertyDescriptor aliquotProp = new GWTPropertyDescriptor("aliquotProp", "string");
            aliquotProp.setDerivationDataScope(ExpSchema.DerivationDataScopeType.ChildOnly.name()); // -> m_aliquot join
            props.add(aliquotProp);
            return SampleTypeService.get().createSampleType(_c, _user, name, null, props, Collections.emptyList(), -1, -1, -1, -1, null);
        }

        private ExpMaterialTableImpl getSamplesTable(ExpSampleType st)
        {
            return (ExpMaterialTableImpl) QueryService.get().getUserSchema(_user, _c, SamplesSchema.SCHEMA_NAME).getTable(st.getName());
        }

        private @NotNull QueryUpdateService getUpdateService(ExpSampleType st)
        {
            TableInfo table = getSamplesTable(st);
            QueryUpdateService service = table.getUpdateService();
            if (service == null)
                throw new IllegalArgumentException("No QueryUpdateService found for table " + st.getName());

            return service;
        }

        private List<Integer> insertRoots(ExpSampleType st, String... names) throws Exception
        {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (int i = 0; i < names.length; i++)
                rows.add(CaseInsensitiveHashMap.of("name", names[i], "rootProp", "root" + i));
            return insert(st, rows);
        }

        private List<Integer> insertAliquots(ExpSampleType st, String rootName, int count) throws Exception
        {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (int i = 0; i < count; i++)
                rows.add(CaseInsensitiveHashMap.of("name", rootName + "-" + i, "AliquotedFrom", rootName, "aliquotProp", "aliquot" + i));
            return insert(st, rows);
        }

        private List<Integer> insert(ExpSampleType st, List<Map<String, Object>> rows) throws Exception
        {
            BatchValidationException errors = new BatchValidationException();
            List<Map<String, Object>> inserted = getUpdateService(st).insertRows(_user, _c, rows, errors, null, null);
            if (errors.hasErrors())
                throw errors;

            if (inserted == null || inserted.isEmpty())
                return Collections.emptyList();

            List<Integer> rowIds = new ArrayList<>();
            for (Map<String, Object> row : inserted)
                rowIds.add(((Number) row.get(RowId.name())).intValue());
            return rowIds;
        }

        private void updateRows(ExpSampleType st, List<Map<String, Object>> rows) throws Exception
        {
            BatchValidationException errors = new BatchValidationException();
            getUpdateService(st).updateRows(_user, _c, rows, null, errors, null, null);
            if (errors.hasErrors())
                throw errors;
        }

        private void mergeRows(ExpSampleType st, List<Map<String, Object>> rows) throws Exception
        {
            BatchValidationException errors = new BatchValidationException();
            getUpdateService(st).mergeRows(_user, _c, MapDataIterator.of(rows), errors, null, null);
            if (errors.hasErrors())
                throw errors;
        }

        /**
         * Trigger materialization (and any pending incremental update) synchronously, then assert the cached
         * temp table equals a fresh derivation of the join query in both directions.
         */
        private void assertCacheMatchesFreshDerivation(ExpMaterialTableImpl table, String lsid)
        {
            _MaterializedQueryHelper mqh = table.getOrCreateMQH();
            assertNotNull("MQH should not be null for a sample type table", mqh);
            SQLFragment cached = new SQLFragment("SELECT * FROM ").append(mqh.getFromSql("_cached_view_"));
            SQLFragment fresh = table.getJoinSQL(null).append(" WHERE CpasType = ").appendValue(lsid);
            DbScope scope = ExperimentServiceImpl.getExpSchema().getScope();
            assertEquals("Cached rows not found in fresh derivation", 0, countDiff(scope, cached, fresh));
            assertEquals("Fresh-derivation rows not found in cache", 0, countDiff(scope, fresh, cached));
        }

        private int countDiff(DbScope scope, SQLFragment a, SQLFragment b)
        {
            SQLFragment sql = new SQLFragment("SELECT COUNT(*) FROM (\n").append(a).append("\nEXCEPT\n").append(b).append("\n) diff_");
            Integer count = new SqlSelector(scope, sql).getObject(Integer.class);
            return count == null ? 0 : count;
        }
    }
}
