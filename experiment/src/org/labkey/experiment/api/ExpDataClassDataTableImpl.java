/*
 * Copyright (c) 2015-2019 LabKey Corporation
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

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.converters.IntegerConverter;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentParentFactory;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.collections.ArrayListMap;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.Sets;
import org.labkey.api.compliance.TableRules;
import org.labkey.api.compliance.TableRulesManager;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnHeaderType;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.PHI;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.UnionContainerFilter;
import org.labkey.api.data.UpdateableTableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.dataiterator.AttachmentDataIterator;
import org.labkey.api.dataiterator.CoerceDataIterator;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.DataIteratorUtil;
import org.labkey.api.dataiterator.DetailedAuditLogDataIterator;
import org.labkey.api.dataiterator.DropColumnsDataIterator;
import org.labkey.api.dataiterator.LoggingDataIterator;
import org.labkey.api.dataiterator.NameExpressionDataIterator;
import org.labkey.api.dataiterator.SimpleTranslator;
import org.labkey.api.dataiterator.StandardDataIteratorBuilder;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.MvColumn;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.exp.api.NameExpressionOptionService;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.ConceptURIVocabularyDomainProvider;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.DomainUtil;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.query.ExpDataClassDataTable;
import org.labkey.api.exp.query.ExpDataTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.gwt.client.AuditBehaviorType;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.PropertyForeignKey;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.QueryUrls;
import org.labkey.api.query.RowIdForeignKey;
import org.labkey.api.query.UserIdForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.query.column.BuiltInColumnTypes;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.DataClassReadPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.MediaReadPermission;
import org.labkey.api.security.permissions.MoveEntitiesPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.study.assay.FileLinkDisplayColumn;
import org.labkey.api.usageMetrics.SimpleMetricsService;
import org.labkey.api.util.CachingSupplier;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewContext;
import org.labkey.data.xml.TableType;
import org.labkey.experiment.ExpDataIterators;
import org.labkey.experiment.ExpDataIterators.AliasDataIteratorBuilder;
import org.labkey.experiment.ExpDataIterators.PersistDataIteratorBuilder;
import org.labkey.experiment.controllers.exp.ExperimentController;
import org.labkey.experiment.lineage.LineageMethod;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.labkey.api.exp.api.ExpRunItem.PARENT_IMPORT_ALIAS_MAP_PROP;
import static org.labkey.api.exp.query.ExpDataClassDataTable.Column.Name;
import static org.labkey.api.exp.query.ExpDataClassDataTable.Column.QueryableInputs;
import static org.labkey.api.exp.query.ExpDataClassDataTable.Column.RowId;
import static org.labkey.experiment.ExpDataIterators.incrementCounts;

/**
 * User: kevink
 * Date: 9/29/15
 */
public class ExpDataClassDataTableImpl extends ExpRunItemTableImpl<ExpDataClassDataTable.Column> implements ExpDataClassDataTable
{
    private final @NotNull ExpDataClassImpl _dataClass;
    private final Supplier<TableInfo> _dataClassDataTableSupplier;
    public static final String DATA_COUNTER_SEQ_PREFIX = "DataNameGenCounter-";

    public static final Set<String> DATA_CLASS_ALT_MERGE_KEYS;
    public static final Set<String> DATA_CLASS_ALT_UPDATE_KEYS;
    private static final Set<String> ALLOWED_IMPORT_HEADERS;
    static {
        DATA_CLASS_ALT_MERGE_KEYS = new HashSet<>(Arrays.asList(Column.ClassId.name(), Name.name()));
        DATA_CLASS_ALT_UPDATE_KEYS = new HashSet<>(Arrays.asList(Column.LSID.name()));
        ALLOWED_IMPORT_HEADERS = new HashSet<>(Arrays.asList("name", "description", "flag", "comment", "alias", "datafileurl"));
    }

    private Map<String/*domain name*/, DataClassVocabularyProviderProperties> _vocabularyDomainProviders;

    public ExpDataClassDataTableImpl(String name, UserSchema schema, ContainerFilter cf, @NotNull ExpDataClassImpl dataClass)
    {
        super(name, ExperimentService.get().getTinfoData(), schema, cf);
        _dataClass = dataClass;
        _dataClassDataTableSupplier = new CachingSupplier<>(_dataClass::getTinfo);
        addAllowablePermission(InsertPermission.class);
        addAllowablePermission(UpdatePermission.class);
        addAllowablePermission(MoveEntitiesPermission.class);
        ActionURL url = PageFlowUtil.urlProvider(ExperimentUrls.class).getImportDataURL(getContainer(), _dataClass.getName());
        setImportURL(new DetailsURL(url));

        // Filter exp.data to only those rows that are members of the DataClass
        addCondition(new SimpleFilter(FieldKey.fromParts("classId"), _dataClass.getRowId()));

        setAllowedInsertOption(QueryUpdateService.InsertOption.MERGE);
    }

    @Override
    @NotNull
    public Domain getDomain()
    {
        return _dataClass.getDomain();
    }

    @Override
    public AuditBehaviorType getAuditBehavior()
    {
        // if there is xml config, use xml config
        if (_auditBehaviorType == AuditBehaviorType.NONE && getXmlAuditBehaviorType() == null)
        {
            ExpSchema.DataClassCategoryType categoryType = ExpSchema.DataClassCategoryType.fromString(_dataClass.getCategory());
            if (categoryType != null && categoryType.defaultBehavior != null)
                return categoryType.defaultBehavior;
        }

        return _auditBehaviorType;
    }

    @Override
    @NotNull
    public Set<String> getExtraDetailedUpdateAuditFields()
    {
        ExpSchema.DataClassCategoryType categoryType = ExpSchema.DataClassCategoryType.fromString(_dataClass.getCategory());
        if (categoryType != null)
            return categoryType.additionalAuditFields;

        return Collections.emptySet();
    }

    @Override
    protected ColumnInfo resolveColumn(String name)
    {
        ColumnInfo result = super.resolveColumn(name);
        if (result == null)
        {
            if (QueryableInputs.name().equalsIgnoreCase(name))
            {
                result = createColumn(QueryableInputs.name(), QueryableInputs);
            }
        }
        return result;
    }

