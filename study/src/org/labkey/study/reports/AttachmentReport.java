/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

package org.labkey.study.reports;

import org.apache.struts.upload.FormFile;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.StrutsAttachmentFile;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.security.User;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: Mark Igra
 * Date: Jul 6, 2006
 * Time: 5:08:19 PM
 */
public class AttachmentReport extends RedirectReport implements AttachmentParent
{
    public static final String TYPE = "Study.attachmentReport";
    public static final String FILE_PATH = "filePath";
    public static final String MODIFIED = "modified";

    private Attachment[] attachments;

    public String getType()
    {
        return TYPE;
    }

    public String getTypeDescription()
    {
        return "Study Attachment Report";
    }

    public String getEntityId()
    {
        return getDescriptor().getEntityId();
    }

    public String getContainerId()
    {
        return getDescriptor().getContainerId();
    }

    // Do nothing
    public void setAttachments(Collection<Attachment> attachments)
    {
    }

    @Override
    public String getParams()
    {
        return getFilePath() == null ? null : "filePath=" + PageFlowUtil.encode(getFilePath());
    }

    @Override
    public void setParams(String params)
    {
        if (null == params)
            setFilePath(null);
        else
        {
            Map<String,String> paramMap = PageFlowUtil.mapFromQueryString(params);
            setFilePath(paramMap.get("filePath"));
        }
    }

//    @Override
    public boolean canHavePermissions()
    {
        return true;
    }

    protected Container getContainer()
    {
        return ContainerManager.getForId(getDescriptor().getContainerId());
    }
    
    @Override
    public String getUrl(ViewContext context)
    {
        Container c = getContainer();
        String entityId = getEntityId();

        //Can't throw because table layer calls this in uninitialized state...
        if (null == c || null == entityId)
            return null;

        if (null != getFilePath())
        {
            ActionURL url = new ActionURL("Study-Reports", "downloadReportFile.view", getContainer());
            url.addParameter("reportId", getReportId().toString());
            return url.getLocalURIString();
        }

        getAttachments(false);

        if (null == attachments || attachments.length == 0)
            return null;

        return getLatestVersion().getDownloadUrl("Study-Reports");
    }

    public Attachment getLatestVersion()
    {
        getAttachments(false);

        if (null == attachments || attachments.length == 0)
            return null;

        return attachments[attachments.length - 1];
    }

    public Attachment[] getAttachments(boolean forceRefresh)
    {
        if (forceRefresh)
            attachments = null;

        if (null != attachments)
            return attachments;

        if (null == getEntityId())
            return null;

        attachments = AttachmentService.get().getAttachments(this);
        return attachments;
    }

    // TODO: Delete?  Not used...
    public void postNewVersion(User user, FormFile formFile, Date reportDate) throws SQLException, IOException, AttachmentService.DuplicateFilenameException
    {
        //try to unique-ify file dates since same file name might get uploaded many times
        //But if same file is uploaded with same date, this is probably user error.
        formFile.setFileName(DateUtil.toISO(reportDate) + " " + formFile.getFileName());

        AttachmentService.get().addAttachments(user, this, StrutsAttachmentFile.createList(formFile));

        setModified(reportDate);
        //ReportManager.get().updateReport(user, this);

        getAttachments(true);
    }

    public void setFilePath(String filePath)
    {
        getDescriptor().setProperty(FILE_PATH, filePath);
    }

    public String getFilePath()
    {
        return getDescriptor().getProperty(FILE_PATH);
    }

    public void setModified(Date modified)
    {
        getDescriptor().setProperty(MODIFIED, DateUtil.formatDate(modified));
    }
}
