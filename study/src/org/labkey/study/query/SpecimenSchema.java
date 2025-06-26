package org.labkey.study.query;

import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.study.StudyService;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

class SpecimenSchema extends StudyQuerySchema
{
    private final StudyQuerySchema _parentSchema;

    SpecimenSchema(StudyQuerySchema parent)
    {
        super(new SchemaKey(parent.getSchemaPath(), SPECIMENS_SCHEMA_NAME), "Specimen repository", parent.getStudy(), parent.getContainer(), parent.getUser(), parent._contextualRole);
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
                addSpecimenTables(names);
            }
            _tableNames = Collections.unmodifiableSet(names);
        }

        return _tableNames;
    }
}
