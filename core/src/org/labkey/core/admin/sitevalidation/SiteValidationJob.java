package org.labkey.core.admin.sitevalidation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspTemplate;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewContext;
import org.labkey.core.admin.AdminController.SiteValidationForm;

import java.io.File;
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
        setLogFile(new File(pipeRoot.getLogDirectory(), FileUtil.makeFileNameWithTimestamp("site_validation", "log")).toPath());
        _form = form;
    }

    @Override
    public URLHelper getStatusHref()
    {
        return null;
    } // TODO: Link to HTML file

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
        JspTemplate<SiteValidationForm> template = new JspTemplate<>("/org/labkey/core/admin/sitevalidation/siteValidation.jsp", _form);
        ViewContext context = new ViewContext();
        context.setUser(getUser());
        context.setContainer(getContainer());
        context.setActionURL(new ActionURL());
        template.setViewContext(context);

        File results = new File(getPipeRoot().getLogDirectory(), "site_validation.html");

        try (PrintWriter out = new PrintWriter(results, StringUtilsLabKey.DEFAULT_CHARSET))
        {
            out.println(template.render());
        }
        catch (Exception e)
        {
            getLogger().error("Site validation failed", e);
            finalStatus = TaskStatus.error;
        }

        info("Site validation complete");
        setStatus(finalStatus);
    }
}
