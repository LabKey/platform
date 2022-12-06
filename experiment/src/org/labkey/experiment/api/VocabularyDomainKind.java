package org.labkey.experiment.api;

import org.jetbrains.annotations.Nullable;
import org.json.old.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.DomainNotFoundException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.TemplateInfo;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.exp.property.BaseAbstractDomainKind;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.DomainUtil;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.vocabulary.security.DesignVocabularyPermission;
import org.labkey.api.writer.ContainerUser;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
* VocabularyDomainKind can be used to hold ad hoc properties.
* */
public class VocabularyDomainKind extends BaseAbstractDomainKind
{
    public static final String KIND_NAME = "Vocabulary";

    @Override
    public String getKindName()
    {
        return KIND_NAME;
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
    public ActionURL urlShowData(Domain domain, ContainerUser containerUser)
    {
        return null;
    }

    @Override
    public @Nullable ActionURL urlEditDefinition(Domain domain, ContainerUser containerUser)
    {
        if (!containerUser.getContainer().isContainerFor(ContainerType.DataType.domainDefinitions))
            return null;

        return PageFlowUtil.urlProvider(ExperimentUrls.class).getDomainEditorURL(containerUser.getContainer(), domain);
    }

    @Override
    public Set<String> getReservedPropertyNames(Domain domain, User user)
    {
        Set<String> reservedProperties = new HashSet<>();
        reservedProperties.add("RowId");
        reservedProperties.add("LSID");
        reservedProperties.add("EntityId");
        reservedProperties.add("Container");
        reservedProperties.add("Folder");
        reservedProperties.add("CreatedBy");
        reservedProperties.add("Created");
        reservedProperties.add("ModifiedBy");
        reservedProperties.add("Modified");
        reservedProperties.add("Owner");
        reservedProperties.add("LastIndexed");
        return reservedProperties;
    }

    @Override
    public boolean canEditDefinition(User user, Domain domain)
    {
        return domain.getContainer().hasPermission(user, DesignVocabularyPermission.class);
    }

    @Override
    public boolean canCreateDefinition(User user, Container container)
    {
        return container.hasPermission(user, DesignVocabularyPermission.class);
    }

    @Override
    public boolean canDeleteDefinition(User user, Domain domain)
    {
        return domain.getContainer().hasPermission(user, DesignVocabularyPermission.class);
    }

    @Override
    public void deleteDomain(User user, Domain domain)
    {
        try
        {
            if (domain.getContainer().hasPermission(user, DesignVocabularyPermission.class))
                domain.delete(user);
        }
        catch (DomainNotFoundException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Priority getPriority(String domainURI)
    {
        if (!domainURI.contains(getKindName()))
            return null;
        Lsid lsid = new Lsid(domainURI);
        return getKindName().equals(lsid.getNamespacePrefix()) ? Priority.MEDIUM : null;
    }

    @Override
    public String generateDomainURI(String schemaName, String queryName, Container container, User user)
    {
        // This makes "queryName" synonymous with "vocabularyName" which it isn't, however, this is the
        // interface exposed variant of generateDomainURI() allowing for resolution of a VocabularyDomainKind URI
        // without needing access to the class.
        return generateDomainURI(queryName, container);
    }

    public String generateDomainURI(String vocabularyName, Container container)
    {
        return new Lsid(getKindName()+".Folder-"+container.getRowId(), vocabularyName).toString();
    }

    @Override
    public Domain createDomain(GWTDomain domain, JSONObject arguments, Container container, User user, @Nullable TemplateInfo templateInfo)
    {
        String name = domain.getName();
        if (name == null)
            throw new IllegalArgumentException("Vocabulary name required");

        if (!container.isContainerFor(ContainerType.DataType.domainDefinitions))
        {
            throw new IllegalArgumentException("Vocabulary can not be created in this Container type.");
        }
        String domainURI = generateDomainURI(name, container);

        List<GWTPropertyDescriptor> properties = domain.getFields();
        Domain vocabularyDomain = PropertyService.get().createDomain(container, domainURI, domain.getName(), templateInfo);

        Set<String> propertyUris = new HashSet<>();
        Map<DomainProperty, Object> defaultValues = new HashMap<>();
        try
        {
            for (GWTPropertyDescriptor pd : properties)
            {
                DomainUtil.addProperty(vocabularyDomain, pd, defaultValues, propertyUris, null);
            }
            vocabularyDomain.save(user);
        }
        catch (ChangePropertyDescriptorException e)
        {
            throw new RuntimeException(e);
        }

        return PropertyService.get().getDomain(container, domainURI);
    }
}
