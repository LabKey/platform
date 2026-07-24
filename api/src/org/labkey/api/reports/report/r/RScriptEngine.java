/*
 * Copyright (c) 2013-2026 LabKey Corporation
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
package org.labkey.api.reports.report.r;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.JdbcType;
import org.labkey.api.reports.ExternalScriptEngine;
import org.labkey.api.reports.ExternalScriptEngineDefinition;
import org.labkey.api.reports.report.ScriptPackageUsageTracker;
import org.labkey.vfs.FileLike;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngineFactory;
import java.util.Arrays;
import java.util.List;

/*
* User: dax
* Date: May 15, 2013
* Time: 4:33:23 PM
*/
public class RScriptEngine extends ExternalScriptEngine
{
    private static final String PACKAGES_FILE = "labkeyRPackages.txt";
    public static final String KNITR_FORMAT = "r.script.engine.knitrFormat";
    public static final String KNITR_OUTPUT = "r.script.engine.knitrOutput";
    public static final String PANDOC_USE_DEFAULT_OUTPUT_FORMAT = "r.script.engine.pandocUseDefaultOutputFormat";
    public static final String PANDOC_OUTPUT_OPTIONS_LIST = "r.script.engine.pandocUseCustomOutputOptions";
    public static final String PANDOC_DEFAULT_OUTPUT_OPTIONS_LIST = "keep_md=TRUE, self_contained=FALSE, fig_caption=TRUE, theme=NULL, css=NULL, smart=TRUE, highlight=\"default\"";

    // script engine properties that report can request
    public static final String PROP_REMOTE = "remote";

    public static final String DOCKER_IMAGE_TYPE = "docker";

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

    @Override
    protected FileLike prepareScriptFile(String script, ScriptContext context, List<String> extensions)
    {
        FileLike scriptFile;
        if (getKnitrFormat(context) != RReportDescriptor.KnitrFormat.None)
        {
            //
            // If we are using Knitr then we need to write a new R script that calls knitr and passes
            // the input R script into it
            //

            // write the incoming script as the input of the preprocessor {ex: script.rhtml}
            List<String> preprocessExtensions = Arrays.asList(getKnitrExtension(context, extensions));
            scriptFile = writeScriptFile(script, context, preprocessExtensions);

            // write a new script (the actual .R script to be run) as the preprocessing script and use
            // this as the script file we pass to the script engine
            String preprocessScript = createKnitrScript(context, scriptFile);
            scriptFile = writeScriptFile(preprocessScript, context, extensions);
        }
        else
        {
            scriptFile = writeScriptFile(script, context, extensions);
        }

        return scriptFile;
    }

    /**
     * R appended to the end of the user script (in the same R session) that captures the set of loaded packages,
     * writing them (one per line) to a sidecar file in the working directory for {@link #recordPackageUsage} to read
     * back. Wrapped in tryCatch so a capture failure can never break the report or transform run.
     */
    @Override
    protected @Nullable String getPackageCaptureEpilog(ScriptContext context)
    {
        // For knitr the executed file is a generated wrapper (createKnitrScript), not the user's R, so appending R here
        // wouldn't run. We instead record the wrapper's known libraries in recordSuccessfulRun().
        if (getKnitrFormat(context) != RReportDescriptor.KnitrFormat.None)
            return null;

        return """
                # --- LabKey R package usage capture ---
                tryCatch({
                    writeLines(sort(loadedNamespaces()), "%s")
                }, error = function(e) invisible(NULL))
                """.formatted(PACKAGES_FILE);
    }

    @Override
    protected void recordPackageUsage(ScriptContext context)
    {
        readPackageSidecar(context, PACKAGES_FILE, "r");
    }

    @Override
    protected void recordSuccessfulRun(ScriptContext context)
    {
        // Non-knitr R runs report their loaded packages via the capture epilog (see getPackageCaptureEpilog).
        if (getKnitrFormat(context) == RReportDescriptor.KnitrFormat.None)
            return;

        // For knitr, the generated wrapper (createKnitrScript) always loads knitr and, for the markdown+pandoc path,
        // rmarkdown; record those as R package usage so they show up alongside other packages.
        // NOTE: this only captures the wrapper's libraries, not packages loaded inside the report's own R chunks (e.g.
        // ggplot2 used within an .rmd). Fully capturing those would require injecting a loadedNamespaces() write into
        // createKnitrScript after the knit()/render() call.
        ScriptPackageUsageTracker.record("r", "knitr");
        if (getKnitrFormat(context) == RReportDescriptor.KnitrFormat.Markdown && isPandocEnabled())
            ScriptPackageUsageTracker.record("r", "rmarkdown");
    }

    private boolean isPandocEnabled()
    {
        return _def.isPandocEnabled();
    }

    protected String getKnitrExtension(ScriptContext context, List<String> extensions)
    {
        // consider: make a format class and then just override the specified html, md, functions
        if (getKnitrFormat(context) == RReportDescriptor.KnitrFormat.Html)
            return extensions.getFirst() + "html";

        if (getKnitrFormat(context) == RReportDescriptor.KnitrFormat.Markdown)
            return extensions.getFirst() + "md";

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

    protected String getPandocOutputOptionsList(ScriptContext context)
    {
        Bindings bindings = context.getBindings(ScriptContext.ENGINE_SCOPE);

        Object v = bindings.get(PANDOC_OUTPUT_OPTIONS_LIST);
        if (null == v)
            return null;
        return (String) v;
    }

    protected String getInputFilename(FileLike inputScript)
    {
        return inputScript.toNioPathForRead().toFile().getAbsolutePath().replaceAll("\\\\", "/");
    }

    protected String getOutputFilename(FileLike inputScript)
    {
        String outputFilename;
        // do not call getInputFilename here as we do not want to invoke
        // any overrides.  The output file name should be the local path even
        // in the Rserve case since this file is manipulated on the labkey
        // server
        String inputFilename = inputScript.toNioPathForRead().toFile().getAbsolutePath().replaceAll("\\\\", "/");
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


    public String getRemotePath(FileLike localFile)
    {
        return RReport.getLocalPath(localFile);
    }


    public String getRemotePath(String localURI)
    {
        return localURI;
    }


    protected String createKnitrScript(ScriptContext context, FileLike inputScript)
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
                    sb.append("html_document(")
                            .append(PANDOC_DEFAULT_OUTPUT_OPTIONS_LIST)
                            .append("), ");
                }
                else
                {
                    String outputOptions = getPandocOutputOptionsList(context);
                    sb.append("output_options=list(")
                            .append(StringUtils.isEmpty(outputOptions) ? PANDOC_DEFAULT_OUTPUT_OPTIONS_LIST : outputOptions)
                            .append("), ");
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

    @Override
    public boolean isSandboxed()
    {
        return _def.isSandboxed();
    }
}