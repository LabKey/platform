/*
 * Copyright (c) 2008-2009 LabKey Corporation
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
package org.labkey.api.reports.report;

import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.*;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.reports.ExternalScriptEngine;
import org.labkey.api.reports.report.r.ParamReplacement;
import org.labkey.api.reports.report.r.ParamReplacementSvc;
import org.labkey.api.reports.report.r.view.*;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.reports.report.view.RunReportView;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.*;
import org.labkey.api.query.QueryView;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.io.*;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * User: Karl Lum
 * Date: Dec 3, 2008
 * Time: 2:45:37 PM
 *
 * A Report implementation that uses a ExternalScriptEngine instance to execute the associated script. External
 * script engines are invoked by running an application in an external process. Information is exchanged between the
 * web server and application through the file system.
 */
public class ExternalScriptEngineReport extends ScriptEngineReport implements AttachmentParent
{
    public static final String TYPE = "ReportService.externalScriptEngineReport";
    public static final String CACHE_DIR = "cached";
    private static final Map<ReportIdentifier, ActionURL> _cachedReportURLMap = new HashMap<ReportIdentifier, ActionURL>();

    public String getType()
    {
        return TYPE;
    }

    public HttpView renderReport(ViewContext context) throws Exception
    {
        VBox view = new VBox();
        String script = getDescriptor().getProperty(RReportDescriptor.Prop.script);

/*
        if (validateConfiguration(getRExe(), getRCmd(), getTempFolder(), getRScriptHandler()) != null)
        {
            final String error = "The R program has not been configured to be used by the LabKey server yet, navigate to the <a href='" + PageFlowUtil.filter(PageFlowUtil.urlProvider(AdminUrls.class).getAdminConsoleURL()) + "'>admin console</a> to configure R.";
            view.addView(new HtmlView("<span class=\"labkey-error\">" + error + "</span>"));
            return view;
        }
*/

        List<String> errors = new ArrayList<String>();
        if (!validateScript(script, errors))
        {
            for (String error : errors)
                view.addView(new HtmlView("<span class=\"labkey-error\">" + error + "</span>"));
            return view;
        }

        List<ParamReplacement> outputSubst = new ArrayList<ParamReplacement>();
        if (!getCachedReport(context, outputSubst))
        {
            try {
                runScript(context, outputSubst, createInputDataFile(context));
            }
            catch (Exception e)
            {
                final String error1 = "Error executing command";
                final String error2 = PageFlowUtil.filter(e.getMessage());

                errors.add(error1);
                errors.add(error2);

                String err = "<font class=\"labkey-error\">" + error1 + "</font><pre>" + error2 + "</pre>";
                HttpView errView = new HtmlView(err);
                view.addView(errView);
            }
            cacheResults(context, outputSubst);
        }
        renderViews(this, view, outputSubst, false);

        return view;
    }

