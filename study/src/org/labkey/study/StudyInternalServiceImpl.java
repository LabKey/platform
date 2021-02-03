package org.labkey.study;

import org.labkey.api.data.Container;
import org.labkey.api.study.StudyInternalService;
import org.labkey.study.model.StudyManager;

public class StudyInternalServiceImpl implements StudyInternalService
{
    @Override
    public void clearCaches(Container container)
    {
        StudyManager.getInstance().clearCaches(container, false);
    }
}
