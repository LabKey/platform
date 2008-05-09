package org.labkey.api.reports.report;

import org.apache.commons.lang.StringUtils;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.api.reports.report.r.ParamReplacement;
import org.rosuda.REngine.REXP;
import org.rosuda.REngine.REXPDouble;
import org.rosuda.REngine.REXPMismatchException;
import org.rosuda.REngine.Rserve.RConnection;
import org.rosuda.REngine.Rserve.RserveException;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Aug 12, 2007
 */
public class RServeScriptRunner extends AbstractScriptRunner
{
    public static final String ID = "RServeRunner";
    public RServeScriptRunner(RReport report, ViewContext context)
    {
        super(report, context);
    }

    public boolean runScript(VBox view, List<ParamReplacement> outputSubst)
    {
        File inputFile = _data;
        try {
            ensureServer();
            // create the input file from the source query view
            if (inputFile == null)
                inputFile = DefaultScriptRunner.createInputDataFile(_report, _context);

            return runScript(view, inputFile, outputSubst, new ArrayList<String>());
        }
        catch (Exception e)
        {
            String error = "<font color='red'>An error occurred rendering this view, you may not have permission to view it</font>";
            view.addView(new HtmlView(error));
            return false;
        }
        finally
        {
            if (inputFile != null && inputFile.exists())
                inputFile.delete();
        }
    }

    private boolean runScript(VBox view, File inputFile, List<ParamReplacement> outputSubst, List<String> errors) throws Exception
    {
        boolean ret = true;
        RConnection connection = null;
        try
        {
            String script = processScript(_report.getDescriptor().getProperty(RReportDescriptor.Prop.script), inputFile, outputSubst);
            connection = new RConnection();
            try {
                StringBuilder sb = new StringBuilder();
                StringTokenizer tokenizer = new StringTokenizer(script, "\n\r");

                boolean output = false;
                while(tokenizer.hasMoreTokens())
                {
                    final String cmd = formatCmd(tokenizer.nextToken());
                    if (cmd != null)
                    {
                        REXP exp = connection.eval(cmd);
                        if (output)
                        {
                            outputEval(exp, sb);
                            sb.append("\n");
                        }
                        output = true;
                    }
                }
                if (isDebug())
                    view.addView(new ConsoleOutputView(sb.toString()));
            }
            catch (RserveException rse)
            {
                String err = "<font color='red'>An Error occurred executing the script</font><pre>" + rse.getMessage() + "</pre>";
                view.addView(new HtmlView(err));
            }
        }
        catch (RserveException rse)
        {
            String err = "<font color='red'>Unable to connect to Rserve</font><pre>" + rse.getMessage() + "</pre>";
            view.addView(new HtmlView(err));
        }
        finally
        {
            if (connection != null)
                connection.close();
        }
        return ret;
    }

    /**
     * Formats an R command to be executed by an Rserve server.
     * @param token
     * @return
     */
    private String formatCmd(String token)
    {
        // need to remove comment portions of a command
        int idx = token.indexOf('#');
        if (idx != -1)
            token = token.substring(0, idx);

        if (!StringUtils.isEmpty(token))
        {
            return "capture.output(print(" + token + "))";
        }
        return null;
    }

    private void outputEval(REXP eval, StringBuilder sb) throws Exception
    {
        try {
            if (eval.isString())
            {
                for (String s : eval.asStrings())
                {
                    sb.append(s);
                    sb.append(' ');
                }
            }
            else if (eval.isInteger())
            {
                for (int i : eval.asIntegers())
                {
                    sb.append(i);
                    sb.append(' ');
                }
            }
            else if (REXPDouble.class.isAssignableFrom(eval.getClass()))
            {
                for (double d : eval.asDoubles())
                {
                    sb.append(d);
                    sb.append(' ');
                }
            }
            else if (eval.isList())
            {
                for (Object o : eval.asList().values())
                {
                    if (REXP.class.isAssignableFrom(o.getClass()))
                        outputEval((REXP)o, sb);
                    sb.append('\n');
                }
            }
            else
                sb.append(eval.toDebugString());
        }
        catch (REXPMismatchException me)
        {
            sb.append("conversion error for object: " + eval.toDebugString());
            sb.append("\n");
        }
    }

    protected String processScript(String script, File inputFile, List<ParamReplacement> outputSubst) throws Exception
    {
        // process the primary script
        String processedScript = createScript(script, inputFile, outputSubst);

        // process any included scripts
/*
            for (String includedReport : ((RReportDescriptor)_report.getDescriptor()).getIncludedReports())
            {
                Report report = ReportService.get().getReport(null, NumberUtils.toInt(includedReport));

                if (report != null && RReport.class.isAssignableFrom(report.getClass()))
                {
                    final String rName = report.getDescriptor().getProperty(ReportDescriptor.REPORT_NAME);
                    final String rScript = report.getDescriptor().getProperty(RReportDescriptor.SCRIPT);
                    final File rScriptFile = getFile(reportFile.includedScript, rName + ".R");

                    createScript(rScript, rScriptFile, inputFile, outputSubst);
                }
            }
*/
        return processedScript;
    }

    protected String createScript(String script, File inputFile, List<ParamReplacement> outputSubst) throws Exception
    {
        if (!StringUtils.isEmpty(script))
        {
            // handle the data file substitution param
            script = labkeyObjectProlog() + script;
            script = processInputReplacement(script, inputFile);
            script = processOutputReplacements(script, outputSubst);

            return script;
        }
        return null;
    }

    public static void ensureServer()
    {
        if (!isServerStarted())
        {
            File rserve = findRServeExe();
            if (rserve != null && rserve.exists())
            {
                ProcessBuilder pb = new ProcessBuilder(rserve.getAbsolutePath());
                try
                {
                    pb.redirectErrorStream(true);
                    Process proc = pb.start();
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
            }
        }
    }

    public static File findRServeExe()
    {
        String exePath = RReport.getRExe();
        if (!StringUtils.isEmpty(exePath))
        {
            File current = new File(exePath).getParentFile();
            File libDir = null;
            while (current != null && current.exists())
            {
                libDir = new File(current, "library");
                if (libDir.exists())
                    break;
                current = current.getParentFile();
            }

            if (libDir != null && libDir.exists())
            {
                File rserveDir = new File(libDir, "Rserve");
                if (rserveDir.exists())
                {
                    File rserve = new File(rserveDir, "Rserve");
                    if (rserve.exists())
                        return rserve;
                    else
                        return new File(rserveDir, "Rserve.exe");
                }
            }
        }
        return null;
    }

    public static boolean isServerStarted()
    {
        RConnection c = null;
        try {
            c = new RConnection();
            return c.isConnected();
        }
        catch (RserveException rse)
        {
            return false;
        }
        finally
        {
            if (c != null)
                c.close();
        }
    }

    public static void stopRServer()
    {
        try {
            RConnection c = new RConnection();
            c.shutdown();
        }
        catch (RserveException rse)
        {
            return;
        }
    }

    protected static class ConsoleOutputView extends HttpView
    {
        String _output;
        ConsoleOutputView(String output)
        {
            _output = output;
        }

        @Override
        protected void renderInternal(Object model, PrintWriter out) throws Exception
        {
            if (!StringUtils.isEmpty(_output))
            {
                out.write("<table width=\"100%\">");
                out.write("<tr class=\"wpHeader\"><th colspan=2 align=center>Console output</th></tr>");
                out.write("<tr><td><pre>");
                out.write(_output);
                out.write("</pre></td></tr>");
                out.write("</table>");
            }
        }
    }
}

