package org.labkey.assay.plate;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.TemplateInfo;
import org.labkey.api.exp.property.BaseAbstractDomainKind;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.ContainerUser;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class PlateMetadataDomainKind extends BaseAbstractDomainKind
{
    public static final String KIND_NAME = "PlateMetadata";
    public static final String DOMAiN_NAME = "PlateMetadataDomain";
    private static final Set<String> RESERVED_NAMES;
    private static final String PROVISIONED_SCHEMA_NAME = "assaywell";

    static
    {
        RESERVED_NAMES = new CaseInsensitiveHashSet(List.of(
                "rowId",
                "lsid",
                "created",
                "createdBy",
                "modified",
                "modifiedBy"
        ));
    }

    @Override
    public String getKindName()
    {
        return KIND_NAME;
    }

    public static String generateDomainURI(@NotNull Container container)
    {
        return new Lsid(KIND_NAME + ".Folder-" + container.getRowId(), DOMAiN_NAME).toString();
    }

    @Override
    public Set<PropertyStorageSpec> getBaseProperties(Domain domain)
    {
        return Collections.singleton(new PropertyStorageSpec("Lsid", JdbcType.VARCHAR, 200).setNullable(false));
    }

    @Override
    public Domain createDomain(GWTDomain<GWTPropertyDescriptor> domain, JSONObject arguments, Container container, User user, @Nullable TemplateInfo templateInfo)
    {
        try
        {
            String domainURI = generateDomainURI(container);
            Domain metadataDomain = PropertyService.get().createDomain(container, domainURI, domain.getName(), templateInfo);
            metadataDomain.save(user);

            return PropertyService.get().getDomain(container, domainURI);
        }
        catch (Exception e)
        {
            throw UnexpectedException.wrap(e);
        }
    }

    @Override
    public DbSchemaType getSchemaType()
    {
        return DbSchemaType.Provisioned;
    }

    @Override
    public String getStorageSchemaName()
    {
        return PROVISIONED_SCHEMA_NAME;
    }

    public DbSchema getSchema()
    {
        return DbSchema.get(PROVISIONED_SCHEMA_NAME, DbSchemaType.Provisioned);
    }

    @Override
    public DbScope getScope()
    {
        return getSchema().getScope();
    }

    @Override
    public boolean canCreateDefinition(User user, Container container)
    {
        return container.hasPermission(user, AdminPermission.class);
    }

    @Override
    public String getTypeLabel(Domain domain)
    {
        return domain.getName();
    }

    @Override
    public SQLFragment sqlObjectIdsInDomain(Domain domain)
    {
        return null;
    }

    @Override
    public @Nullable ActionURL urlShowData(Domain domain, ContainerUser containerUser)
    {
        return null;
    }

    @Override
    public @Nullable ActionURL urlEditDefinition(Domain domain, ContainerUser containerUser)
    {
        return null;
    }

    @Override
    public Set<String> getReservedPropertyNames(Domain domain, User user)
    {
        return RESERVED_NAMES;
    }

    @Override
    public @Nullable Priority getPriority(String domainURI)
    {
        Lsid lsid = new Lsid(domainURI);
        return getKindName().equals(lsid.getNamespacePrefix()) ? Priority.MEDIUM : null;
    }
}
