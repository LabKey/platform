package org.labkey.studydesign.view;

import org.labkey.api.data.Container;
import org.labkey.api.settings.OptionalFeatureService;
import org.labkey.api.study.StudyUtils;
import org.labkey.api.view.BaseWebPartFactory;

public abstract class StudyDesignWebpartFactory extends BaseWebPartFactory
{
    public StudyDesignWebpartFactory(String name)
    {
        super(name);
    }

    protected boolean canShow()
    {
        return OptionalFeatureService.get().isFeatureEnabled(StudyUtils.STUDY_DESIGN_FEATURE_FLAG);
    }

    @Override
    public boolean isAvailable(Container c, String scope, String location)
    {
        return canShow() ? super.isAvailable(c, scope, location) : false;
    }
}