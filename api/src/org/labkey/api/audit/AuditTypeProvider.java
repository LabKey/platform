package org.labkey.api.audit;

import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;

import java.util.Map;

/**
 * User: klum
 * Date: 7/8/13
 */
public interface AuditTypeProvider
{
    /**
     * The audit event name associated with this audit provider. Must be
     * unique within the system.
     */
    public String getEventName();
    public String getLabel();
    public String getDescription();

    /**
     * Perform any initialization of the provider at registration time such as
     * domain creation.
     * @param user User useed when saving the backing Domain.
     */
    public void initializeProvider(User user);

    public Domain getDomain();
    public TableInfo createTableInfo(UserSchema schema);

    /**
     * Conversion from legacy untyped event fields to new provider specific
     * fields
     */
    public <K extends AuditTypeEvent> K convertEvent(AuditLogEvent event);

    /**
     * Mapping from old audit table names ("intKey1", "key1", and "Property/Foo" to the new column names.)
     */
    Map<FieldKey, String> legacyNameMap();

}
