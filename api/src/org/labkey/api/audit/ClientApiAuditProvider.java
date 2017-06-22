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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.audit.query.DefaultAuditTypeTable;
import org.labkey.api.data.PropertyStorageSpec.Index;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.PageFlowUtil;

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
public class ClientApiAuditProvider extends AbstractAuditTypeProvider implements AuditTypeProvider
{
    public static final String EVENT_TYPE = "Client API Actions";

    public static final String COLUMN_NAME_SUBTYPE = "SubType";
    public static final String COLUMN_NAME_STRING1 = "String1";
    public static final String COLUMN_NAME_STRING2 = "String2";
    public static final String COLUMN_NAME_STRING3 = "String3";
    public static final String COLUMN_NAME_INT1 = "Int1";
    public static final String COLUMN_NAME_INT2 = "Int2";
    public static final String COLUMN_NAME_INT3 = "Int3";

    static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();

    static {
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_IMPERSONATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_PROJECT_ID));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_SUBTYPE));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_COMMENT));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_STRING1));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_STRING2));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_STRING3));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_INT1));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_INT2));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_INT3));
    }


    @Override
    protected AbstractAuditDomainKind getDomainKind()
    {
        return new ClientApiAuditDomainKind();
    }

    @Override
    public String getEventName()
    {
        return EVENT_TYPE;
    }

    @Override
    public String getLabel()
    {
        return EVENT_TYPE;
    }

    @Override
    public String getDescription()
    {
        return "Information about audit events created through the client API.";
    }

    @Override
    public Map<FieldKey, String> legacyNameMap()
    {
        Map<FieldKey, String> legacyNames = super.legacyNameMap();

        // 'key1' mapped to 'subtype' and other 'keyN' are mapped to 'stringN-1'
        legacyNames.put(FieldKey.fromParts("key1"), COLUMN_NAME_SUBTYPE);
        legacyNames.put(FieldKey.fromParts("key2"), COLUMN_NAME_STRING1);
        legacyNames.put(FieldKey.fromParts("key3"), COLUMN_NAME_STRING2);
        legacyNames.put(FieldKey.fromParts("intKey1"), COLUMN_NAME_INT1);
        legacyNames.put(FieldKey.fromParts("intKey2"), COLUMN_NAME_INT2);
        legacyNames.put(FieldKey.fromParts("intKey3"), COLUMN_NAME_INT3);
        return legacyNames;
    }

    @Override
    public TableInfo createTableInfo(UserSchema userSchema)
    {
        DefaultAuditTypeTable table = new DefaultAuditTypeTable(this, createStorageTableInfo(), userSchema, defaultVisibleColumns)
        {
            @Override
            public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
            {
                // Don't allow deletes or updates for audit events, and don't let guests insert.
                // AuditQueryView disables the insert and import buttons in the html UI, but
                // this permission check allows the LABKEY.Query.insertRows() api to still work.
                return ((perm.equals(InsertPermission.class) && !isGuest(user)) || perm.equals(ReadPermission.class)) &&
                        getContainer().hasPermission(user, perm);
            }

            private boolean isGuest(UserPrincipal user)
            {
                return user instanceof User && user.isGuest();
            }
        };

        return table;
    }

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return defaultVisibleColumns;
    }

    @Override
    public <K extends AuditTypeEvent> Class<K> getEventClass()
    {
        return (Class<K>)ClientApiAuditEvent.class;
    }

    public static class ClientApiAuditEvent extends AuditTypeEvent
    {
        private String _subType;
        private String _string1;
        private String _string2;
        private String _string3;
        private int _int1;
        private int _int2;
        private int _int3;

        public ClientApiAuditEvent()
        {
            super();
        }

        public ClientApiAuditEvent(String container, String comment)
        {
            super(EVENT_TYPE, container, comment);
        }

        public String getSubType()
        {
            return _subType;
        }

        public void setSubType(String subType)
        {
            _subType = subType;
        }

        public String getString1()
        {
            return _string1;
        }

        public void setString1(String string1)
        {
            _string1 = string1;
        }

        public String getString2()
        {
            return _string2;
        }

        public void setString2(String string2)
        {
            _string2 = string2;
        }

        public String getString3()
        {
            return _string3;
        }

        public void setString3(String string3)
        {
            _string3 = string3;
        }

        public int getInt1()
        {
            return _int1;
        }

        public void setInt1(int int1)
        {
            _int1 = int1;
        }

        public int getInt2()
        {
            return _int2;
        }

        public void setInt2(int int2)
        {
            _int2 = int2;
        }

        public int getInt3()
        {
            return _int3;
        }

        public void setInt3(int int3)
        {
            _int3 = int3;
        }

        @Override
        public Map<String, Object> getAuditLogMessageElements()
        {
            Map<String, Object> elements = new LinkedHashMap<>();
            if (getSubType() != null)
                elements.put("subType", getSubType());
            if (getString1() != null)
                elements.put("string1", getString1());
            if (getString2() != null)
                elements.put("string2", getString2());
            if (getString3() != null)
                elements.put("string3", getString3());
            elements.put("int1", getInt1());
            elements.put("int2", getInt2());
            elements.put("int3", getInt3());
            elements.putAll(super.getAuditLogMessageElements());
            return elements;
        }
    }

    public static class ClientApiAuditDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "ClientApiAuditDomain";
        public static String NAMESPACE_PREFIX = "Audit-" + NAME;

        private final Set<PropertyDescriptor> _fields;

        public ClientApiAuditDomainKind()
        {
            super(EVENT_TYPE);

            Set<PropertyDescriptor> fields = new LinkedHashSet<>();
            fields.add(createPropertyDescriptor(COLUMN_NAME_SUBTYPE, PropertyType.STRING, null, null, false, 64));
            fields.add(createPropertyDescriptor(COLUMN_NAME_STRING1, PropertyType.STRING));
            fields.add(createPropertyDescriptor(COLUMN_NAME_STRING2, PropertyType.STRING));
            fields.add(createPropertyDescriptor(COLUMN_NAME_STRING3, PropertyType.STRING));
            fields.add(createPropertyDescriptor(COLUMN_NAME_INT1, PropertyType.INTEGER));
            fields.add(createPropertyDescriptor(COLUMN_NAME_INT2, PropertyType.INTEGER));
            fields.add(createPropertyDescriptor(COLUMN_NAME_INT3, PropertyType.INTEGER));
            _fields = Collections.unmodifiableSet(fields);
        }

        @Override
        public Set<PropertyDescriptor> getProperties()
        {
            return _fields;
        }

        @Override
        public Set<Index> getPropertyIndices(Domain domain)
        {
            return PageFlowUtil.set(new Index(false, COLUMN_NAME_SUBTYPE));
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
