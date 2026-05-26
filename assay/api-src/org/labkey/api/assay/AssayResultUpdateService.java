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
package org.labkey.api.assay;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.sample.AssaySampleLookupContext;
import org.labkey.api.assay.transform.DataTransformService;
import org.labkey.api.assay.transform.DefaultTransformResult;
import org.labkey.api.assay.transform.TransformResult;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.DataIteratorUtil;
import org.labkey.api.dataiterator.MapDataIterator;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.OntologyObject;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ProvenanceService;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.vfs.FileLike;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.labkey.api.dataiterator.DetailedAuditLogDataIterator.AuditConfigs.AuditUserComment;

public class AssayResultUpdateService extends DefaultQueryUpdateService
{
    private final AssaySampleLookupContext _assaySampleLookupContext;
    private final AssayProtocolSchema _schema;

    private Map<Long, Integer> dataChangeCount;

    public AssayResultUpdateService(AssayProtocolSchema schema, FilteredTable table)
    {
        super(table, table.getRealTable(), createMVMapping(schema.getProvider().getResultsDomain(schema.getProtocol())));
        if (!(table instanceof AssayResultTable))
            throw new IllegalArgumentException("Expected AssayResultTable");

        _assaySampleLookupContext = new AssaySampleLookupContext();
        _schema = schema;
    }

    private void addRunAuditSummary(User user, @Nullable Map<Enum, Object> configParameters, String verb)
    {
        String userComment = configParameters == null ? null : (String) configParameters.get(AuditUserComment);

        for (Long runId: dataChangeCount.keySet())
        {
            var run = ExperimentService.get().getExpRun(runId);
            int deletedCount = dataChangeCount.get(runId);

            ExperimentService.get().auditRunEvent(user, run.getProtocol(), run, null, deletedCount + " data row" + (deletedCount > 1 ? "s have" : " has") + " been " + verb + " in " + run.getProtocol().getName() + ".", userComment);

        }
        dataChangeCount = null;
    }

    private void incrementAuditRowCount(ExpRun run)
    {
        long runId = run.getRowId();
        dataChangeCount.putIfAbsent(runId, 0);
        dataChangeCount.put(runId, dataChangeCount.get(runId) + 1);
    }

    @Override
    public List<Map<String, Object>> updateRows(
        User user,
        Container container,
        List<Map<String, Object>> rows,
        List<Map<String, Object>> oldKeys,
        BatchValidationException errors,
        @Nullable Map<Enum, Object> configParameters,
        Map<String, Object> extraScriptContext
    ) throws InvalidKeyException, BatchValidationException, QueryUpdateServiceException, SQLException
    {
        dataChangeCount = new LinkedHashMap<>();
        // handle transform scripts
        rows = transform(container, user, rows, oldKeys);
        var result = super.updateRows(user, container, rows, oldKeys, errors, configParameters, extraScriptContext);

        _assaySampleLookupContext.syncLineage(container, user, errors);

        if (errors.hasErrors())
            throw errors;

        addRunAuditSummary(user, configParameters, "edited");

        return result;
    }

