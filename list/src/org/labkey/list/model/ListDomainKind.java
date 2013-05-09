package org.labkey.list.model;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.AbstractDomainKind;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.ContainerUser;

import java.util.Set;

/**
 * User: Nick
 * Date: 5/8/13
 * Time: 4:12 PM
 */
public class ListDomainKind extends AbstractDomainKind
{
    @Override
    public String getKindName()
    {
        return ListDefinitionImpl.NAMESPACE_PREFIX;
    }

    @Override
    public String getTypeLabel(Domain domain)
    {
        return "HD List '" + domain.getName() + "'";
    }

    @Override
    public SQLFragment sqlObjectIdsInDomain(Domain domain)
    {
        throw new UnsupportedOperationException("sqlObjectIdsInDomain NYI for ListDomainKind");
    }

    @Override
    public ActionURL urlShowData(Domain domain, ContainerUser containerUser)
    {
        ListDefinition listDef = ListService.get().getList(domain);
        if (null == listDef)
            return null;
        return listDef.urlShowData();
    }

    @Nullable
    @Override
    public ActionURL urlEditDefinition(Domain domain, ContainerUser containerUser)
    {
        ListDefinition listDef = ListService.get().getList(domain);
        if (null == listDef)
            return null;
        return listDef.urlShowDefinition();
    }

    @Override
    public Set<String> getReservedPropertyNames(Domain domain)
    {
        return PageFlowUtil.set("ObjectId", "EntityId", "Created", "CreatedBy", "Modified", "ModifiedBy", "LastIndexed");
    }

    @Override
    public Priority getPriority(String object)
    {
        return Priority.MEDIUM; // TODO: Figure out what this does
    }

    @Override
    public Set<PropertyStorageSpec> getBaseProperties()
    {
        return super.getBaseProperties();
    }

    @Override
    public String getStorageSchemaName()
    {
        return ListSchema.getInstance().getSchemaName();
    }

    @Override
    public DbScope getScope()
    {
        return ListSchema.getInstance().getSchema().getScope();
    }

    public static Lsid generateDomainURI(String name, Container c, String entityId)
    {
        String typeURI = "urn:lsid:" + PageFlowUtil.encode(AppProps.getInstance().getDefaultLsidAuthority()) + ":" + ListDefinitionImpl.NAMESPACE_PREFIX + ".Folder-" + c.getRowId() + ":" + PageFlowUtil.encode(name);
        //bug 13131.  uniqueify the lsid for situations where a preexisting list was renamed
        int i = 1;
        String uniqueURI = typeURI;
        while (OntologyManager.getDomainDescriptor(uniqueURI, c) != null)
        {
            uniqueURI = typeURI + '-' + (i++);
        }
        return new Lsid(uniqueURI);
    }

    public static Lsid generateDomainURI(String name, Container container)
    {
        return generateDomainURI(name, container, "");
    }

    public String generateDomainURI(String schemaName, String name, Container container, User user)
    {
        return generateDomainURI(name, container).toString();
    }

}
