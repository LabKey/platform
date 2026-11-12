/*
 * Copyright (C) 2006 Sun Microsystems, Inc. All rights reserved.
 * Use is subject to license terms.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met: Redistributions of source code
 * must retain the above copyright notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials
 * provided with the distribution. Neither the name of the Sun Microsystems nor the names of
 * is contributors may be used to endorse or promote products derived from this software
 * without specific prior written permission.

 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY
 * AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package org.labkey.core.script;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.reports.LabKeyScriptEngine;
import org.labkey.api.util.ExceptionUtil;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.ImporterTopLevel;
import org.mozilla.javascript.JavaScriptException;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.Wrapper;

import javax.script.AbstractScriptEngine;
import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/**
 * JSR-223 ScriptEngine implementation backed by Mozilla Rhino.
 * Protected methods serve as extension points for {@link RhinoEngine}.
 */
public class RhinoScriptEngine extends AbstractScriptEngine implements LabKeyScriptEngine, Invocable, Compilable
{
    private final Logger _log = LogManager.getLogger(RhinoScriptEngine.class);

    private final ScriptableObject topLevel;
    private final Map<Object, Object> indexedProps;
    private ScriptEngineFactory factory;

    protected RhinoScriptEngine()
    {
        topLevel = createTopLevel();
        indexedProps = new HashMap<>();
    }

    protected ScriptableObject createTopLevel()
    {
        try (Context cx = Context.enter())
        {
            return new ImporterTopLevel(cx, false);
        }
    }

    protected ScriptableObject getTopLevel()
    {
        return topLevel;
    }

    @Override
    public Object eval(Reader reader, ScriptContext ctxt) throws ScriptException
    {
        try (Context cx = Context.enter())
        {
            // Converting a thrown JS value to a message can call back into script, so catch while the Context is still entered
            try
            {
                Scriptable scope = getRuntimeScope(ctxt);
                scope.put("context", scope, ctxt);
                Object ret = cx.evaluateReader(scope, preProcessScriptSource(reader), getFilename(ctxt), 1, null);
                return unwrapReturnValue(ret);
            }
            catch (JavaScriptException jse)
            {
                _log.debug(jse);
                throw toScriptException(jse);
            }
            catch (RhinoException re)
            {
                _log.debug(re);
                throw toScriptException(re);
            }
        }
        catch (IOException ee)
        {
            throw new ScriptException(ee);
        }
    }

    @Override
    public Object eval(String script, ScriptContext ctxt) throws ScriptException
    {
        if (script == null)
            throw new NullPointerException("null script");
        return eval(new StringReader(script), ctxt);
    }

    @Override
    public ScriptEngineFactory getFactory()
    {
        return factory != null ? factory : RhinoService.RHINO_FACTORY;
    }

    @Override
    public Bindings createBindings()
    {
        return new SimpleBindings();
    }

    @Override
    public Object invokeFunction(String name, Object... args) throws ScriptException, NoSuchMethodException
    {
        return invokeMethod(null, name, args);
    }

    @Override
    public Object invokeMethod(Object thiz, String name, Object... args) throws ScriptException, NoSuchMethodException
    {
        try (Context cx = Context.enter())
        {
            try
            {
                if (name == null)
                    throw new NullPointerException("method name is null");

                if (thiz != null && !(thiz instanceof Scriptable))
                    thiz = cx.toObject(thiz, topLevel);

                Scriptable localScope = (thiz != null) ? (Scriptable) thiz : getRuntimeScope(context);
                Object obj = ScriptableObject.getProperty(localScope, name);
                if (!(obj instanceof Function func))
                    throw new NoSuchMethodException("no such method: " + name);

                Scriptable scope = func.getParentScope();
                if (scope == null)
                    scope = getRuntimeScope(context);
                Object result = func.call(cx, scope, localScope, wrapArguments(args));
                return unwrapReturnValue(result);
            }
            catch (JavaScriptException jse)
            {
                _log.debug(jse);
                throw toScriptException(jse);
            }
            catch (RhinoException re)
            {
                _log.debug(re);
                throw toScriptException(re);
            }
        }
    }

    @Override
    public <T> T getInterface(Class<T> clasz)
    {
        return implement(null, clasz);
    }

    @Override
    public <T> T getInterface(Object thiz, Class<T> clasz)
    {
        if (thiz == null)
            throw new IllegalArgumentException("script object can not be null");
        return implement(thiz, clasz);
    }

