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

package org.labkey.api.reports.report;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.r.ParamReplacement;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;

import javax.script.*;
import javax.servlet.ServletException;
import java.io.*;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Aug 12, 2007
 */
public class DefaultScriptRunner extends AbstractScriptRunner
{
    private static final Pattern scriptPattern = Pattern.compile("'([^']+)'|\\\"([^\\\"]+)\\\"|(^[^\\s]+)|(\\s[^\\s^'^\\\"]+)");
    public static final String ID = "DefaultRunner";
    public DefaultScriptRunner(RReport report, ViewContext context)
    {
        super(report, context);
    }

    public boolean runScript(VBox view, List<ParamReplacement> outputSubst)
    {
        File inputFile = _data;
        try {
            // create the input file from the source query view
            if (inputFile == null)
                inputFile = createInputDataFile(_report, _context);

            return runScript(view, inputFile, outputSubst, new ArrayList<String>());
        }
        catch (Exception e)
        {
            String error = "<font class=\"labkey-error\">An error occurred rendering this view, you may not have permission to view it</font>";
            _log.error("Error while rendering RReport", e);
            view.addView(new HtmlView(error));
            return false;
        }
    }

    private boolean runScript(VBox view, File inputFile, List<ParamReplacement> outputSubst, List<String> errors) throws Exception
    {
        String script = _report.getDescriptor().getProperty(RReportDescriptor.Prop.script);
        boolean ret = true;

        File outFile = null;
        File scriptFile = null;
        try
        {
            ScriptEngineManager mgr = ServiceRegistry.get().getService(ScriptEngineManager.class);

            ScriptEngine engine = mgr.getEngineByExtension("r");
            if (engine != null)
            {
                Bindings bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);

                //bindings.put(ExternalScriptEngine.WORKING_DIRECTORY, _report.getReportDir().getAbsolutePath());
                engine.eval(processScript(script, inputFile, outputSubst));
            }
        }
        catch (ScriptException e)
        {
            final String error1 = "Error executing command";
            final String error2 = PageFlowUtil.filter(e.getMessage());

            errors.add(error1);
            errors.add(error2);

            String err = "<font class=\"labkey-error\">" + error1 + "</font><pre>" + error2 + "</pre>";
            HttpView errView = new HtmlView(err);
            view.addView(errView);
            ret = false;
        }
        catch (IOException e)
        {
            throw new RuntimeException("Could not create file.", e);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        catch (ServletException e)
        {
            throw new RuntimeException(e);
        }
        finally
        {
            // add the console output
            if (isDebug())
            {
/*
                File consoleFile = RReport.getFile(_report, RReport.reportFile.console, null);
                if (consoleFile.exists())
                {
                    ParamReplacement param = ParamReplacementSvc.get().getHandlerInstance(ConsoleOutput.ID);
                    param.setName("console");
                    param.setFile(consoleFile);

                    outputSubst.add(param);
                }
*/
            }

            if (_deleteTempFiles && null != outFile && outFile.exists())
                outFile.delete();
            if (_deleteTempFiles && null != scriptFile && scriptFile.exists())
                scriptFile.delete();
        }
        return ret;
    }

    protected String processScript(String script, File inputFile, List<ParamReplacement> outputSubst) throws Exception
    {
        // process the primary script
        //File scriptFile = RReport.getFile(_report, RReport.reportFile.script, null);
        StringBuffer finalScript = new StringBuffer();

        createScript(script, finalScript,/*scriptFile,*/ inputFile, outputSubst);

        // process any included scripts
        for (String includedReport : ((RReportDescriptor)_report.getDescriptor()).getIncludedReports())
        {
            Report report = ReportService.get().getReport(NumberUtils.toInt(includedReport));

            if (validateSharedPermissions(report) && RReport.class.isAssignableFrom(report.getClass()))
            {
                final String rName = report.getDescriptor().getProperty(ReportDescriptor.Prop.reportName);
                final String rScript = report.getDescriptor().getProperty(RReportDescriptor.Prop.script);
                //final File rScriptFile = RReport.getFile(_report, RReport.reportFile.includedScript, rName + ".R");

                //createScript(rScript, rScriptFile, inputFile, outputSubst);
            }
        }
        return finalScript.toString();
    }