    public String runScript(ViewContext context, List<ParamReplacement> outputSubst, File inputDataTsv) throws ScriptException
    {
        ScriptEngine engine = getScriptEngine();
        if (engine != null)
        {
            try
            {
                Bindings bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);

                bindings.put(ExternalScriptEngine.WORKING_DIRECTORY, getReportDir().getAbsolutePath());
                Object output = engine.eval(createScript(context, outputSubst, inputDataTsv));

                // render the output into the console
                if (output != null)
                {
                    File console = new File(getReportDir(), CONSOLE_OUTPUT);
                    PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(console)));
                    pw.write(output.toString());
                    pw.close();

                    ParamReplacement param = ParamReplacementSvc.get().getHandlerInstance(ConsoleOutput.ID);
                    param.setName("console");
                    param.setFile(console);

                    outputSubst.add(param);
                }
                return output != null ? output.toString() : "";
            }
            catch(Exception e)
            {
                throw new ScriptException(e);
            }
        }
        throw new ScriptException("A script engine implementation was not found for the specified report");
    }

    protected void cacheResults(ViewContext context, List<ParamReplacement> replacements)
    {
        if (getDescriptor().getReportId() != null &&
            BooleanUtils.toBoolean(getDescriptor().getProperty(ReportDescriptor.Prop.cached)))
        {
            synchronized(_cachedReportURLMap)
            {
                File cacheDir = getCacheDir();
                if(null == cacheDir)
                    return;
                try {
                    File mapFile = new File(cacheDir, SUBSTITUTION_MAP);
                    for (ParamReplacement param : replacements)
                    {
                        File src = param.getFile();
                        File dst = new File(cacheDir, src.getName());

                        if (dst.createNewFile())
                        {
                            FileUtil.copyFile(src, dst);
                            if (param.getId().equals(ConsoleOutput.ID))
                            {
                                BufferedWriter bw = null;
                                try {
                                    bw = new BufferedWriter(new FileWriter(dst, true));
                                    bw.write("\nLast cached update : " + DateUtil.formatDateTime() + "\n");
                                }
                                finally
                                {
                                    if (bw != null)
                                        try {bw.close();} catch (IOException ioe) {}
                                }
                            }
                            param.setFile(dst);
                        }
                    }
                    ParamReplacementSvc.get().toFile(replacements, mapFile);
                    _cachedReportURLMap.put(getDescriptor().getReportId(), getCacheURL(context.getActionURL()));
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public void clearCache()
    {
        File cacheDir = getCacheDir();
        if (null != cacheDir && cacheDir.exists())
            FileUtil.deleteDir(cacheDir);
    }

    protected boolean getCachedReport(ViewContext context, List<ParamReplacement> replacements)
    {
        if (getDescriptor().getReportId() != null &&
            BooleanUtils.toBoolean(getDescriptor().getProperty(ReportDescriptor.Prop.cached)))
        {
            synchronized(_cachedReportURLMap)
            {
                if (urlDirty(context.getActionURL()))
                {
                    clearCache();
                    return false;
                }
                File cacheDir = getCacheDir();
                if(null == cacheDir)
                    return false;

                try {
                    for (ParamReplacement param : ParamReplacementSvc.get().fromFile(new File(cacheDir, SUBSTITUTION_MAP)))
                    {
                        replacements.add(param);
                    }
                    return !replacements.isEmpty();
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
            }
        }
        return false;
    }

    private ActionURL getCacheURL(ActionURL url)
    {
        return url.clone().deleteParameter(RunReportView.CACHE_PARAM).
                deleteParameter(RunReportView.TAB_PARAM);
    }

    /**
     * Detect whether the URL params have changed since this cached report was last rendered.
     */
    private boolean urlDirty(ActionURL url)
    {
        ActionURL cachedURL = _cachedReportURLMap.get(getDescriptor().getReportId());
        if (cachedURL != null)
        {
            Map cur = PageFlowUtil.mapFromQueryString(getCacheURL(url).getQueryString());
            Map prev = PageFlowUtil.mapFromQueryString(cachedURL.getQueryString());

            return !cur.equals(prev);
        }
        return true;
    }

    /**
     * Called before this report is saved or updated
     * @param context
     */
    public void beforeSave(ViewContext context)
    {
        super.beforeSave(context);
        clearCache();
    }

    /**
     * Called before this report is deleted
     * @param context
     */
    public void beforeDelete(ViewContext context)
    {
        try {
            // clean up any temp files
            clearCache();
            deleteReportDir();
            AttachmentService.get().deleteAttachments(this);
            super.beforeDelete(context);
        }
        catch (SQLException se)
        {
            throw new RuntimeException(se);
        }
    }

    protected File getCacheDir()
    {
        if(getDescriptor().getReportId() == null)
            return null;

        File cacheDir = new File(getTempRoot(), "Report_" + FileUtil.makeLegalName(getDescriptor().getReportId().toString()) + File.separator + CACHE_DIR);
        if (!cacheDir.exists())
            cacheDir.mkdirs();

        return cacheDir;
    }

    public String getEntityId()
    {
        return getDescriptor().getEntityId();
    }

    public String getContainerId()
    {
        return getDescriptor().getContainerId();
    }

    public void setAttachments(Collection<Attachment> attachments)
    {
    }

    public HttpView renderDataView(ViewContext context) throws Exception
    {
        QueryView view = createQueryView(context, getDescriptor());
        if (view != null)
            return view;
        else
            return new HtmlView("No Data view available for this report");
    }

    public ActionURL getRunReportURL(ViewContext context)
    {
        return ReportUtil.getRunReportURL(context, this);
    }
}
