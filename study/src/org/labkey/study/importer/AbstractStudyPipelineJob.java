/*
 * Copyright (c) 2012-2016 LabKey Corporation
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
package org.labkey.study.importer;

import org.apache.xmlbeans.XmlObject;
import org.labkey.api.action.NullSafeBindException;
import org.labkey.api.admin.FolderExportContext;
import org.labkey.api.admin.FolderWriterImpl;
import org.labkey.api.admin.PipelineJobLoggerGetter;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.security.User;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.pipeline.StudyImportSpecimenTask;
import org.labkey.study.xml.StudyDocument;
import org.springframework.validation.BindException;

import java.io.File;

/**
 * User: adam
 * Date: 9/27/12
 * Time: 9:58 PM
 */

// Allows some sharing of code between snapshot/ancillary study publication and specimen refresh
public abstract class AbstractStudyPipelineJob extends PipelineJob
{
    protected transient Container _dstContainer;
    protected String _dstContainerId;

    // TODO: This is legacy error handling that we should remove
    protected final BindException _errors;

    public AbstractStudyPipelineJob(Container source, Container destination, User user, ActionURL url, PipeRoot root)
    {
        super(null, new ViewBackgroundInfo(source, user, url), root);

        setDstContainer(destination);

        File tempLogFile = new File(root.getLogDirectory(), FileUtil.makeFileNameWithTimestamp(getLogName(), "log"));
        setLogFile(tempLogFile);

        _errors = new NullSafeBindException(this, getLogName());
    }

    public Container getDstContainer()
    {
        if (null == _dstContainer)
            _dstContainer = ContainerManager.getForId(_dstContainerId);

        return _dstContainer;
    }

    public void setDstContainer(Container dstContainer)
    {
        _dstContainer = dstContainer;
        _dstContainerId = dstContainer.getId();
    }

    abstract protected String getLogName();

    @Override
    public URLHelper getStatusHref()
    {
        return PageFlowUtil.urlProvider(ProjectUrls.class).getStartURL(getDstContainer());
    }

    @Override
    public final void run()
    {
        boolean success = false;

        // Silliness required because some stupid classes grab context from thread local
        try (ViewContext.StackResetter resetter = ViewContext.pushMockViewContext(getUser(), getContainer(), getActionURL()))
        {
            success = run(resetter.getContext());
        }
        finally
        {
            setStatus(success ? TaskStatus.complete : TaskStatus.error);
        }
    }

    protected abstract boolean run(ViewContext context);

    protected void exportFromParentStudy(FolderExportContext ctx, VirtualFile vf) throws Exception
    {
        // the set of data types to write is determined by the export context dataTypes variable
        FolderWriterImpl writer = new FolderWriterImpl();
        writer.write(ctx.getContainer(), ctx, vf);
    }

    protected void importSpecimenData(StudyImpl destStudy, VirtualFile vf) throws Exception
    {
        VirtualFile studyDir = vf.getDir("study");
        StudyDocument studyDoc = getStudyDocument(studyDir);
        if (studyDoc != null)
        {
            StudyImportContext ctx = new StudyImportContext(getUser(), destStudy.getContainer(), studyDoc, null, new PipelineJobLoggerGetter(this), studyDir);
            StudyImportSpecimenTask.doImport(null, this, ctx, false);
        }
    }

    protected StudyDocument getStudyDocument(VirtualFile studyDir) throws Exception
    {
        XmlObject studyXml = studyDir.getXmlBean("study.xml");
        if (studyXml instanceof StudyDocument)
            return (StudyDocument)studyXml;
        return null;
    }
}
