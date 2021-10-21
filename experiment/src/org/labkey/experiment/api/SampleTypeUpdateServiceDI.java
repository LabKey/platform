/*
 * Copyright (c) 2019 LabKey Corporation
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
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.CaseInsensitiveMapWrapper;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.Filter;
import org.labkey.api.data.ImportAliasable;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.UpdateableTableInfo;
import org.labkey.api.dataiterator.AttachmentDataIterator;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.DetailedAuditLogDataIterator;
import org.labkey.api.dataiterator.LoggingDataIterator;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpRunItem;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.inventory.InventoryService;
import org.labkey.api.qc.DataState;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;
import org.labkey.experiment.ExpDataIterators;
import org.labkey.experiment.SampleTypeAuditProvider;
import org.labkey.experiment.samples.SampleStateManager;
import org.labkey.experiment.samples.UploadSamplesHelper;
import org.springframework.util.StringUtils;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Collections.emptyMap;
import static org.labkey.api.exp.query.ExpMaterialTable.Column.AliquotedFromLSID;
import static org.labkey.api.exp.query.ExpMaterialTable.Column.RootMaterialLSID;

/**
 *
 * This replaces the old row at a time UploadSamplesHelper.uploadMaterials() implementations.
 *
 * originally copied from ExpDataClassDataTableImpl.DataClassDataUpdateService,
 *
 * TODO find remaining shared code and refactor
 *
 */
public class SampleTypeUpdateServiceDI extends DefaultQueryUpdateService
{
    public static final Logger LOG = LogManager.getLogger(SampleTypeUpdateServiceDI.class);

    public enum Options {
        SkipDerivation,
        SkipAliquot
    }


    // SampleType may be null for read or delete. We don't allow insert or update without a sample type.
    final @Nullable ExpSampleTypeImpl _sampleType;
    final UserSchema _schema;
    final @Nullable TableInfo _samplesTable;
    // super.getRootTable() is UserSchema table
    // getDbTable() is exp.materials
    // getSamplesTable() is the materialized table with row properties

    public SampleTypeUpdateServiceDI(ExpMaterialTableImpl table, @Nullable ExpSampleTypeImpl sampleType)
    {
        super(table, table.getRealTable(), sampleType == null ? emptyMap() : createMVMapping(sampleType.getDomain()));
        _sampleType = sampleType;
        _schema = table.getUserSchema();
        _samplesTable = sampleType == null ? null : sampleType.getTinfo();
        // we do this in ExpMaterialTableImpl.persistRows() via ExpDataIterators.PersistDataIteratorBuilder
        _enableExistingRecordsDataIterator = false;
    }

    UserSchema getSchema()
    {
        return _schema;
    }

    Container getContainer()
    {
        return _schema.getContainer();
    }

    User getUser()
    {
        return _schema.getUser();
    }


    @Override
    protected DataIteratorBuilder preTriggerDataIterator(DataIteratorBuilder in, DataIteratorContext context)
    {
        // MOVE PrepareDataIteratorBuilder into this file
        assert _sampleType != null : "SampleType required for insert/update, but not required for read/delete";
        return new UploadSamplesHelper.PrepareDataIteratorBuilder(_sampleType, getQueryTable(), in, getContainer());
    }

    @Override
    public int importRows(User user, Container container, DataIteratorBuilder rows, BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext)
    {
        assert _sampleType != null : "SampleType required for insert/update, but not required for read/delete";
        int ret = _importRowsUsingDIB(user, container, rows, null, getDataIteratorContext(errors, InsertOption.INSERT, configParameters), extraScriptContext);
        if (ret > 0 && !errors.hasErrors())
        {
            onSamplesChanged();
            audit(QueryService.AuditAction.INSERT);
        }
        return ret;
    }

