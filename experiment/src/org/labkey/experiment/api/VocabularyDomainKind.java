package org.labkey.experiment.api;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.exp.TemplateInfo;
import org.labkey.api.exp.property.AbstractDomainKind;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.vocabulary.security.DesignVocabularyPermission;
import org.labkey.api.writer.ContainerUser;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
* VocabularyDomainKind can be used to hold ad hoc properties.
* */
public class VocabularyDomainKind extends AbstractDomainKind
{
    @Override
    public String getKindName()
    {
        return null;
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
        return null;
    }

    @Override
    public Set<String> getReservedPropertyNames(Domain domain)
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
    public @Nullable Priority getPriority(String object)
    {
        return null;
    }

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

    }

    @Override
    public Domain createDomain(GWTDomain domain, Map<String, Object> arguments, Container container, User user, @Nullable TemplateInfo templateInfo)
    {
        String name = domain.getName();
        if (name == null)
            throw new IllegalArgumentException("SampleSet name required");
        if(container.isWorkbook()) // add labbook
        {
            throw new IllegalArgumentException("Vocabulary can not be created in Workbook folder.");
        }

        return PropertyService.get().createDomain(container, "", domain.getName(), templateInfo);
    }
}
