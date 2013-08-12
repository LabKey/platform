/*
 * Copyright (c) 2007-2013 LabKey Corporation
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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.audit.query.DefaultAuditTypeTable;
import org.labkey.api.data.BeanObjectFactory;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.Filter;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.view.HttpView;
import org.labkey.audit.AuditSchema;

import java.sql.SQLException;
import java.util.HashMap;
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
        return getSchema().getTable("AuditLog");
    }

    public <K extends AuditTypeEvent> AuditLogEvent insertEvent(User user, AuditLogEvent event) throws SQLException
    {
        validateFields(event);

        AuditTypeProvider provider = AuditLogService.get().getAuditProvider(event.getEventType());

        if (provider != null && (AuditLogService.get().isMigrateComplete() || AuditLogService.get().hasEventTypeMigrated(event.getEventType())))
        {
            K bean = provider.convertEvent(event);
            bean = _insertEvent(user, bean);
            return event;
        }
        else
            return Table.insert(user, getTinfoAuditLog(), event);
    }

    public <K extends AuditTypeEvent> K _insertEvent(User user, K type) throws SQLException
    {
        AuditTypeProvider provider = AuditLogService.get().getAuditProvider(type.getEventType());

        if (provider != null)
        {
            try {

                Container c = ContainerManager.getForId(type.getContainer());

                BeanObjectFactory factory = new BeanObjectFactory(type.getClass());
                Map<String, Object> props = new HashMap<>();
                factory.toMap(type, props);

                UserSchema schema = AuditLogService.getAuditLogSchema(user, c);

                if (schema != null)
                {
                    TableInfo table = schema.getTable(provider.getEventName());

                    if (table instanceof DefaultAuditTypeTable)
                    {
                        // consider using etl data iterator for inserts
                        TableInfo dbTable = ((DefaultAuditTypeTable)table).getRealTable();
                        K ret = Table.insert(user, dbTable, type);

                        return ret;
/*
                        TableViewForm tvf = new TableViewForm(table);

                        tvf.setTypedValues(props, false);

                        Map<String, Object> values = tvf.getTypedColumns();
                        QueryUpdateService qus = table.getUpdateService();
                        if (qus == null)
                            throw new IllegalArgumentException("The query '" + table.getName() + "' in the schema '" + table.getSchema().getName() + "' is not updatable.");

                        Map<String, Object> row;

                        BatchValidationException batchErrors = new BatchValidationException();
                        List<Map<String, Object>> updated = qus.insertRows(user, c, Collections.singletonList(values), batchErrors, null);
                        if (batchErrors.hasErrors())
                            throw batchErrors;

                        assert(updated.size() == 1);
                        row = updated.get(0);

                        K bean = (K)type.getClass().newInstance();
                        return (K)factory.fromMap(bean, row);
*/
                    }
                }
            }
            catch (SQLException x)
            {
                if (!SqlDialect.isConstraintException(x))
                    throw x;
            }
/*
            catch (BatchValidationException x)
            {
                throw new RuntimeException(x);
            }
*/
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    public AuditLogEvent getEvent(int rowId)
    {
        SimpleFilter filter = new SimpleFilter("RowId", rowId);
        return new TableSelector(getTinfoAuditLog(), filter, null).getObject(AuditLogEvent.class);
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

    public List<AuditLogEvent> getEvents(Filter filter, Sort sort)
    {
        return new TableSelector(getTinfoAuditLog(), filter, sort).getArrayList(AuditLogEvent.class);
    }

    /**
     * Ensure that the string key fields don't exceed the length specified in the schema
     */
    private void validateFields(AuditLogEvent event)
    {
        event.setKey1(ensureMaxLength(event.getKey1(), STRING_KEY_MAX));
        event.setKey2(ensureMaxLength(event.getKey2(), STRING_KEY_MAX));
        event.setKey3(ensureMaxLength(event.getKey3(), STRING_KEY_MAX));

        event.setComment(ensureMaxLength(event.getComment(), COMMENT_MAX));
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
}