    @Override
    public DataIteratorBuilder createImportDIB(User user, Container container, DataIteratorBuilder data, DataIteratorContext context)
    {
        assert _sampleType != null : "SampleType required for insert/update, but not required for read/delete";

        DataIteratorBuilder dib = new ExpDataIterators.ExpMaterialDataIteratorBuilder(getQueryTable(), data, container, user);

        dib = ((UpdateableTableInfo)getQueryTable()).persistRows(dib, context);
        dib = AttachmentDataIterator.getAttachmentDataIteratorBuilder(getQueryTable(), dib, user, context.getInsertOption().batch ? getAttachmentDirectory() : null, container, getAttachmentParentFactory());
        dib = DetailedAuditLogDataIterator.getDataIteratorBuilder(getQueryTable(), dib, context.getInsertOption() == InsertOption.MERGE ? QueryService.AuditAction.MERGE : QueryService.AuditAction.INSERT, user, container);

        UserSchema userSchema = getQueryTable().getUserSchema();
        if (InventoryService.get() != null && userSchema != null)
        {
            ExpSampleType sampleType = ((ExpMaterialTableImpl) getQueryTable()).getSampleType();
            dib = LoggingDataIterator.wrap(InventoryService.get().getPersistStorageItemDataIteratorBuilder(dib, userSchema.getContainer(), userSchema.getUser(), sampleType.getMetricUnit()));
        }

        if (userSchema != null)
        {
            dib = LoggingDataIterator.wrap(new ExpDataIterators.AutoLinkToStudyDataIteratorBuilder(dib, true, userSchema.getContainer(), userSchema.getUser(), getQueryTable()));
        }

        return dib;
    }

    @Override
    public int loadRows(User user, Container container, DataIteratorBuilder rows, DataIteratorContext context, @Nullable Map<String, Object> extraScriptContext)
    {
        assert _sampleType != null : "SampleType required for insert/update, but not required for read/delete";
        int ret = super.loadRows(user, container, rows, context, extraScriptContext);
        if (ret > 0 && !context.getErrors().hasErrors())
        {
            onSamplesChanged();
            audit(context.getInsertOption().mergeRows ? QueryService.AuditAction.MERGE : QueryService.AuditAction.INSERT);
        }
        return ret;
    }

    @Override
    public int mergeRows(User user, Container container, DataIteratorBuilder rows, BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext)
    {
        assert _sampleType != null : "SampleType required for insert/update, but not required for read/delete";
        int ret = _importRowsUsingDIB(user, container, rows, null, getDataIteratorContext(errors, InsertOption.MERGE, configParameters), extraScriptContext);
        if (ret > 0 && !errors.hasErrors())
        {
            onSamplesChanged();
            audit(QueryService.AuditAction.MERGE);
        }
        return ret;
    }

    @Override
    public List<Map<String, Object>> insertRows(User user, Container container, List<Map<String, Object>> rows, BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext) throws SQLException
    {
        assert _sampleType != null : "SampleType required for insert/update, but not required for read/delete";
        // insertRows with lineage is pretty good at deadlocking against it self, so use retry loop

        DbScope scope = getSchema().getDbSchema().getScope();
        List<Map<String, Object>> results = scope.executeWithRetry(transaction ->
                super._insertRowsUsingDIB(user, container, rows, getDataIteratorContext(errors, InsertOption.INSERT, configParameters), extraScriptContext));

        if (results != null && results.size() > 0 && !errors.hasErrors())
        {
            if (InventoryService.get() != null)
                InventoryService.get().recomputeSampleTypeRollup(_sampleType, container);

            onSamplesChanged();
            audit(QueryService.AuditAction.INSERT);
        }
        return results;
    }

    @Override
    public List<Map<String, Object>> updateRows(User user, Container container, List<Map<String, Object>> rows, List<Map<String, Object>> oldKeys, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext) throws InvalidKeyException, BatchValidationException, QueryUpdateServiceException, SQLException
    {
        assert _sampleType != null : "SampleType required for insert/update, but not required for read/delete";
        var ret = super.updateRows(user, container, rows, oldKeys, configParameters, extraScriptContext);

        /* setup mini dataiterator pipeline to process lineage */
        DataIterator di = _toDataIterator("updateRows.lineage", ret);
        ExpDataIterators.derive(user, container, di, true, true);

        if (ret.size() > 0)
        {
            onSamplesChanged();
            audit(QueryService.AuditAction.UPDATE);
        }

        return ret;
    }

