package org.labkey.study.query;

import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.data.Container;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.Study;
import org.labkey.study.StudySchema;

public class StudySchemaProvider extends DefaultSchema.SchemaProvider
{
    public QuerySchema getSchema(DefaultSchema schema)
    {
        Container container = schema.getContainer();
        Study study = StudyManager.getInstance().getStudy(container);
        if (study == null)
        {
            return null;
        }
        return new StudyQuerySchema(study, schema.getUser(), true);
    }
}
