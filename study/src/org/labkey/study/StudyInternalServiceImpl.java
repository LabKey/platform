package org.labkey.study;

import org.labkey.api.data.Container;
import org.labkey.api.security.SecurityManager.ViewFactory;
import org.labkey.api.security.User;
import org.labkey.api.specimen.query.SpecimenQueryView;
import org.labkey.api.specimen.requirements.SpecimenRequest;
import org.labkey.api.study.CohortFilter;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyInternalService;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.model.ParticipantDataset;
import org.labkey.api.study.model.ParticipantInfo;
import org.labkey.api.view.ViewContext;
import org.labkey.study.controllers.specimen.SpecimenUtils;
import org.labkey.study.model.SecurityType;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.springframework.validation.BindException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class StudyInternalServiceImpl implements StudyInternalService
{
    @Override
    public void clearCaches(Container container)
    {
        StudyManager.getInstance().clearCaches(container, false);
    }

    @Override
    public Map<String, ParticipantInfo> getParticipantInfos(Study study, User user, boolean isShiftDates, boolean isAlternateIds)
    {
        return StudyManager.getInstance().getParticipantInfos(study, user, isShiftDates, isAlternateIds);
    }

    @Override
    public void generateNeededAlternateParticipantIds(Study study, User user)
    {
        StudyManager.getInstance().generateNeededAlternateParticipantIds(study, user);
    }

    @Override
    public void sendNewRequestNotifications(ViewContext context, SpecimenRequest request, BindException errors) throws Exception
    {
        new SpecimenUtils(context).sendNewRequestNotifications(request, errors);
    }

    @Override
    public void setLastSpecimenRequest(Study study, Integer lastSpecimenRequest)
    {
        ((StudyImpl)study).setLastSpecimenRequest(lastSpecimenRequest);
    }

    @Override
    public Integer getLastSpecimenRequest(Study study)
    {
        return ((StudyImpl)study).getLastSpecimenRequest();
    }

    @Override
    public SpecimenQueryView getSpecimenQueryView(ViewContext context, boolean showVials, boolean forExport, ParticipantDataset[] cachedFilterData, SpecimenQueryView.Mode viewMode, CohortFilter cohortFilter)
    {
        return new SpecimenUtils(context).getSpecimenQueryView(showVials, forExport, cachedFilterData, viewMode, cohortFilter);
    }

    public static final List<ViewFactory> VIEW_FACTORIES = new CopyOnWriteArrayList<>();

    @Override
    public void registerManageStudyViewFactory(ViewFactory factory)
    {
        VIEW_FACTORIES.add(factory);
    }

    @Override
    public Integer getParticipantCommentDatasetId(Study study)
    {
        return ((StudyImpl)study).getParticipantCommentDatasetId();
    }

    @Override
    public String getParticipantCommentProperty(Study study)
    {
        return null;
    }

    @Override
    public Integer getParticipantVisitCommentDatasetId(Study study)
    {
        return ((StudyImpl)study).getParticipantVisitCommentDatasetId();
    }

    @Override
    public String getParticipantVisitCommentProperty(Study study)
    {
        return ((StudyImpl)study).getParticipantVisitCommentProperty();
    }

    @Override
    public List<? extends Dataset> getDatasets(Study study)
    {
        return StudyManager.getInstance().getDatasetDefinitions(study);
    }

    @Override
    public boolean hasEditableDatasets(Study study)
    {
        SecurityType securityType = ((StudyImpl)study).getSecurityType();
        return securityType != SecurityType.ADVANCED_READ && securityType != SecurityType.BASIC_READ;
    }

    @Override
    public void saveCommentsSettings(Study s, User user, Integer participantCommentDatasetId, String participantCommentProperty, Integer participantVisitCommentDatasetId, String participantVisitCommentProperty)
    {
        StudyImpl study = (StudyImpl)s;

        // participant comment dataset
        study.setParticipantCommentDatasetId(participantCommentDatasetId);
        study.setParticipantCommentProperty(participantCommentProperty);

        // participant/visit comment dataset
        if (study.getTimepointType() != TimepointType.CONTINUOUS)
        {
            study.setParticipantVisitCommentDatasetId(participantVisitCommentDatasetId);
            study.setParticipantVisitCommentProperty(participantVisitCommentProperty);
        }

        StudyManager.getInstance().updateStudy(user, study);
    }
}