    @Override
    protected Map<String, Object> _select(Container container, Object[] keys) throws ConversionException
    {
        throw new IllegalStateException("Overridden .getRow()/.getRows() calls .getMaterialMap()");
    }

    public Set<String> getAliquotSpecificFields()
    {
        Domain domain = getDomain();
        Set<String> fields = domain.getProperties().stream()
                .filter(dp -> ExpSchema.DerivationDataScopeType.ChildOnly.name().equalsIgnoreCase(dp.getDerivationDataScope()))
                .map(ImportAliasable::getName)
                .collect(Collectors.toSet());

        return new CaseInsensitiveHashSet(fields);
    }

    public Set<String> getSampleMetaFields()
    {
        Domain domain = getDomain();
        Set<String> fields = domain.getProperties().stream()
                .filter(dp -> !ExpSchema.DerivationDataScopeType.ChildOnly.name().equalsIgnoreCase(dp.getDerivationDataScope()))
                .map(ImportAliasable::getName)
                .collect(Collectors.toSet());

        return new CaseInsensitiveHashSet(fields);
    }

    @Override
    protected Map<String, Object> _update(User user, Container c, Map<String, Object> row, Map<String, Object> oldRow, Object[] keys) throws SQLException, ValidationException
    {
        assert _sampleType != null : "SampleType required for insert/update, but not required for read/delete";
        // LSID was stripped by super.updateRows() and is needed to insert into the dataclass provisioned table
        String lsid = (String) oldRow.get("lsid");
        if (lsid == null)
            throw new ValidationException("lsid required to update row");

        String oldAliquotedFromLSID = (String) oldRow.get(AliquotedFromLSID.name());
        boolean isAliquot = !StringUtils.isEmpty(oldAliquotedFromLSID);
        Set<String> aliquotFields = getAliquotSpecificFields();
        Set<String> sampleMetaFields = getSampleMetaFields();

        // Replace attachment columns with filename and keep AttachmentFiles
        Map<String, Object> rowCopy = new CaseInsensitiveHashMap<>();

        // remove aliquotedFrom from row, or error out
        rowCopy.putAll(row);
        String newAliquotedFromLSID = (String) rowCopy.get(AliquotedFromLSID.name());
        if (!StringUtils.isEmpty(newAliquotedFromLSID) && newAliquotedFromLSID.equals(oldAliquotedFromLSID))
            throw new ValidationException("Updating aliquotedFrom is not supported");

        // We need to allow updating from one locked status to another locked status, but without other changes
        // and updating from either locked or unlocked to something else while also updating other metadata
        SampleStateManager statusManager = SampleStateManager.getInstance();
        DataState oldStatus = statusManager.getStateForRowId(getContainer(), (Integer) oldRow.get(ExpMaterialTable.Column.SampleState.name()));
        boolean oldAllowsOp = statusManager.isOperationPermitted(oldStatus, SampleTypeService.SampleOperations.EditMetadata);
        DataState newStatus = statusManager.getStateForRowId(getContainer(), (Integer) rowCopy.get(ExpMaterialTable.Column.SampleState.name()));
        boolean newAllowsOp = statusManager.isOperationPermitted(newStatus, SampleTypeService.SampleOperations.EditMetadata);

        rowCopy.remove(AliquotedFromLSID.name());
        rowCopy.remove(RootMaterialLSID.name());

        Map<String, Object> ret = new CaseInsensitiveHashMap<>(super._update(user, c, rowCopy, oldRow, keys));

        Map<String, Object> validRowCopy = new CaseInsensitiveHashMap<>();
        boolean hasNonStatusChange = false;
        for (String updateField : rowCopy.keySet())
        {
            Object updateValue = rowCopy.get(updateField);
            boolean isAliquotField = aliquotFields.contains(updateField);
            boolean isSampleMetaField = sampleMetaFields.contains(updateField);

            if (isAliquot && isSampleMetaField)
            {
                LOG.warn("Sample metadata update has been skipped for an aliquot");
            }
            else if (!isAliquot && isAliquotField)
            {
                LOG.warn("Aliquot-specific field update has been skipped for a sample.");
            }
            else
            {
                hasNonStatusChange = hasNonStatusChange || !SampleTypeServiceImpl.statusUpdateColumns.contains(updateField.toLowerCase());
                validRowCopy.put(updateField, updateValue);
            }
        }
        // had a locked status before and either not updating the status or updating to a new locked status
        if (hasNonStatusChange && !oldAllowsOp && (newStatus == null || !newAllowsOp))
        {
            throw new ValidationException(String.format("Updating sample metadata when status is '%s' is not allowed.", oldStatus.getLabel()));
        }

        keys = new Object[]{lsid};
        TableInfo t = _sampleType.getTinfo();
        // Sample type uses FILE_LINK not FILE_ATTACHMENT, use convertTypes() to handle posted files
        convertTypes(c, validRowCopy, t, "sampletype");
        if (t.getColumnNameSet().stream().anyMatch(validRowCopy::containsKey))
        {
            ret.putAll(Table.update(user, t, validRowCopy, t.getColumn("lsid"), keys, null, Level.DEBUG));
        }

        // update comment
        ExpMaterialImpl sample = null;
        if (row.containsKey("flag") || row.containsKey("comment"))
        {
            Object o = row.containsKey("flag") ? row.get("flag") : row.get("comment");
            String flag = Objects.toString(o, null);

            sample = ExperimentServiceImpl.get().getExpMaterial(lsid);
            sample.setComment(user, flag);
        }

        // update aliases
        if (row.containsKey("Alias"))
            AliasInsertHelper.handleInsertUpdate(getContainer(), user, lsid, ExperimentService.get().getTinfoMaterialAliasMap(), row.get("Alias"));

        // search index
        SearchService ss = SearchService.get();
        if (ss != null)
        {
            if (sample == null)
                sample = ExperimentServiceImpl.get().getExpMaterial(lsid);
            sample.index(null);
        }

        ret.put("lsid", lsid);
        return ret;
    }

