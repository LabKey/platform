/*
 * Copyright (c) 2007-2017 LabKey Corporation
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

package org.labkey.audit.model;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.audit.query.DefaultAuditTypeTable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.BeanObjectFactory;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.view.HttpView;
import org.labkey.audit.AuditSchema;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * User: Karl Lum
 * Date: Oct 4, 2007
 */
public class LogManager
{
    private static final Logger _log = Logger.getLogger(LogManager.class);
    private static final LogManager _instance = new LogManager();
    private static final int COMMENT_MAX = 500;
    private static final int STRING_KEY_MAX = 1000;

    private LogManager(){}
    static public LogManager get()
    {
        return _instance;
    }

    public DbSchema getSchema()
    {
        return AuditSchema.getInstance().getSchema();
    }

    public TableInfo getTinfoAuditLog()
    {
        return getSchema().getTable("auditlog");
    }

    public <K extends AuditTypeEvent> K _insertEvent(User user, K type)
    {
        Logger auditLogger = Logger.getLogger("org.labkey.audit.event." + type.getEventType().replaceAll(" ", ""));
        auditLogger.info(type.getAuditLogMessage());

        AuditTypeProvider provider = AuditLogService.get().getAuditProvider(type.getEventType());

        if (provider != null)
        {
            try
            {
                Container c = ContainerManager.getForId(type.getContainer());

                UserSchema schema = AuditLogService.getAuditLogSchema(user, c != null ? c : ContainerManager.getRoot());

                if (schema != null)
                {
                    TableInfo table = schema.getTable(provider.getEventName());

                    if (table instanceof DefaultAuditTypeTable)
                    {
                        // consider using etl data iterator for inserts
                        type = validateFields(provider, type);
                        TableInfo dbTable = ((DefaultAuditTypeTable)table).getRealTable();
                        K ret = Table.insert(user, dbTable, type);
                        return ret;
                    }
                }
            }
            catch (RuntimeSQLException x)
            {
                if (!x.isConstraintException())
                    throw x;
            }
        }
        return null;
    }

    @Nullable
    public <K extends AuditTypeEvent> K getAuditEvent(User user, String eventType, int rowId)
    {
        AuditTypeProvider provider = AuditLogService.get().getAuditProvider(eventType);
        if (provider != null)
        {
            UserSchema schema = AuditLogService.getAuditLogSchema(user, HttpView.currentContext().getContainer());

            if (schema != null)
            {
                TableInfo table = schema.getTable(provider.getEventName());
                TableSelector selector = new TableSelector(table, null, null);

                return (K)selector.getObject(rowId, provider.getEventClass());
            }
        }
        return null;
    }

    public <K extends AuditTypeEvent> List<K> getAuditEvents(Container container, User user, String eventType, @Nullable SimpleFilter filter, @Nullable Sort sort)
    {
        AuditTypeProvider provider = AuditLogService.get().getAuditProvider(eventType);
        if (provider != null)
        {
            UserSchema schema = AuditLogService.getAuditLogSchema(user, container);

            if (schema != null)
            {
                TableInfo table = schema.getTable(provider.getEventName());
                TableSelector selector = new TableSelector(table, filter, sort);

                return (List<K>)selector.getArrayList(provider.getEventClass());
            }
        }
        return Collections.emptyList();
    }

    private String ensureMaxLength(String input, int max)
    {
        if (input != null && input.length() > max)
        {
            _log.warn("Audit field input : \n" + input + "\nexceeded the maximum length : " + max);
            return input.substring(0, max-3) + "...";
        }
        return input;
    }

    /**
     * Ensure that the string properties don't exceed the length of the provisioned columns.
     * Values will be trimmed to the max length.
     */
    private <K extends AuditTypeEvent> K validateFields(@NotNull AuditTypeProvider provider, @NotNull K type)
    {
        BeanObjectFactory<K> factory = new BeanObjectFactory<>((Class<K>) type.getClass());
        Map<String, Object> values = new CaseInsensitiveHashMap<>();
        factory.toMap(type, values);

        boolean changed = false;
        Domain domain = provider.getDomain();

        DomainKind domainKind = domain.getDomainKind();
        for (PropertyStorageSpec prop : domainKind.getBaseProperties(domain))
        {
            Object value = values.get(prop.getName());
            if (prop.getJdbcType().isText() && value instanceof String)
            {
                int scale = prop.getSize();
                if (((String)value).length() > scale)
                {
                    _log.warn("Audit field input : \n" + prop.getName() + "\nexceeded the maximum length : " + scale);
                    String trimmed = ((String)value).substring(0, scale-3) + "...";
                    values.put(prop.getName(), trimmed);
                    changed = true;
                }
            }
        }

        for (DomainProperty dp : domain.getProperties())
        {
            // For now, only check for string length like we were doing for the old audit event fields
            PropertyDescriptor pd = dp.getPropertyDescriptor();
            Object value = values.get(dp.getName());
            if (pd.isStringType() && value instanceof String)
            {
                int scale = dp.getScale();
                if (scale > 0 && ((String)value).length() > scale)
                {
                    _log.warn("Audit field input : \n" + pd.getName() + "\nexceeded the maximum length : " + scale);
                    String trimmed;
                    if (scale > 100)
                        trimmed = ((String)value).substring(0, scale-3) + "...";
                    else
                        trimmed = ((String) value).substring(0, scale);
                    values.put(pd.getName(), trimmed);
                    changed = true;
                }
            }
        }

        if (changed)
            return factory.fromMap(values);
        else
            return type;
    }

}
