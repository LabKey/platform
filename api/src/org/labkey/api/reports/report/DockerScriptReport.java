package org.labkey.api.reports.report;

import org.labkey.api.reports.report.r.ParamReplacement;
import org.labkey.api.view.ViewContext;

import javax.script.ScriptException;
import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * This is a base class for Reports that encapsulate their report executing in a Docker container, for security and/or configuration control.
 *
 * For code reuse and flexibility, the goal is to make docker script reports as language agnostic as possible.  This code handles the
 * language agnostic part, and the ideally the subclasses just handle
 *   a) setting up the language binding in the report editor
 *   b) custom properties in the report editor
 *   c) the docker image should handle loading properties and launching the script interpreter
 * really cares about the language is a) our editor b) the code
 */
abstract public class DockerScriptReport extends ScriptProcessReport
{
    protected DockerScriptReport(String reportType, String defaultDescriptorType)
    {
        super(reportType, defaultDescriptorType);
    }

    @Override
    public String runScript(ViewContext context, List<ParamReplacement> outputSubst, File inputDataTsv, Map<String, Object> inputParameters) throws ScriptException
    {
        return "I'm abstract";
    }
}
