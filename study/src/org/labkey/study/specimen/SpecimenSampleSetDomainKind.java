package org.labkey.study.specimen;

import org.labkey.api.data.Container;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.ContainerUser;
import org.labkey.experiment.api.SampleSetDomainKind;

import static org.labkey.study.SpecimenManager.STUDY_SPECIMENS_SAMPLE_SET_NAME;

public class SpecimenSampleSetDomainKind extends SampleSetDomainKind
{
    public SpecimenSampleSetDomainKind()
    {
    }

    @Override
    public Priority getPriority(String domainURI)
    {
        Lsid lsid = new Lsid(domainURI);
        String prefix = lsid.getNamespacePrefix();
        String name = lsid.getObjectId();
        if ("SampleSet".equals(prefix) && STUDY_SPECIMENS_SAMPLE_SET_NAME.equals(name))
            return Priority.HIGH;
        return null;
    }

    @Override
    public String getStorageSchemaName()
    {
        // Don't provision table with no properties
        return null;
    }

    @Override
    public ActionURL urlEditDefinition(Domain domain, ContainerUser containerUser)
    {
        return null;
    }

    @Override
    public boolean canEditDefinition(User user, Domain domain)
    {
        return false;
    }

    @Override
    public boolean canCreateDefinition(User user, Container container)
    {
        return false;
    }

    @Override
    public boolean canDeleteDefinition(User user, Domain domain)
    {
        return false;
    }

    @Override
    public ActionURL urlCreateDefinition(String schemaName, String queryName, Container container, User user)
    {
        return null;
    }
}
