/*
 * Copyright (c) 2008-2017 LabKey Corporation
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
package org.labkey.api.reports;

import org.labkey.api.data.JdbcType;
import org.labkey.api.reports.report.RReport;
import org.labkey.api.reports.report.RReportDescriptor;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;
import java.io.File;
import java.util.Arrays;
import java.util.List;

/*
* User: dax
* Date: May 15, 2013
* Time: 4:33:23 PM
*/
public class RScriptEngine extends ExternalScriptEngine
{
    public static final String KNITR_FORMAT = "r.script.engine.knitrFormat";
    public static final String KNITR_OUTPUT = "r.script.engine.knitrOutput";
    public static final String PANDOC_USE_DEFAULT_OUTPUT_FORMAT = "r.script.engine.pandocUseDefaultOutputFormat";

    // script engine properties that report can request
    public static final String PROP_REMOTE = "remote";

    private RReportDescriptor.KnitrFormat _knitrFormat;

    public RScriptEngine(ExternalScriptEngineDefinition def)
    {
        super(def);
    }

    @Override
    public ScriptEngineFactory getFactory()
    {
        return new RScriptEngineFactory(_def);
    }

    public File prepareScript(String script)
    {
        return prepareScriptFile(script, context, Arrays.asList(_def.getExtensions()), false);
    }

    protected File prepareScriptFile(String script, ScriptContext context, List<String> extensions, boolean createWrapper)
    {
        File scriptFile = null;
        if (getKnitrFormat(context) != RReportDescriptor.KnitrFormat.None)
        {
            //
            // If we are using Knitr then we need to write a new R script that calls knitr and passes
            // the input R script into it
            //

            // write the incoming script as the input of the preprocessor {ex: script.rhtml}
            List<String> preprocessExtensions = Arrays.asList(getKnitrExtension(context, extensions));
            scriptFile = writeScriptFile(script, context, preprocessExtensions);

            if (createWrapper)
            {
                // write a new script (the acutal .R script to be run) as the preprocessing script and use
                // this as the script file we pass to the script engine
                String preprocessScript = createKnitrScript(context, scriptFile);
                scriptFile = writeScriptFile(preprocessScript, context, extensions);
            }
        }
        else
        {
            scriptFile = writeScriptFile(script, context, extensions);
        }

        return scriptFile;
    }

    @Override
    public Object eval(String script, ScriptContext context) throws ScriptException
    {
        List<String> extensions = getFactory().getExtensions();

        if (!extensions.isEmpty())
        {
            File scriptFile = prepareScriptFile(script, context, extensions, true);
            return eval(scriptFile, context);
        }
        else
            throw new ScriptException("There are no file name extensions registered for this ScriptEngine : " + getFactory().getLanguageName());
    }

    private boolean isPandocEnabled()
    {
        return _def.isPandocEnabled();
    }

    protected String getKnitrExtension(ScriptContext context, List<String> extensions)
    {
        // consider: make a format class and then just override the specified html, md, functions
        if (getKnitrFormat(context) == RReportDescriptor.KnitrFormat.Html)
            return extensions.get(0) + "html";

        if (getKnitrFormat(context) == RReportDescriptor.KnitrFormat.Markdown)
            return extensions.get(0) + "md";

        return null;
    }

    protected RReportDescriptor.KnitrFormat getKnitrFormat(ScriptContext context)
    {
        if (null == _knitrFormat)
        {
            Bindings bindings = context.getBindings(ScriptContext.ENGINE_SCOPE);

            if (bindings.containsKey(KNITR_FORMAT))
                _knitrFormat = ((RReportDescriptor.KnitrFormat)bindings.get(KNITR_FORMAT));
            else
                _knitrFormat = RReportDescriptor.KnitrFormat.None;
        }

        return _knitrFormat;
    }

    protected boolean useDefaultOutputFormat(ScriptContext context)
    {
        Bindings bindings = context.getBindings(ScriptContext.ENGINE_SCOPE);

        if (!bindings.containsKey(PANDOC_USE_DEFAULT_OUTPUT_FORMAT))
            return true;
        Object v = bindings.get(PANDOC_USE_DEFAULT_OUTPUT_FORMAT);
        if (null == v)
            return true;
        return (Boolean)JdbcType.BOOLEAN.convert(v);
    }

    protected String getInputFilename(File inputScript)
    {
        return inputScript.getAbsolutePath().replaceAll("\\\\", "/");
    }