    @Override
    protected int truncateRows(User user, Container container)
    {
        if (_sampleType == null)
            return 0;

        int ret = SampleTypeServiceImpl.get().truncateSampleType(_sampleType, user, container);
        if (ret > 0)
        {
            // NOTE: Not necessary to call onSamplesChanged -- already called by truncateSampleSet
            audit(QueryService.AuditAction.TRUNCATE);
        }
        return ret;
    }

    @Override
    protected Domain getDomain()
    {
        return _sampleType == null ? null : _sampleType.getDomain();
    }

    @Override
    protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRowMap)
    {
        List<Integer> id = new LinkedList<>();
        Integer rowId = getMaterialRowId(oldRowMap);
        id.add(rowId);
        ExperimentServiceImpl.get().deleteMaterialByRowIds(user, container, id, true, _sampleType, false);
        return oldRowMap;
    }


    @Override
    public List<Map<String, Object>> deleteRows(User user, Container container, List<Map<String, Object>> keys, @Nullable Map<Enum, Object> configParameters, @Nullable Map<String, Object> extraScriptContext)
            throws QueryUpdateServiceException, SQLException, InvalidKeyException, BatchValidationException
    {
        SampleStateManager statusManager = SampleStateManager.getInstance();

        List<Map<String, Object>> result = new ArrayList<>(keys.size());

        // Check for trigger scripts
        if (getQueryTable().hasTriggers(container))
        {
            result = super.deleteRows(user, container, keys, configParameters, extraScriptContext);
        }
        else
        {
            List<Integer> ids = new LinkedList<>();
            Set<String> aliquotParents = new HashSet<>();

            for (Map<String, Object> k : keys)
            {
                Integer rowId = getMaterialRowId(k);
                // Issue 40621
                // adding input fields is expensive, skip input fields for delete since deleted samples are not surfaced on Timeline UI
                Map<String, Object> map = getMaterialMap(rowId, getMaterialLsid(k), user, container, false);
                if (map == null)
                    throw new QueryUpdateServiceException("No Sample Type Material found for RowID or LSID");

                if (rowId == null)
                    rowId = getMaterialRowId(map);
                if (rowId == null)
                    throw new QueryUpdateServiceException("RowID is required to delete a Sample Type Material");

                if (!statusManager.isOperationPermitted(getContainer(), (Integer) map.get(ExpMaterialTable.Column.SampleState.name()), SampleTypeService.SampleOperations.Delete))
                    throw new QueryUpdateServiceException(String.format("Sample with RowID %d cannot be deleted due to its current status (%s)", rowId, statusManager.getStateForRowId(container, (Integer) map.get(ExpMaterialTable.Column.SampleState.name()))));

                if (map.containsKey("RootMaterialLSID") && !StringUtils.isEmpty(map.get("RootMaterialLSID")))
                    aliquotParents.add((String) map.get("RootMaterialLSID"));

                ids.add(rowId);
                result.add(map);
            }
            // TODO check if this handle attachments???
            ExperimentServiceImpl.get().deleteMaterialByRowIds(user, container, ids, true, _sampleType, false);
        }

        if (result.size() > 0)
        {
            // NOTE: Not necessary to call onSamplesChanged -- already called by deleteMaterialByRowIds
            audit(QueryService.AuditAction.DELETE);
            addAuditEvent(user, container,  QueryService.AuditAction.DELETE, configParameters, result, null);
        }
        return result;
    }

    private String getMaterialLsid(Map<String, Object> row)
    {
        Object o = row.get(ExpMaterialTable.Column.LSID.name());
        if (o instanceof String)
            return (String)o;

        return null;
    }


    IntegerConverter _converter = new IntegerConverter();

    private Integer getMaterialRowId(Map<String, Object> row)
    {
        if (row != null)
        {
            Object o = row.get(ExpMaterialTable.Column.RowId.name());
            if (o != null)
                return (Integer) (_converter.convert(Integer.class, o));
        }

        return null;
    }

    private Map<String, Object> getMaterialMap(Integer rowId, String lsid, User user, Container container, boolean addInputs)
            throws QueryUpdateServiceException
    {
        Filter filter;
        if (rowId != null)
            filter = new SimpleFilter(FieldKey.fromParts(ExpMaterialTable.Column.RowId), rowId);
        else if (lsid != null)
            filter = new SimpleFilter(FieldKey.fromParts(ExpMaterialTable.Column.LSID), lsid);
        else
            throw new QueryUpdateServiceException("Either RowId or LSID is required to get Sample Type Material.");

        Map<String, Object> sampleRow = new TableSelector(getQueryTable(), filter, null).getMap();
        if (null == sampleRow || !addInputs)
            return sampleRow;

        ExperimentService experimentService = ExperimentService.get();
        ExpMaterial seed = rowId != null ? experimentService.getExpMaterial(rowId) : experimentService.getExpMaterial(lsid);
        if (null == seed)
            return sampleRow;
        Set<ExpMaterial> parentSamples = experimentService.getParentMaterials(container, user, seed);
        if (!parentSamples.isEmpty())
            addParentFields(sampleRow, parentSamples, ExpMaterial.MATERIAL_INPUT_PARENT + "/", user);
        Set<ExpData> parentDatas = experimentService.getParentDatas(container, user, seed);
        if (!parentDatas.isEmpty())
            addParentFields(sampleRow, parentDatas, ExpData.DATA_INPUT_PARENT + "/", user);

        return sampleRow;
    }

    private <T extends ExpRunItem> void addParentFields(Map<String, Object> sampleRow, Set<T> parents, String parentPrefix, User user)
    {
        Map<String, List<String>> parentByType = new HashMap<>();
        for (ExpRunItem parent : parents)
        {
            String type = "";
            if (parent instanceof ExpData)
            {
                ExpDataClass dataClass = ((ExpData) parent).getDataClass(user);
                if (dataClass == null)
                    continue;
                type = dataClass.getName();
            }

            else if (parent instanceof ExpMaterial)
            {
                ExpSampleType sampleType = ((ExpMaterial) parent).getSampleType();
                if (sampleType == null)
                    continue;
                type = sampleType.getName();
            }

            parentByType.computeIfAbsent(type, k -> new ArrayList<>());
            parentByType.get(type).add(parent.getName());
        }

        for (String type : parentByType.keySet())
        {
            String key = parentPrefix + type;
            String value = String.join(",", parentByType.get(type));
            sampleRow.put(key, value);
        }
    }

    @Override
    public List<Map<String, Object>> getRows(User user, Container container, List<Map<String, Object>> keys)
            throws QueryUpdateServiceException
    {
        return getRows(user, container, keys, false /*skip addInputs for insertRows*/);
    }

    @Override
    public Map<Integer, Map<String, Object>> getExistingRows(User user, Container container, Map<Integer, Map<String, Object>> keys)
            throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        return getMaterialMapsWithInput(keys, user, container);
    }

    private Map<Integer, Map<String, Object>> getMaterialMapsWithInput(Map<Integer, Map<String, Object>> keys, User user, Container container)
            throws QueryUpdateServiceException
    {
        Map<Integer, Map<String, Object>> sampleRows = new LinkedHashMap<>();
        Map<Integer, String> rowNumLsid = new HashMap<>();

        Map<Integer, Integer> rowIdRowNumMap = new LinkedHashMap<>();
        Map<String, Integer> lsidRowNumMap = new CaseInsensitiveMapWrapper<>(new LinkedHashMap<>());
        for (Map.Entry<Integer, Map<String, Object>> keyMap : keys.entrySet())
        {
            Integer rowNum = keyMap.getKey();

            Integer rowId = getMaterialRowId(keyMap.getValue());
            String lsid = getMaterialLsid(keyMap.getValue());

            if (rowId != null)
                rowIdRowNumMap.put(rowId, rowNum);
            else if (lsid != null)
            {
                lsidRowNumMap.put(lsid, rowNum);
                rowNumLsid.put(rowNum, lsid);
            }
            else
                throw new QueryUpdateServiceException("Either RowId or LSID is required to get Sample Type Material.");
        }

        if (!rowIdRowNumMap.isEmpty())
        {
            Filter filter = new SimpleFilter(FieldKey.fromParts(ExpMaterialTable.Column.RowId), rowIdRowNumMap.keySet(), CompareType.IN);
            Map<String, Object>[] rows = new TableSelector(getQueryTable(), filter, null).getMapArray();
            for (Map<String, Object> row : rows)
            {
                Integer rowId = (Integer) row.get("rowid");
                Integer rowNum = rowIdRowNumMap.get(rowId);
                String sampleLsid = (String) row.get("lsid");

                rowNumLsid.put(rowNum, sampleLsid);
                sampleRows.put(rowNum, row);
            }
        }

        if (!lsidRowNumMap.isEmpty())
        {
            Filter filter = new SimpleFilter(FieldKey.fromParts(ExpMaterialTable.Column.LSID), lsidRowNumMap.keySet(), CompareType.IN);
            Map<String, Object>[] rows = new TableSelector(getQueryTable(), filter, null).getMapArray();
            for (Map<String, Object> row : rows)
            {
                String sampleLsid = (String) row.get("lsid");
                Integer rowNum = lsidRowNumMap.get(sampleLsid);

                sampleRows.put(rowNum, row);
            }
        }

        List<ExpMaterialImpl> materials = ExperimentServiceImpl.get().getExpMaterialsByLSID(rowNumLsid.values());

        Map<String, Pair<Set<ExpMaterial>, Set<ExpData>>> parents = ExperimentServiceImpl.get().getParentMaterialAndDataMap(container, user, new HashSet<>(materials));

        for (Map.Entry<Integer, Map<String, Object>> rowNumSampleRow : sampleRows.entrySet())
        {
            Integer rowNum = rowNumSampleRow.getKey();
            String lsidKey = rowNumLsid.get(rowNum);
            Map<String, Object> sampleRow = rowNumSampleRow.getValue();

            if (!parents.containsKey(lsidKey))
                continue;

            Pair<Set<ExpMaterial>, Set<ExpData>> sampleParents = parents.get(lsidKey);

            if (!sampleParents.first.isEmpty())
                addParentFields(sampleRow, sampleParents.first, ExpMaterial.MATERIAL_INPUT_PARENT + "/", user);
            if (!sampleParents.second.isEmpty())
                addParentFields(sampleRow, sampleParents.second, ExpData.DATA_INPUT_PARENT + "/", user);
        }

        return sampleRows;
    }

    public List<Map<String, Object>> getRows(User user, Container container, List<Map<String, Object>> keys, boolean addInputs)
            throws QueryUpdateServiceException
    {
        List<Map<String, Object>> result = new ArrayList<>(keys.size());
        for (Map<String, Object> k : keys)
        {
            Map<String, Object> materialMap = getMaterialMap(getMaterialRowId(k), getMaterialLsid(k), user, container, addInputs);
            if (materialMap != null)
                result.add(materialMap);
        }
        return result;
    }

    @Override
    protected Map<String, Object> getRow(User user, Container container, Map<String, Object> keys) throws QueryUpdateServiceException
    {
        return getMaterialMap(getMaterialRowId(keys), getMaterialLsid(keys), user, container, true);
    }

    /* don't need to implement these since we override insertRows() etc. */

