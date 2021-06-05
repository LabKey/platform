package org.labkey.study;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.specimen.requirements.SpecimenRequest;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyInternalService;
import org.labkey.api.study.model.ParticipantInfo;
import org.labkey.api.view.ViewContext;
import org.labkey.study.controllers.specimen.SpecimenUtils;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.springframework.validation.BindException;

import java.util.Map;

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
}
