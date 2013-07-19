package org.labkey.api.audit;

import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.audit.query.DefaultAuditTypeTable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: 7/11/13
 */
public abstract class AbstractAuditTypeProvider implements AuditTypeProvider
{
    public static final String QUERY_SCHEMA_NAME = "auditLog";
    public static final String SCHEMA_NAME = "audit";

    protected abstract DomainKind getDomainKind();

    @Override
    public void initializeProvider(User user)
    {
        Domain domain = getDomain(user);

        // if the domain doesn't exist, create it
        if (domain == null)
        {
            try {
                DomainKind domainKind = getDomainKind();
                PropertyService.get().registerDomainKind(domainKind);

                String domainURI = domainKind.generateDomainURI(QUERY_SCHEMA_NAME, getEventName(), getDomainContainer(), user);
                domain = PropertyService.get().createDomain(getDomainContainer(), domainURI, domainKind.getKindName());
                domain.save(user);
            }
            catch (ChangePropertyDescriptorException e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    protected Domain getDomain(User user)
    {
        DomainKind domainKind = getDomainKind();
        PropertyService.get().registerDomainKind(domainKind);

        String domainURI = domainKind.generateDomainURI(QUERY_SCHEMA_NAME, getEventName(), getDomainContainer(), user);

        return PropertyService.get().getDomain(getDomainContainer(), domainURI);
    }

    @Override
    public TableInfo createTableInfo(UserSchema userSchema)
    {
        Domain domain = getDomain(userSchema.getUser());
        DbSchema dbSchema =  DbSchema.get(SCHEMA_NAME);

        return new DefaultAuditTypeTable(this, domain, dbSchema, userSchema);
    }

    @Override
    public QueryView createDefaultQueryView()
    {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public static Container getDomainContainer()
    {
        return ContainerManager.getSharedContainer();
    }

    protected <K extends AuditTypeEvent> void copyStandardFields(K bean, AuditLogEvent event)
    {
        bean.setImpersonatedBy(event.getImpersonatedBy());
        bean.setEntityId(event.getEntityId());
        bean.setComment(event.getComment());
        bean.setProjectId(event.getProjectId());
        bean.setContainer(event.getContainerId());
        bean.setEventType(event.getEventType());
        bean.setCreated(event.getCreated());
        bean.setCreatedBy(event.getCreatedBy());
    }
}
