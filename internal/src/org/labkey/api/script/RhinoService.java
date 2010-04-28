/*
 * Copyright (c) 2010 LabKey Corporation
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
package org.labkey.api.script;

import com.sun.phobos.script.javascript.RhinoScriptEngine;
import com.sun.phobos.script.javascript.RhinoScriptEngineFactory;
import org.apache.log4j.Logger;
import org.labkey.api.services.ServiceRegistry;
import org.mozilla.javascript.*;

import javax.script.*;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class RhinoService
{
    public static void register()
    {
        SandboxContextFactory.initGlobal();
        ServiceRegistry.get().registerService(ScriptService.class, new RhinoFactory());
    }
}

class RhinoFactory extends RhinoScriptEngineFactory implements ScriptService
{
    @Override
    public ScriptEngine getScriptEngine()
    {
        RhinoEngine engine = new RhinoEngine(this);
        return engine;
    }
}

class RhinoEngine extends RhinoScriptEngine
{
    ScriptEngineFactory factory;

    protected RhinoEngine()
    {
        super();
    }

    public RhinoEngine(ScriptEngineFactory factory)
    {
        this();
        this.factory = factory;
    }

    @Override
    public ScriptEngineFactory getFactory()
    {
        return factory;
    }
}

/**
 * Defines the Rhino sandbox.
 * http://codeutopia.net/blog/2009/01/02/sandboxing-rhino-in-java/
 */
class SandboxContextFactory extends ContextFactory
{
    private static final Logger log = Logger.getLogger(SandboxContextFactory.class);

    private static final ContextFactory SANDBOX = new SandboxContextFactory();
    private static final Set<String> DISALLOWED_METHODS;
    private static final Set<String> ALLOWED_CLASSES;

    static
    {
        HashSet<String> disallowedMethods = new HashSet<String>();
        disallowedMethods.add("class");
        disallowedMethods.add("clone");
        disallowedMethods.add("equals");
        disallowedMethods.add("finalize");
        disallowedMethods.add("getClass");
        disallowedMethods.add("hashCode");
        disallowedMethods.add("notify");
        disallowedMethods.add("notifyAll");
//        disallowedMethods.add("toString");
        disallowedMethods.add("wait");
        DISALLOWED_METHODS = Collections.unmodifiableSet(disallowedMethods);
        
        HashSet<String> allowedClasses = new HashSet<String>();
        allowedClasses.add(Boolean.class.getName());
        allowedClasses.add(Character.class.getName());
        allowedClasses.add(Double.class.getName());
        allowedClasses.add(EcmaError.class.getName());
        allowedClasses.add(Float.class.getName());
        allowedClasses.add(Integer.class.getName());
        allowedClasses.add(JavaScriptException.class.getName());
        allowedClasses.add(Long.class.getName());
        allowedClasses.add(PrintStream.class.getName());
        allowedClasses.add(PrintWriter.class.getName());
        allowedClasses.add(RhinoException.class.getName());
        allowedClasses.add(Short.class.getName());
        allowedClasses.add(SimpleScriptContext.class.getName());
        allowedClasses.add(String.class.getName());
        allowedClasses.add(URI.class.getName());
        ALLOWED_CLASSES = Collections.unmodifiableSet(allowedClasses);
    }

    public static void initGlobal()
    {
        ContextFactory.initGlobal(SANDBOX);
    }

    @Override
    protected Context makeContext()
    {
        Context context = super.makeContext();
        context.setClassShutter(new SandboxShutter());
        context.setWrapFactory(new SandboxWrapFactory());
        return context;
    }

    @Override
    protected boolean hasFeature(Context cx, int featureIndex)
    {
        return super.hasFeature(cx, featureIndex);
    }

    private static class SandboxShutter implements ClassShutter
    {
        @Override
        public boolean visibleToScripts(String fullClassName)
        {
            if (ALLOWED_CLASSES.contains(fullClassName))
                return true;
            log.warn("Rhino sandbox disallowed class: " + fullClassName);
            return false;
        }
    }

    private static class SandboxWrapFactory extends WrapFactory
    {
        @Override
        public Scriptable wrapAsJavaObject(Context cx, Scriptable scope,
                                           Object javaObject, Class staticType) {
          return new SandboxNativeJavaObject(scope, javaObject, staticType);
        }
    }

    private static class SandboxNativeJavaObject extends NativeJavaObject
    {
        public SandboxNativeJavaObject(Scriptable scope, Object javaObject, Class<?> staticType)
        {
            super(scope, javaObject, staticType);
        }

        @Override
        public Object get(String name, Scriptable start)
        {
            if (DISALLOWED_METHODS.contains(name))
                return NOT_FOUND;
            return super.get(name, start);
        }
    }
}