    @Override
    public MutableColumnInfo createColumn(String alias, Column column)
    {
        switch (column)
        {
            case RowId ->
            {
                var c = wrapColumn(alias, getRealTable().getColumn("RowId"));
                // When no sorts are added by views, QueryServiceImpl.createDefaultSort() adds the primary key's default sort direction
                c.setSortDirection(Sort.SortDirection.DESC);
                c.setFk(new RowIdForeignKey(c));
                c.setKeyField(true);
                c.setHidden(true);
                return c;
            }
            case LSID ->
            {
                var c = wrapColumn(alias, getRealTable().getColumn("LSID"));
                c.setHidden(true);
                c.setShownInInsertView(false);
                c.setShownInUpdateView(false);
                c.setUserEditable(false);
                c.setCalculated(true); // So DataIterator won't consider the column as required. See c.isRequiredForInsert()
                return c;
            }
            case Name ->
            {
                var c = wrapColumn(alias, getRealTable().getColumn(column.name()));
                String nameExpression = _dataClass.getNameExpression();
                c.setNameExpression(nameExpression);
                c.setNullable(nameExpression != null);

                // shut off this field in insert and update views if user-specified names are not allowed
                if (!NameExpressionOptionService.get().getAllowUserSpecificNamesValue(getContainer()))
                {
                    c.setShownInInsertView(false);
                    c.setShownInUpdateView(false);
                }
                return c;
            }
            case Created, Modified, Description ->
            {
                return wrapColumn(alias, getRealTable().getColumn(column.name()));
            }
            case CreatedBy, ModifiedBy ->
            {
                var c = wrapColumn(alias, getRealTable().getColumn(column.name()));
                c.setFk(new UserIdForeignKey(getUserSchema()));
                c.setShownInInsertView(false);
                c.setShownInUpdateView(false);
                c.setUserEditable(false);
                return c;
            }
            case ClassId, DataClass ->
            {
                var c = wrapColumn(alias, getRealTable().getColumn("classId"));
                c.setFk(new QueryForeignKey(QueryForeignKey.from(getUserSchema(), getContainerFilter()).schema(ExpSchema.SCHEMA_NAME).to(ExpSchema.TableType.DataClasses.name(), "RowId", "Name"))
                {
                    @Override
                    protected ContainerFilter getLookupContainerFilter()
                    {
                        // Issue 45664: Data Class metadata not available in query when querying cross-folder
                        // Same as CurrentPlusProjectAndShared except it includes the DataClass' container as well.
                        Set<Container> containers = new HashSet<>();
                        containers.add(_dataClass.getContainer());
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
                c.setShownInInsertView(false);
                c.setShownInUpdateView(false);
                c.setUserEditable(false);
                return c;
            }
            case Flag ->
            {
                return createFlagColumn(Column.Flag.toString());
            }
            case Folder ->
            {
                var c = wrapColumn(alias, getRealTable().getColumn("Container"));
                c.setLabel("Folder");
                c.setShownInDetailsView(false);
                return c;
            }
            case Alias ->
            {
                return createAliasColumn(alias, ExperimentService.get()::getTinfoDataAliasMap);
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
            case DataFileUrl ->
            {
                var dataFileUrl = wrapColumn(alias, getRealTable().getColumn("DataFileUrl"));
                dataFileUrl.setUserEditable(false);
                dataFileUrl.setHidden(true);
                DetailsURL url = new DetailsURL(new ActionURL(ExperimentController.ShowFileAction.class, getContainer()), Collections.singletonMap("rowId", "rowId"));
                dataFileUrl.setDisplayColumnFactory(new FileLinkDisplayColumn.Factory(url, getContainer(), FieldKey.fromParts("RowId")));
                return dataFileUrl;
            }
            case Properties ->
            {
                return createPropertiesColumn(alias);
            }
            default -> throw new IllegalArgumentException("Unknown column " + column);
        }
    }

    @Override
    protected void populateColumns()
    {
        UserSchema schema = getUserSchema();

        if (_dataClass.getDescription() != null)
            setDescription(_dataClass.getDescription());
        else
            setDescription("Contains one row per registered data in the " + _dataClass.getName() + " data class");

        TableInfo extTable = _dataClassDataTableSupplier.get();

        LinkedHashSet<FieldKey> defaultVisible = new LinkedHashSet<>();
        defaultVisible.add(FieldKey.fromParts(Column.Name));

        addContainerColumn(Column.Folder, null);
        if (getContainer().hasProductFolders())
            defaultVisible.add(FieldKey.fromParts(Column.Folder));

        defaultVisible.add(FieldKey.fromParts(Column.Flag));

        addColumn(Column.LSID);
        var rowIdCol = addColumn(Column.RowId);
        var nameCol = addColumn(Column.Name);

        String nameExpression = _dataClass.getNameExpression();
        if (!StringUtils.isEmpty(nameExpression))
        {
            String nameExpressionPreview = getExpNameExpressionPreview(getUserSchema().getSchemaName(), _dataClass.getName(), getUserSchema().getUser());
            String nameDesc = ExpMaterialTableImpl.appendNameExpressionDescription(nameCol.getDescription(), nameExpression, nameExpressionPreview);
            nameCol.setDescription(nameDesc);
        }

        addColumn(Column.Created);
        addColumn(Column.CreatedBy);
        addColumn(Column.Modified);
        addColumn(Column.ModifiedBy);
        addColumn(Column.Flag);
        addColumn(Column.ClassId);
        addColumn(Column.DataClass);
        addColumn(Column.Description);
        addColumn(Column.Alias);

        //TODO: may need to expose ExpData.Run as well

        FieldKey lsidFieldKey = FieldKey.fromParts(Column.LSID.name());

        Supplier<Map<DomainProperty, Object>> defaultsSupplier = null;

        // Add the domain columns
        Set<String> skipCols = CaseInsensitiveHashSet.of("lsid", "rowid", "name", "classid");
        for (ColumnInfo col : extTable.getColumns())
        {
            // Don't include PHI columns in full text search index
            // CONSIDER: Can we move this to a base class? Maybe in .addColumn()
            if (schema.getUser().isSearchUser() && !col.getPHI().isLevelAllowed(PHI.NotPHI))
                continue;

            // Skip the lookup column itself, LSID, and exp.data.rowid -- it is added above
            String colName = col.getName();
            if (skipCols.contains(colName))
                continue;

            if (colName.equalsIgnoreCase("genid"))
            {
                ((BaseColumnInfo)col).setHidden(true);
                ((BaseColumnInfo)col).setUserEditable(false);
                ((BaseColumnInfo)col).setShownInDetailsView(false);
                ((BaseColumnInfo)col).setShownInInsertView(false);
                ((BaseColumnInfo)col).setShownInUpdateView(false);
            }
            String newName = col.getName();
            for (int i = 0; null != getColumn(newName); i++)
                newName = newName + i;

            if (col.isMvIndicatorColumn())
                continue;

            // Can't use addWrapColumn here since 'col' isn't from the parent table
            var wrapped = wrapColumnFromJoinedTable(col.getName(), col);
            if (col.isHidden())
                wrapped.setHidden(true);

            // Copy the property descriptor settings to the wrapped column.
            // NOTE: The column must be configured before calling .addColumn() where the PHI ComplianceTableRules will be applied to the column.
            String propertyURI = col.getPropertyURI();
            DomainProperty dp = propertyURI != null ? _dataClass.getDomain().getPropertyByURI(propertyURI) : null;
            PropertyDescriptor pd = (null==dp) ? null : dp.getPropertyDescriptor();
            if (dp != null && pd != null)
            {
                defaultsSupplier = PropertyColumn.copyAttributes(_userSchema.getUser(), wrapped, dp, getContainer(), lsidFieldKey, getContainerFilter(), defaultsSupplier);
                wrapped.setFieldKey(FieldKey.fromParts(dp.getName()));

                if (pd.getPropertyType() == PropertyType.ATTACHMENT)
                {
                    configureAttachmentURL(wrapped);
                }

                if (wrapped.isMvEnabled())
                {
                    // The column in the physical table has a "_MVIndicator" suffix, but we want to expose
                    // it with a "MVIndicator" suffix (no underscore)
                    var mvCol = StorageProvisioner.get().getMvIndicatorColumn(extTable, dp.getPropertyDescriptor(), "No MV column found for: " + dp.getName());
                    var wrappedMvCol = wrapColumnFromJoinedTable(wrapped.getName() + MvColumn.MV_INDICATOR_SUFFIX, mvCol);
                    wrappedMvCol.setHidden(true);
                    wrappedMvCol.setMvIndicatorColumn(true);

                    addColumn(wrappedMvCol);
                    wrappedMvCol.getFieldKey();
                    wrapped.setMvColumnName(wrappedMvCol.getFieldKey());
                }
            }

            addColumn(wrapped);

            if (isVisibleByDefault(col))
                defaultVisible.add(FieldKey.fromParts(col.getName()));
        }

        addColumn(Column.DataFileUrl);

        List<FieldKey> vocabularyDomainFields = addVocabularyDomainFields();
        defaultVisible.addAll(vocabularyDomainFields);

        List<FieldKey> calculatedFieldKeys = DomainUtil.getCalculatedFieldsForDefaultView(this);
        defaultVisible.addAll(calculatedFieldKeys);

        addColumn(Column.Properties);

        ColumnInfo colInputs = addColumn(Column.Inputs);
        addMethod("Inputs", new LineageMethod(colInputs, true), Set.of(colInputs.getFieldKey()));

        ColumnInfo colOutputs = addColumn(Column.Outputs);
        addMethod("Outputs", new LineageMethod(colOutputs, false), Set.of(colOutputs.getFieldKey()));

        MutableColumnInfo lineageLookup = ClosureQueryHelper.createAncestorLookupColumnInfo("Ancestors", this, _rootTable.getColumn("rowid"), _dataClass, false);
        addColumn(lineageLookup);

        ActionURL gridUrl = new ActionURL(ExperimentController.ShowDataClassAction.class, getContainer());
        gridUrl.addParameter("rowId", _dataClass.getRowId());
        setGridURL(new DetailsURL(gridUrl));

        ActionURL actionURL = new ActionURL(ExperimentController.ShowDataAction.class, getContainer());
        DetailsURL detailsURL = new DetailsURL(actionURL, Collections.singletonMap("rowId", "rowId"));
        setDetailsURL(detailsURL);

        rowIdCol.setURL(detailsURL);
        nameCol.setURL(detailsURL);

        if (canUserAccessPhi())
        {
            ActionURL deleteUrl = ExperimentController.ExperimentUrlsImpl.get().getDeleteDatasURL(getContainer(), null);
            setDeleteURL(new DetailsURL(deleteUrl));
        }
        else
        {
            setImportURL(LINK_DISABLER);
            setInsertURL(LINK_DISABLER);
            setUpdateURL(LINK_DISABLER);
        }

        setTitleColumn("Name");
        setDefaultVisibleColumns(defaultVisible);

        addExpObjectMethod();
    }

    private void configureAttachmentURL(MutableColumnInfo col)
    {
        ActionURL url = new ActionURL(ExperimentController.DataClassAttachmentDownloadAction.class, getUserSchema().getContainer())
                .addParameter("lsid", "${LSID}")
                .addParameter("name", "${" + col.getName() + "}");
        if (FileLinkDisplayColumn.AS_ATTACHMENT_FORMAT.equalsIgnoreCase(col.getFormat()))
        {
            url.addParameter("inline", "false");
            col.setURLTargetWindow(null);
        }
        else
        {
            col.setURLTargetWindow("_blank");
        }
        col.setURL(StringExpressionFactory.createURL(url));
    }

    @Override
    public void overlayMetadata(Collection<TableType> metadata, UserSchema schema, Collection<QueryException> errors)
    {
        super.overlayMetadata(metadata, schema, errors);

        // Reset URLs in case the XML metadata changed the view/download format option for the file
        for (MutableColumnInfo col : getMutableColumns())
        {
            if (col.getPropertyType() == PropertyType.ATTACHMENT)
            {
                configureAttachmentURL(col);
            }
        }
    }

    private Map<String, DataClassVocabularyProviderProperties> getVocabularyDomainProviders()
    {
        if (_vocabularyDomainProviders != null)
            return _vocabularyDomainProviders;

        TableInfo extTable = _dataClassDataTableSupplier.get();
        _vocabularyDomainProviders = new CaseInsensitiveHashMap<>();

        for (ColumnInfo col : extTable.getColumns())
        {
            String conceptURI = col.getConceptURI();
            if (!StringUtils.isEmpty(conceptURI))
            {
                ConceptURIVocabularyDomainProvider conceptURIVocabularyDomainProvider = PropertyService.get().getConceptUriVocabularyDomainProvider(conceptURI);
                if (conceptURIVocabularyDomainProvider != null)
                {
                    String domainName = conceptURIVocabularyDomainProvider.getDomainName(col.getName(), _dataClass);
                    String domainURI = conceptURIVocabularyDomainProvider.getDomainURI(col.getName(), _dataClass);
                    if (StringUtils.isEmpty(domainName) || StringUtils.isEmpty(domainURI))
                        continue;

                    Domain domain = PropertyService.get().getDomain(getContainer(), domainURI);
                    if (domain == null)
                        continue;

                    String propertyColumnName = getVocabularyDomainColumnName(domain);
                    _vocabularyDomainProviders.put(domainName, new DataClassVocabularyProviderProperties(col.getName(), col.getLabel(), propertyColumnName, conceptURIVocabularyDomainProvider));

                }
            }
        }

        return _vocabularyDomainProviders;
    }

    @Override
    public ColumnInfo getExpObjectColumn()
    {
        var ret = wrapColumn("ExpDataClassTableImpl_object_", _rootTable.getColumn("objectid"));
        ret.setConceptURI(BuiltInColumnTypes.EXPOBJECTID_CONCEPT_URI);
        return ret;
    }

    @Override
    protected PropertyForeignKey getDomainColumnForeignKey(Domain domain)
    {
        return new PropertyForeignKey(_userSchema, getContainerFilter(), domain)
        {
            @Override
            public void decorateColumn(MutableColumnInfo columnInfo, PropertyDescriptor pd)
            {
                super.decorateColumn(columnInfo, pd);
                if (pd.getPropertyType() == PropertyType.ATTACHMENT)
                {
                    columnInfo.setURL(StringExpressionFactory.createURL(
                            new ActionURL(ExperimentController.DataClassAttachmentDownloadAction.class, getContainer())
                                    .addParameter("lsid", "${LSID}")
                                    .addParameter("name", "${" + columnInfo.getFieldKey() + "}")));

                }

                DataClassVocabularyProviderProperties fieldVocabularyDomainProvider = getVocabularyDomainProviders().get(domain.getName());
                if (fieldVocabularyDomainProvider != null)
                    fieldVocabularyDomainProvider.conceptURIVocabularyDomainProvider().decorateColumn(columnInfo, pd, _userSchema.getContainer());
            }

            @Override
            protected @NotNull FieldKey decideColumnName(@NotNull ColumnInfo parent, @NotNull String displayField, @NotNull PropertyDescriptor pd)
            {
                DataClassVocabularyProviderProperties fieldVocabularyDomainProvider = getVocabularyDomainProviders().get(domain.getName());
                if (fieldVocabularyDomainProvider != null)
                    return fieldVocabularyDomainProvider.conceptURIVocabularyDomainProvider().getColumnFieldKey(parent, pd);

                return super.decideColumnName(parent, displayField, pd);
          }

        };
    }

    public String getVocabularyDomainColumnName(Domain domain)
    {
        return domain.getName().replaceAll(" ", "") + domain.getTypeId();
    }

    private List<FieldKey> addVocabularyDomainFields()
    {
        List<FieldKey> domainFields = new ArrayList<>();

        List<? extends Domain> domains = PropertyService.get().getDomains(getContainer(), getUserSchema().getUser(), new VocabularyDomainKind(), true);
        for (Domain domain : domains)
        {
            String columnName = getVocabularyDomainColumnName(domain);
            var col = this.addDomainColumns(domain, columnName);
            col.setLabel(domain.getName());
            col.setDescription("Properties from " + domain.getLabel(getContainer()));

            DataClassVocabularyProviderProperties fieldVocabularyDomainProvider = getVocabularyDomainProviders().get(domain.getName());
            if (fieldVocabularyDomainProvider != null)
            {
                col.setLabel(fieldVocabularyDomainProvider.conceptURIVocabularyDomainProvider().getDomainLabel(fieldVocabularyDomainProvider.sourceColumnLabel()));
                List<FieldKey> fieldKeys = fieldVocabularyDomainProvider.conceptURIVocabularyDomainProvider().addLookupColumns(_dataClass, this, col, fieldVocabularyDomainProvider.sourceColumnName());
                if (fieldKeys != null)
                    domainFields.addAll(fieldKeys);
            }
        }

        return domainFields;
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
        checkReadBeforeExecute();
        TableInfo provisioned = _dataClassDataTableSupplier.get();
        SqlDialect dialect = _rootTable.getSqlDialect();

        Set<String> dataCols = new CaseInsensitiveHashSet(_rootTable.getColumnNameSet());

        // all columns from dataclass property table except name, lsid, and classid
        Set<String> pCols = new CaseInsensitiveHashSet(provisioned.getColumnNameSet());
        pCols.remove("name");
        pCols.remove("lsid");
        pCols.remove("classid");

        boolean hasProvisionedColumns = containsProvisionedColumns(selectedColumns, pCols);

        SQLFragment sql = new SQLFragment();
        sql.append("(SELECT * FROM\n");
        sql.append("(SELECT ");
        String comma = "";
        for (String dataCol : dataCols)
        {
            sql.append(comma);
            sql.append("d.").append(dataCol);
            comma = ", ";
        }

        if (hasProvisionedColumns)
        {
            for (String pCol : pCols)
            {
                sql.append(comma);
                sql.append(provisioned.getColumn(pCol).getValueSql("p"));
            }
        }
        sql.append(" FROM ");
        sql.append(_rootTable, "d");
        if (hasProvisionedColumns)
            sql.append(" INNER JOIN ").append(provisioned, "p").append(" ON d.lsid = p.lsid");
        String subAlias = alias + "_dc_sub";
        sql.append(") ").append(subAlias);
        sql.append("\n");

        // WHERE
        Map<FieldKey, ColumnInfo> columnMap = Table.createColumnMap(getFromTable(), getFromTable().getColumns());
        SQLFragment filterFrag = getFilter().getSQLFragment(_rootTable.getSqlDialect(), subAlias, columnMap);
        sql.append("\n").append(filterFrag).append(") ").append(alias);

        return getTransformedFromSQL(sql);
    }

    @Override
    public boolean supportTableRules() // intentional override
    {
        return true;
    }

    @Override
    protected @NotNull TableRules findTableRules()
    {
        return TableRulesManager.get().getTableRules(_dataClass.getContainer(), getUserSchema().getUser(), getUserSchema().getContainer());
    }

    private static final Set<String> DEFAULT_HIDDEN_COLS = new CaseInsensitiveHashSet("Container", "Created", "CreatedBy", "ModifiedBy", "Modified", "Owner", "EntityId", "RowId");

    private boolean isVisibleByDefault(ColumnInfo col)
    {
        return (!col.isHidden() && !col.isUnselectable() && !DEFAULT_HIDDEN_COLS.contains(col.getName()));
    }

    public ExpDataClass getDataClass()
    {
        return _dataClass;
    }

    @Override
    public List<Pair<String, String>> getImportTemplates(ViewContext ctx)
    {
        Set<String> excludeColumns = new HashSet<>();
        for (String vocabularyDomainName : getVocabularyDomainProviders().keySet())
        {
            DataClassVocabularyProviderProperties fieldVocabularyDomainProvider = getVocabularyDomainProviders().get(vocabularyDomainName);
            if (fieldVocabularyDomainProvider != null)
            {
                excludeColumns.addAll(fieldVocabularyDomainProvider.conceptURIVocabularyDomainProvider().getImportTemplateExcludeColumns(fieldVocabularyDomainProvider.vocabularyDomainName()));
            }
        }

        if (!excludeColumns.isEmpty())
        {
            List<Pair<String, String>> templates = new ArrayList<>();
            ActionURL url = PageFlowUtil.urlProvider(QueryUrls.class).urlCreateExcelTemplate(ctx.getContainer(), getPublicSchemaName(), getName());
            url.addParameter("headerType", ColumnHeaderType.DisplayFieldKey.name());
            for (String excludeKey : excludeColumns)
                url.addParameter("excludeColumn", excludeKey);
            url.addParameter("filenamePrefix", this.getName());
            templates.add(Pair.of("Download Template", url.toString()));
            return templates;

        }

        return super.getImportTemplates(ctx);
    }

    @NotNull
    @Override
    public Map<String, Pair<IndexType, List<ColumnInfo>>> getUniqueIndices()
    {
        Map<String, Pair<IndexType, List<ColumnInfo>>> indices = new HashMap<>(super.getUniqueIndices());
        indices.putAll(wrapTableIndices(_dataClassDataTableSupplier.get()));

        // Issue 46948: RemapCache unable to resolve ExpData objects with addition of ClassId column
        // RemapCache is used to findExpData using name/rowId remap.
        // The addition of "ClassId" column to the TableInfo is causing violation of RemapCache's requirement of "unique index over a single column that isn't the primary key".
        // Because this is a joined table between exp.data and the dataclass provisioned table, it's safe to ignore "ClassId" as part of the unique key.
        Map<String, Pair<IndexType, List<ColumnInfo>>> filteredIndices = new HashMap<>();
        for (Map.Entry<String, Pair<IndexType, List<ColumnInfo>>> index : indices.entrySet())
        {
            IndexType type = index.getValue().getKey();
            List<ColumnInfo> columns = index.getValue().getValue();

            List<ColumnInfo> filteredColumns = new ArrayList<>();
            if (type == IndexType.Unique && columns.size() > 1)
            {
                for (ColumnInfo columnInfo : columns)
                {
                    if (Column.ClassId.name().equalsIgnoreCase(columnInfo.getName()))
                        continue;

                    filteredColumns.add(columnInfo);
                }
            }

            filteredIndices.put(index.getKey(), new Pair<>(type, filteredColumns.isEmpty() ? columns : filteredColumns));
        }
        return Collections.unmodifiableMap(filteredIndices);
    }

    @NotNull
    @Override
    public Map<String, Pair<IndexType, List<ColumnInfo>>> getAllIndices()
    {
        Map<String, Pair<IndexType, List<ColumnInfo>>> indices = new HashMap<>(super.getAllIndices());
        indices.putAll(wrapTableIndices(_dataClassDataTableSupplier.get()));
        return Collections.unmodifiableMap(indices);
    }

    @Override
    public boolean hasDbTriggers()
    {
        return super.hasDbTriggers() || _dataClassDataTableSupplier.get().hasDbTriggers();
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        if (perm == ReadPermission.class)
            return getContainer().hasPermission(user, getReadPermissionClass());
        return super.hasPermission(user, perm);
    }

    /**
     * Issue 49452: Apply the appropriate data class read permission to the container filter so that folder filters
     * do not include unauthorized rows.
     */
    @Override
    protected SimpleFilter.FilterClause getContainerFilterClause(ContainerFilter filter, FieldKey fieldKey)
    {
        return filter.createFilterClause(getSchema(), fieldKey, getReadPermissionClass(), null);
    }

    private @NotNull Class<? extends Permission> getReadPermissionClass()
    {
        return _dataClass != null && _dataClass.isMedia() ? MediaReadPermission.class : DataClassReadPermission.class;
    }

    //
    // UpdatableTableInfo
    //

    @Override
    public @Nullable CaseInsensitiveHashSet skipProperties()
    {
        return super.skipProperties();
    }

    @Nullable
    @Override
    public CaseInsensitiveHashMap<String> remapSchemaColumns()
    {
        if (null != getRealTable().getColumn("container") && null != getColumn("folder"))
        {
            CaseInsensitiveHashMap<String> m = new CaseInsensitiveHashMap<>();
            m.put("container", "folder");
            return m;
        }
        return null;
    }

    @Override
    public Set<String> getAltMergeKeys(DataIteratorContext context)
    {
        if (context.getInsertOption().updateOnly && context.getConfigParameterBoolean(ExperimentService.QueryOptions.UseLsidForUpdate))
            return getAltKeysForUpdate();
        return DATA_CLASS_ALT_MERGE_KEYS;
    }

    @Override
    @NotNull
    public Set<String> getAltKeysForUpdate()
    {
        return DATA_CLASS_ALT_UPDATE_KEYS;
    }

    @Override
    @NotNull
    public Map<String, String> getAdditionalRequiredInsertColumns()
    {
        if (getDataClass() == null)
            return Collections.emptyMap();

        Map<String, String> required = new CaseInsensitiveHashMap<>();
        try
        {
            required.putAll(getDataClass().getRequiredImportAliases());
            return required;
        }
        catch (IOException e)
        {
            return Collections.emptyMap();
        }
    }

    @Override
    public DataIteratorBuilder persistRows(DataIteratorBuilder data, DataIteratorContext context)
    {
        TableInfo propertiesTable = _dataClassDataTableSupplier.get();
        try
        {
            PersistDataIteratorBuilder step0 = new ExpDataIterators.PersistDataIteratorBuilder(data, this, propertiesTable, _dataClass, getUserSchema().getContainer(), getUserSchema().getUser(), _dataClass.getImportAliases(), null);
            SearchService searchService = SearchService.get();
            ExperimentServiceImpl experimentServiceImpl = ExperimentServiceImpl.get();
            if (null != searchService)
            {
                final var scope = propertiesTable.getSchema().getScope();
                step0.setIndexFunction(searchIndexDataKeys -> scope.addCommitTask(() ->
                {
                    List<Integer> orderedRowIds = searchIndexDataKeys.orderedRowIds();
                    // Issue 51263: order by RowId to reduce deadlock
                    ListUtils.partition(orderedRowIds, 100).forEach(sublist ->
                            searchService.defaultTask().addRunnable(SearchService.PRIORITY.group, () ->
                                    scope.executeWithRetryReadOnly(tx -> {
                                        for (ExpDataImpl expData : experimentServiceImpl.getExpDatas(sublist))
                                            expData.index(searchService.defaultTask(), this);
                                        return (Void) null;
                                    }))
                    );

                    List<String> lsids = searchIndexDataKeys.lsids();
                    ListUtils.partition(lsids, 100).forEach(sublist ->
                            searchService.defaultTask().addRunnable(SearchService.PRIORITY.group, () ->
                                    scope.executeWithRetryReadOnly(tx -> {
                                        for (ExpDataImpl expData : experimentServiceImpl.getExpDatasByLSID(sublist))
                                            expData.index(searchService.defaultTask(), this);
                                        return (Void) null;
                                    }))
                    );

                }, DbScope.CommitTaskOption.POSTCOMMIT));
            }
            DataIteratorBuilder builder = LoggingDataIterator.wrap(step0);
            return LoggingDataIterator.wrap(new AliasDataIteratorBuilder(builder, getUserSchema().getContainer(), getUserSchema().getUser(), ExperimentService.get().getTinfoDataAliasMap()));
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    private class PreTriggerDataIteratorBuilder implements DataIteratorBuilder
    {
        private static final int BATCH_SIZE = 100;

        private DataIteratorContext _context;
        private final DataIteratorBuilder _in;

        public PreTriggerDataIteratorBuilder(@NotNull DataIteratorBuilder in, DataIteratorContext context)
        {
            _context = context;
            _in = in;
        }

        private static boolean isReservedHeader(String name)
        {
            for (ExpDataTable.Column column : ExpDataTable.Column.values()) // use ExpDataTable instead of ExpDataClassDataTable for a larger set of reserved fields
            {
                if (column.name().equalsIgnoreCase(name))
                    return !ALLOWED_IMPORT_HEADERS.contains(column.name().toLowerCase());
            }
            return false;
        }

        @Override
        public DataIterator getDataIterator(DataIteratorContext context)
        {
            _context = context;
            DataIterator input = _in.getDataIterator(context);
            if (null == input)
                return null;           // Can happen if context has errors

            var drop = new CaseInsensitiveHashSet();
            for (int i = 1; i <= input.getColumnCount(); i++)
            {
                String name = input.getColumnInfo(i).getName();

                boolean isContainerField = name.equalsIgnoreCase("Container") || name.equalsIgnoreCase("Folder");
                if (isContainerField)
                {
                    if (context.getInsertOption().updateOnly || !context.isCrossFolderImport())
                        drop.add(name);
                }
                else if (isReservedHeader(name))
                    drop.add(name);
                else if (Column.ClassId.name().equalsIgnoreCase(name))
                    drop.add(name);
            }
            if (context.getConfigParameterBoolean(ExperimentService.QueryOptions.UseLsidForUpdate))
            {
                drop.remove("lsid");
                drop.remove("rowid");// keep rowid for audit log
            }

            if (!drop.isEmpty())
                input = new DropColumnsDataIterator(input, drop);

            final Container c = getContainer();
            final ExperimentService svc = ExperimentService.get();

            SimpleTranslator step0 = new SimpleTranslator(input, context);
            step0.setDebugName("step0");

            TableInfo expData = svc.getTinfoData();

            // Ensure we have a dataClass column and it is of the right value
            // use materialized classId so that parameter binding works for both exp.data as well as materialized table
            ColumnInfo classIdCol = _dataClassDataTableSupplier.get().getColumn("classId");
            step0.addColumn(classIdCol, new SimpleTranslator.ConstantColumn(_dataClass.getRowId()));

            // Ensure we have a cpasType column and it is of the right value
            ColumnInfo cpasTypeCol = expData.getColumn("cpasType");
            step0.addColumn(cpasTypeCol, new SimpleTranslator.ConstantColumn(_dataClass.getLSID()));

            if (context.getInsertOption() == QueryUpdateService.InsertOption.UPDATE)
            {
                step0.selectAll();
                return LoggingDataIterator.wrap(step0.getDataIterator(context));
            }

            step0.selectAll(Sets.newCaseInsensitiveHashSet("lsid", "dataClass", "genId")); //TODO can this be moved up?

            // Ensure we have a name column -- makes the NameExpressionDataIterator easier
            if (!DataIteratorUtil.createColumnNameMap(step0).containsKey("name"))
            {
                ColumnInfo nameCol = expData.getColumn("name");
                step0.addColumn(nameCol, (Supplier<String>)() -> null);
            }

            ColumnInfo lsidCol = expData.getColumn("lsid");

            // TODO: validate dataFileUrl column, it will be saved later

            // Generate LSID before inserting
            step0.addColumn(lsidCol, (Supplier<String>) () -> svc.generateGuidLSID(c, ExpData.class));

            // auto gen a sequence number for genId - reserve BATCH_SIZE numbers at a time so we don't select the next sequence value for every row
            ColumnInfo genIdCol = _dataClass.getTinfo().getColumn(FieldKey.fromParts("genId"));
            final int batchSize = _context.getInsertOption().batch ? BATCH_SIZE : 1;
            step0.addSequenceColumn(genIdCol, _dataClass.getContainer(), ExpDataClassImpl.SEQUENCE_PREFIX, _dataClass.getRowId(), batchSize, _dataClass.getMinGenId());

            // Table Counters
            ExpDataClassDataTableImpl queryTable = ExpDataClassDataTableImpl.this;
            var counterDIB = ExpDataIterators.CounterDataIteratorBuilder.create(step0, _dataClass.getContainer(), queryTable, ExpDataClassImpl.SEQUENCE_PREFIX, _dataClass.getRowId());
            DataIterator di;

            // Generate names
            if (_dataClass.getNameExpression() != null)
            {
                step0.addColumn(new BaseColumnInfo("nameExpression", JdbcType.VARCHAR), (Supplier<String>) _dataClass::getNameExpression);

                // Don't create CounterDataIterator until 'nameExpression' has been added
                di = LoggingDataIterator.wrap(counterDIB.getDataIterator(context));

//              CoerceDataIterator to handle the lookup/alternatekeys functionality of loadRows(),
//              TODO check if this covers all the functionality, in particular how is alternateKeyCandidates used?
                di = LoggingDataIterator.wrap(new CoerceDataIterator(di, context, ExpDataClassDataTableImpl.this, false));

                TableInfo dataClassTInfo = ExpDataClassDataTableImpl.this;
                if (c.hasProductFolders() && !c.isProject())
                {
                    // Issue 46939: Naming Patterns for Not Working in Sub Projects
                    User user = getUserSchema().getUser();
                    ContainerFilter cf = new ContainerFilter.CurrentPlusProjectAndShared(c, user); // use lookup CF
                    dataClassTInfo = QueryService.get().getUserSchema(user, c, "exp.data").getTable(getName(), cf);
                }

                Map<String, String> importAliasMap = null;
                try
                {
                    importAliasMap = _dataClass.getImportAliases();
                }
                catch (IOException e)
                {
                    // do nothing
                }
                Map<String, String> finalImportAliasMap = importAliasMap;
                di = LoggingDataIterator.wrap(new NameExpressionDataIterator(di, context, dataClassTInfo, getContainer(), _dataClass.getMaxDataCounterFunction(), DATA_COUNTER_SEQ_PREFIX + _dataClass.getRowId() + "-", importAliasMap)
                        .setAllowUserSpecifiedNames(NameExpressionOptionService.get().getAllowUserSpecificNamesValue(getContainer()))
                        .addExtraPropsFn(() -> {
                            Map<String, Object> props = new HashMap<>();
                            props.put(PARENT_IMPORT_ALIAS_MAP_PROP, finalImportAliasMap);
                            props.put(NameExpressionOptionService.FOLDER_PREFIX_TOKEN, StringUtils.trimToEmpty(NameExpressionOptionService.get().getExpressionPrefix(c)));

                            return props;
                        })
                );
            }
            else
            {
                di = counterDIB.getDataIterator(context);
            }

            return LoggingDataIterator.wrap(di.getDataIterator(context));
        }
    }

    @Nullable
    @Override
    public QueryUpdateService getUpdateService()
    {
        return new DataClassDataUpdateService(this);
    }

    class DataClassDataUpdateService extends DefaultQueryUpdateService
    {
        IntegerConverter _converter = new IntegerConverter();
        final ExpDataClassDataTableImpl _expDataClassDataTableImpl;

        public DataClassDataUpdateService(ExpDataClassDataTableImpl table)
        {
            super(table, table.getRealTable());
            _expDataClassDataTableImpl = table;
            // Note that this class actually overrides createImportDIB(), so currently we're not looking at this flag.
            _enableExistingRecordsDataIterator = false;
        }

        @Override
        protected DataIteratorBuilder preTriggerDataIterator(DataIteratorBuilder in, DataIteratorContext context)
        {
            return new PreTriggerDataIteratorBuilder(in, context);
        }

        @Override
        public int importRows(User user, Container container, DataIteratorBuilder rows, BatchValidationException errors, @Nullable Map<Enum,Object> configParameters, Map<String, Object> extraScriptContext)
        {
            return _importRowsUsingDIB(user, container, rows, null, getDataIteratorContext(errors, InsertOption.INSERT, configParameters), extraScriptContext);
        }

        @Override
        public int loadRows(User user, Container container, DataIteratorBuilder rows, DataIteratorContext context, @Nullable Map<String, Object> extraScriptContext)
        {
            try
            {
                configureCrossFolderImport(rows, context);
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
            return super.loadRows(user, container, rows, context, extraScriptContext);
        }

        @Override
        public int mergeRows(User user, Container container, DataIteratorBuilder rows, BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext)
        {
            return _importRowsUsingDIB(user, container, rows, null, getDataIteratorContext(errors, InsertOption.MERGE, configParameters), extraScriptContext);
        }

        @Override
        public Map<String, Object> moveRows(User user, Container container, Container targetContainer, List<Map<String, Object>> rows, BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, @Nullable Map<String, Object> extraScriptContext) throws InvalidKeyException, BatchValidationException, QueryUpdateServiceException, SQLException
        {
            Map<String, Integer> allContainerResponse = new HashMap<>();

            AuditBehaviorType auditType = configParameters != null ? (AuditBehaviorType) configParameters.get(DetailedAuditLogDataIterator.AuditConfigs.AuditBehavior) : null;
            String auditUserComment = configParameters != null ? (String) configParameters.get(DetailedAuditLogDataIterator.AuditConfigs.AuditUserComment) : null;

            Map<Container, List<ExpData>> containerObjects = getDataClassObjectsForMoveRows(targetContainer, rows, errors);
            if (!errors.hasErrors() && containerObjects != null)
            {
                for (Container c : containerObjects.keySet())
                {
                    if (!c.hasPermission(user, MoveEntitiesPermission.class))
                        throw new UnauthorizedException("You do not have permission to move sources out of '" + c.getName() + "'.");

                    try
                    {
                        Map<String, Integer> response = ExperimentService.get().moveDataClassObjects(containerObjects.get(c), c, targetContainer, user, auditUserComment, auditType);
                        incrementCounts(allContainerResponse, response);
                    }
                    catch (ExperimentException e)
                    {
                        throw new QueryUpdateServiceException(e);
                    }
                }

                SimpleMetricsService.get().increment(ExperimentService.MODULE_NAME, "moveEntities", "sources");
            }
            return new HashMap<>(allContainerResponse);
        }


        private Map<Container, List<ExpData>> getDataClassObjectsForMoveRows(Container targetContainer, List<Map<String, Object>> rows, BatchValidationException errors)
        {
            Set<Integer> dataIds = rows.stream().map(row -> (Integer) row.get(RowId.toString())).collect(Collectors.toSet());
            if (dataIds.isEmpty())
            {
                errors.addRowError(new ValidationException("Source IDs must be specified for the move operation."));
                return null;
            }

            List<? extends ExpData> dataClassObjects = ExperimentServiceImpl.get().getExpDatas(dataIds);
            if (dataClassObjects.size() != dataIds.size())
            {
                errors.addRowError(new ValidationException("Unable to find all sources for the move operation."));
                return null;
            }

            // Filter out materials already in the target container
            dataClassObjects = dataClassObjects
                    .stream().filter(dataObject -> dataObject.getContainer().getEntityId() != targetContainer.getEntityId()).toList();

            Map<Container, List<ExpData>> containerObjects = new HashMap<>();
            dataClassObjects.forEach(dataClassObject -> {
                if (!containerObjects.containsKey(dataClassObject.getContainer()))
                    containerObjects.put(dataClassObject.getContainer(), new ArrayList<>());
                containerObjects.get(dataClassObject.getContainer()).add(dataClassObject);
            });

            return containerObjects;
        }

        @Override
        public List<Map<String, Object>> insertRows(User user, Container container, List<Map<String, Object>> rows, BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext)
        {
            return super._insertRowsUsingDIB(user, container, rows, getDataIteratorContext(errors, InsertOption.INSERT, configParameters), extraScriptContext);
        }

        @Override
        protected Map<String, Object> getRow(User user, Container container, Map<String, Object> keys) throws InvalidKeyException
        {
            return getRow(user, container, keys, false);
        }

        /* This class overrides getRow() in order to support getRow() using "rowid" or "lsid" */
        @Override
        protected Map<String, Object> getRow(User user, Container container, Map<String, Object> keys, boolean allowCrossContainer) throws InvalidKeyException
        {
            aliasColumns(_columnMapping, keys);

            Integer rowid = (Integer)JdbcType.INTEGER.convert(keys.get(Column.RowId.name()));
            String lsid = (String)JdbcType.VARCHAR.convert(keys.get(Column.LSID.name()));
            String name = (String)JdbcType.VARCHAR.convert(keys.get(Name.name()));
            Integer classId = (Integer)JdbcType.INTEGER.convert(keys.get(Column.ClassId.name()));

            if (classId == null)
                classId = _dataClass.getRowId();

            if (null==rowid && null==lsid && null == name)
                throw new InvalidKeyException("Value must be supplied for key field 'rowid' or 'lsid' or 'name'", keys);

            Map<String,Object> row = _select(container, rowid, lsid, name, classId, allowCrossContainer);

            //PostgreSQL includes a column named _row for the row index, but since this is selecting by
            //primary key, it will always be 1, which is not only unnecessary, but confusing, so strip it
            if (null != row)
            {
                if (row instanceof ArrayListMap arrayListMap)
                    arrayListMap.getFindMap().remove("_row");
                else
                    row.remove("_row");
            }

            return row;
        }

        @Override
        protected Map<String, Object> _select(Container container, Object[] keys) throws ConversionException
        {
            throw new IllegalStateException();
        }

        protected Map<String, Object> _select(Container container, Integer rowid, String lsid, String name, Integer classId, boolean allowCrossContainer) throws ConversionException
        {
            if (null == rowid && null == lsid && (null == name || null == classId))
                return null;

            TableInfo d = getDbTable();
            TableInfo t = _dataClassDataTableSupplier.get();

            SQLFragment sql = new SQLFragment()
                    .append("SELECT t.*, d.RowId, d.Name, d.ClassId, d.Container, d.Description, d.CreatedBy, d.Created, d.ModifiedBy, d.Modified")
                    .append(" FROM ").append(d, "d")
                    .append(" LEFT OUTER JOIN ").append(t, "t")
                    .append(" ON d.lsid = t.lsid WHERE ");

            if (null != rowid)
                sql.append("d.rowid=?").add(rowid);
            else if (null != lsid)
                sql.append("d.lsid=?").add(lsid);
            else
                sql.append("d.classid=? AND d.name=?").add(classId).add(name);

            if (!allowCrossContainer)
                sql.append(" AND d.Container=?").add(container.getEntityId());

            return new SqlSelector(getDbTable().getSchema(), sql).getMap();
        }

        @Override
        protected Map<String, Object> _update(User user, Container c, Map<String, Object> row, Map<String, Object> oldRow, Object[] keys) throws SQLException, ValidationException
        {
            // LSID was stripped by super.updateRows() and is needed to insert into the dataclass provisioned table
            String lsid = (String)oldRow.get("lsid");
            if (lsid == null)
                throw new ValidationException("lsid required to update row");

            String newName = (String) row.get(Name.name());
            String oldName = (String) oldRow.get(Name.name());
            boolean hasNameChange = !StringUtils.isEmpty(newName) && !newName.equals(oldName);

            // Replace attachment columns with filename and keep AttachmentFiles
            Map<String, Object> rowStripped = new CaseInsensitiveHashMap<>();
            Map<String, Object> attachments = new CaseInsensitiveHashMap<>();
            row.forEach((name, value) -> {
                if (isAttachmentProperty(name) && value instanceof AttachmentFile file)
                {
                    if (null != file.getFilename())
                    {
                        rowStripped.put(name, file.getFilename());
                        attachments.put(name, value);
                    }
                }
                else
                {
                    rowStripped.put(name, value);
                }
            });

            for (String vocabularyDomainName : getVocabularyDomainProviders().keySet())
            {
                DataClassVocabularyProviderProperties fieldVocabularyDomainProvider = getVocabularyDomainProviders().get(vocabularyDomainName);
                if (fieldVocabularyDomainProvider != null)
                    rowStripped.putAll(fieldVocabularyDomainProvider.conceptURIVocabularyDomainProvider().getUpdateRowProperties(user, c, rowStripped, oldRow, getAttachmentParentFactory(), fieldVocabularyDomainProvider.sourceColumnName(), fieldVocabularyDomainProvider.vocabularyDomainName(), getVocabularyDomainProviders().size() > 1));
            }

            // update exp.data
            Map<String, Object> ret = new CaseInsensitiveHashMap<>(super._update(user, c, rowStripped, oldRow, keys));

            // update provisioned table -- note that LSID isn't the PK so we need to use the filter to update the correct row instead
            keys = new Object[] {lsid};
            TableInfo t = _dataClassDataTableSupplier.get();
            if (t.getColumnNameSet().stream().anyMatch(rowStripped::containsKey))
            {
                ret.putAll(Table.update(user, t, rowStripped, t.getColumn("lsid"), keys, null, Level.DEBUG));
            }

            ExpDataImpl data = null;
            if (hasNameChange)
            {
                data = ExperimentServiceImpl.get().getExpData(lsid);
                ExperimentService.get().addObjectLegacyName(data.getObjectId(), ExperimentServiceImpl.getNamespacePrefix(ExpData.class), oldName, user);
            }

            // update comment
            if (row.containsKey("flag") || row.containsKey("comment"))
            {
                Object o = row.containsKey("flag") ? row.get("flag") : row.get("comment");
                String flag = Objects.toString(o, null);

                if (data == null)
                    data = ExperimentServiceImpl.get().getExpData(lsid);
                data.setComment(user, flag);
            }

            // update aliases
            if (row.containsKey("Alias"))
                AliasInsertHelper.handleInsertUpdate(getContainer(), user, lsid, ExperimentService.get().getTinfoDataAliasMap(), row.get("Alias"));

            // handle attachments
            removePreviousAttachments(user, c, row, oldRow);
            ret.putAll(attachments);
            addAttachments(user, c, ret, lsid);

            // search index done in postcommit

            ret.put("RowId", oldRow.get("RowId")); // return rowId for SearchService
            ret.put("lsid", lsid);
            return ret;
        }

        @Override
        public List<Map<String, Object>> updateRows(User user, Container container, List<Map<String, Object>> rows, List<Map<String, Object>> oldKeys, BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext) throws InvalidKeyException, BatchValidationException, QueryUpdateServiceException, SQLException
        {
            boolean useDib = false;
            if (rows != null && !rows.isEmpty() && oldKeys == null)
                useDib = rows.get(0).containsKey("lsid");

            useDib = useDib && hasUniformKeys(rows);

            List<Map<String, Object>> results;
            if (useDib)
            {
                Map<Enum, Object> finalConfigParameters = configParameters == null ? new HashMap<>() : configParameters;
                finalConfigParameters.put(ExperimentService.QueryOptions.UseLsidForUpdate, true);
                results = super._updateRowsUsingDIB(user, container, rows, getDataIteratorContext(errors, InsertOption.UPDATE, finalConfigParameters), extraScriptContext);
            }
            else
            {
                results = super.updateRows(user, container, rows, oldKeys, errors, configParameters, extraScriptContext);

                SearchService searchService = SearchService.get();
                if (searchService != null)
                {
                    DbScope scope = getUserSchema().getDbSchema().getScope();
                    scope.addCommitTask(() ->
                    {
                        List<Integer> orderedRowIds = new ArrayList<>();
                        for (Map<String, Object> result : results)
                        {
                            Integer rowId = (Integer) result.get(RowId.name());
                            if (rowId != null)
                                orderedRowIds.add(rowId);
                        }
                        Collections.sort(orderedRowIds);

                        // Issue 51263: order by RowId to reduce deadlock
                        ListUtils.partition(orderedRowIds, 100).forEach(sublist ->
                                searchService.defaultTask().addRunnable(SearchService.PRIORITY.group, () ->
                                {
                                    for (ExpDataImpl expData : ExperimentServiceImpl.get().getExpDatas(sublist))
                                        expData.index(null, _expDataClassDataTableImpl);
                                })
                        );
                    }, DbScope.CommitTaskOption.POSTCOMMIT);
                }

                /* setup mini dataiterator pipeline to process lineage */
                DataIterator di = _toDataIteratorBuilder("updateRows.lineage", results).getDataIterator(new DataIteratorContext());
                ExpDataIterators.derive(user, container, di, false, _dataClass, true);
            }

            return results;
        }

        @Override
        protected void _delete(Container c, Map<String, Object> row) throws InvalidKeyException
        {
            String lsid = (String)row.get("lsid");
            if (lsid == null)
                throw new InvalidKeyException("lsid required to delete row");

            // NOTE: The provisioned table row will be deleted in ExperimentServiceImpl.deleteDataByRowIds()
            //Table.delete(getDbTable(), new SimpleFilter(FieldKey.fromParts("lsid"), lsid));
            ExpData data = ExperimentService.get().getExpData(lsid);
            data.delete(getUserSchema().getUser());

            ExperimentServiceImpl.get().deleteDataClassAttachments(c, Collections.singletonList(lsid));
        }

        @Override
        protected int truncateRows(User user, Container container)
        {
            return ExperimentServiceImpl.get().truncateDataClass(_dataClass, user, container);
        }

        private void removePreviousAttachments(User user, Container c, Map<String, Object> newRow, Map<String, Object> oldRow)
        {
            Lsid lsid = new Lsid((String)oldRow.get("LSID"));

            for (Map.Entry<String, Object> entry : newRow.entrySet())
            {
                if (isAttachmentProperty(entry.getKey()) && oldRow.get(entry.getKey()) != null)
                {
                    AttachmentParent parent = new ExpDataClassAttachmentParent(c, lsid);

                    AttachmentService.get().deleteAttachment(parent, (String) oldRow.get(entry.getKey()), user);
                }
            }
        }

        @Override
        protected Domain getDomain()
        {
            return _dataClass.getDomain();
        }

        private void addAttachments(User user, Container c, Map<String, Object> row, String lsidStr)
        {
            if (row != null && lsidStr != null)
            {
                ArrayList<AttachmentFile> attachmentFiles = new ArrayList<>();
                for (Map.Entry<String, Object> entry : row.entrySet())
                {
                    if (isAttachmentProperty(entry.getKey()) && entry.getValue() instanceof AttachmentFile file)
                    {
                        if (null != file.getFilename())
                            attachmentFiles.add(file);
                    }
                }

                if (!attachmentFiles.isEmpty())
                {
                    Lsid lsid = new Lsid(lsidStr);
                    AttachmentParent parent = new ExpDataClassAttachmentParent(c, lsid);

                    try
                    {
                        AttachmentService.get().addAttachments(parent, attachmentFiles, user);
                    }
                    catch (IOException e)
                    {
                        throw UnexpectedException.wrap(e);
                    }
                }
            }
        }

        @Override
        public void configureDataIteratorContext(DataIteratorContext context)
        {
            if (context.getInsertOption().allowUpdate)
                context.putConfigParameter(QueryUpdateService.ConfigParameters.CheckForCrossProjectData, true);
            if (context.getInsertOption() == InsertOption.IMPORT || context.getInsertOption() == InsertOption.MERGE)
            {
                AuditBehaviorType auditType = (AuditBehaviorType) context.getConfigParameter(DetailedAuditLogDataIterator.AuditConfigs.AuditBehavior);
                if (auditType != null && auditType != AuditBehaviorType.NONE)
                    context.setSelectIds(true); // select rowId for QueryUpdateAuditEvent.rowPk
            }
        }

        @Override
        public DataIteratorBuilder createImportDIB(User user, Container container, DataIteratorBuilder data, DataIteratorContext context)
        {
            if (context.isCrossFolderImport())
                return new ExpDataIterators.MultiDataTypeCrossProjectDataIteratorBuilder(user, container, data, context.isCrossTypeImport(), context.isCrossFolderImport(), _dataClass, false);

            StandardDataIteratorBuilder standard = StandardDataIteratorBuilder.forInsert(getQueryTable(), data, container, user, context);
            DataIteratorBuilder dib = ((UpdateableTableInfo)getQueryTable()).persistRows(standard, context);
            dib = AttachmentDataIterator.getAttachmentDataIteratorBuilder(getQueryTable(), dib, user, context.getInsertOption().batch ? getAttachmentDirectory() : null,
                    container, getAttachmentParentFactory(), FieldKey.fromParts(Column.LSID));

            dib = getConceptURIVocabularyDomainDataIteratorBuilder(user, container, dib);

            dib = DetailedAuditLogDataIterator.getDataIteratorBuilder(getQueryTable(), dib, context.getInsertOption(), user, container);

            return dib;
        }

        private DataIteratorBuilder getConceptURIVocabularyDomainDataIteratorBuilder(User user, Container container, DataIteratorBuilder data)
        {
            for (DomainProperty property : getDomain().getProperties())
            {
                if (StringUtils.isEmpty(property.getConceptURI()))
                    continue;

                ConceptURIVocabularyDomainProvider conceptURIVocabularyDomainProvider = PropertyService.get().getConceptUriVocabularyDomainProvider(property.getConceptURI());

                if (conceptURIVocabularyDomainProvider != null)
                    return conceptURIVocabularyDomainProvider.getDataIteratorBuilder(data, _dataClass, getAttachmentParentFactory(), container, user);
            }

            return data;
        }

        @Override
        protected AttachmentParentFactory getAttachmentParentFactory()
        {
            return (entityId, c) -> new ExpDataClassAttachmentParent(c, Lsid.parse(entityId));
        }

        @Override
        public boolean hasExistingRowsInOtherContainers(Container container, Map<Integer, Map<String, Object>> keys)
        {
            Integer dataClassId = null;
            Set<String> dataNames = new HashSet<>();
            for (Map.Entry<Integer, Map<String, Object>> keyMap : keys.entrySet())
            {
                Object oName = keyMap.getValue().get("Name");

                if (oName != null)
                    dataNames.add((String) oName);

                if (dataClassId == null)
                {
                    Object oClassId = keyMap.getValue().get("ClassId");
                    if (oClassId != null)
                        dataClassId = _converter.convert(Integer.class, oClassId);
                }

            }

            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("ClassId"), dataClassId);
            filter.addCondition(FieldKey.fromParts("Name"), dataNames, CompareType.IN);
            filter.addCondition(FieldKey.fromParts("Container"), container, CompareType.NEQ);

            return new TableSelector(ExperimentService.get().getTinfoData(), filter, null).exists();
        }

    }
}
