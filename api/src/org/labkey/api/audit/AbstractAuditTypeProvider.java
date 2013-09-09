/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.api.audit;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.query.DefaultAuditTypeTable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.TableChange;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * User: klum
 * Date: 7/11/13
 */
public abstract class AbstractAuditTypeProvider implements AuditTypeProvider
{
    public static final String QUERY_SCHEMA_NAME = "auditLog";
    public static final String SCHEMA_NAME = "audit";

    public static final String COLUMN_NAME_ROW_ID = "RowId";
    public static final String COLUMN_NAME_CONTAINER = "Container";
    public static final String COLUMN_NAME_COMMENT = "Comment";
    public static final String COLUMN_NAME_EVENT_TYPE = "EventType";
    public static final String COLUMN_NAME_CREATED = "Created";
    public static final String COLUMN_NAME_CREATED_BY = "CreatedBy";
    public static final String COLUMN_NAME_IMPERSONATED_BY = "ImpersonatedBy";
    public static final String COLUMN_NAME_PROJECT_ID = "ProjectId";

    protected abstract DomainKind getDomainKind();

    @Override
    public void initializeProvider(User user)
    {
        // Register the DomainKind
        DomainKind domainKind = getDomainKind();
        PropertyService.get().registerDomainKind(domainKind);

        // if the domain doesn't exist, create it
        Domain domain = getDomain();
        if (domain == null)
        {
            try
            {
                String domainURI = domainKind.generateDomainURI(QUERY_SCHEMA_NAME, getEventName(), getDomainContainer(), null);
                domain = PropertyService.get().createDomain(getDomainContainer(), domainURI, domainKind.getKindName());
                domain.save(user);
            }
            catch (ChangePropertyDescriptorException e)
            {
                throw new RuntimeException(e);
            }
        }
        else
        {
            // ensure the domain fields are in sync with the domain kind specification
            ensureProperties(user, domain, domainKind);
        }
    }

    protected void ensureProperties(User user, Domain domain, DomainKind domainKind)
    {
        if (domain != null && domainKind != null)
        {
            DbScope scope = domainKind.getScope();

            try (DbScope.Transaction transaction = scope.ensureTransaction())
            {
                StorageProvisioner.ProvisioningReport preport = StorageProvisioner.getProvisioningReport(domain.getTypeURI());
                if (preport.getProvisionedDomains().size() != 1)
                {
                    return;
                }
                StorageProvisioner.ProvisioningReport.DomainReport report = preport.getProvisionedDomains().iterator().next();

                TableChange drops = new TableChange(domainKind.getStorageSchemaName(), domain.getStorageTableName(), TableChange.ChangeType.DropColumns);
                boolean hasDrops = false;
                TableChange adds = new TableChange(domainKind.getStorageSchemaName(), domain.getStorageTableName(), TableChange.ChangeType.AddColumns);
                boolean hasAdds = false;

                for (StorageProvisioner.ProvisioningReport.ColumnStatus st : report.getColumns())
                {
                    if (!st.hasProblem)
                        continue;
                    if (st.spec == null && st.prop == null)
                    {
                        if (null != st.colName)
                        {
                            drops.dropColumnExactName(st.colName);
                            hasDrops = true;
                        }
                    }
                    else if (st.spec != null && st.prop == null)
                    {
                        if (st.colName == null)
                        {
                            adds.addColumn(st.spec);
                            hasAdds = true;
                        }
                    }
                }
                Connection conn = scope.getConnection();
                if (hasDrops)
                    executeChange(scope, conn, drops);
                if (hasAdds)
                    executeChange(scope, conn, adds);

                if (hasDrops || hasAdds)
                    domainKind.invalidate(domain);

                scope.commitTransaction();
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    private void executeChange(DbScope scope, Connection conn, TableChange change) throws SQLException
    {
        for (String sql : scope.getSqlDialect().getChangeStatements(change))
        {
            conn.prepareStatement(sql).execute();
        }
    }

    @Override
    public final Domain getDomain()
    {
        DomainKind domainKind = getDomainKind();

        String domainURI = domainKind.generateDomainURI(QUERY_SCHEMA_NAME, getEventName(), getDomainContainer(), null);

        return PropertyService.get().getDomain(getDomainContainer(), domainURI);
    }

    @Override
    public TableInfo createTableInfo(UserSchema userSchema)
    {
        Domain domain = getDomain();
        DbSchema dbSchema =  DbSchema.get(SCHEMA_NAME);

        return new DefaultAuditTypeTable(this, domain, dbSchema, userSchema);
    }

    @Override
    public Map<FieldKey, String> legacyNameMap()
    {
        Map<FieldKey, String> legacyNames = new LinkedHashMap<>();
        legacyNames.put(FieldKey.fromParts("ContainerId"), "Container");
        legacyNames.put(FieldKey.fromParts("Date"), "Created");
        return legacyNames;
    }

    public static Container getDomainContainer()
    {
        return ContainerManager.getSharedContainer();
    }

    protected <K extends AuditTypeEvent> void copyStandardFields(K bean, AuditLogEvent event)
    {
        bean.setImpersonatedBy(event.getImpersonatedBy());
        bean.setComment(event.getComment());
        bean.setProjectId(event.getProjectId());
        bean.setContainer(event.getContainerId());
        bean.setEventType(event.getEventType());
        bean.setCreated(event.getCreated());
        bean.setCreatedBy(event.getCreatedBy());
    }

    @Override
    public <K extends AuditTypeEvent> K convertEvent(AuditLogEvent event, @Nullable Map<String, Object> dataMap)
    {
        if (dataMap == null)
            return convertEvent(event);
        else
            throw new IllegalArgumentException("Provider needs to override convertEvent in order to handle a non-null dataMap");
    }
}
