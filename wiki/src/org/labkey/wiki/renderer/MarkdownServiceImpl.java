package org.labkey.wiki.renderer;

import org.labkey.api.markdown.MarkdownService;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.reports.LabKeyScriptEngineManager;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.Path;
import org.labkey.api.util.StringUtilsLabKey;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.io.BufferedInputStream;
import java.io.InputStreamReader;

public class MarkdownServiceImpl implements MarkdownService
{
    ScriptEngine engine;
    Object mdCompiled;

    public MarkdownServiceImpl()
    {
        LabKeyScriptEngineManager engineManager = new LabKeyScriptEngineManager();
        engine = engineManager.getEngineByName("nashorn");
        try
        {
            Module module = ModuleLoader.getInstance().getModule("Wiki");
            Path path = Path.parse("scripts/").append("markdown-it.js");
            Resource r = module.getModuleResource(path);
            if (r != null && r.isFile() && r.exists())
            {
                engine.eval(new InputStreamReader(new BufferedInputStream(r.getInputStream(), 1024 * 20), StringUtilsLabKey.DEFAULT_CHARSET));
                engine.eval("var md = new markdownit()");
                mdCompiled = engine.eval("md");
            }
        }

        catch (Exception e)
        {
            // todo: unable to create the nashorn engine probably should log that cause the md2htm isnt going to work if its not there
        }
    }

    public String mdToHtml(String mdText)
    {
        // make sure that the source text has the carriage returns escaped and the whole thing encoded
        // otherwise it wont parse right as a js string if it hits a cr or a quote.
        //        mdText = mdText.replace("\n", "\\n");
        //        mdText = PageFlowUtil.filter(mdText);
        //        Object testResult = engine.eval("md.render('" + mdText + "')");

        // Better yet, avoid js injection by using the invokeMethod syntax that takes a java String as a param
        Invocable invocable = (Invocable) engine;
        Object testResult = null;
        try
        {
            testResult = invocable.invokeMethod(mdCompiled, "render", mdText);
        }
        catch (NoSuchMethodException | ScriptException e)
        {
            throw new IllegalStateException("Java script engine not able to invoke markdownit method to translate markdown to html.");
        }

        String testString = testResult.toString();
        return testString;
    }
}
