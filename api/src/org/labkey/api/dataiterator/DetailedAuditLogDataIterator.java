/*
 * Copyright (c) 2017-2026 LabKey Corporation
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
package org.labkey.api.dataiterator;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AuditHandler;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.gwt.client.AuditBehaviorType;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.security.User;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.function.Function;

import static org.labkey.api.gwt.client.AuditBehaviorType.DETAILED;

/**
 * Used for adding detailed audit logs for each row in a data import, which records the full values
 * of the row that's being inserted or updated.
 *
 * This does not change the data, only adds an audit log when detailed logging is requested for the table.
 */
public class DetailedAuditLogDataIterator extends AbstractDataIterator
{
    public enum AuditConfigs {
        AuditBehavior,
        AuditUserComment
    }

    final MapDataIterator _data;
    final User _user;
    final Container _container;
    final TableInfo _table;
    final String _userComment;
    final QueryService.AuditAction _auditAction;
    final AuditHandler _auditHandler;
    final boolean _useTransactionAuditCache;
    Function<Map<String, Object>, Map<String, Object>> _extractProvidedValues;

    // for batching
    final ArrayList<Map<String,Object>> _updatedRows = new ArrayList<>();
    final ArrayList<Map<String, Object>> _providedValues = new ArrayList<>();
    final ArrayList<Map<String,Object>> _existingRows;

    protected DetailedAuditLogDataIterator(DataIterator data, DataIteratorContext context, TableInfo table, QueryService.AuditAction auditAction, User user, Container c, @Nullable Function<Map<String, Object>, Map<String, Object>> extractProvidedValues)
    {
        super(context);
        _table = table;
        _data = (MapDataIterator)data;
        _user = user;
        _container = c;
        _userComment = (String) _context.getConfigParameter(AuditConfigs.AuditUserComment);
        _useTransactionAuditCache = !context.getInsertOption().updateOnly && context.isUseTransactionAuditCache();
        _auditAction = auditAction;
        _auditHandler = table.getAuditHandler(DETAILED);
        _extractProvidedValues = extractProvidedValues;

        assert DETAILED == table.getEffectiveAuditBehavior((AuditBehaviorType) context.getConfigParameter(AuditConfigs.AuditBehavior));
        assert !context.getInsertOption().mergeRows || _data.supportsGetExistingRecord();
        assert !context.getConfigParameterBoolean(QueryUpdateService.ConfigParameters.BulkLoad);
        assert !context.getConfigParameterBoolean(QueryUpdateService.ConfigParameters.ByPassAudit);

        _existingRows = _data.supportsGetExistingRecord() ? new ArrayList<>() : null;
    }

    @Override
    public int getColumnCount()
    {
        return _data.getColumnCount();
    }

    @Override
    public ColumnInfo getColumnInfo(int i)
    {
        return _data.getColumnInfo(i);
    }

    @Override
    public boolean next() throws BatchValidationException
    {
        boolean hasNext = _data.next();

        if (!hasNext || _updatedRows.size() > 1000)
        {
            if (!_updatedRows.isEmpty())
                _auditHandler.addAuditEvent(_user, _container, _table, DETAILED, _userComment, _auditAction, _updatedRows, _existingRows, _providedValues, _useTransactionAuditCache);
            _updatedRows.clear();
            if (null != _existingRows)
                _existingRows.clear();
        }
        if (hasNext)
        {
            Map<String, Object> map = _data.getMap();
            _updatedRows.add(map);
            if (_extractProvidedValues != null)
                _providedValues.add(_extractProvidedValues.apply(map));
            else
                _providedValues.add(null);

            if (null != _existingRows)
                _existingRows.add(_data.getExistingRecord());
        }
        return hasNext;
    }

    @Override
    public Object get(int i)
    {
        return _data.get(i);
    }

    @Override
    public void close() throws IOException
    {
        _data.close();
    }

    public static DataIteratorBuilder getDataIteratorBuilder(TableInfo queryTable, @NotNull final DataIteratorBuilder builder, QueryUpdateService.InsertOption insertOption, final User user, final Container container, @Nullable Function<Map<String, Object>, Map<String, Object>> extractProvidedValues)
    {
        return context ->
        {
            DataIterator it = builder.getDataIterator(context);
            if (it == null)
                return null; // can happen if context has errors

            AuditBehaviorType auditType = AuditBehaviorType.NONE;
            if (queryTable.supportsAuditTracking())
                auditType = queryTable.getEffectiveAuditBehavior((AuditBehaviorType) context.getConfigParameter(AuditConfigs.AuditBehavior));

            // Detailed auditing and not set to bulk load in ETL
            if (auditType == DETAILED && !context.getConfigParameterBoolean(QueryUpdateService.ConfigParameters.BulkLoad) && !context.getConfigParameterBoolean(QueryUpdateService.ConfigParameters.ByPassAudit))
            {
                DataIterator in = DataIteratorUtil.wrapMap(it, true);
                return new DetailedAuditLogDataIterator(in, context, queryTable, insertOption.auditAction, user, container, extractProvidedValues);
            }

            // Nothing to do, so just return input DataIterator
            return it;
        };
    }

    @Override
    public void debugLogInfo(StringBuilder sb)
    {
        super.debugLogInfo(sb);
        if (null != _data)
            _data.debugLogInfo(sb);
    }

    @Override
    public boolean supportsGetExistingRecord()
    {
        return _data.supportsGetExistingRecord();
    }
}
