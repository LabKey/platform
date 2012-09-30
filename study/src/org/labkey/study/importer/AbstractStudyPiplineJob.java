package org.labkey.study.importer;

import org.apache.xmlbeans.XmlObject;
import org.labkey.api.action.NullSafeBindException;
import org.labkey.api.admin.FolderExportContext;
import org.labkey.api.admin.FolderWriterImpl;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.security.User;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
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
public abstract class AbstractStudyPiplineJob extends PipelineJob
{
    protected transient Container _dstContainer;
    protected transient StudyImpl _sourceStudy;

    // TODO: This is legacy error handling that we should remove
    protected final BindException _errors;

    public AbstractStudyPiplineJob(Container c, User user, ActionURL url, PipeRoot root)
    {
        super(null, new ViewBackgroundInfo(c, user, url), root);

        File tempLogFile = new File(root.getRootPath(), FileUtil.makeFileNameWithTimestamp(getLogName(), "log"));
        setLogFile(tempLogFile);

        _errors = new NullSafeBindException(this, getLogName());
    }

    abstract protected String getLogName();

    @Override
    public URLHelper getStatusHref()
    {
        return PageFlowUtil.urlProvider(ProjectUrls.class).getStartURL(_dstContainer);
    }

    @Override
    public final void run()
    {
        boolean success = false;
        // Silliness required because some stupid classes grab context from thread local
        int stackSize = HttpView.getStackSize();

        try
        {
            ViewContext context = ViewContext.getMockViewContext(getUser(), getContainer(), getActionURL(), true);
            success = run(context);
        }
        finally
        {
            if (stackSize > -1)
                HttpView.resetStackSize(stackSize);

            setStatus(success ? PipelineJob.COMPLETE_STATUS : PipelineJob.ERROR_STATUS);
        }
    }

    protected abstract boolean run(ViewContext context);

    protected void exportFromParentStudy(FolderExportContext ctx, VirtualFile vf) throws Exception
    {
        // the set of data types to write is determined by the export context dataTypes variable
        FolderWriterImpl writer = new FolderWriterImpl();
        writer.write(_sourceStudy.getContainer(), ctx, vf);
    }

    protected void importSpecimenData(StudyImpl destStudy, VirtualFile vf) throws Exception
    {
        VirtualFile studyDir = vf.getDir("study");
        StudyDocument studyDoc = getStudyDocument(studyDir);
        if (studyDoc != null)
        {
            StudyImportContext ctx = new StudyImportContext(getUser(), destStudy.getContainer(), studyDoc, getLogger(), studyDir);
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
