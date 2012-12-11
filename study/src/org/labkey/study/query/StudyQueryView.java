package org.labkey.study.query;

import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.study.CohortFilter;
import org.springframework.validation.BindException;

/**
 * Created with IntelliJ IDEA.
 * User: matthewb
 * Date: 2012-12-10
 * Time: 2:03 PM
 *
 * Base class for DatasetQueryView and SpecimenQueryView
 */
public class StudyQueryView extends QueryView
{
    protected Study _study;

    public StudyQueryView(UserSchema schema, QuerySettings settings, BindException errors)
    {
        super(schema, settings, errors);
        _study = StudyService.get().getStudy(getContainer());
    }

    public CohortFilter getCohortFilter()
    {
        return null;
    }
}
