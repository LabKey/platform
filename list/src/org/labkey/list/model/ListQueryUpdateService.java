/*
 * Copyright (c) 2009-2019 LabKey Corporation
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
package org.labkey.list.model;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentParentFactory;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.TransactionAuditProvider;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.LookupResolutionType;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.Selector.ForEachBatchBlock;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.DetailedAuditLogDataIterator;
import org.labkey.api.dataiterator.ImportProgress;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListItem;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.IPropertyValidator;
import org.labkey.api.exp.property.ValidatorContext;
import org.labkey.api.gwt.client.AuditBehaviorType;
import org.labkey.api.lists.permissions.ManagePicklistsPermission;
import org.labkey.api.query.AbstractQueryUpdateService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.PropertyValidationError;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.ValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.security.ElevatedUser;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.MoveEntitiesPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.security.roles.EditorRole;
import org.labkey.api.usageMetrics.SimpleMetricsService;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.writer.VirtualFile;
import org.labkey.list.view.ListItemAttachmentParent;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.labkey.api.util.IntegerUtils.isIntegral;

/**
 * Implementation of QueryUpdateService for Lists
 */
public class ListQueryUpdateService extends DefaultQueryUpdateService
{
    private final ListDefinitionImpl _list;
    private static final String ID = "entityId";

    public ListQueryUpdateService(ListTable queryTable, TableInfo dbTable, @NotNull ListDefinition list)
    {
        super(queryTable, dbTable, createMVMapping(queryTable.getList().getDomain()));
        _list = (ListDefinitionImpl) list;
    }

    @Override
    public void configureDataIteratorContext(DataIteratorContext context)
    {
        if (context.getInsertOption().batch)
        {
            context.setMaxRowErrors(100);
            context.setFailFast(false);
        }

        context.putConfigParameter(ConfigParameters.TrimStringRight, Boolean.TRUE);
    }

    @Override
    protected @Nullable AttachmentParentFactory getAttachmentParentFactory()
    {
        return new ListItemAttachmentParentFactory();
    }

    @Override
    protected Map<String, Object> getRow(User user, Container container, Map<String, Object> listRow) throws InvalidKeyException
    {
        Map<String, Object> ret = null;

        if (null != listRow)
        {
            SimpleFilter keyFilter = getKeyFilter(listRow);

            if (null != keyFilter)
            {
                TableInfo queryTable = getQueryTable();
                Map<String, Object> raw = new TableSelector(queryTable, keyFilter, null).getMap();

                if (null != raw && !raw.isEmpty())
                {
                    ret = new CaseInsensitiveHashMap<>();

                    // EntityId
                    ret.put("EntityId", raw.get("entityid"));

                    for (DomainProperty prop : _list.getDomainOrThrow().getProperties())
                    {
                        String propName = prop.getName();
                        ColumnInfo column = queryTable.getColumn(propName);
                        Object value = column.getValue(raw);
                        if (value != null)
                            ret.put(propName, value);
                    }
                }
            }
        }

        return ret;
    }


    @Override
    public List<Map<String, Object>> insertRows(User user, Container container, List<Map<String, Object>> rows, BatchValidationException errors,
                                                @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext)
    {
        for (Map<String, Object> row : rows)
        {
            aliasColumns(getColumnMapping(), row);
        }

        DataIteratorContext context = getDataIteratorContext(errors, InsertOption.INSERT, configParameters);
        recordDataIteratorUsed(configParameters);
        List<Map<String, Object>> result = this._insertRowsUsingDIB(getListUser(user, container), container, rows, context, extraScriptContext);

        if (null != result)
        {
            ListManager mgr = ListManager.get();

            for (Map<String, Object> row : result)
            {
                if (null != row.get(ID))
                {
                    // Audit each row
                    String entityId = (String) row.get(ID);
                    String newRecord = mgr.formatAuditItem(_list, user, row);

                    mgr.addAuditEvent(_list, user, container, "A new list record was inserted", entityId, null, newRecord);
                }
            }

            if (!result.isEmpty() && !errors.hasErrors())
                mgr.indexList(_list);
        }

        return result;
    }

    private User getListUser(User user, Container container)
    {
        if (_list.isPicklist() && container.hasPermission(user, ManagePicklistsPermission.class))
        {
            // if the list is a picklist and you have permission to manage picklists, that equates
            // to having editor permission.
            return ElevatedUser.ensureContextualRoles(container, user, Pair.of(DeletePermission.class, EditorRole.class));
        }
        return user;
    }

