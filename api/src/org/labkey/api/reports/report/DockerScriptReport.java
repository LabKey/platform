package org.labkey.api.reports.report;

import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.JavaScriptExportScriptFactory;
import org.labkey.api.query.JavaScriptExportScriptModel;
import org.labkey.api.query.QueryView;
import org.labkey.api.reports.report.r.ParamReplacement;
import org.labkey.api.settings.AppProps;
import org.labkey.api.view.ViewContext;

import javax.script.ScriptException;
import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * This is a base class for Reports that encapsulate their report executing in a Docker container, for security and/or configuration control.
 *
 * For code reuse and flexibility, the goal is to make docker script reports as language agnostic as possible.  This code handles the
 * language agnostic part, and ideally the subclasses just handle
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

    protected JSONObject createReportConfig(ViewContext context, File ipynb)
    {
        ReportDescriptor descriptor = getDescriptor();
        JSONObject sourceQuery = null;
        QueryView queryView = createQueryView(context, descriptor);
        if (null != queryView)
        {
            // TODO validateQueryView(queryView);
            JavaScriptExportScriptModel model = new JavaScriptExportScriptFactory().getModel(queryView);
            sourceQuery = model.getJSON(17.1);

            // getModel() returns "exploded" fieldkeys.  It's more useful here to use FieldKey.toString()
            JSONArray columnsFK = sourceQuery.optJSONArray("columns");
            if (null != columnsFK)
            {
                JSONArray columnsStr = new JSONArray();
                for (int i=0 ; i<columnsFK.length() ; i++)
                {
                    List<String> fieldKeyParts = columnsFK.getJSONArray(i).toList().stream().map(p -> (String)p).toList();
                    columnsStr.put(FieldKey.fromParts(fieldKeyParts));
                }
                sourceQuery.put("columns",columnsStr);
            }
        }

        // TODO : handle "inputParameters" e.g. ReportsControl.ExecuteAction
        var pairs = context.getActionURL().getParameters();
        var parameters= pairs.stream().map(p -> new Object[] {p.first, p.second}).toArray();

        final JSONObject reportConfig = new JSONObject();
        reportConfig.put("scriptName", ipynb.getName());
        if (sourceQuery != null)
            reportConfig.put("sourceQuery", sourceQuery);
        String baseServerUrl = AppProps.getInstance().getBaseServerUrl();
        if (AppProps.getInstance().isDevMode())
        {
            baseServerUrl = baseServerUrl.replace("localhost", "host.docker.internal");
        }
        reportConfig.put("baseUrl", baseServerUrl);
        reportConfig.put("contextPath", AppProps.getInstance().getContextPath());
        reportConfig.put("containerPath", context.getContainer().getPath());
        reportConfig.put("parameters", parameters);
        reportConfig.put("version", 1.0);
        return reportConfig;
    }

    @Override
    public boolean supportsPipeline()
    {
        return false;
    }
}
