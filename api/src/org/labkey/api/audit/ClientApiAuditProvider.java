package org.labkey.api.audit;

import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.query.FieldKey;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
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

    @Override
    protected DomainKind getDomainKind()
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
    public <K extends AuditTypeEvent> K convertEvent(AuditLogEvent event)
    {
        ClientApiAuditEvent bean = new ClientApiAuditEvent();
        copyStandardFields(bean, event);

        // 'key1' mapped to 'subtype' and other 'keyN' are mapped to 'stringN-1'
        bean.setSubType(event.getKey1());
        bean.setString1(event.getKey2());
        bean.setString2(event.getKey3());
        bean.setInt1(event.getIntKey1());
        bean.setInt2(event.getIntKey2());
        bean.setInt3(event.getIntKey3());

        return (K)bean;
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
    }

    public static class ClientApiAuditDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "ClientApiAuditDomain";
        public static String NAMESPACE_PREFIX = "Audit-" + NAME;
        private static final Set<PropertyStorageSpec> _fields = new LinkedHashSet<>();

        static {
            _fields.add(createFieldSpec(COLUMN_NAME_SUBTYPE, JdbcType.VARCHAR));
            _fields.add(createFieldSpec(COLUMN_NAME_STRING1, JdbcType.VARCHAR));
            _fields.add(createFieldSpec(COLUMN_NAME_STRING2, JdbcType.VARCHAR));
            _fields.add(createFieldSpec(COLUMN_NAME_STRING3, JdbcType.VARCHAR));
            _fields.add(createFieldSpec(COLUMN_NAME_INT1, JdbcType.INTEGER));
            _fields.add(createFieldSpec(COLUMN_NAME_INT2, JdbcType.INTEGER));
            _fields.add(createFieldSpec(COLUMN_NAME_INT3, JdbcType.INTEGER));
        }

        @Override
        protected Set<PropertyStorageSpec> getColumns()
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
