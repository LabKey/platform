package org.labkey.api.audit;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.query.DefaultAuditTypeTable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;

import java.util.HashMap;
import java.util.Map;

/**
 * User: klum
 * Date: 7/11/13
 */
public abstract class AbstractAuditTypeProvider implements AuditTypeProvider
{
    public static final String QUERY_SCHEMA_NAME = "auditLog";
    public static final String SCHEMA_NAME = "audit";

    public static final String COLUMN_NAME_ROW_ID = "RowId";
    public static final String COLUMN_NAME_CONTAINER = "Container";
    public static final String COLUMN_NAME_COMMENT = "Comment";
    public static final String COLUMN_NAME_EVENT_TYPE = "EventType";
    public static final String COLUMN_NAME_CREATED = "Created";
    public static final String COLUMN_NAME_CREATED_BY = "CreatedBy";
    public static final String COLUMN_NAME_IMPERSONATED_BY = "ImpersonatedBy";
    public static final String COLUMN_NAME_PROJECT_ID = "ProjectId";
    public static final String COLUMN_NAME_ENTITY_ID = "EntityId";
    public static final String COLUMN_NAME_MESSAGE_ID = "MessageId";

    protected abstract DomainKind getDomainKind();

    @Override
    public void initializeProvider(User user)
    {
        Domain domain = getDomain();

        // if the domain doesn't exist, create it
        if (domain == null)
        {
            try {
                DomainKind domainKind = getDomainKind();
                PropertyService.get().registerDomainKind(domainKind);

                String domainURI = domainKind.generateDomainURI(QUERY_SCHEMA_NAME, getEventName(), getDomainContainer(), null);
                domain = PropertyService.get().createDomain(getDomainContainer(), domainURI, domainKind.getKindName());
                domain.save(user);
            }
            catch (ChangePropertyDescriptorException e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public final Domain getDomain()
    {
        DomainKind domainKind = getDomainKind();
        PropertyService.get().registerDomainKind(domainKind);

        String domainURI = domainKind.generateDomainURI(QUERY_SCHEMA_NAME, getEventName(), getDomainContainer(), null);

        return PropertyService.get().getDomain(getDomainContainer(), domainURI);
    }

    @Override
    public TableInfo createTableInfo(UserSchema userSchema)
    {
        Domain domain = getDomain();
        DbSchema dbSchema =  DbSchema.get(SCHEMA_NAME);

        return new DefaultAuditTypeTable(this, domain, dbSchema, userSchema);
    }

    @Override
    public Map<FieldKey, String> legacyNameMap()
    {
        Map<FieldKey, String> legacyNames = new HashMap<>();
        legacyNames.put(FieldKey.fromParts("ContainerId"), "Container");
        legacyNames.put(FieldKey.fromParts("Date"), "Created");
        return legacyNames;
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

    @Override
    public <K extends AuditTypeEvent> K convertEvent(AuditLogEvent event, @Nullable Map<String, Object> dataMap)
    {
        if (dataMap == null)
            return convertEvent(event);
        else
            throw new IllegalArgumentException("Provider needs to override convertEvent in order to handle a non-null dataMap");
    }
}