    @Override
    protected @Nullable List<Map<String, Object>> _insertRowsUsingDIB(User user, Container container, List<Map<String, Object>> rows, DataIteratorContext context, @Nullable Map<String, Object> extraScriptContext)
    {
        if (!_list.isVisible(user))
            throw new UnauthorizedException("You do not have permission to insert data into this table.");

        return super._insertRowsUsingDIB(getListUser(user, container), container, rows, context, extraScriptContext);
    }

    @Override
    protected @Nullable List<Map<String, Object>> _updateRowsUsingDIB(User user, Container container, List<Map<String, Object>> rows,
                                                                      DataIteratorContext context, @Nullable Map<String, Object> extraScriptContext)
    {
        if (!hasPermission(user, UpdatePermission.class))
            throw new UnauthorizedException("You do not have permission to update data in this table.");

        return super._updateRowsUsingDIB(getListUser(user, container), container, rows, context, extraScriptContext);
    }

    public int insertUsingDataIterator(DataLoader loader, User user, Container container, BatchValidationException errors, @Nullable VirtualFile attachmentDir,
                                       @Nullable ImportProgress progress, boolean supportAutoIncrementKey, InsertOption insertOption, LookupResolutionType lookupResolutionType)
    {
        if (!_list.isVisible(user))
            throw new UnauthorizedException("You do not have permission to insert data into this table.");

        User updatedUser = getListUser(user, container);
        DataIteratorContext context = new DataIteratorContext(errors);
        context.setFailFast(false);
        context.setInsertOption(insertOption);    // this method is used by ListImporter and BackgroundListImporter
        context.setSupportAutoIncrementKey(supportAutoIncrementKey);
        context.setLookupResolutionType(lookupResolutionType);
        setAttachmentDirectory(attachmentDir);
        TableInfo ti = _list.getTable(updatedUser);

        if (null != ti)
        {
            try (DbScope.Transaction transaction = ti.getSchema().getScope().ensureTransaction())
            {
                int imported = _importRowsUsingDIB(updatedUser, container, loader, null, context, new HashMap<>());

                if (!errors.hasErrors())
                {
                    //Make entry to audit log if anything was inserted
                    if (imported > 0)
                        ListManager.get().addAuditEvent(_list, updatedUser, "Bulk " + (insertOption.updateOnly ? "updated " : (insertOption.mergeRows ? "imported " : "inserted ")) + imported + " rows to list.");

                    transaction.addCommitTask(() -> ListManager.get().indexList(_list), DbScope.CommitTaskOption.POSTCOMMIT);
                    transaction.commit();

                    return imported;
                }

                return 0;
            }
        }

        return 0;
    }


    @Override
    public int mergeRows(User user, Container container, DataIteratorBuilder rows, BatchValidationException errors,
                         @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext)
    {
        if (!_list.isVisible(user))
            throw new UnauthorizedException("You do not have permission to update data in this table.");

        recordDataIteratorUsed(configParameters);
        return _importRowsUsingDIB(getListUser(user, container), container, rows, null, getDataIteratorContext(errors, InsertOption.MERGE, configParameters), extraScriptContext);
    }


    @Override
    public int importRows(User user, Container container, DataIteratorBuilder rows, BatchValidationException errors,
                          Map<Enum,Object> configParameters, Map<String, Object> extraScriptContext)
    {
        if (!_list.isVisible(user))
            throw new UnauthorizedException("You do not have permission to insert data into this table.");

        recordDataIteratorUsed(configParameters);
        DataIteratorContext context = getDataIteratorContext(errors, InsertOption.IMPORT, configParameters);
        int count = _importRowsUsingDIB(getListUser(user, container), container, rows, null, context, extraScriptContext);
        if (count > 0 && !errors.hasErrors())
            ListManager.get().indexList(_list);
        return count;
    }

    @Override
    public List<Map<String, Object>> updateRows(User user, Container container, List<Map<String, Object>> rows, List<Map<String, Object>> oldKeys,
                                                BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext)
            throws InvalidKeyException, BatchValidationException, QueryUpdateServiceException, SQLException
    {
        if (!_list.isVisible(user))
            throw new UnauthorizedException("You do not have permission to update data into this table.");

        List<Map<String, Object>> result = super.updateRows(getListUser(user, container), container, rows, oldKeys, errors, configParameters, extraScriptContext);
        if (!result.isEmpty())
            ListManager.get().indexList(_list);
        return result;
    }

