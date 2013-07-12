package org.labkey.api.audit;

import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: 7/8/13
 */
public interface AuditTypeProvider
{
    /**
     * The audit event name associated with this audit provider. Must be
     * unique within the system.
     * @return
     */
    public String getEventName();
    public String getLabel();
    public String getDescription();

    /**
     * Perform any initialization of the provider at registration time such as
     * domain creation.
     * @param user
     */
    public void initializeProvider(User user);

    //public DomainKind getDomainKind();
    //public Domain getDomain(Container c, User user);
    public TableInfo createTableInfo(UserSchema schema);
    public QueryView createDefaultQueryView();
}
