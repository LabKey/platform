package org.labkey.studydesign;

import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.study.AssaySpecimenConfig;
import org.labkey.api.study.Product;
import org.labkey.api.study.Treatment;
import org.labkey.api.study.Visit;
import org.labkey.api.studydesign.StudyDesignService;
import org.labkey.api.studydesign.query.StudyDesignSchema;
import org.labkey.studydesign.model.AssaySpecimenConfigImpl;
import org.labkey.studydesign.model.TreatmentManager;

import java.util.Collection;
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

    @Override
    public Collection<? extends AssaySpecimenConfig> getAssaySpecimenConfigs(Container c)
    {
        return new TableSelector(
                StudyDesignSchema.getInstance().getTableInfoAssaySpecimen(),
                SimpleFilter.createContainerFilter(c), null).getCollection(AssaySpecimenConfigImpl.class);
    }

    @Override
    public void deleteTreatmentVisitMapForCohort(Container container, Integer cohortId)
    {
        TreatmentManager.getInstance().deleteTreatmentVisitMapForCohort(container, cohortId);
    }

    @Override
    public void deleteTreatmentVisitMapForVisit(Container container, Integer visitId)
    {
        TreatmentManager.getInstance().deleteTreatmentVisitMapForVisit(container, visitId);
    }

    @Override
    public void deleteAssaySpecimenVisits(Container container, int visitId)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition(FieldKey.fromParts("VisitId"), visitId);
        Table.delete(StudyDesignSchema.getInstance().getTableInfoAssaySpecimenVisit(), filter);
    }
}
