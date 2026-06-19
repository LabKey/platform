/*
 * Copyright (c) 2013-2019 LabKey Corporation
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
package org.labkey.list.model;

import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.audit.DetailedAuditTypeEvent;
import org.labkey.api.audit.TransactionAuditProvider;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.audit.query.DefaultAuditTypeTable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainAuditProvider;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.util.StringExpressionFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ListAuditProvider extends AbstractAuditTypeProvider implements AuditTypeProvider
{
    public static final String COLUMN_NAME_LIST_ID = "ListId";
    public static final String COLUMN_NAME_LIST_DOMAIN_URI = "ListDomainUri";
    public static final String COLUMN_NAME_LIST_ITEM_ENTITY_ID = "ListItemEntityId";
    public static final String COLUMN_NAME_LIST_NAME = "ListName";

    private static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();

    static
    {
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_IMPERSONATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_PROJECT_ID));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_LIST_DOMAIN_URI));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_COMMENT));
    }

    public ListAuditProvider()
    {
        super(new ListAuditDomainKind());
    }

    @Override
    public String getEventName()
    {
        return ListManager.LIST_AUDIT_EVENT;
    }

    @Override
    public String getLabel()
    {
        return "List events";
    }

    @Override
    public String getDescription()
    {
        return "Data about list creation, deletion, insertion, etc.";
    }

    @Override
    public TableInfo createTableInfo(UserSchema userSchema, ContainerFilter cf)
    {
        DefaultAuditTypeTable table = new DefaultAuditTypeTable(this, createStorageTableInfo(), userSchema, cf, defaultVisibleColumns)
        {
            @Override
            protected void initColumn(MutableColumnInfo col)
            {
                if (COLUMN_NAME_LIST_DOMAIN_URI.equalsIgnoreCase(col.getName()))
                {
                    col.setLabel("List");
                    col.setDisplayColumnFactory(colInfo -> new DomainAuditProvider.DomainColumn(colInfo, COLUMN_NAME_CONTAINER, COLUMN_NAME_LIST_NAME));
                }
            }
        };
        appendValueMapColumns(table, null, true);

        // Render a details URL only for rows that have a listItemEntityId
        DetailsURL url = DetailsURL.fromString("list/listItemDetails.view?listId=${listId}&name=${listName}&entityId=${listItemEntityId}&rowId=${rowId}", null, StringExpressionFactory.AbstractStringExpression.NullValueBehavior.NullResult);
        table.setDetailsURL(url);

        return table;
    }


    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return defaultVisibleColumns;
    }

    @Override
    public Map<FieldKey, String> legacyNameMap()
    {
        Map<FieldKey, String> legacyMap =  super.legacyNameMap();
        legacyMap.put(FieldKey.fromParts("intKey1"), COLUMN_NAME_LIST_ID);
        legacyMap.put(FieldKey.fromParts("key1"), COLUMN_NAME_LIST_DOMAIN_URI);
        legacyMap.put(FieldKey.fromParts("key2"), COLUMN_NAME_LIST_ITEM_ENTITY_ID);
        legacyMap.put(FieldKey.fromParts("key3"), COLUMN_NAME_LIST_NAME);
        legacyMap.put(FieldKey.fromParts("Property", AbstractAuditDomainKind.OLD_RECORD_PROP_NAME), AbstractAuditDomainKind.OLD_RECORD_PROP_NAME);
        legacyMap.put(FieldKey.fromParts("Property", AbstractAuditDomainKind.NEW_RECORD_PROP_NAME), AbstractAuditDomainKind.NEW_RECORD_PROP_NAME);
        // Unused Property/oldRecord and Property/newRecord columns should just be migrated to the oldRecordMap and newRecordMap columns
        legacyMap.put(FieldKey.fromParts("Property", "OldRecord"), AbstractAuditDomainKind.OLD_RECORD_PROP_NAME);
        legacyMap.put(FieldKey.fromParts("Property", "NewRecord"), AbstractAuditDomainKind.NEW_RECORD_PROP_NAME);
        return legacyMap;
    }

    @Override
    public <K extends AuditTypeEvent> Class<K> getEventClass()
    {
        return (Class<K>)ListAuditEvent.class;
    }

    public int moveEvents(Container targetContainer, List<String> listRowEntityIds)
    {
        return moveEvents(targetContainer, COLUMN_NAME_LIST_ITEM_ENTITY_ID, listRowEntityIds);
    }

    /**
     * Verifies that a loaded {@link ListAuditEvent} actually pertains to the requested list and container before its
     * old/new record maps are surfaced to the caller.
     * <p>
     * Called by {@code ListController.ListItemDetailsAction} to close CWE-639 (IDOR via user-controlled {@code rowId}):
     * without this predicate, a user with audit-read access in container A could pass {@code listId=X&rowId=N-for-Y}
     * and have List Y's audit payload render inside List X's details page. The container check is defense-in-depth
     * in case the audit schema's default ContainerFilter is ever changed away from {@code Current}.
     */
    public static boolean auditEventMatchesList(@Nullable ListAuditEvent event, int expectedListId, @NotNull Container expectedContainer)
    {
        return event != null
            && event.getListId() == expectedListId
            && Objects.equals(event.getContainer(), expectedContainer);
    }

    public static class ListAuditEvent extends DetailedAuditTypeEvent
    {
        private int _listId;
        private String _listDomainUri;
        private String _listItemEntityId;
        private String _listName;

        /** Important for reflection-based instantiation */
        @SuppressWarnings("unused")
        public ListAuditEvent()
        {
            super();
        }

        public ListAuditEvent(Container container, String comment, ListDefinitionImpl list)
        {
            super(ListManager.LIST_AUDIT_EVENT, container, comment);
            setListDomainUri(list.getDomain().getTypeURI());
            setListId(list.getListId());
            setListName(list.getName());
            setTransactionEvent(TransactionAuditProvider.getCurrentTransactionAuditEvent(), ListManager.LIST_AUDIT_EVENT);
        }

        public int getListId()
        {
            return _listId;
        }

        public void setListId(int listId)
        {
            _listId = listId;
        }

        public String getListDomainUri()
        {
            return _listDomainUri;
        }

        public void setListDomainUri(String listDomainUri)
        {
            _listDomainUri = listDomainUri;
        }

        public String getListItemEntityId()
        {
            return _listItemEntityId;
        }

        public void setListItemEntityId(String listItemEntityId)
        {
            _listItemEntityId = listItemEntityId;
        }

        public String getListName()
        {
            return _listName;
        }

        public void setListName(String listName)
        {
            _listName = listName;
        }

        @Override
        public Map<String, Object> getAuditLogMessageElements()
        {
            Map<String, Object> elements = new LinkedHashMap<>();
            elements.put("list",  getListName() + " (" + getListId() + ")");
            elements.put("listDomainUri", getListDomainUri());
            elements.put("listItemEntityId", getListItemEntityId());
            // N.B. oldRecordMap and newRecordMap can be very large and are not included here
            elements.putAll(super.getAuditLogMessageElements());
            return elements;
        }
    }

    public static class TestCase extends Assert
    {
        private static final String CONTAINER_A = "11111111-1111-1111-1111-111111111111";
        private static final String CONTAINER_B = "22222222-2222-2222-2222-222222222222";

        @Test
        public void matches_whenListIdAndContainerAgree()
        {
            Container c = testContainer(CONTAINER_A);
            assertTrue(auditEventMatchesList(eventFor(42, c), 42, c));
        }

        @Test
        public void rejects_nullEvent()
        {
            assertFalse(auditEventMatchesList(null, 42, testContainer(CONTAINER_A)));
        }

        @Test
        public void rejects_wrongListId()
        {
            // Event's listId is 99, but URL asked for list 42: the cross-list-in-same-container
            // attack. Without this check, List 42's details page renders List 99's payload.
            Container c = testContainer(CONTAINER_A);
            assertFalse(auditEventMatchesList(eventFor(99, c), 42, c));
        }

        @Test
        public void rejects_wrongContainer()
        {
            // Defense-in-depth: even if a future audit-schema CF change ever let an event from
            // another container leak through getAuditEvent(), this check would still block it.
            assertFalse(auditEventMatchesList(
                eventFor(42, testContainer(CONTAINER_B)), 42, testContainer(CONTAINER_A)));
        }

        @Test
        public void rejects_nullEventContainer()
        {
            // Event present but its container is null (could happen if the audit event was
            // hand-constructed or persisted without a container): must not match.
            assertFalse(auditEventMatchesList(eventFor(42, null), 42, testContainer(CONTAINER_A)));
        }

        private static Container testContainer(String guid)
        {
            // Container.equals compares the GUID id, so any non-null parent / name / rowId
            // values are fine for this test; only the id field is read by the assertion.
            return new Container(null, "junit-" + guid.substring(0, 8), guid, 0, 0, new Date(0), 0, false);
        }

        private static ListAuditEvent eventFor(int listId, @Nullable Container container)
        {
            ListAuditEvent event = new ListAuditEvent();
            event.setListId(listId);
            if (container != null)
                event.setContainer(container);
            return event;
        }
    }

    public static class ListAuditDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "ListAuditDomain";
        public static String NAMESPACE_PREFIX = "Audit-" + NAME;

        private final Set<PropertyDescriptor> _fields;

        public ListAuditDomainKind()
        {
            super(ListManager.LIST_AUDIT_EVENT);

            Set<PropertyDescriptor> fields = new LinkedHashSet<>();
            fields.add(createPropertyDescriptor(COLUMN_NAME_LIST_ID, PropertyType.INTEGER));
            fields.add(createPropertyDescriptor(COLUMN_NAME_LIST_DOMAIN_URI, PropertyType.STRING));
            // Choose a length that should be much larger than necessary to give extra buffer, but still small enough
            // to be indexed
            fields.add(createPropertyDescriptor(COLUMN_NAME_LIST_ITEM_ENTITY_ID, PropertyType.STRING, 300)); // UNDONE: is needed ? .setEntityId(true));
            fields.add(createPropertyDescriptor(COLUMN_NAME_LIST_NAME, PropertyType.STRING));
            fields.add(createPropertyDescriptor(COLUMN_NAME_TRANSACTION_ID, PropertyType.BIGINT));
            fields.add(createOldDataMapPropertyDescriptor());
            fields.add(createNewDataMapPropertyDescriptor());
            _fields = Collections.unmodifiableSet(fields);
        }

        @Override
        public Set<PropertyDescriptor> getProperties()
        {
            return _fields;
        }

        @Override
        protected String getNamespacePrefix()
        {
            return NAMESPACE_PREFIX;
        }

        @Override
        public String getKindName()
        {
            return NAME;
        }

        @Override
        public Set<PropertyStorageSpec.Index> getPropertyIndices(Domain domain)
        {
            Set<PropertyStorageSpec.Index> indexes = super.getPropertyIndices(domain);
            indexes.add(new PropertyStorageSpec.Index(false, COLUMN_NAME_LIST_ITEM_ENTITY_ID));
            return indexes;
        }
    }
}
