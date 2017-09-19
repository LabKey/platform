package org.labkey.api.attachments;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.ExportAction;
import org.labkey.api.util.Pair;
import org.springframework.validation.BindException;

import javax.servlet.http.HttpServletResponse;

// Abstract action that downloads an attachment associated with an AttachmentParent. Modules need an action in their own controller
// that extends this class and handles security, creates the correct attachment parent, and specifies the attachment filename.
public abstract class BaseDownloadAction<FORM> extends ExportAction<FORM>
{
    @Override
    protected String getCommandClassMethodName()
    {
        return "getAttachment";
    }

    @Override
    public void export(FORM form, HttpServletResponse response, BindException errors) throws Exception
    {
        Pair<AttachmentParent, String> attachment = getAttachment(form);

        if (null != attachment)
            AttachmentService.get().download(response, attachment.first, attachment.second);
    }

    public abstract @Nullable Pair<AttachmentParent, String> getAttachment(FORM form);
}
