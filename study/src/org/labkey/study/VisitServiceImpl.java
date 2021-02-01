package org.labkey.study;

import org.labkey.api.study.Study;
import org.labkey.api.study.Visit;
import org.labkey.api.study.model.VisitService;
import org.labkey.study.model.StudyManager;

import java.util.List;

public class VisitServiceImpl implements VisitService
{
    @Override
    public List<? extends Visit> getVisits(Study study, Visit.Order order)
    {
        return StudyManager.getInstance().getVisits(study, order);
    }
}
