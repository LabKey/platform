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
package org.labkey.list.model;

import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.audit.query.DefaultAuditTypeTable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainAuditProvider;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringExpressionFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: klum
 * Date: 7/21/13
 */
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

    @Override
    protected AbstractAuditDomainKind getDomainKind()
    {
        return new ListAuditDomainKind();
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
    public TableInfo createTableInfo(UserSchema userSchema)
    {
        DefaultAuditTypeTable table = new DefaultAuditTypeTable(this, createStorageTableInfo(), userSchema, defaultVisibleColumns)
        {
            @Override
            protected void initColumn(ColumnInfo col)
            {
                if (COLUMN_NAME_LIST_DOMAIN_URI.equalsIgnoreCase(col.getName()))
                {
                    final ColumnInfo nameCol = getColumn(FieldKey.fromParts(COLUMN_NAME_LIST_NAME));
                    final ColumnInfo containerCol = getColumn(FieldKey.fromParts(COLUMN_NAME_CONTAINER));

                    col.setLabel("List");
                    col.setDisplayColumnFactory(new DisplayColumnFactory()
                    {
                        public DisplayColumn createRenderer(ColumnInfo colInfo)
                        {
                            return new DomainAuditProvider.DomainColumn(colInfo, containerCol, nameCol);
                        }
                    });
                }
            }
        };

        // Render a details URL only for rows that have a listItemEntityId
        DetailsURL url = DetailsURL.fromString("list/listItemDetails.view?listId=${listId}&entityId=${listItemEntityId}&rowId=${rowId}", null, StringExpressionFactory.AbstractStringExpression.NullValueBehavior.NullResult);
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

    public static class ListAuditEvent extends AuditTypeEvent
    {
        private int _listId;
        private String _listDomainUri;
        private String _listItemEntityId;
        private String _listName;
        private String _oldRecordMap;
        private String _newRecordMap;

        public ListAuditEvent()
        {
            super();
        }

        public ListAuditEvent(String container, String comment)
        {
            super(ListManager.LIST_AUDIT_EVENT, container, comment);
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

        public String getOldRecordMap()
        {
            return _oldRecordMap;
        }

        public void setOldRecordMap(String oldRecordMap)
        {
            _oldRecordMap = oldRecordMap;
        }

        public String getNewRecordMap()
        {
            return _newRecordMap;
        }

        public void setNewRecordMap(String newRecordMap)
        {
            _newRecordMap = newRecordMap;
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
            fields.add(createPropertyDescriptor(OLD_RECORD_PROP_NAME, PropertyType.STRING, -1));        // varchar max
            fields.add(createPropertyDescriptor(NEW_RECORD_PROP_NAME, PropertyType.STRING, -1));        // varchar max
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
            return PageFlowUtil.set(new PropertyStorageSpec.Index(false, COLUMN_NAME_LIST_ITEM_ENTITY_ID));
        }
    }
}
