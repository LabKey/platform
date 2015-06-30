/*
 * Copyright (c) 2013-2015 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.audit.query.DefaultAuditTypeTable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainAuditViewFactory;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
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
        return "List events";
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
                            return new DomainAuditViewFactory.DomainColumn(colInfo, containerCol, nameCol);
                        }
                    });
                }
            }
        };

        DetailsURL url = DetailsURL.fromString("list/listItemDetails.view?listId=${listId}&entityId=${listItemEntityId}&rowId=${rowId}");
        table.setDetailsURL(url);

        return table;
    }


    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return defaultVisibleColumns;
    }

    @Override
    public <K extends AuditTypeEvent> K convertEvent(AuditLogEvent event)
    {
        ListAuditEvent bean = new ListAuditEvent();
        copyStandardFields(bean, event);

        if (event.getIntKey1() != null)
            bean.setListId(event.getIntKey1());

        bean.setListDomainUri(event.getKey1());
        bean.setListItemEntityId(event.getKey2());
        bean.setListName(event.getKey3());

        return (K)bean;
    }

    @Override
    public <K extends AuditTypeEvent> K convertEvent(AuditLogEvent event, @Nullable Map<String, Object> dataMap)
    {
        ListAuditEvent bean = convertEvent(event);

        if (dataMap != null)
        {
            if (dataMap.containsKey(ListAuditDomainKind.OLD_RECORD_PROP_NAME))
                bean.setOldRecordMap(String.valueOf(dataMap.get(ListAuditDomainKind.OLD_RECORD_PROP_NAME)));
            if (dataMap.containsKey(ListAuditDomainKind.NEW_RECORD_PROP_NAME))
                bean.setNewRecordMap(String.valueOf(dataMap.get(ListAuditDomainKind.NEW_RECORD_PROP_NAME)));
        }
        return (K)bean;
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
            fields.add(createPropertyDescriptor(COLUMN_NAME_LIST_ITEM_ENTITY_ID, PropertyType.STRING)); // UNDONE: is needed ? .setEntityId(true));
            fields.add(createPropertyDescriptor(COLUMN_NAME_LIST_NAME, PropertyType.STRING));
            fields.add(createPropertyDescriptor(OLD_RECORD_PROP_NAME, PropertyType.STRING));
            fields.add(createPropertyDescriptor(NEW_RECORD_PROP_NAME, PropertyType.STRING));
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
    }
}
