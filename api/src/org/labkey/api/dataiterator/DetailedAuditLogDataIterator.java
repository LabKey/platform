/*
 * Copyright (c) 2017 LabKey Corporation
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
import org.labkey.api.data.AuditConfigurable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.gwt.client.AuditBehaviorType;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;

import java.io.IOException;
import java.util.Collections;

import static org.labkey.api.gwt.client.AuditBehaviorType.DETAILED;

/**
 * Used for adding detailed audit logs for each row in a data import, which records the full values
 * of the row that's being inserted or updated.
 *
 * This does not change the data, only adds an audit log when detailed logging is requested for the table.
 */
public class DetailedAuditLogDataIterator extends AbstractDataIterator
{
    final DataIterator _data;
    final User _user;
    final Container _container;
    final TableInfo _table;
    final QueryService.AuditAction _auditAction;

    protected DetailedAuditLogDataIterator(DataIterator data, DataIteratorContext context, TableInfo table, QueryService.AuditAction auditAction, User user, Container c)
    {
        super(context);
        _table = table;
        _data = data;
        _user = user;
        _container = c;
        _auditAction = auditAction;
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
        if (!_data.next())
            return false;

        if (_table.supportsAuditTracking())
        {
            AuditConfigurable auditConfigurable = (AuditConfigurable) _table;
            AuditBehaviorType auditType = auditConfigurable.getAuditBehavior();

            if (auditType == DETAILED)
                QueryService.get().addAuditEvent(_user, _container, _table, _auditAction, Collections.singletonList(((MapDataIterator) _data).getMap()));
        }

        return true;
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


    public static DataIteratorBuilder getDataIteratorBuilder(TableInfo queryTable, @NotNull final DataIteratorBuilder builder, QueryService.AuditAction auditAction, final User user, final Container container)
    {
        return context ->
        {
            DataIterator it = builder.getDataIterator(context);
            DataIterator in = DataIteratorUtil.wrapMap(it, false);
            return new DetailedAuditLogDataIterator(in, context, queryTable, auditAction, user, container);
        };
    }
}
