package org.labkey.api.audit.query;

import org.labkey.api.security.User;
import org.labkey.api.data.Container;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.query.UserSchema;

import java.util.Set;
import java.util.Collections;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Oct 5, 2007
 */
public class DefaultAuditSchema extends UserSchema
{
    public DefaultAuditSchema(User user, Container container)
    {
        super("default", user, container, CoreSchema.getInstance().getSchema());
    }

    public Set<String> getTableNames()
    {
        return Collections.singleton("default");
    }}
