package org.labkey.api.specimen;

import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;

public class SpecimenQuerySchema
{
    public static final String SIMPLE_SPECIMEN_TABLE_NAME = "SimpleSpecimen";
    public static final String SPECIMEN_DETAIL_TABLE_NAME = "SpecimenDetail";
    public static final String SPECIMEN_WRAP_TABLE_NAME = "SpecimenWrap";
    public static final String SPECIMEN_EVENT_TABLE_NAME = "SpecimenEvent";
    public static final String SPECIMEN_SUMMARY_TABLE_NAME = "SpecimenSummary";
    public static final String PARTICIPANT_GROUP_COHORT_UNION_TABLE_NAME = "ParticipantGroupCohortUnion";
    public static final String LOCATION_SPECIMEN_LIST_TABLE_NAME = "LocationSpecimenList";
    public static final String LOCATION_TABLE_NAME = "Location";
    public static final String SPECIMEN_PRIMARY_TYPE_TABLE_NAME = "SpecimenPrimaryType";
    public static final String SPECIMEN_DERIVATIVE_TABLE_NAME = "SpecimenDerivative";
    public static final String SPECIMEN_ADDITIVE_TABLE_NAME = "SpecimenAdditive";
    public static final String VIAL_TABLE_NAME = "Vial";

    public static UserSchema get(Study study, User user)
    {
        return StudyService.get().getStudyQuerySchema(study, user);
    }
}
