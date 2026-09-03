/*
 * Copyright (c) 2013-2026 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.audit.data.DataMapColumn;
import org.labkey.api.audit.data.DataMapDiffColumn;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.audit.query.DefaultAuditTypeTable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.MultiChoice;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.dataiterator.ExistingRecordDataIterator;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;

import java.sql.Time;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.labkey.api.audit.query.AbstractAuditDomainKind.AUDIT_RECORD_DATA_MAP_CONCEPT_URI;
import static org.labkey.api.audit.query.AbstractAuditDomainKind.NEW_RECORD_PROP_NAME;
import static org.labkey.api.audit.query.AbstractAuditDomainKind.OLD_RECORD_PROP_NAME;

public abstract class AbstractAuditTypeProvider implements AuditTypeProvider
{
    public static final String QUERY_SCHEMA_NAME = "auditLog";
    public static final String SCHEMA_NAME = "audit";

    public static final String COLUMN_NAME_ROW_ID = "RowId";
    public static final String COLUMN_NAME_CONTAINER = "Container";
    public static final String COLUMN_NAME_COMMENT = "Comment";
    public static final String COLUMN_NAME_USER_COMMENT = "UserComment";
    public static final String COLUMN_NAME_EVENT_TYPE = "EventType";
    public static final String COLUMN_NAME_CREATED = "Created";
    public static final String COLUMN_NAME_CREATED_BY = "CreatedBy";
    public static final String COLUMN_NAME_IMPERSONATED_BY = "ImpersonatedBy";
    public static final String COLUMN_NAME_PROJECT_ID = "ProjectId";
    public static final String COLUMN_NAME_TRANSACTION_ID = "TransactionID";
    public static final String COLUMN_NAME_DATA_CHANGES = "DataChanges";

    private final AbstractAuditDomainKind _domainKind;

    private record CachedStorageTable(SchemaTableInfo schemaTableInfo, TableInfo storageTableInfo) {}
    private volatile CachedStorageTable _cachedStorageTable;

    public AbstractAuditTypeProvider(@NotNull AbstractAuditDomainKind domainKind)
    {
        // TODO: consolidate domain kind initialization to this constructor and stop overriding getDomainKind()
        _domainKind = domainKind;
        // Register the DomainKind
        PropertyService.get().registerDomainKind(getDomainKind());
    }

    protected final AbstractAuditDomainKind getDomainKind()
    {
        if (_domainKind == null)
            throw new IllegalStateException(String.format("The audit type : \"%s\" has a null domain kind", getLabel()));

        return _domainKind;
    }

    // Expose the domain kind to AbstractAuditDomainKind$TestCase without touching every subclass
    public AbstractAuditDomainKind getAuditDomainKind()
    {
        return getDomainKind();
    }

    @Override
    public final void initializeProvider(User user)
    {
        AbstractAuditDomainKind domainKind = getDomainKind();
        domainKind.validate();

        Domain domain = getDomain(true);

        // if the domain doesn't exist, create it
        if (domain == null)
        {
            try
            {
                String domainURI = domainKind.generateDomainURI(QUERY_SCHEMA_NAME, getEventName(), getDomainContainer(), null);
                domain = PropertyService.get().createDomain(getDomainContainer(), domainURI, domainKind.getKindName());
                for (PropertyDescriptor pd : domainKind.getProperties())
                {
                    domain.addPropertyOfPropertyDescriptor(pd);
                }
                domain.save(user);
                domain = getDomain(true);
            }
            catch (ChangePropertyDescriptorException e)
            {
                throw new RuntimeException(e);
            }
        }

        // adjust potential domain kind changes
        ensureProperties(user, domain);
    }

    // NOTE: Changing the name of an existing PropertyDescriptor will lose data!
    private void ensureProperties(User user, Domain domain)
    {
        AbstractAuditDomainKind domainKind = getDomainKind();
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

                assert domain.getStorageTableName() != null;
                assert domain.getDomainKind() != null;
                assert domain.getDomainKind().getClass().equals(domainKind.getClass());

                StorageProvisioner.get().ensureTableIndices(domain);
                transaction.commit();
            }
            catch (ChangePropertyDescriptorException e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    // Issue 26311: We want to trigger a save if the scale has changed
    // CONSIDER: check for other differences here as well.
    private boolean differ(PropertyDescriptor pd, DomainProperty dp, Container c)
    {
        return dp.getScale() != pd.getScale() || !dp.getRangeURI().equals(pd.getRangeURI());
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
        return getDomain(false);
    }

    @Override
    public final Domain getDomain(boolean forUpdate)
    {
        AbstractAuditDomainKind domainKind = getDomainKind();

        String domainURI = domainKind.generateDomainURI(QUERY_SCHEMA_NAME, getEventName(), getDomainContainer(), null);

        return PropertyService.get().getDomain(getDomainContainer(), domainURI, forUpdate);
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

    @Override @NotNull
    public TableInfo getStorageTableInfoForInsert()
    {
        Domain domain = getDomain();
        if (null == domain)
            throw new IllegalStateException("Could not find domain for audit event type " + getEventName());

        // We want to reuse a cached provisioned TableInfo to avoid construction costs. Getting the SchemaTableInfo is
        // cheap so use that as a guide for when the table has changed (primarily during startup as its shape may
        // need to be updated based on current code expections) and when it's safe to reuse the previous copy.
        SchemaTableInfo schemaTableInfo = StorageProvisioner.get().getSchemaTableInfo(domain);
        CachedStorageTable cached = _cachedStorageTable;
        if (null != cached && cached.schemaTableInfo() == schemaTableInfo)
            return cached.storageTableInfo();

        TableInfo storageTableInfo = StorageProvisioner.createTableInfo(domain);
        // Shared across threads from here on
        storageTableInfo.setLocked(true);
        _cachedStorageTable = new CachedStorageTable(schemaTableInfo, storageTableInfo);

        return storageTableInfo;
    }

    @Override
    public TableInfo createTableInfo(UserSchema userSchema, ContainerFilter cf)
    {
        return new DefaultAuditTypeTable(this, createStorageTableInfo(), userSchema, cf, getDefaultVisibleColumns());
    }

    public List<FieldKey> getDefaultVisibleColumns()
    {
        return null;
    }

    public static Container getDomainContainer()
    {
        return ContainerManager.getSharedContainer();
    }

    protected void appendValueMapColumns(AbstractTableInfo table)
    {
        appendValueMapColumns(table, null);
    }

    protected void appendValueMapColumns(AbstractTableInfo table, String eventName)
    {
        appendValueMapColumns(table, eventName, false);
    }

    protected void appendValueMapColumns(AbstractTableInfo table, String eventName, boolean noUrl)
    {
        MutableColumnInfo oldCol = table.getMutableColumn(FieldKey.fromString(OLD_RECORD_PROP_NAME));
        MutableColumnInfo newCol = table.getMutableColumn(FieldKey.fromString(NEW_RECORD_PROP_NAME));

        if (oldCol != null)
        {
            var added = table.addColumn(new AliasedColumn(table, "OldValues", oldCol));
            added.setDisplayColumnFactory(DataMapColumn::new);
            added.setLabel(AbstractAuditDomainKind.OLD_RECORD_PROP_CAPTION);
            added.setConceptURI(AUDIT_RECORD_DATA_MAP_CONCEPT_URI);
            oldCol.setHidden(true);
        }

        if (newCol != null)
        {
            var added = table.addColumn(new AliasedColumn(table, "NewValues", newCol));
            added.setDisplayColumnFactory(DataMapColumn::new);
            added.setLabel(AbstractAuditDomainKind.NEW_RECORD_PROP_CAPTION);
            added.setConceptURI(AUDIT_RECORD_DATA_MAP_CONCEPT_URI);
            newCol.setHidden(true);
        }

        // add a column to show the differences between old and new values
        if (oldCol != null && newCol != null)
            table.addColumn(new DataMapDiffColumn(table, COLUMN_NAME_DATA_CHANGES, oldCol, newCol));

        if (!noUrl)
        {
            String urlStr = "audit-detailedAuditChanges.view?auditRowId=${rowId}";
            if (!StringUtils.isEmpty(eventName))
                urlStr = urlStr + "&auditEventType=" + eventName;
            DetailsURL url = DetailsURL.fromString(urlStr);
            url.setStrictContainerContextEval(true);
            table.setDetailsURL(url);
        }

    }

    @Override
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

        Map<String,String> stringMap = new LinkedHashMap<>();
        for (Map.Entry<String,?> entry :  properties.entrySet())
        {
            // see AuditHandler.getRecordForInsert(), rather than create a new map just skip values here
            if (entry.getKey().equals(DataIterator.ROWNUMBER_COLUMNNAME) ||
                entry.getKey().equals(ExistingRecordDataIterator.EXISTING_RECORD_COLUMN_NAME) ||
                entry.getKey().equals(ExperimentService.ALIASCOLUMNALIAS))
                continue;
            Object value = entry.getValue();
            if (value instanceof Time time)
            {
                String formatted = DateUtil.formatIsoLongTime(time);
                stringMap.put(entry.getKey(), formatted);
            }
            else if (value instanceof Date date)
            {
                // Issue 35002 - normalize Date values to avoid Timestamp/Date toString differences
                // Issue 36472 - use iso format to show date-time values
                String formatted = DateUtil.toISO(date);
                stringMap.put(entry.getKey(), formatted);
            }
            else if (value instanceof java.sql.Array arr)
            {
                // GitHub Issue 1073: Updating a List MVTC field shows array in audit for values with quotes
                var arrayVal = MultiChoice.Converter.getInstance().convert(MultiChoice.Array.class, arr);
                stringMap.put(entry.getKey(), PageFlowUtil.joinValuesToStringForExport(arrayVal));
            }
            else
                stringMap.put(entry.getKey(), value == null ? null : value.toString());
        }
        return PageFlowUtil.toQueryString(stringMap.entrySet());
    }

    public int moveEvents(Container targetContainer, String idColumnName, Collection<?> ids)
    {
        return Table.updateContainer(createStorageTableInfo(), idColumnName, ids, targetContainer, null, false);
    }
}
