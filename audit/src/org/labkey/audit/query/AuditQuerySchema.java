/*
 * Copyright (c) 2007-2016 LabKey Corporation
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

import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.module.Module;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.view.ViewContext;
import org.labkey.audit.AuditSchema;
import org.labkey.audit.model.LogManager;
import org.springframework.validation.BindException;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * User: Karl Lum
 * Date: Oct 5, 2007
 */
public class AuditQuerySchema extends UserSchema
{
    public static final String SCHEMA_NAME = "auditLog";
    public static final String SCHEMA_DESCR = "Contains data about audit log events.";
    public static final String AUDIT_TABLE_NAME = "audit";

    static public void register(Module module)
    {
        DefaultSchema.registerProvider(SCHEMA_NAME, new DefaultSchema.SchemaProvider(module) {
            @Override
            public boolean isAvailable(DefaultSchema schema, Module module)
            {
                return true;
            }

            @Override
            public QuerySchema createSchema(DefaultSchema schema, Module module)
            {
                return new AuditQuerySchema(schema.getUser(), schema.getContainer());
            }
        });
    }

    public AuditQuerySchema(User user, Container container)
    {
        super(SCHEMA_NAME, SCHEMA_DESCR, user, container, AuditSchema.getInstance().getSchema());
    }

    public Set<String> getTableNames()
    {
        LinkedHashSet<String> tables = new LinkedHashSet<>();

        // new audit table names
        for (AuditTypeProvider provider : AuditLogService.get().getAuditProviders())
        {
            tables.add(provider.getEventName());
        }

        return Collections.unmodifiableSet(tables);
    }

    public TableInfo createTable(String name)
    {
        // event specific audit views are implemented as queries on the audit schema
        AuditTypeProvider provider = AuditLogService.get().getAuditProvider(name);
        if (provider != null)
            return provider.createTableInfo(this);

        if (AUDIT_TABLE_NAME.equalsIgnoreCase(name))
            return new AuditLogUnionTable(this);

        return null;
    }

    @Override
    public QueryView createView(ViewContext context, QuerySettings settings, BindException errors)
    {
        String queryName = settings.getQueryName();
        AuditTypeProvider provider = AuditLogService.get().getAuditProvider(queryName);
        if (provider != null)
            return new AuditQueryView(this, settings, errors);

        return super.createView(context, settings, errors);
    }

    @Override
    protected boolean canReadSchema()
    {
        return true;
    }
}
