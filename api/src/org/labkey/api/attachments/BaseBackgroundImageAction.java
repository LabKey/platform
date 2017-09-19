package org.labkey.api.attachments;

import org.labkey.api.data.CacheableWriter;
import org.labkey.api.util.Pair;
import org.springframework.validation.BindException;

import javax.servlet.http.HttpServletResponse;
import java.util.Calendar;
import java.util.GregorianCalendar;

// Abstract action that renders a portal image for a portal selection pages (e.g., GEL, Argos). Modules need an action
// in their own controller that extends this class and handles security, creates the correct attachment parent, and
// specifies the image filename.
public abstract class BaseBackgroundImageAction<FORM> extends BaseDownloadAction<FORM>
{
    @Override
    public void export(FORM form, HttpServletResponse response, BindException errors) throws Exception
    {
        Pair<AttachmentParent, String> attachment = getAttachment(form);

        if (null != attachment)
        {
            CacheableWriter writer = PortalBackgroundImageCache.getImageWriter(attachment.first, attachment.second);

            if (null != writer)
            {
                // review: is this correct?
                Calendar expiration = new GregorianCalendar();
                expiration.add(Calendar.YEAR, 1);
                writer.writeToResponse(response, expiration);
            }
        }
    }
}
