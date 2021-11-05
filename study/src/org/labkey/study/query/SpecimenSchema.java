package org.labkey.study.query;

import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.specimen.query.SpecimenPivotByDerivativeType;
import org.labkey.api.specimen.query.SpecimenPivotByPrimaryType;
import org.labkey.api.specimen.query.SpecimenPivotByRequestingLocation;
import org.labkey.api.study.StudyService;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.labkey.api.specimen.model.SpecimenTablesProvider.SPECIMENVIALCOUNT_TABLENAME;
import static org.labkey.api.specimen.model.SpecimenTablesProvider.VIALREQUEST_TABLENAME;

class SpecimenSchema extends StudyQuerySchema
{
    private final StudyQuerySchema _parentSchema;

    SpecimenSchema(StudyQuerySchema parent)
    {
        super(new SchemaKey(parent.getSchemaPath(), "Specimens"), "Specimen repository", parent.getStudy(), parent.getContainer(), parent.getUser(), parent._contextualRole);
        _parentSchema = parent;
        setSessionParticipantGroup(parent.getSessionParticipantGroup());
    }

    @Override
    public Set<String> getSubSchemaNames()
    {
        return Collections.emptySet();
    }

    @Override
    public QuerySchema getSchema(String name)
    {
        return _parentSchema.getSchema(name);
    }

    @Override
    public Set<String> getTableNames()
    {
        if (_tableNames == null)
        {
            Set<String> names = new LinkedHashSet<>();

            if (_study != null)
            {
                StudyService studyService = StudyService.get();
                if (null == studyService)
                    throw new IllegalStateException("No StudyService!");

                names.add(LOCATION_TABLE_NAME);
                names.add(SPECIMEN_EVENT_TABLE_NAME);
                names.add(SPECIMEN_DETAIL_TABLE_NAME);
                names.add(SPECIMEN_SUMMARY_TABLE_NAME);
                names.add(SPECIMENVIALCOUNT_TABLENAME);
                names.add(SIMPLE_SPECIMEN_TABLE_NAME);
                names.add("SpecimenRequest");
                names.add("SpecimenRequestStatus");
                names.add(VIALREQUEST_TABLENAME);
                names.add(SPECIMEN_ADDITIVE_TABLE_NAME);
                names.add(SPECIMEN_DERIVATIVE_TABLE_NAME);
                names.add(SPECIMEN_PRIMARY_TYPE_TABLE_NAME);
                names.add("SpecimenComment");

                // CONSIDER: show under queries instead of tables?
                // specimen report pivots
                names.add(SpecimenPivotByPrimaryType.PIVOT_BY_PRIMARY_TYPE);
                names.add(SpecimenPivotByDerivativeType.PIVOT_BY_DERIVATIVE_TYPE);
                names.add(SpecimenPivotByRequestingLocation.PIVOT_BY_REQUESTING_LOCATION);

                names.add(LOCATION_SPECIMEN_LIST_TABLE_NAME);
            }
            _tableNames = Collections.unmodifiableSet(names);
        }

        return _tableNames;
    }
}
