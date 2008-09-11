/*
 * Copyright (c) 2007-2008 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.filecontent;

import org.labkey.api.view.*;
import org.labkey.api.data.Container;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.AttachmentDirectory;
import org.apache.commons.lang.StringUtils;

/**
 * Created by IntelliJ IDEA.
 * User: Mark Igra
 * Date: Jul 9, 2007
 * Time: 2:13:18 PM
 */
public class FilesWebPart extends JspView<AttachmentDirectory>
{
    private boolean wide = true;
    private boolean showAdmin = false;
    private String fileSet;
    private Container container;

    public FilesWebPart(Container c)
    {
        super("/org/labkey/filecontent/view/files.jsp", null);
        container = c;
        setFileSet(null);
        setTitle("Files");
        setTitleHref(new ActionURL("FileContent", "begin", c));
    }

    public FilesWebPart(Container c, String fileSet)
    {
        super("/org/labkey/filecontent/view/files.jsp", AttachmentService.get().getRegisteredDirectory(c, fileSet));
        container = c;
        this.fileSet = fileSet;
        setTitle(fileSet);
        setTitleHref(new ActionURL("FileContent", "begin", c).addParameter("fileSetName",fileSet));
    }

    public FilesWebPart(ViewContext ctx, Portal.WebPart webPartDescriptor)
    {
        this(ctx.getContainer());
        setWide(null == webPartDescriptor.getLocation() || HttpView.BODY.equals(webPartDescriptor.getLocation()));
        setShowAdmin(ctx.isAdminMode());
        fileSet = StringUtils.trimToNull(webPartDescriptor.getPropertyMap().get("fileSet"));
        if (null != fileSet)
        {
            AttachmentDirectory dir = AttachmentService.get().getRegisteredDirectory(ctx.getContainer(), fileSet);
            setModelBean(dir);
        }
        String path = webPartDescriptor.getPropertyMap().get("path");
    }

    public boolean isWide()
    {
        return wide;
    }

    public void setWide(boolean wide)
    {
        this.wide = wide;
    }

    public boolean isShowAdmin()
    {
        return showAdmin;
    }

    public void setShowAdmin(boolean showAdmin)
    {
        this.showAdmin = showAdmin;
    }

    public String getFileSet()
    {
        return fileSet;
    }

    public void setFileSet(String fileSet)
    {
        this.fileSet = fileSet;
        if (null == fileSet)
        {
            try
            {
                setModelBean(AttachmentService.get().getMappedAttachmentDirectory(container, false));
            }
            catch (AttachmentService.MissingRootDirectoryException ex)
            {
                setModelBean(null);
            }
        }
        else
            setModelBean(AttachmentService.get().getRegisteredDirectory(container, fileSet));
    }

    public static class Factory extends WebPartFactory
    {
        public Factory(String location)
        {
            super("Files", location, true, false);
        }

        public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
        {
            return new FilesWebPart(portalCtx, webPart);
        }

        public boolean isAvailable(Container c, String location)
        {
            return location.equals(getDefaultLocation());
        }

        @Override
        public HttpView getEditView(Portal.WebPart webPart)
        {
            JspView editView = new CustomizeFilesWebPartView(webPart);

            return editView;
        }
    }
}
