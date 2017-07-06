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

    public MarkdownServiceImpl() throws Exception
    {
        LabKeyScriptEngineManager engineManager = new LabKeyScriptEngineManager();
        engine = engineManager.getEngineByName("nashorn");

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

    @Override
    public String toHtml(String mdText) throws NoSuchMethodException, ScriptException
    {
        // make sure that the source text has the carriage returns escaped and the whole thing encoded
        // otherwise it wont parse right as a js string if it hits a cr or a quote.
        //        mdText = mdText.replace("\n", "\\n");
        //        mdText = PageFlowUtil.filter(mdText);
        //        Object testResult = engine.eval("md.render('" + mdText + "')");

        // Better yet, avoid js injection by using the invokeMethod syntax that takes a java String as a param
        Invocable invocable = (Invocable) engine;
        // markdownit wont accept null as input param
        if (null == mdText)
            mdText = "";
        Object html = invocable.invokeMethod(mdCompiled, "render", mdText);
        if (null == html)
            return null;
        return html.toString();
    }
}
