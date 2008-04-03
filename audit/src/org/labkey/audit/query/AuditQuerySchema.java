package org.labkey.audit.query;

import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.audit.AuditSchema;
import org.labkey.audit.model.LogManager;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Oct 5, 2007
 */
public class AuditQuerySchema extends UserSchema
{
    public static final String SCHEMA_NAME = "auditLog";
    public static final String AUDIT_TABLE_NAME = "audit";

    static public void register()
    {
        DefaultSchema.registerProvider(SCHEMA_NAME, new DefaultSchema.SchemaProvider() {
            public QuerySchema getSchema(DefaultSchema schema)
            {
                return new AuditQuerySchema(schema.getUser(), schema.getContainer());
            }
        });
    }

    public AuditQuerySchema(User user, Container container)
    {
        super(SCHEMA_NAME, user, container, AuditSchema.getInstance().getSchema());
    }

    public Set<String> getTableNames()
    {
        return new HashSet<String>(Arrays.asList(AUDIT_TABLE_NAME));
    }

    public TableInfo getTable(String name, String alias)
    {
        return new AuditLogTable(this, LogManager.get().getTinfoAuditLog(), name);
    }
}
