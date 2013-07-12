package org.labkey.core.query;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: 7/9/13
 */
public class UserAuditProvider extends AbstractAuditTypeProvider implements AuditTypeProvider
{
    @Override
    public String getEventName()
    {
        return UserManager.USER_AUDIT_EVENT;
    }

    @Override
    public String getLabel()
    {
        return "User events";
    }

    @Override
    public String getDescription()
    {
        return "Describes information about user logins, impersonations, and modifications.";
    }

    @Override
    protected DomainKind getDomainKind()
    {
        return new UserAuditDomainKind();
    }

    public static class UserAuditDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "UserAuditDomain";
        public static String NAMESPACE_PREFIX = "Audit-" + NAME;

        private static final Set<PropertyStorageSpec> _fields = new LinkedHashSet<>();

        static {
            _fields.add(createFieldSpec("User", JdbcType.INTEGER));
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
