/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
package org.labkey.api.query;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.labkey.api.assay.AssayFileWriter;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentParentFactory;
import org.labkey.api.attachments.SpringAttachmentFile;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.TransactionAuditProvider;
import org.labkey.api.collections.ArrayListMap;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.DbSequenceManager;
import org.labkey.api.data.ImportAliasable;
import org.labkey.api.data.MultiValuedForeignKey;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.UpdateableTableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.dataiterator.AttachmentDataIterator;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.DataIteratorUtil;
import org.labkey.api.dataiterator.DetailedAuditLogDataIterator;
import org.labkey.api.dataiterator.ExistingRecordDataIterator;
import org.labkey.api.dataiterator.ListofMapsDataIterator;
import org.labkey.api.dataiterator.LoggingDataIterator;
import org.labkey.api.dataiterator.MapDataIterator;
import org.labkey.api.dataiterator.Pump;
import org.labkey.api.dataiterator.StandardDataIteratorBuilder;
import org.labkey.api.dataiterator.TriggerDataBuilderHelper;
import org.labkey.api.dataiterator.WrapperDataIterator;
import org.labkey.api.exceptions.OptimisticConflictException;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.MvColumn;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.gwt.client.AuditBehaviorType;
import org.labkey.api.ontology.OntologyService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.test.TestWhen;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.writer.VirtualFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static org.labkey.api.audit.TransactionAuditProvider.DB_SEQUENCE_NAME;
import static org.labkey.api.dataiterator.DetailedAuditLogDataIterator.AuditConfigs.AuditBehavior;
import static org.labkey.api.dataiterator.DetailedAuditLogDataIterator.AuditConfigs.AuditUserComment;

public abstract class AbstractQueryUpdateService implements QueryUpdateService
{
    private TableInfo _queryTable = null;
    private boolean _bulkLoad = false;
    private CaseInsensitiveHashMap<ColumnInfo> _columnImportMap = null;
    private VirtualFile _att = null;

    /* AbstractQueryUpdateService is generally responsible for some shared functionality
     *   - triggers
     *   - coercion/validation
     *   - detailed logging
     *   - attachements
     *
     *  If a subclass wants to disable some of these features (w/o subclassing), put flags here...
    */
    protected boolean _enableExistingRecordsDataIterator = true;

    protected AbstractQueryUpdateService(TableInfo queryTable)
    {
        if (queryTable == null)
            throw new IllegalArgumentException();
        _queryTable = queryTable;
    }

    protected TableInfo getQueryTable()
    {
        return _queryTable;
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, Class<? extends Permission> acl)
    {
        return getQueryTable().hasPermission(user, acl);
    }

    protected abstract Map<String, Object> getRow(User user, Container container, Map<String, Object> keys)
            throws InvalidKeyException, QueryUpdateServiceException, SQLException;