    protected String getOutputFilename(File inputScript)
    {
        String outputFilename = null;
        // do not call getInputFilename here as we do not want to invoke
        // any overrides.  The output file name should be the local path even
        // in the Rserve case since this file is manipulated on the labkey
        // server
        String inputFilename = inputScript.getAbsolutePath().replaceAll("\\\\", "/");
        String ext = "html";

        if (inputFilename.lastIndexOf('.') != -1)
            outputFilename = inputFilename.substring(0, inputFilename.lastIndexOf('.') + 1) + ext;
        else
            outputFilename = inputFilename + "." + ext;

        return outputFilename.replaceAll("\\\\", "/");
    }

    protected void setKnitrOutput(ScriptContext context, String value)
    {
        Bindings bindings = context.getBindings(ScriptContext.ENGINE_SCOPE);
        bindings.put(KNITR_OUTPUT, value);
    }

    protected String getRWorkingDir(ScriptContext context)
    {
        return RReport.getLocalPath(getWorkingDir(context));
    }


    public String getRemotePath(File localFile)
    {
        return RReport.getLocalPath(localFile);
    }


    public String getRemotePath(String localURI)
    {
        return localURI;
    }


    protected String createKnitrScript(ScriptContext context, File inputScript)
    {
        if (getKnitrFormat(context) == RReportDescriptor.KnitrFormat.None)
            return null;

        // consider adding 'quiet mode' if we don't want knitr processing output
        // or have a knitr options argument for the R script engine definition
        StringBuilder sb = new StringBuilder();

        // Set the working directory for knitr reports to be the same as
        // where we load the input script.  Knitr will output the R results and final
        // html to this working directory
        // pandoc will fail if HOME is not set
        String wd = getRWorkingDir(context);
        String remote = getRemotePath(wd);
        sb.append("setwd(\"").append(remote).append("\")\n");
        sb.append("Sys.setenv(HOME = \"").append(remote).append("\")\n");
        sb.append("library(knitr)\n");

        //
        // setup a knitr hook to translate the knitr-generated filename to a parameter
        // replacement token so that we can fixup the url to the file
        //
        sb.append("labkey.makeHref <- function(filename)\n");
        sb.append("{ return (paste0(\"${hrefout:\", filename, \"}\")) }\n");

        //
        // if the format is markdown then we use a knit2html to combine knit and markdownToHtml functions
        // and return html to the user
        //
        if (getKnitrFormat(context) == RReportDescriptor.KnitrFormat.Markdown)
        {
            if(isPandocEnabled())
            {
                sb.append("library(rmarkdown)\n");
                sb.append("opts_knit$set(upload.fun = labkey.makeHref)\n");
                sb.append("render(run_pandoc=TRUE, ");
                if (useDefaultOutputFormat(context))
                {
                    sb.append("output_format=html_document_base(keep_md=TRUE, self_contained=FALSE, fig_caption=TRUE, " +
                            "theme=NULL, css=NULL, smart=TRUE, highlight=\"default\"), ");
                }
                else
                {
                    // TODO: should the output_options list params match between these and the ones above (note missing theme=NULL and css=NULL here)
                    sb.append("output_options=list(keep_md=TRUE, self_contained=FALSE, fig_caption=TRUE, smart=TRUE, highlight=\"default\"), ");
                }
            }
            else
            {

                //TODO: this should be outside if statement, but is not correctly substituting currently for markdown v2
                sb.append("opts_knit$set(upload.fun = labkey.makeHref)\n");

                //
                // if we just use the knit2html defaults then it overrides the labkey styles.  So suppress the style
                // block by specifying no css.  Also, use the default options for knit2html except don't base64 encode
                // images
                //
                sb.append("knit2html(options=c('use_xhtml', 'smartypants', 'mathjax', 'highlight_code'), stylesheet='', ");
            }
        }
        else
        {
            sb.append("opts_knit$set(upload.fun = labkey.makeHref)\n");
            sb.append("knit(");
        }

        sb.append("input=\"");
        sb.append(remote).append("/").append(inputScript.getName());
        sb.append("\")\n");

        //
        // No need to specify the output filename in this script.  Knitr will use the input filename to derive the output
        // filename.  Remember the name, however, so that we can return it later after the script runs.
        //
        setKnitrOutput(context, getOutputFilename(inputScript));

        return sb.toString();
    }
}