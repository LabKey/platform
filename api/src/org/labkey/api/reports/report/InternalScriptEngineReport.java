/*
 * Copyright (c) 2009-2016 LabKey Corporation
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

import org.labkey.api.reports.ExternalScriptEngine;
import org.labkey.api.reports.report.r.ParamReplacement;
import org.labkey.api.reports.report.r.ParamReplacementSvc;
import org.labkey.api.reports.report.r.view.ConsoleOutput;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.ContainerUser;
import org.labkey.api.writer.PrintWriters;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/*
* User: Karl Lum
* Date: Jan 19, 2009
* Time: 4:11:54 PM
*/
public class InternalScriptEngineReport extends ScriptEngineReport
{
    public static final String TYPE = "ReportService.internalScriptEngineReport";

    public String getType()
    {
        return TYPE;
    }

    public HttpView renderReport(ViewContext context) throws Exception
    {
        VBox view = new VBox();
        String script = getDescriptor().getProperty(ScriptReportDescriptor.Prop.script);
        List<String> errors = new ArrayList<>();

        if (!validateScript(script, errors))
        {
            for (String error : errors)
                view.addView(new HtmlView("<span class=\"labkey-error\">" + error + "</span>"));
            return view;
        }

        List<ParamReplacement> outputSubst = new ArrayList<>();

        try
        {
            // todo: when we refactor, pass inputParameters down from the upper layer
            // through the renderReport API.
            runScript(context, outputSubst, createInputDataFile(context), null);
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
        }

        renderViews(this, view, outputSubst, false);

        return view;
    }

    @Override
    public String runScript(ViewContext context, List<ParamReplacement> outputSubst, File inputDataTsv, Map<String, Object> inputParameters) throws ScriptException
    {
        ScriptEngine engine = getScriptEngine();
        if (engine != null)
        {
            StringWriter errors = new StringWriter();
            StringWriter consoleOut = new StringWriter();
            try
            {
                PrintWriter consolePw = new PrintWriter(consoleOut);
                engine.getContext().setErrorWriter(errors);
                engine.getContext().setWriter(consolePw);

                Bindings bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);

                bindings.put(ScriptEngine.FILENAME, "ScriptReport");
                bindings.put("viewContext", context);
                bindings.put("consoleOut", consolePw);

                bindings.put(ExternalScriptEngine.WORKING_DIRECTORY, getReportDir(context.getContainer().getId()).getAbsolutePath());
                Object output = engine.eval(createScript(engine, context, outputSubst, inputDataTsv, inputParameters));
                consolePw.flush();
                String consoleString = consoleOut.toString();

                // render the output into the console
                if (output != null || consoleString != null)
                {
                    File console = new File(getReportDir(context.getContainer().getId()), CONSOLE_OUTPUT);

                    try (PrintWriter pw = PrintWriters.getPrintWriter(console))
                    {
                        if (output != null)
                            pw.write(output.toString());
                        if (consoleString != null)
                            pw.write(consoleOut.toString());
                    }

                    ParamReplacement param = ParamReplacementSvc.get().getHandlerInstance(ConsoleOutput.ID);
                    param.setName("console");
                    param.addFile(console);

                    outputSubst.add(param);
                }
                return output != null ? output.toString() : "";
            }
            catch(Exception e)
            {
                if (errors.getBuffer().length() > 0)
                    throw new ScriptException(e.getMessage() + errors.getBuffer().toString());
                else
                    throw new ScriptException(e);
            }
        }
        throw new ScriptException("A script engine implementation was not found for the specified report");
    }

    /**
     * Called before this report is deleted
     * @param context
     */
    public void beforeDelete(ContainerUser context)
    {
        deleteReportDir(context);
        super.beforeDelete(context);
    }
}
