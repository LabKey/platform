package org.labkey.studydesign.view;

import org.labkey.api.data.Container;
import org.labkey.api.studydesign.StudyDesignManager;
import org.labkey.api.view.BaseWebPartFactory;

public abstract class StudyDesignWebpartFactory extends BaseWebPartFactory
{
    public StudyDesignWebpartFactory(String name)
    {
        super(name);
    }

    protected boolean canShow(Container c)
    {
        return StudyDesignManager.get().isModuleActive(c);
    }

    @Override
    public boolean isAvailable(Container c, String scope, String location)
    {
        return canShow(c) ? super.isAvailable(c, scope, location) : false;
    }
}