    @Override
    protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, @NotNull Map<String, Object> oldRow, @Nullable Map<Enum, Object> configParameters)
            throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        // TODO: Check for equivalency so that attachments can be deleted etc.

        Map<String, DomainProperty> dps = new HashMap<>();
        for (DomainProperty dp : _list.getDomainOrThrow().getProperties())
        {
            dps.put(dp.getPropertyURI(), dp);
        }

        ValidatorContext validatorCache = new ValidatorContext(container, user);

        ListItm itm = new ListItm();
        itm.setEntityId((String) oldRow.get(ID));
        itm.setListId(_list.getListId());
        itm.setKey(oldRow.get(_list.getKeyName()));

        ListItem item = new ListItemImpl(_list, itm);

        if (item.getProperties() != null)
        {
            List<ValidationError> errors = new ArrayList<>();
            for (Map.Entry<String, DomainProperty> entry : dps.entrySet())
            {
                Object value = row.get(entry.getValue().getName());
                validateProperty(entry.getValue(), value, row, errors, validatorCache);
            }

            if (!errors.isEmpty())
                throw new ValidationException(errors);
        }

        // MVIndicators
        Map<String, Object> rowCopy = new CaseInsensitiveHashMap<>();
        ArrayList<ColumnInfo> modifiedAttachmentColumns = new ArrayList<>();
        ArrayList<AttachmentFile> attachmentFiles = new ArrayList<>();

        TableInfo qt = getQueryTable();
        for (Map.Entry<String, Object> r : row.entrySet())
        {
            ColumnInfo column = qt.getColumn(FieldKey.fromParts(r.getKey()));
            rowCopy.put(r.getKey(), r.getValue());

            // 22747: Attachment columns
            if (null != column)
            {
                DomainProperty dp = _list.getDomainOrThrow().getPropertyByURI(column.getPropertyURI());
                if (null != dp && isAttachmentProperty(dp))
                {
                    modifiedAttachmentColumns.add(column);

                    // setup any new attachments
                    if (r.getValue() instanceof AttachmentFile file)
                    {
                        if (null != file.getFilename())
                            attachmentFiles.add(file);
                    }
                    else if (r.getValue() != null && !StringUtils.isEmpty(String.valueOf(r.getValue())))
                    {
                        throw new ValidationException("Cannot upload '" + r.getValue() + "' to Attachment type field '" + r.getKey() + "'.");
                    }
                }
            }
        }

        // Attempt to include key from oldRow if not found in row (As stated in the QUS Interface)
        Object newRowKey = getField(rowCopy, _list.getKeyName());
        Object oldRowKey = getField(oldRow, _list.getKeyName());

        if (null == newRowKey && null != oldRowKey)
            rowCopy.put(_list.getKeyName(), oldRowKey);

        Map<String, Object> result = super.updateRow(getListUser(user, container), container, rowCopy, oldRow, true, false);

        if (null != result)
        {
            result = getRow(user, container, result);

            if (null != result && null != result.get(ID))
            {
                ListManager mgr = ListManager.get();
                String entityId = (String) result.get(ID);

                try
                {
                    // Remove prior attachment -- only includes columns which are modified in this update
                    for (ColumnInfo col : modifiedAttachmentColumns)
                    {
                        Object value = oldRow.get(col.getName());
                        if (null != value)
                        {
                            AttachmentService.get().deleteAttachment(new ListItemAttachmentParent(entityId, _list.getContainer()), value.toString(), user);
                        }
                    }

                    // Update attachments
                    if (!attachmentFiles.isEmpty())
                        AttachmentService.get().addAttachments(new ListItemAttachmentParent(entityId, _list.getContainer()), attachmentFiles, user);
                }
                catch (AttachmentService.DuplicateFilenameException | AttachmentService.FileTooLargeException e)
                {
                    // issues 21503, 28633: turn these into a validation exception to get a nicer error
                    throw new ValidationException(e.getMessage());
                }
                catch (IOException e)
                {
                    throw UnexpectedException.wrap(e);
                }
                finally
                {
                    for (AttachmentFile attachmentFile : attachmentFiles)
                    {
                        try { attachmentFile.closeInputStream(); } catch (IOException ignored) {}
                    }
                }

                String oldRecord = mgr.formatAuditItem(_list, user, oldRow);
                String newRecord = mgr.formatAuditItem(_list, user, result);

                // Audit
                mgr.addAuditEvent(_list, user, container, "An existing list record was modified", entityId, oldRecord, newRecord);
            }
        }

        return result;
    }

    // TODO: Consolidate with ColumnValidator and OntologyManager.validateProperty()
    private boolean validateProperty(DomainProperty prop, Object value, Map<String, Object> newRow, List<ValidationError> errors, ValidatorContext validatorCache)
    {
        //check for isRequired
        if (prop.isRequired())
        {
            // for mv indicator columns either an indicator or a field value is sufficient
            boolean hasMvIndicator = prop.isMvEnabled() && (value instanceof ObjectProperty && ((ObjectProperty)value).getMvIndicator() != null);
            if (!hasMvIndicator && (null == value || (value instanceof ObjectProperty && ((ObjectProperty)value).value() == null)))
            {
                if (newRow.containsKey(prop.getName()) && newRow.get(prop.getName()) == null)
                {
                    errors.add(new PropertyValidationError("The field '" + prop.getName() + "' is required.", prop.getName()));
                    return false;
                }
            }
        }

        if (null != value)
        {
            for (IPropertyValidator validator : prop.getValidators())
            {
                if (!validator.validate(prop.getPropertyDescriptor(), value, errors, validatorCache))
                    return false;
            }
        }

        return true;
    }

    private record ListRecord(Object key, String entityId) { }

    @Override
    public Map<String, Object> moveRows(
        User _user,
        Container container,
        Container targetContainer,
        List<Map<String, Object>> rows,
        BatchValidationException errors,
        @Nullable Map<Enum, Object> configParameters,
        @Nullable Map<String, Object> extraScriptContext
    ) throws InvalidKeyException, BatchValidationException, QueryUpdateServiceException
    {
        // Ensure the list is in scope for the target container
        if (null == ListService.get().getList(targetContainer, _list.getName(), true))
        {
            errors.addRowError(new ValidationException(String.format("List '%s' is not accessible from folder %s.", _list.getName(), targetContainer.getPath())));
            throw errors;
        }

        User user = getListUser(_user, container);
        Map<GUID, List<ListRecord>> containerRows = getListRowsForMoveRows(container, user, targetContainer, rows, errors);
        if (errors.hasErrors())
            throw errors;

        int fileAttachmentsMovedCount = 0;
        int listAuditEventsCreatedCount = 0;
        int listAuditEventsMovedCount = 0;
        int listRecordsCount = 0;
        int queryAuditEventsMovedCount = 0;

        if (containerRows.isEmpty())
            return moveRowsCounts(fileAttachmentsMovedCount, listAuditEventsCreatedCount, listAuditEventsMovedCount, listRecordsCount, queryAuditEventsMovedCount);

        AuditBehaviorType auditBehavior = configParameters != null ? (AuditBehaviorType) configParameters.get(DetailedAuditLogDataIterator.AuditConfigs.AuditBehavior) : null;
        String auditUserComment = configParameters != null ? (String) configParameters.get(DetailedAuditLogDataIterator.AuditConfigs.AuditUserComment) : null;
        boolean hasAttachmentProperties = _list.getDomainOrThrow()
                .getProperties()
                .stream()
                .anyMatch(prop -> PropertyType.ATTACHMENT.equals(prop.getPropertyType()));

        ListAuditProvider listAuditProvider = new ListAuditProvider();
        final int BATCH_SIZE = 5_000;
        boolean isAuditEnabled = auditBehavior != null && AuditBehaviorType.NONE != auditBehavior;

        try (DbScope.Transaction tx = getDbTable().getSchema().getScope().ensureTransaction())
        {
            if (isAuditEnabled && tx.getAuditEvent() == null)
            {
                TransactionAuditProvider.TransactionAuditEvent auditEvent = createTransactionAuditEvent(targetContainer, QueryService.AuditAction.UPDATE);
                auditEvent.updateCommentRowCount(containerRows.values().stream().mapToInt(List::size).sum());
                AbstractQueryUpdateService.addTransactionAuditEvent(tx, user, auditEvent);
            }

            List<ListAuditProvider.ListAuditEvent> listAuditEvents = new ArrayList<>();

            for (GUID containerId : containerRows.keySet())
            {
                Container sourceContainer = ContainerManager.getForId(containerId);
                if (sourceContainer == null)
                    throw new InvalidKeyException("Container '" + containerId + "' does not exist.");

                if (!sourceContainer.hasPermission(user, MoveEntitiesPermission.class))
                    throw new UnauthorizedException("You do not have permission to move list records out of '" + sourceContainer.getName() + "'.");

                TableInfo listTable = _list.getTable(user, sourceContainer);
                if (listTable == null)
                    throw new QueryUpdateServiceException(String.format("Failed to retrieve table for list '%s' in folder %s.", _list.getName(), sourceContainer.getPath()));

                List<ListRecord> records = containerRows.get(containerId);
                int numRecords = records.size();

                for (int start = 0; start < numRecords; start += BATCH_SIZE)
                {
                    int end = Math.min(start + BATCH_SIZE, numRecords);
                    List<ListRecord> batch = records.subList(start, end);
                    List<Object> rowPks = batch.stream().map(ListRecord::key).toList();

                    // Before trigger per batch
                    Map<String, Object> extraContext = Map.of("targetContainer", targetContainer, "keys", rowPks);
                    listTable.fireBatchTrigger(sourceContainer, user, TableInfo.TriggerType.MOVE, true, errors, extraContext);
                    if (errors.hasErrors())
                        throw errors;

                    listRecordsCount += Table.updateContainer(getDbTable(), _list.getKeyName(), rowPks, targetContainer, user, true);
                    if (errors.hasErrors())
                        throw errors;

                    if (hasAttachmentProperties)
                    {
                        fileAttachmentsMovedCount += moveAttachments(user, sourceContainer, targetContainer, batch, errors);
                        if (errors.hasErrors())
                            throw errors;
                    }

                    queryAuditEventsMovedCount += QueryService.get().moveAuditEvents(targetContainer, rowPks, ListQuerySchema.NAME, _list.getName());
                    listAuditEventsMovedCount += listAuditProvider.moveEvents(targetContainer, batch.stream().map(ListRecord::entityId).toList());

                    // Detailed audit events per row
                    if (AuditBehaviorType.DETAILED == listTable.getEffectiveAuditBehavior(auditBehavior))
                        listAuditEventsCreatedCount += addDetailedMoveAuditEvents(user, sourceContainer, targetContainer, batch);

                    // After trigger per batch
                    listTable.fireBatchTrigger(sourceContainer, user, TableInfo.TriggerType.MOVE, false, errors, extraContext);
                    if (errors.hasErrors())
                        throw errors;
                }

                // Create a summary audit event for the source container
                if (isAuditEnabled)
                {
                    String comment = String.format("Moved %s to %s", StringUtilsLabKey.pluralize(numRecords, "row"), targetContainer.getPath());
                    ListAuditProvider.ListAuditEvent event = new ListAuditProvider.ListAuditEvent(sourceContainer, comment, _list);
                    event.setUserComment(auditUserComment);
                    listAuditEvents.add(event);
                }

                // Create a summary audit event for the target container
                if (isAuditEnabled)
                {
                    String comment = String.format("Moved %s from %s", StringUtilsLabKey.pluralize(numRecords, "row"), sourceContainer.getPath());
                    ListAuditProvider.ListAuditEvent event = new ListAuditProvider.ListAuditEvent(targetContainer, comment, _list);
                    event.setUserComment(auditUserComment);
                    listAuditEvents.add(event);
                }
            }

            if (!listAuditEvents.isEmpty())
            {
                AuditLogService.get().addEvents(user, listAuditEvents, true);
                listAuditEventsCreatedCount += listAuditEvents.size();
            }

            tx.addCommitTask(() -> ListManager.get().indexList(_list), DbScope.CommitTaskOption.POSTCOMMIT);

            tx.commit();

            SimpleMetricsService.get().increment(ExperimentService.MODULE_NAME, "moveEntities", "list");
        }

        return moveRowsCounts(fileAttachmentsMovedCount, listAuditEventsCreatedCount, listAuditEventsMovedCount, listRecordsCount, queryAuditEventsMovedCount);
    }

    private Map<String, Object> moveRowsCounts(int fileAttachmentsMovedCount, int listAuditEventsCreated, int listAuditEventsMoved, int listRecords, int queryAuditEventsMoved)
    {
        return Map.of(
            "fileAttachmentsMoved", fileAttachmentsMovedCount,
            "listAuditEventsCreated", listAuditEventsCreated,
            "listAuditEventsMoved", listAuditEventsMoved,
            "listRecords", listRecords,
            "queryAuditEventsMoved", queryAuditEventsMoved
        );
    }

    private Map<GUID, List<ListRecord>> getListRowsForMoveRows(
        Container container,
        User user,
        Container targetContainer,
        List<Map<String, Object>> rows,
        BatchValidationException errors
    ) throws QueryUpdateServiceException
    {
        if (rows.isEmpty())
            return Collections.emptyMap();

        String keyName = _list.getKeyName();
        List<Object> keys = new ArrayList<>();
        for (var row : rows)
        {
            Object key = getField(row, keyName);
            if (key == null)
            {
                errors.addRowError(new ValidationException("Key field value required for moving list rows."));
                return Collections.emptyMap();
            }

            keys.add(getKeyFilterValue(key));
        }

        SimpleFilter filter = new SimpleFilter();
        FieldKey fieldKey = FieldKey.fromParts(keyName);
        filter.addInClause(fieldKey, keys);
        filter.addCondition(FieldKey.fromParts("Container"), targetContainer.getId(), CompareType.NEQ);

        // Request all rows without a container filter so that rows are more easily resolved across the list scope.
        // Read permissions are subsequently checked upon loading a row.
        TableInfo table = _list.getTable(user, container, ContainerFilter.getUnsafeEverythingFilter());
        if (table == null)
            throw new QueryUpdateServiceException(String.format("Failed to resolve table for list %s in %s", _list.getName(), container.getPath()));

        Map<GUID, List<ListRecord>> containerRows = new HashMap<>();
        try (var result = new TableSelector(table, PageFlowUtil.set(keyName, "Container", "EntityId"), filter, null).getResults())
        {
            while (result.next())
            {
                GUID containerId = new GUID(result.getString("Container"));
                if (!containerRows.containsKey(containerId))
                {
                    var c = ContainerManager.getForId(containerId);
                    if (c == null)
                        throw new QueryUpdateServiceException(String.format("Failed to resolve container for row in list %s in %s.", _list.getName(), container.getPath()));
                    else if (!c.hasPermission(user, ReadPermission.class))
                        throw new UnauthorizedException("You do not have permission to read list rows in all source containers.");
                }

                containerRows.computeIfAbsent(containerId, k -> new ArrayList<>());
                containerRows.get(containerId).add(new ListRecord(result.getObject(fieldKey), result.getString("EntityId")));
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }

        return containerRows;
    }

    private int moveAttachments(User user, Container sourceContainer, Container targetContainer, List<ListRecord> records, BatchValidationException errors)
    {
        List<AttachmentParent> parents = new ArrayList<>();
        for (ListRecord record : records)
            parents.add(new ListItemAttachmentParent(record.entityId, sourceContainer));

        int count = 0;
        try
        {
            count = AttachmentService.get().moveAttachments(targetContainer, parents, user);
        }
        catch (IOException e)
        {
            errors.addRowError(new ValidationException("Failed to move attachments when moving list rows. Error: " + e.getMessage()));
        }

        return count;
    }

    private int addDetailedMoveAuditEvents(User user, Container sourceContainer, Container targetContainer, List<ListRecord> records)
    {
        List<ListAuditProvider.ListAuditEvent> auditEvents = new ArrayList<>(records.size());
        String keyName = _list.getKeyName();
        String sourcePath = sourceContainer.getPath();
        String targetPath = targetContainer.getPath();

        for (ListRecord record : records)
        {
            ListAuditProvider.ListAuditEvent event = new ListAuditProvider.ListAuditEvent(targetContainer, "An existing list record was moved", _list);
            event.setListItemEntityId(record.entityId);
            event.setOldRecordMap(AbstractAuditTypeProvider.encodeForDataMap(CaseInsensitiveHashMap.of("Folder", sourcePath, keyName, record.key.toString())));
            event.setNewRecordMap(AbstractAuditTypeProvider.encodeForDataMap(CaseInsensitiveHashMap.of("Folder", targetPath, keyName, record.key.toString())));
            auditEvents.add(event);
        }

        AuditLogService.get().addEvents(user, auditEvents, true);

        return auditEvents.size();
    }

    @Override
    protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRowMap) throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        if (!_list.isVisible(user))
            throw new UnauthorizedException("You do not have permission to delete data from this table.");

        Map<String, Object> result = super.deleteRow(getListUser(user, container), container, oldRowMap);

        if (null != result)
        {
            String entityId = (String) result.get(ID);

            if (null != entityId)
            {
                ListManager mgr = ListManager.get();
                String deletedRecord = mgr.formatAuditItem(_list, user, result);

                // Audit
                mgr.addAuditEvent(_list, user, container, "An existing list record was deleted", entityId, deletedRecord, null);

                // Remove attachments
                if (hasAttachmentProperties())
                    AttachmentService.get().deleteAttachments(new ListItemAttachmentParent(entityId, container));

                // Clean up Search indexer
                if (!result.isEmpty())
                    mgr.deleteItemIndex(_list, entityId);
            }
        }

        return result;
    }


    // Deletes attachments & discussions, and removes list documents from full-text search index.
    public void deleteRelatedListData(final User user, final Container container)
    {
        // Unindex all item docs and the entire list doc
        ListManager.get().deleteIndexedList(_list);

        // Delete attachments and discussions associated with a list in batches of 1,000
        new TableSelector(getDbTable(), Collections.singleton("entityId"), SimpleFilter.createContainerFilter(container), null).forEachBatch(String.class, 1000, new ForEachBatchBlock<>()
        {
            @Override
            public boolean accept(String entityId)
            {
                return null != entityId;
            }

            @Override
            public void exec(List<String> entityIds)
            {
                // delete the related list data for this block
                deleteRelatedListData(user, container, entityIds);
            }
        });
    }

    // delete the related list data for this block of entityIds
    private void deleteRelatedListData(User user, Container container, List<String> entityIds)
    {
        // Build up set of entityIds and AttachmentParents
        List<AttachmentParent> attachmentParents = new ArrayList<>();

        // Delete Attachments
        if (hasAttachmentProperties())
        {
            for (String entityId : entityIds)
            {
                attachmentParents.add(new ListItemAttachmentParent(entityId, container));
            }
            AttachmentService.get().deleteAttachments(attachmentParents);
        }
    }

    @Override
    protected int truncateRows(User user, Container container) throws QueryUpdateServiceException, SQLException
    {
        int result;
        try (DbScope.Transaction transaction = getDbTable().getSchema().getScope().ensureTransaction())
        {
            deleteRelatedListData(user, container);
            result = super.truncateRows(getListUser(user, container), container);
            transaction.addCommitTask(() -> ListManager.get().addAuditEvent(_list, user, "Deleted " + result + " rows from list."), DbScope.CommitTaskOption.POSTCOMMIT);
            transaction.commit();
        }

        return result;
    }

    @Nullable
    public SimpleFilter getKeyFilter(Map<String, Object> map) throws InvalidKeyException
    {
        String keyName = _list.getKeyName();
        Object key = getField(map, keyName);

        if (null == key)
        {
            // Auto-increment lists might not provide a key so allow them to pass through
            if (ListDefinition.KeyType.AutoIncrementInteger.equals(_list.getKeyType()))
                return null;
            throw new InvalidKeyException("No " + keyName + " provided for list \"" + _list.getName() + "\"");
        }

        return new SimpleFilter(FieldKey.fromParts(keyName), getKeyFilterValue(key));
    }

    @NotNull
    private Object getKeyFilterValue(@NotNull Object key)
    {
        ListDefinition.KeyType type = _list.getKeyType();

        // Check the type of the list to ensure proper casting of the key type
        if (ListDefinition.KeyType.Integer.equals(type) || ListDefinition.KeyType.AutoIncrementInteger.equals(type))
            return isIntegral(key) ? key : Integer.valueOf(key.toString());

        return key.toString();
    }

    @Nullable
    private Object getField(Map<String, Object> map, String key)
    {
        /* TODO: this is very strange, we have a TableInfo we should be using its ColumnInfo objects to figure out aliases, we don't need to guess */
        Object value = map.get(key);

        if (null == value)
            value = map.get(key + "_");

        if (null == value)
            value = map.get(getDbTable().getSqlDialect().legalNameFromName(key));

        return value;
    }

    /**
     * Delegate class to generate an AttachmentParent
     */
    public static class ListItemAttachmentParentFactory implements AttachmentParentFactory
    {
        @Override
        public AttachmentParent generateAttachmentParent(String entityId, Container c)
        {
            return new ListItemAttachmentParent(entityId, c);
        }
    }

    /**
     * Get Domain from list definition, unless null then get from super
     */
    @Override
    protected Domain getDomain()
    {
        return _list != null?
                _list.getDomain() :
                super.getDomain();
    }
}
