package org.labkey.api.studydesign;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.study.AssaySpecimenConfig;
import org.labkey.api.study.Product;
import org.labkey.api.study.Treatment;
import org.labkey.api.study.Visit;

import java.util.Collection;
import java.util.List;

public interface StudyDesignService
{
    @Nullable
    static StudyDesignService get()
    {
        return ServiceRegistry.get().getService(StudyDesignService.class);
    }

    List<? extends Product> getStudyProducts(Container c, User user, String role);
    List<? extends Treatment> getStudyTreatments(Container c, User user);
    List<? extends Visit> getVisitsForTreatmentSchedule(Container c);
    Collection<? extends AssaySpecimenConfig> getAssaySpecimenConfigs(Container c);
    void deleteTreatmentVisitMapForCohort(Container container, Integer cohortId);
    void deleteTreatmentVisitMapForVisit(Container container, Integer visitId);
    void deleteAssaySpecimenVisits(Container container, int visitId);

}
