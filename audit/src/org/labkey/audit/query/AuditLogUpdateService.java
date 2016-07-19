/*
 * Copyright (c) 2011-2016 LabKey Corporation
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
package org.labkey.audit.query;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.ClientApiAuditProvider;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.AbstractQueryUpdateService;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * User: jeckels
 * Date: Mar 6, 2011
 */
public class AuditLogUpdateService extends AbstractQueryUpdateService
{
    public AuditLogUpdateService(TableInfo table)
    {
        super(table);
    }

    @Override
    protected Map<String, Object> getRow(User user, Container container, Map<String, Object> keys) throws InvalidKeyException, QueryUpdateServiceException
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("RowId"), keys.get("RowId"));
        if (keys.get("EventType") != null)
            filter.addCondition(FieldKey.fromParts("EventType"), keys.get("EventType"));
        return new TableSelector(getQueryTable(), filter, null).getMap();
    }

    @Override
    protected Map<String, Object> insertRow(User user, Container container, Map<String, Object> row) throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        ClientApiAuditProvider.ClientApiAuditEvent event = new ClientApiAuditProvider.ClientApiAuditEvent(container.getId(), getString(row, "Comment"));
        if (row.get("EventType") != null)
        {
            String eventType = row.get("EventType").toString();
            if (!ClientApiAuditProvider.EVENT_TYPE.equalsIgnoreCase(eventType))
            {
                throw new ValidationException("Only audit entries with EventType '" + ClientApiAuditProvider.EVENT_TYPE + "' can be inserted.");
            }
        }
        event.setSubType(getString(row, "Key1"));
        event.setString1(getString(row, "Key2"));
        event.setString2(getString(row, "Key3"));
        Integer intKey1 = getInteger(row, "IntKey1");
        if (intKey1 != null)
        {
            event.setInt1(intKey1.intValue());
        }
        Integer intKey2 = getInteger(row, "IntKey2");
        if (intKey2 != null)
        {
            event.setInt2(intKey2.intValue());
        }
        Integer intKey3 = getInteger(row, "IntKey3");
        if (intKey3 != null)
        {
            event.setInt3(intKey3.intValue());
        }
        event = AuditLogService.get().addEvent(user, event);
        try
        {
            Map<String, Object> keys = new HashMap<>();
            keys.put("RowId", event.getRowId());
            keys.put("EventType", event.getEventType());
            return getRow(user, container, keys);
        }
        catch (InvalidKeyException e)
        {
            throw new QueryUpdateServiceException(e);
        }
    }

    private String getString(Map<String, Object> row, String propertyName)
    {
        return row.get(propertyName) == null ? null : row.get(propertyName).toString();
    }

    private Integer getInteger(Map<String, Object> row, String propertyName) throws ValidationException
    {
        try
        {
            return (Integer)ConvertUtils.convert(getString(row, propertyName), Integer.class);
        }
        catch (ConversionException e)
        {
            throw new ValidationException("Invalid value for integer field '" + propertyName + "': " + getString(row, propertyName));
        }
    }

    @Override
    protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, @NotNull Map<String, Object> oldRow) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        throw new UnsupportedOperationException("Audit records aren't editable");
    }

    @Override
    protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRow)
    {
        throw new UnsupportedOperationException("Audit records aren't deleteable");
    }
}
