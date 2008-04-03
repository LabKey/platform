package org.labkey.filecontent;

import org.labkey.api.view.JspView;
import org.labkey.api.view.Portal;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;

/**
 * Created by IntelliJ IDEA.
 * User: Mark Igra
 * Date: Jul 16, 2007
 * Time: 2:27:37 PM
 */
public class CustomizeFilesWebPartView extends JspView<FileContentController.CustomizeWebPartForm>
{
    public CustomizeFilesWebPartView(Portal.WebPart currentConfig)
    {
        super("/org/labkey/filecontent/view/customizeWebPart.jsp");
        setModelBean(new FileContentController.CustomizeWebPartForm(currentConfig));
    }
}