    @Override
    public List<Map<String, Object>> getRows(User user, Container container, List<Map<String, Object>> keys)
            throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        if (!hasPermission(user, ReadPermission.class))
            throw new UnauthorizedException("You do not have permission to read data from this table.");

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> rowKeys : keys)
        {
            Map<String, Object> row = getRow(user, container, rowKeys);
            if (row != null)
                result.add(row);
        }
        return result;
    }

    @Override
    public Map<Integer, Map<String, Object>> getExistingRows(User user, Container container, Map<Integer, Map<String, Object>> keys)
            throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        if (!hasPermission(user, ReadPermission.class))
            throw new UnauthorizedException("You do not have permission to read data from this table.");

        Map<Integer, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map.Entry<Integer, Map<String, Object>> key : keys.entrySet())
        {
            Map<String, Object> row = getRow(user, container, key.getValue());
            if (row != null)
                result.put(key.getKey(), row);
        }
        return result;
    }

    public static TransactionAuditProvider.TransactionAuditEvent createTransactionAuditEvent(Container container, QueryService.AuditAction auditAction)
    {
        long auditId = DbSequenceManager.get(ContainerManager.getRoot(), DB_SEQUENCE_NAME).next();
        return new TransactionAuditProvider.TransactionAuditEvent(container.getId(), auditAction, auditId);
    }

    public static void addTransactionAuditEvent(DbScope.Transaction transaction,  User user, TransactionAuditProvider.TransactionAuditEvent auditEvent)
    {
        UserSchema schema = AuditLogService.getAuditLogSchema(user, ContainerManager.getRoot());

        if (schema != null)
        {
            // This is a little hack to ensure that the audit table has actually been created and gets put into the table cache by the time the
            // pre-commit task is executed.  Otherwise, since the creation of the table happens while within the commit for the
            // outermost transaction, it looks like there is a close that hasn't happened when trying to commit the transaction for creating the
            // table.
            schema.getTable(auditEvent.getEventType(), false);

            transaction.addCommitTask(() -> AuditLogService.get().addEvent(user, auditEvent), DbScope.CommitTaskOption.PRECOMMIT);

            transaction.setAuditId(auditEvent.getRowId());
        }
    }

    protected final DataIteratorContext getDataIteratorContext(BatchValidationException errors, InsertOption forImport, Map<Enum, Object> configParameters)
    {
        if (null == errors)
            errors = new BatchValidationException();
        DataIteratorContext context = new DataIteratorContext(errors);
        context.setInsertOption(forImport);
        context.setConfigParameters(configParameters);
        configureDataIteratorContext(context);
        return context;
    }

    /**
     *  if QUS want to use something other than PK's to select existing rows for merge it can override this method
     * Used only for generating  ExistingRecordDataIterator at the moment
     */
    protected Set<String> getSelectKeys(DataIteratorContext context)
    {
        if (!context.getAlternateKeys().isEmpty())
            return context.getAlternateKeys();
        return null;
    }

    /*
     * construct the core DataIterator transformation pipeline for this table, may be just StandardDataIteratorBuilder.
     * does NOT handle triggers or the insert/update iterator.
     */
    public DataIteratorBuilder createImportDIB(User user, Container container, DataIteratorBuilder data, DataIteratorContext context)
    {
        DataIteratorBuilder dib = StandardDataIteratorBuilder.forInsert(getQueryTable(), data, container, user);
        if (_enableExistingRecordsDataIterator)
        {
            // some tables need to generate PK's, so they need to add ExistingRecordDataIterator in persistRows() (after generating PK, before inserting)
            dib = ExistingRecordDataIterator.createBuilder(dib, getQueryTable(), getSelectKeys(context));
        }
        dib = ((UpdateableTableInfo)getQueryTable()).persistRows(dib, context);
        dib = AttachmentDataIterator.getAttachmentDataIteratorBuilder(getQueryTable(), dib, user, context.getInsertOption().batch ? getAttachmentDirectory() : null, container, getAttachmentParentFactory());
        dib = DetailedAuditLogDataIterator.getDataIteratorBuilder(getQueryTable(), dib, context.getInsertOption() == InsertOption.MERGE ? QueryService.AuditAction.MERGE : QueryService.AuditAction.INSERT, user, container);
        return dib;
    }


    /**
     * Implementation to use insertRows() while we migrate to using DIB for all code paths
     *
     * DataIterator should/must use same error collection as passed in
     */
    @Deprecated
    protected int _importRowsUsingInsertRows(User user, Container container, DataIterator rows, BatchValidationException errors, Map<String, Object> extraScriptContext)
    {
        MapDataIterator mapIterator = DataIteratorUtil.wrapMap(rows, true);
        List<Map<String, Object>> list = new ArrayList<>();
        List<Map<String, Object>> ret;
        Exception rowException;

        try
        {
            while (mapIterator.next())
                list.add(mapIterator.getMap());
            ret = insertRows(user, container, list, errors, null, extraScriptContext);
            if (errors.hasErrors())
                return 0;
            return ret.size();
        }
        catch (BatchValidationException x)
        {
            assert x == errors;
            assert x.hasErrors();
            return 0;
        }
        catch (QueryUpdateServiceException | DuplicateKeyException | SQLException x)
        {
            rowException = x;
        }
        finally
        {
            DataIteratorUtil.closeQuietly(mapIterator);
        }
        errors.addRowError(new ValidationException(rowException.getMessage()));
        return 0;
    }


    protected int _importRowsUsingDIB(User user, Container container, DataIteratorBuilder in, @Nullable final ArrayList<Map<String, Object>> outputRows, DataIteratorContext context, @Nullable Map<String, Object> extraScriptContext)
    {
        if (!hasPermission(user, InsertPermission.class))
            throw new UnauthorizedException("You do not have permission to insert data into this table.");

        context.getErrors().setExtraContext(extraScriptContext);
        if (extraScriptContext != null)
        {
            context.setDataSource((String) extraScriptContext.get(DataIteratorUtil.DATA_SOURCE));
        }

        boolean skipTriggers = context.getConfigParameterBoolean(ConfigParameters.SkipTriggers);
        boolean hasTableScript = hasTableScript(container);
        TriggerDataBuilderHelper helper = new TriggerDataBuilderHelper(getQueryTable(), container, user, extraScriptContext, context.getInsertOption().useImportAliases);
        if (!skipTriggers)
        {
            in = preTriggerDataIterator(in, context);
            if (hasTableScript)
                in = helper.before(in);
        }
        DataIteratorBuilder importDIB = createImportDIB(user, container, in, context);
        DataIteratorBuilder out = importDIB;

        if (!skipTriggers)
        {
            if (hasTableScript)
                out = helper.after(importDIB);

            out = postTriggerDataIterator(out, context);
        }

        if (hasTableScript)
        {
            context.setFailFast(false);
            context.setMaxRowErrors(Math.max(context.getMaxRowErrors(),1000));
        }
        int count = _pump(out, outputRows, context);

        if (context.getErrors().hasErrors())
            return 0;
        else
        {
            AuditBehaviorType auditType = (AuditBehaviorType) context.getConfigParameter(DetailedAuditLogDataIterator.AuditConfigs.AuditBehavior);
            getQueryTable().getAuditHandler(auditType).addSummaryAuditEvent(user, container, getQueryTable(), QueryService.AuditAction.INSERT, count, auditType);
            return count;
        }
    }

    protected DataIteratorBuilder preTriggerDataIterator(DataIteratorBuilder in, DataIteratorContext context)
    {
        return in;
    }

    protected DataIteratorBuilder postTriggerDataIterator(DataIteratorBuilder out, DataIteratorContext context)
    {
        return out;
    }


    /** this is extracted so subclasses can add wrap */
    protected int _pump(DataIteratorBuilder etl, final @Nullable ArrayList<Map<String, Object>> rows,  DataIteratorContext context)
    {
        DataIterator it = etl.getDataIterator(context);

        try
        {
            if (null != rows)
            {
                MapDataIterator maps = DataIteratorUtil.wrapMap(it, false);
                it = new WrapperDataIterator(maps)
                {
                    @Override
                    public boolean next() throws BatchValidationException
                    {
                        boolean ret = super.next();
                        if (ret)
                            rows.add(((MapDataIterator)_delegate).getMap());
                        return ret;
                    }
                };
            }

            Pump pump = new Pump(it, context);
            pump.run();

            return pump.getRowCount();
        }
        finally
        {
            DataIteratorUtil.closeQuietly(it);
        }
    }

    /* can be used for simple book keeping tasks, per row processing belongs in a data iterator */
    protected void afterInsertUpdate(int count, BatchValidationException errors)
    {}

    @Override
    public int loadRows(User user, Container container, DataIteratorBuilder rows, DataIteratorContext context, @Nullable Map<String, Object> extraScriptContext)
    {
        configureDataIteratorContext(context);
        int count = _importRowsUsingDIB(user, container, rows, null, context, extraScriptContext);
        afterInsertUpdate(count, context.getErrors());
        return count;
    }

    @Override
    public int importRows(User user, Container container, DataIteratorBuilder rows, BatchValidationException errors, Map<Enum, Object> configParameters, @Nullable Map<String, Object> extraScriptContext)
    {
        DataIteratorContext context = getDataIteratorContext(errors, InsertOption.IMPORT, configParameters);
        int count = _importRowsUsingInsertRows(user, container, rows.getDataIterator(context), errors, extraScriptContext);
        afterInsertUpdate(count, errors);
        return count;
    }

    @Override
    public int mergeRows(User user, Container container, DataIteratorBuilder rows, BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext)
    {
        throw new UnsupportedOperationException("merge is not supported for all tables");
    }

    private boolean hasTableScript(Container container)
    {
        return getQueryTable().hasTriggers(container);
    }


    protected Map<String, Object> insertRow(User user, Container container, Map<String, Object> row)
        throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        throw new UnsupportedOperationException("Not implemented by this QueryUpdateService");
    }


    protected @Nullable List<Map<String, Object>> _insertRowsUsingDIB(User user, Container container, List<Map<String, Object>> rows,
                                                      DataIteratorContext context, @Nullable Map<String, Object> extraScriptContext)
    {
        if (!hasPermission(user, InsertPermission.class))
            throw new UnauthorizedException("You do not have permission to insert data into this table.");

        DataIterator di = _toDataIterator(getClass().getSimpleName() + ".insertRows()", rows);
        DataIteratorBuilder dib = new DataIteratorBuilder.Wrapper(di);
        ArrayList<Map<String,Object>> outputRows = new ArrayList<>();
        int count = _importRowsUsingDIB(user, container, dib, outputRows, context, extraScriptContext);
        afterInsertUpdate(count, context.getErrors());

        if (context.getErrors().hasErrors())
            return null;

        return outputRows;
    }


    protected DataIterator _toDataIterator(String debugName, List<Map<String, Object>> rows)
    {
        // TODO probably can't assume all rows have all columns
        // TODO can we assume that all rows refer to columns consistently? (not PTID and MouseId for the same column)
        // TODO optimize ArrayListMap?
        Set<String> colNames;

        if (rows.size() > 0 && rows.get(0) instanceof ArrayListMap)
        {
            colNames = ((ArrayListMap)rows.get(0)).getFindMap().keySet();
        }
        else
        {
            // Preserve casing by using wrapped CaseInsensitiveHashMap instead of CaseInsensitiveHashSet
            colNames = Sets.newCaseInsensitiveHashSet();
            for (Map<String,Object> row : rows)
                colNames.addAll(row.keySet());
        }

        ListofMapsDataIterator maps = new ListofMapsDataIterator(colNames, rows);
        maps.setDebugName(debugName);
        return LoggingDataIterator.wrap((DataIterator)maps);
    }


    /** @deprecated switch to using DIB based method */
    @Deprecated
    protected List<Map<String, Object>> _insertRowsUsingInsertRow(User user, Container container, List<Map<String, Object>> rows, BatchValidationException errors, Map<String, Object> extraScriptContext)
            throws DuplicateKeyException, BatchValidationException, QueryUpdateServiceException, SQLException
    {
        if (!hasPermission(user, InsertPermission.class))
            throw new UnauthorizedException("You do not have permission to insert data into this table.");

        boolean hasTableScript = hasTableScript(container);

        errors.setExtraContext(extraScriptContext);
        if (hasTableScript)
            getQueryTable().fireBatchTrigger(container, user, TableInfo.TriggerType.INSERT, true, errors, extraScriptContext);

        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++)
        {
            Map<String, Object> row = rows.get(i);
            row = normalizeColumnNames(row);
            try
            {
                row = coerceTypes(row);
                if (hasTableScript)
                {
                    getQueryTable().fireRowTrigger(container, user, TableInfo.TriggerType.INSERT, true, i, row, null, extraScriptContext);
                }
                row = insertRow(user, container, row);
                if (row == null)
                    continue;

                if (hasTableScript)
                    getQueryTable().fireRowTrigger(container, user, TableInfo.TriggerType.INSERT, false, i, row, null, extraScriptContext);
                result.add(row);
            }
            catch (SQLException sqlx)
            {
                if (StringUtils.startsWith(sqlx.getSQLState(), "22") || RuntimeSQLException.isConstraintException(sqlx))
                {
                    ValidationException vex = new ValidationException(sqlx.getMessage());
                    vex.fillIn(getQueryTable().getPublicSchemaName(), getQueryTable().getName(), row, i+1);
                    errors.addRowError(vex);
                }
                else if (SqlDialect.isTransactionException(sqlx) && errors.hasErrors())
                {
                    // if we already have some errors, just break
                    break;
                }
                else
                {
                    throw sqlx;
                }
            }
            catch (ValidationException vex)
            {
                errors.addRowError(vex.fillIn(getQueryTable().getPublicSchemaName(), getQueryTable().getName(), row, i));
            }
            catch (RuntimeValidationException rvex)
            {
                ValidationException vex = rvex.getValidationException();
                errors.addRowError(vex.fillIn(getQueryTable().getPublicSchemaName(), getQueryTable().getName(), row, i));
            }
        }

        if (hasTableScript)
            getQueryTable().fireBatchTrigger(container, user, TableInfo.TriggerType.INSERT, false, errors, extraScriptContext);

        addAuditEvent(user, container, QueryService.AuditAction.INSERT, null, result, null);

        return result;
    }

    protected void addAuditEvent(User user, Container container, QueryService.AuditAction auditAction, @Nullable Map<Enum, Object> configParameters, @Nullable List<Map<String, Object>> rows, @Nullable List<Map<String, Object>> existingRows)
    {
        if (!isBulkLoad())
        {
            AuditBehaviorType auditBehavior = configParameters != null ? (AuditBehaviorType) configParameters.get(AuditBehavior) : null;
            String userComment = configParameters == null ? null : (String) configParameters.get(AuditUserComment);
            getQueryTable().getAuditHandler(auditBehavior)
                    .addAuditEvent(user, container, getQueryTable(), auditBehavior, userComment, auditAction, rows, existingRows);
        }
    }

    private Map<String, Object> normalizeColumnNames(Map<String, Object> row)
    {
        if(_columnImportMap == null)
        {
            _columnImportMap = (CaseInsensitiveHashMap<ColumnInfo>)ImportAliasable.Helper.createImportMap(getQueryTable().getColumns(), false);
        }

        Map<String, Object> newRow = new CaseInsensitiveHashMap<>();
        CaseInsensitiveHashSet columns = new CaseInsensitiveHashSet();
        columns.addAll(row.keySet());

        String newName;
        for(String key : row.keySet())
        {
            if(_columnImportMap.containsKey(key))
            {
                //it is possible for a normalized name to conflict with an existing property.  if so, defer to the original
                newName = _columnImportMap.get(key).getName();
                if(!columns.contains(newName)){
                    newRow.put(newName, row.get(key));
                    continue;
                }
            }
            newRow.put(key, row.get(key));
        }

        return newRow;
    }

    @Override
    public List<Map<String, Object>> insertRows(User user, Container container, List<Map<String, Object>> rows, BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext)
            throws DuplicateKeyException, QueryUpdateServiceException, SQLException
    {
        try
        {
            List<Map<String,Object>> ret = _insertRowsUsingInsertRow(user, container, rows, errors, extraScriptContext);
            afterInsertUpdate(null==ret?0:ret.size(), errors);
            if (errors.hasErrors())
                return null;
            return ret;
        }
        catch (BatchValidationException x)
        {
            assert x == errors;
            assert x.hasErrors();
        }
        return null;
    }


    /** Attempt to make the passed in types match the expected types so the script doesn't have to do the conversion */
    @Deprecated
    protected Map<String, Object> coerceTypes(Map<String, Object> row)
    {
        Map<String, Object> result = new CaseInsensitiveHashMap<>(row.size());
        Map<String, ColumnInfo> columnMap = ImportAliasable.Helper.createImportMap(_queryTable.getColumns(), true);
        for (Map.Entry<String, Object> entry : row.entrySet())
        {
            ColumnInfo col = columnMap.get(entry.getKey());

            Object value = entry.getValue();
            if (col != null && value != null &&
                    !col.getJavaObjectClass().isInstance(value) &&
                    !(value instanceof AttachmentFile) &&
                    !(value instanceof MultipartFile) &&
                    !(value instanceof String[]) &&
                    !(col.getFk() instanceof MultiValuedForeignKey))
            {
                try
                {
                    value = ConvertUtils.convert(value.toString(), col.getJavaObjectClass());
                }
                catch (ConversionException e)
                {
                    // That's OK, the transformation script may be able to fix up the value before it gets inserted
                }
            }
            result.put(entry.getKey(), value);
        }
        return result;
    }


    protected abstract Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, @NotNull Map<String, Object> oldRow)
            throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException;


    protected boolean firstUpdateRow = true;
    Function<Map<String,Object>,Map<String,Object>> updateTransform = Function.identity();

    /* Do standard AQUS stuff here, then call the subclass specific implementation of updateRow() */
    final protected Map<String, Object> updateOneRow(User user, Container container, Map<String, Object> row, @NotNull Map<String, Object> oldRow)
            throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        if (firstUpdateRow)
        {
            firstUpdateRow = false;
            if (null != OntologyService.get())
            {
                var t = OntologyService.get().getConceptUpdateHandler(_queryTable);
                if (null != t)
                    updateTransform = t;
            }
        }
        row = updateTransform.apply(row);
        return updateRow(user, container, row, oldRow);
    }


    @Override
    public List<Map<String, Object>> updateRows(User user, Container container, List<Map<String, Object>> rows, List<Map<String, Object>> oldKeys, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext)
            throws InvalidKeyException, BatchValidationException, QueryUpdateServiceException, SQLException
    {
        if (!hasPermission(user, UpdatePermission.class))
            throw new UnauthorizedException("You do not have permission to update data in this table.");

        if (oldKeys != null && rows.size() != oldKeys.size())
            throw new IllegalArgumentException("rows and oldKeys are required to be the same length, but were " + rows.size() + " and " + oldKeys + " in length, respectively");

        BatchValidationException errors = new BatchValidationException();
        errors.setExtraContext(extraScriptContext);
        getQueryTable().fireBatchTrigger(container, user, TableInfo.TriggerType.UPDATE, true, errors, extraScriptContext);

        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        List<Map<String, Object>> oldRows = new ArrayList<>(rows.size());
        // TODO: Support update/delete without selecting the existing row -- unfortunately, we currently get the existing row to check its container matches the incoming container
        boolean streaming = false; //_queryTable.canStreamTriggers(container) && _queryTable.getAuditBehavior() != AuditBehaviorType.NONE;

        for (int i = 0; i < rows.size(); i++)
        {
            Map<String, Object> row = rows.get(i);
            row = coerceTypes(row);
            try
            {
                Map<String, Object> oldKey = oldKeys == null ? row : oldKeys.get(i);
                Map<String, Object> oldRow = null;
                if (!streaming)
                {
                    oldRow = getRow(user, container, oldKey);
                    if (oldRow == null)
                        throw new NotFoundException("The existing row was not found.");
                }

                getQueryTable().fireRowTrigger(container, user, TableInfo.TriggerType.UPDATE, true, i, row, oldRow, extraScriptContext);
                Map<String, Object> updatedRow = updateOneRow(user, container, row, oldRow);
                if (!streaming && updatedRow == null)
                    continue;

                getQueryTable().fireRowTrigger(container, user, TableInfo.TriggerType.UPDATE, false, i, updatedRow, oldRow, extraScriptContext);
                if (!streaming)
                {
                    result.add(updatedRow);
                    oldRows.add(oldRow);
                }
            }
            catch (ValidationException vex)
            {
                errors.addRowError(vex.fillIn(getQueryTable().getPublicSchemaName(), getQueryTable().getName(), row, i));
            }
            catch (RuntimeValidationException rvex)
            {
                ValidationException vex = rvex.getValidationException();
                errors.addRowError(vex.fillIn(getQueryTable().getPublicSchemaName(), getQueryTable().getName(), row, i));
            }
            catch (OptimisticConflictException e)
            {
                errors.addRowError(new ValidationException("Unable to update. Row may have been deleted."));
            }
        }

        // Fire triggers, if any, and also throw if there are any errors
        getQueryTable().fireBatchTrigger(container, user, TableInfo.TriggerType.UPDATE, false, errors, extraScriptContext);
        afterInsertUpdate(null==result?0:result.size(), errors);

        if (errors.hasErrors())
            throw errors;

        addAuditEvent(user, container, QueryService.AuditAction.UPDATE, configParameters, result, oldRows);

        return result;
    }

    protected abstract Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRow)
            throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException;
    
    @Override
    public List<Map<String, Object>> deleteRows(User user, Container container, List<Map<String, Object>> keys, @Nullable Map<Enum, Object> configParameters, @Nullable Map<String, Object> extraScriptContext)
            throws InvalidKeyException, BatchValidationException, QueryUpdateServiceException, SQLException
    {
        if (!hasPermission(user, DeletePermission.class))
            throw new UnauthorizedException("You do not have permission to delete data from this table.");

        BatchValidationException errors = new BatchValidationException();
        errors.setExtraContext(extraScriptContext);
        getQueryTable().fireBatchTrigger(container, user, TableInfo.TriggerType.DELETE, true, errors, extraScriptContext);

        // TODO: Support update/delete without selecting the existing row -- unfortunately, we currently get the existing row to check its container matches the incoming container
        boolean streaming = false; //_queryTable.canStreamTriggers(container) && _queryTable.getAuditBehavior() != AuditBehaviorType.NONE;

        List<Map<String, Object>> result = new ArrayList<>(keys.size());
        for (int i = 0; i < keys.size(); i++)
        {
            Map<String, Object> key = keys.get(i);
            try
            {
                Map<String, Object> oldRow = null;
                if (!streaming)
                {
                    oldRow = getRow(user, container, key);
                    // if row doesn't exist, bail early
                    if (oldRow == null)
                        continue;
                }

                getQueryTable().fireRowTrigger(container, user, TableInfo.TriggerType.DELETE, true, i, null, oldRow, extraScriptContext);
                Map<String, Object> updatedRow = deleteRow(user, container, oldRow);
                if (!streaming && updatedRow == null)
                    continue;

                getQueryTable().fireRowTrigger(container, user, TableInfo.TriggerType.DELETE, false, i, null, updatedRow, extraScriptContext);
                result.add(updatedRow);
            }
            catch (InvalidKeyException ex)
            {
                ValidationException vex = new ValidationException(ex.getMessage());
                errors.addRowError(vex.fillIn(getQueryTable().getPublicSchemaName(), getQueryTable().getName(), key, i));
            }
            catch (ValidationException vex)
            {
                errors.addRowError(vex.fillIn(getQueryTable().getPublicSchemaName(), getQueryTable().getName(), key, i));
            }
            catch (RuntimeValidationException rvex)
            {
                ValidationException vex = rvex.getValidationException();
                errors.addRowError(vex.fillIn(getQueryTable().getPublicSchemaName(), getQueryTable().getName(), key, i));
            }
        }

        // Fire triggers, if any, and also throw if there are any errors
        getQueryTable().fireBatchTrigger(container, user, TableInfo.TriggerType.DELETE, false, errors, extraScriptContext);

        addAuditEvent(user, container,  QueryService.AuditAction.DELETE, configParameters, result, null);

        return result;
    }

    protected int truncateRows(User user, Container container)
            throws QueryUpdateServiceException, SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int truncateRows(User user, Container container, @Nullable Map<Enum, Object> configParameters, @Nullable Map<String, Object> extraScriptContext)
            throws BatchValidationException, QueryUpdateServiceException, SQLException
    {
        if (!container.hasPermission(user,AdminPermission.class) && !hasPermission(user, DeletePermission.class))
            throw new UnauthorizedException("You do not have permission to truncate this table.");

        BatchValidationException errors = new BatchValidationException();
        errors.setExtraContext(extraScriptContext);
        getQueryTable().fireBatchTrigger(container, user, TableInfo.TriggerType.TRUNCATE, true, errors, extraScriptContext);

        int result = truncateRows(user, container);

        getQueryTable().fireBatchTrigger(container, user, TableInfo.TriggerType.TRUNCATE, false, errors, extraScriptContext);
        addAuditEvent(user, container,  QueryService.AuditAction.TRUNCATE, configParameters, null, null);

        return result;
    }

    @Override
    public void setBulkLoad(boolean bulkLoad)
    {
        _bulkLoad = bulkLoad;
    }

    @Override
    public boolean isBulkLoad()
    {
        return _bulkLoad;
    }

    /**
     * Save uploaded file to dirName directory under file or pipeline root.
     */
    public static Object saveFile(Container container, String name, Object value, @Nullable String dirName) throws ValidationException, QueryUpdateServiceException
    {
        if (value instanceof MultipartFile)
        {
            try
            {
                // Once we've found one, write it to disk and replace the row's value with just the File reference to it
                MultipartFile multipartFile = (MultipartFile)value;
                if (multipartFile.isEmpty())
                {
                    throw new ValidationException("File " + multipartFile.getOriginalFilename() + " for field " + name + " has no content");
                }
                File dir = AssayFileWriter.ensureUploadDirectory(container, dirName);
                File file = AssayFileWriter.findUniqueFileName(multipartFile.getOriginalFilename(), dir);
                file = checkFileUnderRoot(container, file);
                multipartFile.transferTo(file);
                return file;
            }
            catch (ExperimentException | IOException e)
            {
                throw new QueryUpdateServiceException(e);
            }
        }
        else if (value instanceof SpringAttachmentFile)
        {
            SpringAttachmentFile saf = (SpringAttachmentFile)value;
            try
            {
                File dir = AssayFileWriter.ensureUploadDirectory(container, dirName);
                File file = AssayFileWriter.findUniqueFileName(saf.getFilename(), dir);
                file = checkFileUnderRoot(container, file);
                saf.saveTo(file);
                return file;
            }
            catch (IOException | ExperimentException e)
            {
                throw new QueryUpdateServiceException(e);
            }
        }
        else
        {
            return value;
        }
    }

    // For security reasons, make sure the user hasn't tried to reference a file that's not under
    // the pipeline root. Otherwise, they could get access to any file on the server
    static File checkFileUnderRoot(Container container, File file) throws ExperimentException
    {
        PipeRoot root = PipelineService.get().findPipelineRoot(container);
        if (root == null)
            throw new ExperimentException("Pipeline root not available in container " + container.getPath());

        if (!root.isUnderRoot(file))
        {
            File resolved = root.resolvePath(file.toString());
            if (resolved == null)
                throw new ExperimentException("Cannot reference file '" + file + "' from " + container.getPath());

            // File column values are stored as the absolute resolved path.
            file = resolved;
        }

        return file;
    }

    /**
     * Is used by the AttachmentDataIterator to point to the location of the serialized
     * attachment files.
     */
    public void setAttachmentDirectory(VirtualFile att)
    {
        _att = att;
    }

    @Nullable
    protected VirtualFile getAttachmentDirectory()
    {
        return _att;
    }

    /**
     * QUS instances that allow import of attachments through the AttachmentDataIterator should furnish a factory
     * implementation in order to resolve the attachment parent on incoming attachment files.
     * @return
     */
    @Nullable
    protected AttachmentParentFactory getAttachmentParentFactory()
    {
        return null;
    }

    /** Translate between the column name that query is exposing to the column name that actually lives in the database */
    protected static void aliasColumns(Map<String, String> columnMapping, Map<String, Object> row)
    {
        for (Map.Entry<String, String> entry : columnMapping.entrySet())
        {
            if (row.containsKey(entry.getValue()) && !row.containsKey(entry.getKey()))
            {
                row.put(entry.getKey(), row.get(entry.getValue()));
            }
        }
    }

    /**
     * The database table has underscores for MV column names, but we expose a column without the underscore.
     * Therefore, we need to translate between the two sets of column names.
     * @return database column name -> exposed TableInfo column name
     */
    protected static Map<String, String> createMVMapping(Domain domain)
    {
        Map<String, String> result = new CaseInsensitiveHashMap<>();
        if (domain != null)
        {
            for (DomainProperty domainProperty : domain.getProperties())
            {
                if (domainProperty.isMvEnabled())
                {
                    result.put(PropertyStorageSpec.getMvIndicatorStorageColumnName(domainProperty.getPropertyDescriptor()), domainProperty.getName() + MvColumn.MV_INDICATOR_SUFFIX);
                }
            }
        }
        return result;
    }


    @TestWhen(TestWhen.When.BVT)
    public static class TestCase extends Assert
    {
        static TabLoader getTestData() throws IOException
        {
            TabLoader testData = new TabLoader(new StringReader("pk,i,s\n0,0,zero\n1,1,one\n2,2,two"),true);
            testData.parseAsCSV();
            testData.getColumns()[0].clazz = Integer.class;
            testData.getColumns()[1].clazz = Integer.class;
            testData.getColumns()[2].clazz = String.class;
            return testData;
        }

        @BeforeClass
        public static void createList() throws Exception
        {
            if (null == ListService.get())
                return;
            deleteList();

            TabLoader testData = getTestData();
            String hash = GUID.makeHash();
            User user = TestContext.get().getUser();
            Container c = JunitUtil.getTestContainer();
            ListService s = ListService.get();
            UserSchema lists = (UserSchema)DefaultSchema.get(user, c).getSchema("lists");
            assertNotNull(lists);

            ListDefinition R = s.createList(c, "R", ListDefinition.KeyType.Integer);
            R.setKeyName("pk");
            Domain d = requireNonNull(R.getDomain());
            for (int i=0 ; i<testData.getColumns().length ; i++)
            {
                DomainProperty p = d.addProperty();
                p.setPropertyURI(d.getName() + hash + "#" + testData.getColumns()[i].name);
                p.setName(testData.getColumns()[i].name);
                p.setRangeURI(testData.getColumns()[i].clazz == Integer.class ? PropertyType.INTEGER.getTypeUri() : PropertyType.STRING.getTypeUri());
            }
            R.save(user);
            TableInfo rTableInfo = lists.getTable("R", null);
            assertNotNull(rTableInfo);
        }

        public static List<Map<String,Object>> getRows()
        {
            User user = TestContext.get().getUser();
            Container c = JunitUtil.getTestContainer();
            UserSchema lists = (UserSchema)DefaultSchema.get(user, c).getSchema("lists");
            TableInfo rTableInfo = requireNonNull(lists.getTable("R", null));
            return Arrays.asList(new TableSelector(rTableInfo, TableSelector.ALL_COLUMNS, null, new Sort("PK")).getMapArray());
        }

        @Before
        public void resetList() throws Exception
        {
            if (null == ListService.get())
                return;
            User user = TestContext.get().getUser();
            Container c = JunitUtil.getTestContainer();
            TableInfo rTableInfo = ((UserSchema)DefaultSchema.get(user, c).getSchema("lists")).getTable("R", null);
            QueryUpdateService qus = requireNonNull(rTableInfo.getUpdateService());
            qus.truncateRows(user, c, null, null);
        }

        @AfterClass
        public static void deleteList() throws Exception
        {
            if (null == ListService.get())
                return;
            User user = TestContext.get().getUser();
            Container c = JunitUtil.getTestContainer();
            ListService s = ListService.get();
            Map<String, ListDefinition> m = s.getLists(c);
            if (m.containsKey("R"))
                m.get("R").delete(user);
        }

        void validateDefaultData(List<Map<String,Object>> rows)
        {
            assertEquals(rows.size(), 3);

            assertEquals(0, rows.get(0).get("pk"));
            assertEquals(1, rows.get(1).get("pk"));
            assertEquals(2, rows.get(2).get("pk"));

            assertEquals(0, rows.get(0).get("i"));
            assertEquals(1, rows.get(1).get("i"));
            assertEquals(2, rows.get(2).get("i"));

            assertEquals("zero", rows.get(0).get("s"));
            assertEquals("one", rows.get(1).get("s"));
            assertEquals("two", rows.get(2).get("s"));
        }

        @Test
        public void INSERT() throws Exception
        {
            if (null == ListService.get())
                return;
            User user = TestContext.get().getUser();
            Container c = JunitUtil.getTestContainer();
            TableInfo rTableInfo = ((UserSchema)DefaultSchema.get(user, c).getSchema("lists")).getTable("R", null);
            assert(getRows().size()==0);
            QueryUpdateService qus = requireNonNull(rTableInfo.getUpdateService());
            BatchValidationException errors = new BatchValidationException();
            var rows = qus.insertRows(user, c, getTestData().load(), errors, null, null);
            assertFalse(errors.hasErrors());
            validateDefaultData(rows);
            validateDefaultData(getRows());

            qus.insertRows(user, c, getTestData().load(), errors, null, null);
            assertTrue(errors.hasErrors());
        }

        @Test
        public void UPSERT() throws Exception
        {
            if (null == ListService.get())
                return;
            /* not sure how you use/test ImportOptions.UPSERT
             * the only row returning QUS method is insertRows(), which doesn't let you specify the InsertOption?
             */
        }

        @Test
        public void IMPORT() throws Exception
        {
            if (null == ListService.get())
                return;
            User user = TestContext.get().getUser();
            Container c = JunitUtil.getTestContainer();
            TableInfo rTableInfo = ((UserSchema)DefaultSchema.get(user, c).getSchema("lists")).getTable("R", null);
            assert(getRows().size()==0);
            QueryUpdateService qus = requireNonNull(rTableInfo.getUpdateService());
            BatchValidationException errors = new BatchValidationException();
            var count = qus.importRows(user, c, getTestData(), errors, null, null);
            assertFalse(errors.hasErrors());
            assert(count == 3);
            validateDefaultData(getRows());

            qus.importRows(user, c, getTestData(), errors, null, null);
            assertTrue(errors.hasErrors());
        }

        @Test
        public void MERGE() throws Exception
        {
            if (null == ListService.get())
                return;
            INSERT();
            assertEquals("Wrong number of rows after INSERT", 3, getRows().size());

            User user = TestContext.get().getUser();
            Container c = JunitUtil.getTestContainer();
            TableInfo rTableInfo = ((UserSchema)DefaultSchema.get(user, c).getSchema("lists")).getTable("R", null);
            QueryUpdateService qus = requireNonNull(rTableInfo.getUpdateService());
            var mergeRows = new ArrayList<Map<String,Object>>();
            mergeRows.add(CaseInsensitiveHashMap.of("pk",2,"s","TWO"));
            mergeRows.add(CaseInsensitiveHashMap.of("pk",3,"s","THREE"));
            BatchValidationException errors = new BatchValidationException()
            {
                @Override
                public void addRowError(ValidationException vex)
                {
                    LogManager.getLogger(AbstractQueryUpdateService.class).error("test error", vex);
                    fail(vex.getMessage());
                }
            };
            int count=0;
            try (var tx = rTableInfo.getSchema().getScope().ensureTransaction())
            {
                var ret = qus.mergeRows(user, c, new ListofMapsDataIterator(mergeRows.get(0).keySet(), mergeRows), errors, null, null);
                if (!errors.hasErrors())
                {
                    tx.commit();
                    count = ret;
                }
            }
            assertFalse("mergeRows error(s): " + errors.getMessage(), errors.hasErrors());
            assertEquals(count,2);
            var rows = getRows();
            // test existing row value is updated
            assertEquals("TWO", rows.get(2).get("s"));
            // test existing row value is not updated
            assertEquals(2, rows.get(2).get("i"));
            // test new row
            assertEquals("THREE", rows.get(3).get("s"));
            assertNull(rows.get(3).get("i"));
        }

        @Test
        public void REPLACE() throws Exception
        {
            if (null == ListService.get())
                return;
            assert(getRows().size()==0);
            INSERT();

            User user = TestContext.get().getUser();
            Container c = JunitUtil.getTestContainer();
            TableInfo rTableInfo = ((UserSchema)DefaultSchema.get(user, c).getSchema("lists")).getTable("R", null);
            QueryUpdateService qus = requireNonNull(rTableInfo.getUpdateService());
            var mergeRows = new ArrayList<Map<String,Object>>();
            mergeRows.add(CaseInsensitiveHashMap.of("pk",2,"s","TWO"));
            mergeRows.add(CaseInsensitiveHashMap.of("pk",3,"s","THREE"));
            DataIteratorContext context = new DataIteratorContext();
            context.setInsertOption(InsertOption.REPLACE);
            var count = qus.loadRows(user, c, new ListofMapsDataIterator(mergeRows.get(0).keySet(), mergeRows), context, null);
            assertFalse(context.getErrors().hasErrors());
            assertEquals(count,2);
            var rows = getRows();
            // test existing row value is updated
            assertEquals("TWO", rows.get(2).get("s"));
            // test existing row value is updated
            assertNull(rows.get(2).get("i"));
            // test new row
            assertEquals("THREE", rows.get(3).get("s"));
            assertNull(rows.get(3).get("i"));
        }

        @Test
        public void IMPORT_IDENTITY()
        {
            if (null == ListService.get())
                return;
            // TODO
        }
    }
}
