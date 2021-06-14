package org.labkey.api.study;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.annotations.Migrate;
import org.labkey.api.data.Container;
import org.labkey.api.security.SecurityManager.ViewFactory;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.specimen.query.SpecimenQueryView;
import org.labkey.api.specimen.requirements.SpecimenRequest;
import org.labkey.api.study.model.ParticipantDataset;
import org.labkey.api.study.model.ParticipantInfo;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindException;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Provides access to internal study methods for modules that depend on study and are aware of study internals
 * (for example, specimen)
 */
public interface StudyInternalService
{
    static StudyInternalService get()
    {
        return ServiceRegistry.get().getService(StudyInternalService.class);
    }

    static void setInstance(StudyInternalService impl)
    {
        ServiceRegistry.get().registerService(StudyInternalService.class, impl);
    }

    /**
     * Clears all the study caches in this container plus those of any associated ancillary/published studies. Does not
     * clear caches associated with datasets.
     * @param container The study container where cache clearing will take place
     */
    void clearCaches(Container container);

    Map<String, ParticipantInfo> getParticipantInfos(Study study, User user, boolean isShiftDates, boolean isAlternateIds);

    void generateNeededAlternateParticipantIds(Study study, User user);

    @Migrate
    void sendNewRequestNotifications(ViewContext context, SpecimenRequest request, BindException errors) throws Exception;

    void setLastSpecimenRequest(Study study, Integer lastSpecimenRequest);

    Integer getLastSpecimenRequest(Study study);

    SpecimenQueryView getSpecimenQueryView(ViewContext context, boolean showVials, boolean forExport, @Nullable Collection<? extends ParticipantDataset> cachedFilterData, SpecimenQueryView.Mode viewMode, CohortFilter cohortFilter);

    void registerManageStudyViewFactory(ViewFactory factory);

    Integer getParticipantCommentDatasetId(Study study);

    String getParticipantCommentProperty(Study study);

    Integer getParticipantVisitCommentDatasetId(Study study);

    String getParticipantVisitCommentProperty(Study study);

    List<? extends Dataset> getDatasets(Study study);

    Collection<? extends ParticipantDataset> getParticipantDatasets(Container c, Collection<String> lsids);

    boolean hasEditableDatasets(Study study);

    void saveCommentsSettings(Study study, User user, Integer participantCommentDatasetId, String participantCommentProperty, Integer participantVisitCommentDatasetId, String participantVisitCommentProperty);

    String formatSequenceNum(double d);
}
