/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
import org.labkey.api.audit.data.DataMapColumn;
import org.labkey.api.audit.data.DataMapDiffColumn;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.audit.query.DefaultAuditTypeTable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.TableChange;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.DomainNotFoundException;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

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

    public static final String OLD_RECORD_PROP_NAME = "oldRecordMap";
    public static final String OLD_RECORD_PROP_CAPTION = "Old Record Values";
    public static final String NEW_RECORD_PROP_NAME = "newRecordMap";
    public static final String NEW_RECORD_PROP_CAPTION = "New Record Values";

    protected abstract AbstractAuditDomainKind getDomainKind();

    @Override
    public void initializeProvider(User user)
    {
        // Register the DomainKind
        AbstractAuditDomainKind domainKind = getDomainKind();
        PropertyService.get().registerDomainKind(domainKind);

        Domain domain = getDomain();

        // NOTE: Remove domains created prior to migration that used DomainKind base properties instead of normal PropertyDescriptors
        // NOTE: This check will drop domains from servers built from source during 13.3 dev prior to the audit hard table migration.
        if (domain != null && domain.getProperties().isEmpty() && !AuditLogService.get().hasEventTypeMigrated(getEventName()))
        {
            try
            {
                domain.delete(user);
                domain = null;
            }
            catch (DomainNotFoundException e)
            {
                throw new RuntimeException(e);
            }
        }

        // if the domain doesn't exist, create it
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

        // ensure the domain fields are in sync with the domain kind specification
        ensureProperties(user, domain, domainKind);
    }

    // NOTE: Changing the name of an existing PropertyDescriptor will lose data!
    protected void ensureProperties(User user, Domain domain, AbstractAuditDomainKind domainKind)
    {
        if (domain != null && domainKind != null)
        {
            // Create a map of desired properties
            Map<String, PropertyDescriptor> props = new CaseInsensitiveHashMap<>();
            for (PropertyDescriptor pd : domainKind.getProperties())
                props.put(pd.getName(), pd);

            // Create a map of existing properties
            Map<String, PropertyDescriptor> current = new CaseInsensitiveHashMap<>();
            for (DomainProperty dp : domain.getProperties())
            {
                PropertyDescriptor pd = dp.getPropertyDescriptor();
                current.put(pd.getName(), pd);
            }

            Set<PropertyDescriptor> toAdd = new LinkedHashSet<>();
            for (PropertyDescriptor pd : props.values())
                if (!current.containsKey(pd.getName()))
                    toAdd.add(pd);

            Set<PropertyDescriptor> toUpdate = new LinkedHashSet<>();
            Set<PropertyDescriptor> toDelete = new LinkedHashSet<>();
            for (PropertyDescriptor pd : current.values())
            {
                if (props.containsKey(pd.getName()))
                    toUpdate.add(pd);
                else
                    toDelete.add(pd);
            }

            for (PropertyDescriptor pd : toAdd)
            {
                domain.addPropertyOfPropertyDescriptor(pd);
            }

            for (PropertyDescriptor pd : toDelete)
            {
                DomainProperty dp = domain.getPropertyByURI(pd.getPropertyURI());
                if (dp == null)
                    throw new RuntimeException("Failed to find property to delete: " + pd.getName());
                dp.delete();
            }

            try (DbScope.Transaction transaction = domainKind.getScope().ensureTransaction())
            {
                // CONSIDER: Avoid always updating the existing properties -- only update changed props.
                for (PropertyDescriptor pd : toUpdate)
                {
                    PropertyDescriptor desired = props.get(pd.getName());
                    assert desired != null;
                    desired.copyTo(pd);
                    OntologyManager.updatePropertyDescriptor(pd);
                }

                boolean changed = !toAdd.isEmpty() || !toDelete.isEmpty();
                if (changed)
                {
                    domain.save(user);
                }

                transaction.commit();
            }
            catch (ChangePropertyDescriptorException e)
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

    protected DbSchema getSchema()
    {
        return DbSchema.get(SCHEMA_NAME, DbSchemaType.Provisioned);
    }

    @Override
    public TableInfo createTableInfo(UserSchema userSchema)
    {
        Domain domain = getDomain();

        return new DefaultAuditTypeTable(this, domain, userSchema);
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

    protected void appendValueMapColumns(AbstractTableInfo table)
    {
        ColumnInfo oldCol = table.getColumn(FieldKey.fromString(OLD_RECORD_PROP_NAME));
        ColumnInfo newCol = table.getColumn(FieldKey.fromString(NEW_RECORD_PROP_NAME));

        if(oldCol != null){
            ColumnInfo added = table.addColumn(new AliasedColumn(table, "OldValues", oldCol));
            added.setDisplayColumnFactory(new DisplayColumnFactory()
            {
                public DisplayColumn createRenderer(final ColumnInfo colInfo)
                {
                    return new DataMapColumn(colInfo);
                }
            });
            added.setLabel(OLD_RECORD_PROP_CAPTION);
            oldCol.setHidden(true);
        }

        if(newCol != null)
        {
            ColumnInfo added = table.addColumn(new AliasedColumn(table, "NewValues", newCol));
            added.setDisplayColumnFactory(new DisplayColumnFactory()
            {
                public DisplayColumn createRenderer(final ColumnInfo colInfo)
                {
                    return new DataMapColumn(colInfo);
                }

            });
            added.setLabel(NEW_RECORD_PROP_CAPTION);
            newCol.setHidden(true);
        }

        // add a column to show the differences between old and new values
        if (oldCol != null && newCol != null)
            table.addColumn(new DataMapDiffColumn(table, "DataChanges", oldCol, newCol));
    }

    public ActionURL getAuditUrl()
    {
        return AuditLogService.get().getAuditUrl();
    }
}