    @SuppressWarnings("unchecked")
    private <T> @Nullable T implement(@Nullable Object thiz, Class<T> clasz)
    {
        if (clasz == null || !clasz.isInterface())
            return null;
        return (T) Proxy.newProxyInstance(
            clasz.getClassLoader(),
            new Class<?>[]{clasz},
            (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class)
                {
                    return switch (method.getName())
                    {
                        case "equals" -> proxy == args[0];
                        case "hashCode" -> System.identityHashCode(proxy);
                        default -> "Script implementation of " + clasz.getName();
                    };
                }

                try
                {
                    Object result = invokeMethod(thiz, method.getName(), args != null ? args : new Object[0]);
                    Class<?> rt = method.getReturnType();
                    return rt == Void.TYPE ? null : ScriptUtils.jsToJava(result, rt);
                }
                catch (NoSuchMethodException e)
                {
                    throw new UnsupportedOperationException("Script does not implement " + clasz.getName() + "." + method.getName() + "()", e);
                }
                catch (ScriptException e)
                {
                    throw new RuntimeException(e.getMessage(), e);
                }
            });
    }

    @Override
    public CompiledScript compile(String script) throws ScriptException
    {
        return compile(new StringReader(script));
    }

    @Override
    public CompiledScript compile(Reader script) throws ScriptException
    {
        try (Context cx = Context.enter())
        {
            String filename = (String) get(javax.script.ScriptEngine.FILENAME);
            if (filename == null)
                filename = "<Unknown Source>";
            Script scr = cx.compileReader(preProcessScriptSource(script), filename, 1, null);
            return new RhinoCompiledScript(this, scr);
        }
        catch (Exception e)
        {
            _log.debug(e);
            throw new ScriptException(e);
        }
    }

    protected Scriptable getRuntimeScope(ScriptContext ctxt)
    {
        if (ctxt == null)
            throw new NullPointerException("null script context");
        Scriptable newScope = new ExternalScriptable(ctxt, indexedProps);
        newScope.setPrototype(topLevel);
        newScope.put("context", newScope, ctxt);
        return newScope;
    }

    protected Reader preProcessScriptSource(Reader reader)
    {
        return reader;
    }

    protected void setEngineFactory(ScriptEngineFactory fac)
    {
        factory = fac;
    }

    Object[] wrapArguments(Object[] args)
    {
        if (args == null)
            return Context.emptyArgs;
        Object[] res = new Object[args.length];
        for (int i = 0; i < res.length; i++)
            res[i] = Context.javaToJS(args[i], topLevel);
        return res;
    }

    Object unwrapReturnValue(Object result)
    {
        if (result instanceof Wrapper w)
            result = w.unwrap();
        return result instanceof Undefined ? null : result;
    }

    private String getFilename(ScriptContext ctxt)
    {
        String filename = null;
        if (ctxt != null && ctxt.getBindings(ScriptContext.ENGINE_SCOPE) != null)
            filename = (String) ctxt.getBindings(ScriptContext.ENGINE_SCOPE).get(javax.script.ScriptEngine.FILENAME);
        if (filename == null)
            filename = (String) get(javax.script.ScriptEngine.FILENAME);
        return filename != null ? filename : "<Unknown source>";
    }

    static ScriptException toScriptException(JavaScriptException jse)
    {
        int line = jse.lineNumber() == 0 ? -1 : jse.lineNumber();
        Object value = jse.getValue();
        String str = (value != null && "org.mozilla.javascript.NativeError".equals(value.getClass().getName()))
            ? value.toString() : jse.toString();
        ScriptException ex = new ScriptException(str, jse.sourceName(), line);
        ex.initCause(jse);
        ExceptionUtil.decorateException(ex, ExceptionUtil.ExceptionInfo.SkipMothershipLogging, "true", true);
        return ex;
    }

    static ScriptException toScriptException(RhinoException re)
    {
        int line = re.lineNumber() == 0 ? -1 : re.lineNumber();
        ScriptException ex = new ScriptException(re.toString(), re.sourceName(), line);
        ex.initCause(re);
        ExceptionUtil.decorateException(ex, ExceptionUtil.ExceptionInfo.SkipMothershipLogging, "true", true);
        return ex;
    }

    @Override
    public boolean isSandboxed()
    {
        return false;
    }
}
