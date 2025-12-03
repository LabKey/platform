package org.labkey.core.admin.sitevalidation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspTemplate;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewContext;
import org.labkey.core.admin.AdminController.SiteValidationForm;
import org.labkey.core.admin.AdminController.ViewValidationResultsAction;
import org.labkey.vfs.FileLike;

import java.io.PrintWriter;

public class SiteValidationJob extends PipelineJob
{
    private final SiteValidationForm _form;

    @JsonCreator
    protected SiteValidationJob(@JsonProperty("_form") SiteValidationForm form)
    {
        _form = form;
    }

    public SiteValidationJob(ViewBackgroundInfo info, PipeRoot pipeRoot, SiteValidationForm form)
    {
        super("SiteValidation", info, pipeRoot);
        setLogFile(pipeRoot.getLogDirectory(true).resolveChild(FileUtil.makeFileNameWithTimestamp("site_validation", "log")));
        _form = form;
    }

    @Override
    public URLHelper getStatusHref()
    {
        PipelineStatusFile statusFile = PipelineService.get().getStatusFile(getJobGUID());
        return new ActionURL(ViewValidationResultsAction.class, getContainer())
            .addParameter("rowId", statusFile.getRowId());
    }

    @Override
    public String getDescription()
    {
        return "Site Validation";
    }

    @Override
    public void run()
    {
        info("Site validation started");
        PipelineJob.TaskStatus finalStatus = PipelineJob.TaskStatus.complete;
        _form.setLogger(s -> {
            getLogger().info(s);
            setStatus(s);
        });
        JspTemplate<SiteValidationForm> template = new JspTemplate<>("/org/labkey/core/admin/sitevalidation/siteValidation.jsp", _form);
        // Issue 51749 - ensure we have a URL for wiki validation
        ViewBackgroundInfo info = new ViewBackgroundInfo(getInfo().getContainer(),
                getInfo().getUser(),
                getInfo().getURL() == null ? AppProps.getInstance().getHomePageActionURL() : getInfo().getURL());
        ViewContext context = new ViewContext(info);
        template.setViewContext(context);
        FileLike results = getPipeRoot().getLogDirectory(true).resolveChild(getResultsFileName());

        try (PrintWriter out = new PrintWriter(results.openOutputStream(), false, StringUtilsLabKey.DEFAULT_CHARSET))
        {
            out.println(template.render());
        }
        catch (Exception e)
        {
            getLogger().error("Site validation failed", e);
            finalStatus = TaskStatus.error;
        }

        info("Site validation complete. Click the \"Data\" button to see the results.");
        setStatus(finalStatus);
    }

    private String getResultsFileName()
    {
        return getLogFile().getName().replace(".log", ".html");
    }
}
