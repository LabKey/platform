/*
 * Copyright (c) 2017-2019 LabKey Corporation
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
package org.labkey.core.wiki;

import org.apache.commons.pool.KeyedPoolableObjectFactory;
import org.apache.commons.pool.impl.StackKeyedObjectPool;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.labkey.api.markdown.MarkdownService;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.reports.LabKeyScriptEngineManager;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.Path;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.util.logging.LogHelper;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;

public class MarkdownServiceImpl implements MarkdownService
{
    private static final Logger LOG = LogHelper.getLogger(MarkdownServiceImpl.class, "Translates Markdown to HTML");

    /**
     * The JS-based rendering engine is expensive to create so use a simple pool that reuses them. Always creates
     * a new instance if the pool doesn't have one available. Caps the max number in the pool at 5.
     */
    private final StackKeyedObjectPool<Map<Options, Boolean>, MarkdownInvocable> POOL = new StackKeyedObjectPool<>(new PoolFactory(), 5, 0);

    private static class PoolFactory implements KeyedPoolableObjectFactory<Map<Options, Boolean>, MarkdownInvocable>
    {
        @Override
        public MarkdownInvocable makeObject(Map<Options, Boolean> options) throws Exception
        {
            LOG.debug("Initializing new ScriptEngine");
            LabKeyScriptEngineManager svc = LabKeyScriptEngineManager.get();
            if (null == svc)
                throw new ConfigurationException("LabKeyScriptEngineManager service not found.");
            ScriptEngine engine = svc.getEngineByName("graal.js");
            if (null == engine)
                throw new ConfigurationException("Graal.js engine not found");

            Module module = ModuleLoader.getInstance().getCoreModule();
            Path path = Path.parse("scripts/").append("markdown-it.js");
            Resource r = module.getModuleResource(path);
            if (null == r || !r.isFile())
                throw new ConfigurationException("markdown-it.js not found");

            try (var is = new BufferedInputStream(r.getInputStream(), 1024 * 20))
            {
                engine.eval(new InputStreamReader(is, StringUtilsLabKey.DEFAULT_CHARSET));
            }
            catch (IOException x)
            {
                throw new ConfigurationException("Could not open markdown-it.js", x);
            }
            JSONObject json = new JSONObject(Map.of("breaks",true,"linkify",true, "html", false));
            options.forEach((key, value) -> json.put(key.toString(), value));
            engine.eval("var md = new markdownit(" + json + ")");
            Object mdCompiled = engine.eval("md");
            Invocable invocable = (Invocable) engine;
            invocable.invokeMethod(mdCompiled, "render", "# call render method here to ensure that the script engine compiles this method before use by app");

            return new MarkdownInvocable(invocable, mdCompiled);
        }

        @Override
        public void destroyObject(Map<Options, Boolean> key, MarkdownInvocable obj)
        {
        }

        @Override
        public boolean validateObject(Map<Options, Boolean> key, MarkdownInvocable obj)
        {
            // In case of memory leaks within the JS engine and/or Markdown renderer, toss after 100 renderings
            // so it can be garbage collected
            boolean valid = obj.getUses() < 100;
            if (!valid)
            {
                LOG.debug("MarkDown ScriptEngine has reached its end of life and will be discarded");
            }
            return valid;
        }

        @Override
        public void activateObject(Map<Options, Boolean> key, MarkdownInvocable obj) {}

        @Override
        public void passivateObject(Map<Options, Boolean> key, MarkdownInvocable obj) {}
    }


    public MarkdownServiceImpl()
    {
        try
        {
            // fail fast if we can't successfully create engine
            toHtml("");
        }
        catch (ConfigurationException x)
        {
            throw x;
        }
        catch (Exception x)
        {
            throw new ConfigurationException("Could not initialize Markdownservice", x);
        }
    }

    @Override
    public String toHtml(String mdText) throws NoSuchMethodException, ScriptException
    {
        return toHtml(mdText, Map.of());
    }

    @Override
    public String toHtml(String mdText, Map<Options,Boolean> options) throws NoSuchMethodException, ScriptException
    {
        // make sure that the source text has the carriage returns escaped and the whole thing encoded
        // otherwise it wont parse right as a js string if it hits a cr or a quote.
        //        mdText = mdText.replace("\n", "\\n");
        //        mdText = PageFlowUtil.filter(mdText);
        //        Object testResult = engine.eval("md.render('" + mdText + "')");

        // Better yet, avoid js injection by using the invokeMethod syntax that takes a java String as a param

        // markdownit won't accept null as input param
        if (null == mdText)
            mdText = "";

        MarkdownInvocable mi = null;
        try
        {
            mi = POOL.borrowObject(options);
            Object html = mi.invoke(mdText);
            if (null == html)
                return null;
            // #32468 include selector so we can have markdown-specific styling namespace
            return "<div class=\"lk-markdown-container\">" + html + "</div>";
        }
        catch (NoSuchMethodException | ScriptException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw UnexpectedException.wrap(e);
        }
        finally
        {
            if (mi != null)
            {
                try
                {
                    POOL.returnObject(options, mi);
                }
                catch (Exception e)
                {
                    throw UnexpectedException.wrap(e);
                }
            }
        }
    }

    private static class MarkdownInvocable
    {
        private final Invocable _invocable;
        private final Object _mdCompiled;
        int _uses = 0;

        public MarkdownInvocable(Invocable invocable, Object mdCompiled)
        {
            _invocable = invocable;
            _mdCompiled = mdCompiled;
        }

        Object invoke(String mdText) throws NoSuchMethodException, ScriptException
        {
            _uses++;
            return _invocable.invokeMethod(_mdCompiled, "render", mdText);
        }

        public int getUses()
        {
            return _uses;
        }
    }
}
