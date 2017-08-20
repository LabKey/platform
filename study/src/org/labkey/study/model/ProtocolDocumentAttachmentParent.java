package org.labkey.study.model;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.AttachmentType;
import org.labkey.api.data.AttachmentParentEntity;
import org.labkey.api.study.Study;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.study.controllers.StudyController;

public class ProtocolDocumentAttachmentParent extends AttachmentParentEntity
{
    private final Study _study;

    public ProtocolDocumentAttachmentParent(@NotNull Study study)
    {
        setContainer(study.getContainer().getId());
        setEntityId(((StudyImpl) study).getProtocolDocumentEntityId());
        _study = study;
    }

    @Override
    public String getDownloadURL(ViewContext context, String name)
    {
        ActionURL download = new ActionURL(StudyController.ProtocolDocumentDownloadAction.class, _study.getContainer());
        download.addParameter("name", name);
        return download.getLocalURIString(false);
    }

    @Override
    public @NotNull AttachmentType getAttachmentType()
    {
        return ProtocolDocumentType.get();
    }
}