//    @Override
//    protected Map<String, Object> getRow(User user, Container container, Map<String, Object> keys)
//    {
//        throw new IllegalStateException();
//    }

//    @Override
//    protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, @NotNull Map<String, Object> oldRow)
//    {
//        throw new IllegalStateException();
//    }

//    @Override
//    protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRow)
//    {
//        throw new IllegalStateException();
//    }

    private void onSamplesChanged()
    {
        var tx = getSchema().getDbSchema().getScope().getCurrentTransaction();
        if (tx != null)
        {
             if (!tx.isAborted())
                 tx.addCommitTask(this::fireSamplesChanged, DbScope.CommitTaskOption.POSTCOMMIT);
             else
                 LOG.info("Skipping onSamplesChanged callback; transaction aborted");
        }
        else
        {
            fireSamplesChanged();
        }
    }

    private void fireSamplesChanged()
    {
        if (_sampleType != null)
            _sampleType.onSamplesChanged(getUser(), null);
    }

    void audit(QueryService.AuditAction auditAction)
    {
        assert _sampleType != null || auditAction == QueryService.AuditAction.DELETE : "SampleType required for insert/update, but not required for read/delete";
        SampleTypeAuditProvider.SampleTypeAuditEvent event = new SampleTypeAuditProvider.SampleTypeAuditEvent(
                getContainer().getId(), "Samples " + auditAction.getVerbPastTense() + " in: " + (_sampleType == null ? "<Materials>" : _sampleType.getName()));
        var tx = getSchema().getDbSchema().getScope().getCurrentTransaction();
        if (tx != null)
            event.setTransactionId(tx.getAuditId());
        if (_sampleType != null)
        {
            event.setSourceLsid(_sampleType.getLSID());
            event.setSampleSetName(_sampleType.getName());
        }
        event.setInsertUpdateChoice(auditAction.toString().toLowerCase());
        AuditLogService.get().addEvent(getUser(), event);
    }
}
