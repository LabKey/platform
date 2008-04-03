package org.labkey.study.requirements;

import org.labkey.api.data.TableInfo;
import org.labkey.study.StudySchema;
import org.labkey.study.model.*;

import java.util.List;

/**
 * User: brittp
 * Date: Jun 4, 2007
 * Time: 4:29:27 PM
 */
public class SpecimenRequestRequirementProvider extends DefaultRequirementProvider<SampleRequestRequirement, SampleRequestActor>
{
    public SpecimenRequestRequirementProvider()
    {
        super(SampleRequestRequirement.class, SampleRequestActor.class);
    }

    public RequirementType[] getRequirementTypes()
    {
        return SpecimenRequestRequirementType.values();
    }

    protected String getOwnerEntityIdColumnName()
    {
        return "OwnerEntityId";
    }

    protected Object getPrimaryKeyValue(SampleRequestRequirement requirement)
    {
        return requirement.getRowId();
    }

    protected String getActorSortColumnName()
    {
        return "SortOrder";
    }

    protected TableInfo getActorTableInfo()
    {
        return StudySchema.getInstance().getTableInfoSampleRequestActor();
    }

    protected TableInfo getRequirementTableInfo()
    {
        return StudySchema.getInstance().getTableInfoSampleRequestRequirement();
    }

    protected SampleRequestRequirement createMutable(SampleRequestRequirement requirement)
    {
        return requirement.createMutable();
    }

    protected List<SampleRequestRequirement> generateRequirementsFromDefault(RequirementOwner owner, SampleRequestRequirement defaultRequirement, RequirementType type)
    {
        return ((SpecimenRequestRequirementType) type).generateRequirements((SampleRequest) owner, defaultRequirement);
    }
}
