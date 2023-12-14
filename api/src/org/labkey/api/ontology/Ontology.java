package org.labkey.api.ontology;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.util.Path;

import jakarta.validation.constraints.NotNull;
import java.util.Date;

public interface Ontology // extends Entity
{
    default Container getContainer()
    {
        return ContainerManager.getForId(getContainerId());
    }

    default String getContainerId()
    {
        return ContainerManager.getSharedContainer().getId();
    }

    int getCreatedBy();

    Date getCreated();

    int getModifiedBy();

    Date getModified();

    int getRowId();

    String getName();

    String getAbbreviation();

    String getDescription();

    OntologyProvider getProvider();

    String getProviderConfiguration();

    @NotNull ConceptPath getConceptPath();
}
