package org.labkey.studydesign;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.study.Product;
import org.labkey.api.study.Treatment;
import org.labkey.api.study.Visit;
import org.labkey.api.studydesign.StudyDesignService;
import org.labkey.studydesign.model.TreatmentManager;

import java.util.List;

public class StudyDesignServiceImpl implements StudyDesignService
{
    @Override
    public List<? extends Product> getStudyProducts(Container c, User user, String role)
    {
        return TreatmentManager.getInstance().getStudyProducts(c, user, role, null);
    }

    @Override
    public List<? extends Treatment> getStudyTreatments(Container c, User user)
    {
        return TreatmentManager.getInstance().getStudyTreatments(c, user);
    }

    @Override
    public List<? extends Visit> getVisitsForTreatmentSchedule(Container c)
    {
        return TreatmentManager.getInstance().getVisitsForTreatmentSchedule(c);
    }
}
