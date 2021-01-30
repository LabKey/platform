package org.labkey.api.specimen;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;

import java.util.Set;

public class SpecimenQuerySchema extends UserSchema
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

    private final UserSchema _studySchema;
    private final Study _study;

    public SpecimenQuerySchema(Study study, UserSchema studySchema)
    {
        super(studySchema.getName(), studySchema.getDescription(), studySchema.getUser(), studySchema.getContainer(), studySchema.getDbSchema());
        _study = study;
        _studySchema = studySchema;
    }

    public static SpecimenQuerySchema get(Study study, User user)
    {
        return new SpecimenQuerySchema(study, StudyService.get().getStudyQuerySchema(study, user));
    }

    public Study getStudy()
    {
        return _study;
    }

    @Override
    public @Nullable TableInfo createTable(String name, ContainerFilter cf)
    {
        return _studySchema.createTable(name, cf);
    }

    @Override
    public Set<String> getTableNames()
    {
        return _studySchema.getTableNames();
    }
}