    private boolean validateSharedPermissions(Report report)
    {
        if (report != null)
        {
            if (ReportUtil.canReadReport(report, _context.getUser()))
            {
                // if it's not in this container, check that it was shared
                if (!_context.getContainer().getId().equals(report.getDescriptor().getContainerId()))
                {
                    return ReportUtil.isReportInherited(_context.getContainer(), report);
                }
                else
                    return true;
            }
        }
        return false;
    }

    protected void createScript(String script, /*File scriptFile*/ StringBuffer sb, File inputFile, List<ParamReplacement> outputSubst) throws Exception
    {
        if (!StringUtils.isEmpty(script))
        {
            // handle the data file substitution param
            script = labkeyObjectProlog() + script;
            script = processInputReplacement(script, inputFile);
            script = processOutputReplacements(script, outputSubst);

            sb.append(script);
            // write out the script file to disk
/*
            try
            {
                PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(scriptFile)));
                pw.write(script);
                pw.close();
            }
            catch(IOException e)
            {
                _log.error("prepare", e);
                throw new ServletException(e);
            }
*/
        }
    }

    /*
     * Create the .tsv associated with the data grid for this report.
     */
    public static File createInputDataFile(RReport report, ViewContext context) throws Exception
    {
        if (context != null)
        {
/*
            File resultFile = RReport.getFile(report, RReport.reportFile.inputData, null);

            ResultSet rs = report.generateResultSet(context);
            TSVGridWriter tsv = createGridWriter(rs);
            tsv.write(resultFile);

            return resultFile;
*/
        }
        return null;
    }


    private String[] formatCommand(String scriptFilePath)
    {
        List<String> params = new ArrayList<String>();
        String rexe = RReport.getRExe();
        String rcmd = RReport.getRCmd();

        params.add(rexe);

        // see if the command contains parameter substitutions
        int idx = rcmd.indexOf('%');
        if (idx != -1)
            rcmd = String.format(rcmd, scriptFilePath);

        Matcher m = scriptPattern.matcher(rcmd);
        while (m.find())
        {
            String value = m.group().trim();
            if (value.startsWith("'"))
                value = m.group(1).trim();
            else if (value.startsWith("\""))
                value = m.group(2).trim();

            params.add(value);
        }

        // append the script file, if it wasn't part of the command
        if (idx == -1)
            params.add(scriptFilePath);

        return params.toArray(new String[params.size()]);
    }

    private int runProcess(ProcessBuilder pb, File outFile)
    {
        Process proc;
        try
        {
            pb.redirectErrorStream(true);
            proc = pb.start();
        }
        catch (SecurityException se)
        {
            throw new RuntimeException(se);
        }
        catch (IOException eio)
        {
            Map<String, String> env = pb.environment();
            throw new RuntimeException("Failed starting process '" + pb.command() + "'. " +
                    "Must be on server path. (PATH=" + env.get("PATH") + ")", eio);
        }

        BufferedReader procReader = null;
        FileWriter writer = null;
        try
        {
            writer = new FileWriter(outFile);
            procReader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream()));
            String line;
            while ((line = procReader.readLine()) != null)
            {
                writer.write(line);
                writer.write("\n");
            }
        }
        catch (IOException eio)
        {
            throw new RuntimeException("Failed writing output for process in '" + pb.directory().getPath() + "'.", eio);
        }
        finally
        {
            if (procReader != null)
            {
                try
                {
                    procReader.close();
                }
                catch (IOException eio)
                {
                    _log.error("unexpected error", eio);
                }
            }
            if (writer != null)
            {
                try
                {
                    writer.close();
                }
                catch (IOException eio)
                {
                    _log.error("unexpected error", eio);
                }
            }
        }

        try
        {
            return proc.waitFor();
        }
        catch (InterruptedException ei)
        {
            throw new RuntimeException("Interrupted process for '" + pb.command() + " in " + pb.directory() + "'.", ei);
        }
    }
}