    private List<Map<String, Object>> transform(
            Container container,
            User user,
            List<Map<String, Object>> rows,
            List<Map<String, Object>> oldKeys
    ) throws BatchValidationException, InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        try
        {
            List<Map<String, Object>> rowsForTransform = resolveRows(container, user, rows, oldKeys);
            AssayTransformContext context = new AssayTransformContext(container, user, rowsForTransform, _schema.getProtocol(), _schema.getProvider());
            TransformResult result = DataTransformService.get().transformAndValidate(context, null, DataTransformService.TransformOperation.UPDATE);
            Map<ExpData, DataIteratorBuilder> transformedData = result.getTransformedData();

            if (!transformedData.isEmpty())
            {
                ColumnInfo keyCol = null;
                for (ColumnInfo colInfo : getDbTable().getPkColumns())
                {
                    if (rows.getFirst().containsKey(colInfo.getName()))
                    {
                        keyCol = colInfo;
                        break;
                    }
                }

                if (keyCol == null)
                    throw new BatchValidationException((new ValidationException(String.format("The data does not contain a key field value for table : %s.", getQueryTable().getName()))));

                // merge any existing data with transformed rows
                Map<Object, Map<String, Object>> newData = new LinkedHashMap<>();
                for (Map<String, Object> row : rows)
                {
                    if (row.containsKey(keyCol.getName()))
                        newData.put(row.get(keyCol.getName()), new HashMap<>(row));
                    else
                        throw new BatchValidationException(new ValidationException(String.format("Unable to find the key value : %s for a row being updated.", keyCol.getName())));
                }

                boolean dataTypeHandled = false;
                for (Map.Entry<ExpData, DataIteratorBuilder> entry : transformedData.entrySet())
                {
                    ExpData data = entry.getKey();

                    // match the transformed data by data types
                    if (data.getDataType().equals(context.getProvider().getDataType()))
                    {
                        boolean mergeData = false;
                        if (dataTypeHandled)
                            throw new BatchValidationException(new ValidationException(String.format("There was more than one transformed file found for the data type : %s.", context.getProvider().getDataType())));
                        dataTypeHandled = true;

                        try (var it = DataIteratorUtil.wrapMap(entry.getValue().getDataIterator(new DataIteratorContext()), false))
                        {
                            while (it.next())
                            {
                                // merge with original updated rows
                                Map<String, Object> row = it.getMap();
                                Object key = row.get(keyCol.getName());
                                if (key != null)
                                {
                                    if (newData.containsKey(key))
                                        mergeData = true;
                                    newData.put(key, new HashMap<>(row));
                                }
                                else
                                    throw new BatchValidationException(new ValidationException(String.format("Unable to find the key value : %s for a transformed data row.", keyCol.getName())));
                            }

                            if (mergeData)
                            {
                                // replace with merged data
                                rows = new ArrayList<>(newData.values());
                            }
                        }
                    }
                }
            }
            return rows;
        }
        catch (ValidationException ve)
        {
            throw new BatchValidationException(ve);
        }
        catch (IOException ioe)
        {
            throw new BatchValidationException(new ValidationException(ioe.getMessage()));
        }
    }

    /**
     * Merge existing values with the rows being updated prior to handing off to any
     * transform scripts. This is necessary because a transform script will need to see all
     * values for each row (not just the changed values).
     */
    private List<Map<String, Object>> resolveRows(
            Container container,
            User user,
            List<Map<String, Object>> rows,
            List<Map<String, Object>> oldKeys
    ) throws InvalidKeyException, QueryUpdateServiceException, SQLException, ValidationException
    {
        Map<String, ColumnInfo> columnInfoMap = new CaseInsensitiveHashMap<>();
        getQueryTable().getColumns().forEach(ci -> columnInfoMap.put(ci.getName(), ci));

        for (int i=0; i < rows.size(); i++)
        {
            Map<String, Object> row = rows.get(i);
            Map<String, Object> oldKey = oldKeys == null ? row : oldKeys.get(i);

            var oldRow = getRow(user, container, oldKey);
            if (oldRow == null)
                throw new ValidationException("Unable to find existing row");

            for (Map.Entry<String, Object> entry : oldRow.entrySet())
            {
                ColumnInfo col = columnInfoMap.get(entry.getKey());
                if (col != null && !row.containsKey(entry.getKey()))
                {
                    // use column names for existing row values
                    row.put(col.getName(), entry.getValue());
                }
            }
        }
        return rows;
    }

    @Override
    protected Map<String, Object> updateRow(
        User user,
        Container container,
        Map<String, Object> row,
        @NotNull Map<String, Object> oldRow,
        @Nullable Map<Enum, Object> configParameters
    ) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        Map<String, Object> originalRow = getRow(user, container, oldRow);
        if (originalRow == null)
            throw new InvalidKeyException("Could not find row");

        ExpRun run = getRun(originalRow, user, UpdatePermission.class);

        if (!run.getContainer().equals(container))
            throw new UnauthorizedException("Assay results being updated are from a different container.");

        // Assay results use FILE_LINK not FILE_ATTACHMENT, use convertTypes() to handle directing the posted files to the run specific directory
        Path assayResultsRunDir = AssayResultsFileWriter.getAssayFilesDirectoryPath(run);
        convertTypes(user, container, row, getDbTable(), assayResultsRunDir);

        Map<String, Object> result = super.updateRow(user, container, row, oldRow, configParameters);
        Map<String, Object> updatedValues = getRow(user, container, oldRow);

        TableInfo table = getQueryTable();
        for (Map.Entry<String, Object> entry : result.entrySet())
        {
            ColumnInfo col = table.getColumn(entry.getKey());

            if (col != null)
            {
                Object oldValue = col.getValue(originalRow);
                Object newValue = col.getValue(updatedValues);
                boolean hasValueChanged = !Objects.equals(oldValue, newValue);

                if (hasValueChanged)
                    _assaySampleLookupContext.trackSampleLookupChange(container, user, table, col, run);
            }
        }

        incrementAuditRowCount(run);

        return result;
    }


    private ExpRun getRun(Map<String, Object> row, User user, Class<? extends Permission> perm) throws InvalidKeyException
    {
        int dataId = ((Number) row.get("DataId")).intValue();
        ExpData data = ExperimentService.get().getExpData(dataId);
        if (data == null)
        {
            throw new InvalidKeyException("Could not find data object");
        }
        ExpRun run = data.getRun();
        if (run == null)
        {
            throw new InvalidKeyException("Could not find run object");
        }
        if (!run.getContainer().hasPermission(user, perm))
        {
            throw new UnauthorizedException("User does not have " + perm.getSimpleName() + " result in " + run.getContainer());
        }
        return run;
    }

    @Override
    protected Map<String, Object> deleteRow(
        User user,
        Container container,
        Map<String, Object> oldRowMap,
        @Nullable Map<Enum, Object> configParameters,
        @Nullable Map<String, Object> extraScriptContext
    ) throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        ExpRun run = getRun(oldRowMap, user, DeletePermission.class);

        AssayService.get().onBeforeAssayResultDelete(container, user, run, oldRowMap);

        TableInfo datatableInfo = this.getQueryTable();
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("run"), run.getRowId());
        filter.addCondition(FieldKey.fromParts("rowId"), oldRowMap.get("rowId"));

        Map<String, Object> dataObjectMap = new TableSelector(datatableInfo, filter, null).getMap();

        Map<String, Object> result = super.deleteRow(user, container, oldRowMap);

        incrementAuditRowCount(run);

        if (null != dataObjectMap)
        {
            String objectLsid = dataObjectMap.get("LSID").toString();
            OntologyObject objectToDelete = OntologyManager.getOntologyObject(container, objectLsid);

            if (null != objectToDelete)
            {
                ProvenanceService.get().deleteObjectProvenance(objectToDelete.getObjectId());
                OntologyManager.deleteOntologyObject(objectLsid, container, false);
            }
        }

        // Issue 51126: need to track and resync run/sample lineage on delete in the same way we do for update
        _assaySampleLookupContext.trackSampleLookupChange(container, user, datatableInfo, _schema, run);

        return result;
    }

    @Override
    public List<Map<String, Object>> deleteRows(User user, Container container, List<Map<String, Object>> keys, @Nullable Map<Enum, Object> configParameters, @Nullable Map<String, Object> extraScriptContext) throws InvalidKeyException, BatchValidationException, QueryUpdateServiceException, SQLException
    {
        dataChangeCount = new HashMap<>();
        var result = super.deleteRows(user, container, keys, configParameters, extraScriptContext);

        BatchValidationException errors = new BatchValidationException();
        errors.setExtraContext(extraScriptContext);
        _assaySampleLookupContext.syncLineage(container, user, errors);
        if (errors.hasErrors())
            throw errors;

        addRunAuditSummary(user, configParameters, "deleted");

        return result;
    }

    /**
     * Context used during data transforms for update operations
     */
    private static class AssayTransformContext implements AssayRunUploadContext<AssayProvider>
    {
        private final Container _container;
        private final User _user;
        private final ExpProtocol _protocol;
        private final AssayProvider _provider;
        private TransformResult _transformResult;
        private final DataIteratorBuilder _data;

        public AssayTransformContext(Container container, User user, List<Map<String, Object>> rows, ExpProtocol protocol, AssayProvider provider)
        {
            _container = container;
            _user = user;
            _protocol = protocol;
            _provider = provider;
            _data = MapDataIterator.of(rows);
        }

        @Override
        public @NotNull ExpProtocol getProtocol()
        {
            return _protocol;
        }

        @Override
        public Map<DomainProperty, String> getRunProperties()
        {
            return Collections.emptyMap();
        }

        @Override
        public Map<DomainProperty, String> getBatchProperties()
        {
            return Collections.emptyMap();
        }

        @Override
        public String getComments()
        {
            return null;
        }

        @Override
        public String getName()
        {
            return null;
        }

        @Override
        public User getUser()
        {
            return _user;
        }

        @Override
        public @NotNull Container getContainer()
        {
            return _container;
        }

        @Override
        public @Nullable HttpServletRequest getRequest()
        {
            return null;
        }

        @Override
        public ActionURL getActionURL()
        {
            return null;
        }

        @Override
        public @NotNull Map<String, FileLike> getUploadedData()
        {
            return Collections.emptyMap();
        }

        @Override
        public @Nullable DataIteratorBuilder getRawData()
        {
            return _data;
        }

        @Override
        public @NotNull Map<?, String> getInputDatas()
        {
            return Collections.emptyMap();
        }

        @Override
        public AssayProvider getProvider()
        {
            return _provider;
        }

        @Override
        public String getTargetStudy()
        {
            return null;
        }

        @Override
        public TransformResult getTransformResult()
        {
            return _transformResult != null ? _transformResult : DefaultTransformResult.createEmptyResult();
        }

        @Override
        public void setTransformResult(TransformResult result)
        {
            _transformResult = result;
        }

        @Override
        public Long getReRunId()
        {
            return null;
        }

        @Override
        public void uploadComplete(ExpRun run)
        {
        }

        @Override
        public @Nullable Logger getLogger()
        {
            return null;
        }
    }
}
