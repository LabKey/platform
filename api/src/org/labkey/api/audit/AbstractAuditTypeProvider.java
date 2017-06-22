/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.TableChange;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.LimitedUser;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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

    public AbstractAuditTypeProvider()
    {
        // Issue 20310: initialize AuditTypeProvider when registered during startup
        User auditUser = new LimitedUser(UserManager.getGuestUser(), new int[0], Collections.singleton(RoleManager.getRole(ReaderRole.class)), false);
        initializeProvider(auditUser);
    }

    protected abstract AbstractAuditDomainKind getDomainKind();

    @Override
    public void initializeProvider(User user)
    {
        // Register the DomainKind
        AbstractAuditDomainKind domainKind = getDomainKind();
        PropertyService.get().registerDomainKind(domainKind);

        Domain domain = getDomain();

        // if the domain doesn't exist, create it
        if (domain == null)
        {
            try
            {
                String domainURI = domainKind.generateDomainURI(QUERY_SCHEMA_NAME, getEventName(), getDomainContainer(), null);
                domain = PropertyService.get().createDomain(getDomainContainer(), domainURI, domainKind.getKindName());
                domain.save(user);
                // don't keep using domain after domain.save()
                domain = getDomain();
            }
            catch (ChangePropertyDescriptorException e)
            {
                throw new RuntimeException(e);
            }
        }

        // ensure the domain fields are in sync with the domain kind specification
        ensureProperties(user, domain, domainKind);
    }

    protected void updateIndices(Domain domain, AbstractAuditDomainKind domainKind)
    {
        if (domain.getStorageTableName() == null)
            return;

        Map<String, Pair<TableInfo.IndexType, List<ColumnInfo>>> existingIndices = getSchema().getTable(domain.getStorageTableName()).getAllIndices();
        Set<PropertyStorageSpec.Index> newIndices = new HashSet<>();
        newIndices.addAll(domainKind.getPropertyIndices(domain));
        Set<PropertyStorageSpec.Index> toRemove = new HashSet<>();
        for (String name : existingIndices.keySet())
        {
            if (existingIndices.get(name).first == TableInfo.IndexType.Primary)
                continue;
            Pair<TableInfo.IndexType, List<ColumnInfo>> columnIndex = existingIndices.get(name);
            String[] columnNames = new String[columnIndex.second.size()];
            for (int i = 0; i < columnIndex.second.size(); i++)
            {
                columnNames[i] = columnIndex.second.get(i).getColumnName();
            }
            PropertyStorageSpec.Index existingIndex = new PropertyStorageSpec.Index(columnIndex.first == TableInfo.IndexType.Unique, columnNames);
            Boolean foundIt = false;
            for (PropertyStorageSpec.Index propertyIndex : newIndices)
            {
                if (PropertyStorageSpec.Index.isSameIndex(propertyIndex, existingIndex))
                {
                    foundIt = true;
                    newIndices.remove(propertyIndex);
                    break;
                }
            }

            if (!foundIt)
                toRemove.add(existingIndex);
        }

        if (!toRemove.isEmpty())
            StorageProvisioner.addOrDropTableIndices(domain, toRemove, false, TableChange.IndexSizeMode.Normal);
        if (!newIndices.isEmpty())
            StorageProvisioner.addOrDropTableIndices(domain, newIndices, true, TableChange.IndexSizeMode.Normal);
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
            Map<String, DomainProperty> current = new CaseInsensitiveHashMap<>();
            for (DomainProperty dp : domain.getProperties())
            {
                current.put(dp.getName(), dp);
            }

            Set<PropertyDescriptor> toAdd = new LinkedHashSet<>();
            for (PropertyDescriptor pd : props.values())
                if (!current.containsKey(pd.getName()))
                    toAdd.add(pd);

            Set<DomainProperty> toUpdate = new LinkedHashSet<>();
            boolean changed = false;

            for (DomainProperty dp : current.values())
            {
                if (props.containsKey(dp.getName()))
                    toUpdate.add(dp);
                else
                {
                    dp.delete();
                    changed = true;
                }
            }

            for (PropertyDescriptor pd : toAdd)
            {
                domain.addPropertyOfPropertyDescriptor(pd);
            }

            try (DbScope.Transaction transaction = domainKind.getScope().ensureTransaction())
            {
                // CONSIDER: Avoid always updating the existing properties -- only update changed props.
                for (DomainProperty dp : toUpdate)
                {
                    PropertyDescriptor desired = props.get(dp.getName());
                    assert desired != null;

                    if (differ(desired, dp, domain.getContainer()))
                    {
                        changed = true;
                        copyTo(dp, desired, domain.getContainer());
                    }
                }

                changed = changed || !toAdd.isEmpty();
                if (changed)
                {
                    domain.save(user);
                }

                updateIndices(domain, domainKind);
                transaction.commit();
            }
            catch (ChangePropertyDescriptorException e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    // #26311  We want to trigger a save if the scale has changed
    // CONSIDER: check for other differences here as well.
    private boolean differ(PropertyDescriptor pd, DomainProperty dp, Container c)
    {
        return dp.getScale() != pd.getScale()
                || !dp.getRangeURI().equals(pd.getRangeURI())
//                || !dp.getLabel().equals(pd.getLabel())
//                || dp.isRequired() != pd.isRequired()
//                || dp.isHidden() != pd.isHidden()
//                || dp.isMvEnabled() != pd.isMvEnabled()
//                || dp.getDefaultValueTypeEnum() != pd.getDefaultValueTypeEnum()
                ;


    }

    private void copyTo(DomainProperty dp, PropertyDescriptor pd, Container c)
    {
        dp.setRangeURI(pd.getRangeURI());
        dp.setLabel(pd.getLabel());
        dp.setRequired(pd.isRequired());
        dp.setHidden(pd.isHidden());
        dp.setMvEnabled(pd.isMvEnabled());
        dp.setScale(pd.getScale());
        if (pd.getDefaultValueType() != null)
            dp.setDefaultValueTypeEnum(DefaultValueType.valueOf(pd.getDefaultValueType()));
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


    public TableInfo createStorageTableInfo()
    {
        Domain domain = getDomain();
        if (null == domain)
            throw new NullPointerException("Could not find domain for " + getEventName());
        return StorageProvisioner.createTableInfo(domain);
    }

    @Override
    public TableInfo createTableInfo(UserSchema userSchema)
    {
        return new DefaultAuditTypeTable(this, createStorageTableInfo(), userSchema, getDefaultVisibleColumns());
    }

    public List<FieldKey> getDefaultVisibleColumns()
    {
        return null;
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

    public static Map<String, String> decodeFromDataMap(String properties)
    {
        try
        {
            if (properties != null)
            {
                return PageFlowUtil.mapFromQueryString(properties);
            }
            return Collections.emptyMap();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    public static String encodeForDataMap(Map<String, ?> properties)
    {
        if (properties == null) return null;

        Map<String,String> stringMap = new HashMap<>();
        for (Map.Entry<String,?> entry :  properties.entrySet())
        {
            Object value = entry.getValue();
            stringMap.put(entry.getKey(), value == null ? null : value.toString());
        }
        return PageFlowUtil.toQueryString(stringMap.entrySet());
    }
}
