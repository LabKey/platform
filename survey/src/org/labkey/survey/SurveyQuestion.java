package org.labkey.survey;

import org.labkey.api.data.AttachmentParentEntity;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: 12/27/12
 */
public class SurveyQuestion extends AttachmentParentEntity
{
    public SurveyQuestion(String containerId, String entityId)
    {
        setContainerId(containerId);
        setEntityId(entityId);
    }
}
