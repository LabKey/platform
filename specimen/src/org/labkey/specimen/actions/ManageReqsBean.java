package org.labkey.specimen.actions;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.specimen.model.SpecimenRequestActor;
import org.labkey.specimen.requirements.RequirementProvider;
import org.labkey.specimen.requirements.SpecimenRequestRequirement;
import org.labkey.specimen.requirements.SpecimenRequestRequirementProvider;
import org.labkey.specimen.requirements.SpecimenRequestRequirementType;

public class ManageReqsBean
{
    private final SpecimenRequestRequirement[] _providerRequirements;
    private final SpecimenRequestRequirement[] _receiverRequirements;
    private final SpecimenRequestRequirement[] _generalRequirements;
    private final SpecimenRequestRequirement[] _originatorRequirements;
    private final SpecimenRequestActor[] _actors;

    public ManageReqsBean(User user, Container container)
    {
        RequirementProvider<SpecimenRequestRequirement, SpecimenRequestActor> provider =
                SpecimenRequestRequirementProvider.get();
        _originatorRequirements = provider.getDefaultRequirements(container,
                SpecimenRequestRequirementType.ORIGINATING_SITE);
        _providerRequirements = provider.getDefaultRequirements(container,
                SpecimenRequestRequirementType.PROVIDING_SITE);
        _receiverRequirements = provider.getDefaultRequirements(container,
                SpecimenRequestRequirementType.RECEIVING_SITE);
        _generalRequirements = provider.getDefaultRequirements(container,
                SpecimenRequestRequirementType.NON_SITE_BASED);
        _actors = provider.getActors(container);
    }

    public SpecimenRequestRequirement[] getGeneralRequirements()
    {
        return _generalRequirements;
    }

    public SpecimenRequestRequirement[] getReceiverRequirements()
    {
        return _receiverRequirements;
    }

    public SpecimenRequestRequirement[] getProviderRequirements()
    {
        return _providerRequirements;
    }

    public SpecimenRequestRequirement[] getOriginatorRequirements()
    {
        return _originatorRequirements;
    }

    public SpecimenRequestActor[] getActors()
    {
        return _actors;
    }
}
