/*
 * Copyright (c) 2010-2017 LabKey Corporation
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

import com.sun.phobos.script.javascript.RhinoScriptEngineFactory;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.RowMap;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.ModuleResourceCache;
import org.labkey.api.module.ModuleResourceCacheHandler;
import org.labkey.api.module.ModuleResourceCaches;
import org.labkey.api.module.ResourceRootProvider;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.Readers;
import org.labkey.api.resource.Resource;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.test.TestWhen;
import org.labkey.api.util.HeartBeat;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.Path;
import org.labkey.api.util.UnexpectedException;
import org.mozilla.javascript.ClassShutter;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.EcmaError;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.ImporterTopLevel;
import org.mozilla.javascript.JavaScriptException;
import org.mozilla.javascript.LazilyLoadedCtor;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.NativeJavaObject;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.WrapFactory;
import org.mozilla.javascript.commonjs.module.ModuleScript;
import org.mozilla.javascript.commonjs.module.Require;
import org.mozilla.javascript.commonjs.module.provider.ModuleSource;
import org.mozilla.javascript.commonjs.module.provider.ModuleSourceProvider;
import org.mozilla.javascript.commonjs.module.provider.ModuleSourceProviderBase;
import org.mozilla.javascript.commonjs.module.provider.SoftCachingModuleScriptProvider;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;

import javax.script.Bindings;
import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;
import javax.script.SimpleScriptContext;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.lang.ref.WeakReference;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.labkey.api.script.RhinoService.LOG;

public final class RhinoService
{
    public static final Logger LOG = Logger.getLogger(ScriptService.Console.class);
    static final RhinoFactory RHINO_FACTORY = new RhinoFactory();

    public static void register()
    {
        SandboxContextFactory.initGlobal();
        ServiceRegistry.get().registerService(ScriptService.class, RHINO_FACTORY);
    }

    public static void clearCaches()
    {
        // Clear Rhino's internal module script cache and shared topLevel. LabKey caches will be cleared separately.
        LOG.info("Purging RhinoService caches");
        RhinoEngine.clearTopLevel();
    }

    @TestWhen(TestWhen.When.BVT)
    public static class TestCase extends Assert
    {
        private final Module _module = ModuleLoader.getInstance().getModule("simpletest");

        @BeforeClass
        public static void setUp()
        {
            JunitUtil.getTestContainer(); // Just to make sure the folder exists
        }

        @Test
        public void exportsTest() throws Exception
        {
            assertNotNull("RhinoService$TestCase requires module 'simpletest'", _module);

            test("exportTest");
        }

        // TODO: Enable once APIs exist to create tables.
        public void queryTest() throws Exception
        {
            // Prepare table
            // Need APIs

            test("queryTest");
        }

        @Test
        public void simpleQueryTest() throws Exception
        {
            test("simpleQueryTest");
        }

        @Test
        public void filterTest() throws Exception
        {
            test("filterTest");
        }

        @Test
        public void messageTest() throws Exception
        {
            test("messageTest");
        }

        @Test
        public void actionUrlTest() throws Exception
        {
            test("actionUrlTest");
        }

        @Test
        public void securityTest() throws Exception
        {
            test("securityTest");
        }

        @Test
        public void utilsTest() throws Exception
        {
            test("utilsTest");
        }

        @Test
        public void reportTest() throws Exception
        {
            test("reportTest");
        }

        private void test(String scriptName) throws ScriptException, NoSuchMethodException
        {
            Path js = Path.parse(ScriptService.SCRIPTS_DIR + "/validationTest/" + scriptName + ".js");
            ScriptReference script = ScriptService.get().compile(_module, js);
            script.invokeFn("doTest");
        }

        @Test
        public void testModuleResourceCache()
        {
            // Load all the scripts to ensure no exceptions and get a count
            int scriptCount = ScriptReferenceImpl.SCRIPT_CACHE.streamAllResourceMaps()
                .mapToInt(Map::size)
                .sum();

            LOG.info(scriptCount + " scripts in all modules");

            // Load all the top-level script timestamps to ensure no exceptions and get a count
            int timestampCount = LabKeyModuleSourceProvider.TOP_LEVEL_SCRIPT_CACHE.streamAllResourceMaps()
                .mapToInt(Map::size)
                .sum();

            LOG.info(timestampCount + " top-level script timestamps in all modules");

            assertEquals("Mismatch in counts for JavaScript scripts vs. script timestamps", scriptCount, timestampCount);

            // Make sure the cache retrieves the expected number of scripts from the simpletest module, if present

            Module simpleTest = ModuleLoader.getInstance().getModule("simpletest");

            if (null != simpleTest)
            {
                assertEquals("Scripts from the simpletest module", 14, ScriptReferenceImpl.SCRIPT_CACHE.getResourceMap(simpleTest).size());
                assertEquals("Top-level script timestamps from the simpletest module", 14, LabKeyModuleSourceProvider.TOP_LEVEL_SCRIPT_CACHE.getResourceMap(simpleTest).size());
            }
        }
    }
}

class RhinoFactory extends RhinoScriptEngineFactory implements ScriptService
{
    @Override
    public RhinoEngine getScriptEngine()
    {
        return new RhinoEngine(this);
    }

    @Override
    public ScriptReference compile(Module module, Path path) throws ScriptException
    {
        return ScriptReferenceImpl.get(module, path);
    }
}

class ScriptReferenceImpl implements ScriptReference
{
    private static final ModuleResourceCacheHandler<Map<Path, CompiledScript>> CACHE_HANDLER = new ModuleResourceCacheHandler<Map<Path, CompiledScript>>()
    {
        @Override
        public Map<Path, CompiledScript> load(Stream<? extends Resource> resources, Module module)
        {
            return unmodifiable(resources
                .filter(getFilter(".js"))
                .collect(Collectors.toMap(Resource::getPath, this::compile)));
        }

        private CompiledScript compile(Resource r)
        {
            RhinoEngine engine = RhinoService.RHINO_FACTORY.getScriptEngine();
            Context ctx = Context.enter();

            LOG.info("Compiling script '" + r.getPath().toString() + "'");

            try (Reader reader = Readers.getReader(r.getInputStream()))
            {
                engine.put(ScriptEngine.FILENAME, r.getPath().toString());
                return engine.compile(reader);
            }
            catch (IOException | ScriptException e)
            {
                throw new UnexpectedException(e);
            }
            finally
            {
                Context.exit();
            }
        }
    };

    static final ModuleResourceCache<Map<Path, CompiledScript>> SCRIPT_CACHE = ModuleResourceCaches.create(
        "Module JavaScript cache",
        CACHE_HANDLER,
        ResourceRootProvider.getHierarchy(Path.parse(ScriptService.SCRIPTS_DIR)),
        ResourceRootProvider.QUERY_SUBDIRECTORIES);

    private final Module _module;
    private final Path _path;
    private final RhinoEngine _engine;
    private final CompiledScript _script;

    private ScriptContext _context; // context to eval and invoke in, not compile.
    private boolean _evaluated = false;

    static ScriptReferenceImpl get(Module module, Path path) throws ScriptException
    {
        CompiledScript script = SCRIPT_CACHE.getResourceMap(module).get(path);

        return null == script ? null : new ScriptReferenceImpl(module, path, (RhinoEngine)script.getEngine(), script);
    }

    // NB: At first glance, it might seem like we should cache ScriptReferenceImpl instances instead of CompiledScript instances,
    // however, because this class mutates internal state (_context and _evaluated) we need to construct them each time.
    private ScriptReferenceImpl(Module module, Path path, RhinoEngine engine, CompiledScript script) throws ScriptException
    {
        _module = module;
        _path = path;
        _engine = engine;
        _script = script;

        MemTracker.getInstance().put(this);
    }

    public String toString()
    {
        return "[" + _module.getName() + "] " + _path.toString();
    }

    public ScriptContext getContext()
    {
        if (_context == null)
            _context = new SimpleScriptContext();
        return _context;
    }

    public <T> T eval(Class<T> resultType) throws ScriptException
    {
        return eval(resultType, null);
    }

    public <T> T eval(Class<T> resultType, Map<String, ?> map) throws ScriptException
    {
        Context ctx = Context.enter();
        try
        {
            Object result = eval(map);
            if (result == null)
                return null;
            return (T)ScriptUtils.jsToJava(result, resultType);
        }
        finally
        {
            Context.exit();
        }
    }

    public Object eval() throws ScriptException
    {
        return eval((Map<String, ?>)null);
    }

    public Object eval(Map<String, ?> map) throws ScriptException
    {
        Context ctx = Context.enter();
        try
        {
            ScriptContext ctxt = getContext();
            if (map != null)
            {
                Scriptable scope = _engine.getRuntimeScope(ctxt);
                Bindings bindings = ctxt.getBindings(ScriptContext.ENGINE_SCOPE);
                for (Map.Entry<String, ?> entry : map.entrySet())
                    bindings.put(entry.getKey(), Context.javaToJS(entry.getValue(), scope));
                ctxt.setBindings(bindings, ScriptContext.ENGINE_SCOPE);
            }
            ctxt.getBindings(ScriptContext.ENGINE_SCOPE).put(ScriptEngine.FILENAME, _path.toString());

            LOG.debug("Evaluating script '" + _path.toString() + "'");
            Object result = _script.eval(ctxt);
            _evaluated = true;
            return result;
        }
        finally
        {
            Context.exit();
        }
    }

    public boolean evaluated() { return _evaluated; }

    public boolean hasFn(String name) throws ScriptException
    {
        ScriptContext ctxt = getContext();
        Scriptable scope = _engine.getRuntimeScope(ctxt);
        return hasFn(scope, name);
    }

    public boolean hasFn(Object thiz, String name) throws ScriptException
    {
        if (!(thiz instanceof Scriptable))
            throw new ScriptException("Scope must be a Rhino Scriptable object");

        // compile and evaluate if necessary
        if (!_evaluated)
            eval();
        return ScriptableObject.getProperty((Scriptable)thiz, name) instanceof Function;
    }

    public <T> T invokeFn(Class<T> resultType, Object thiz, String name, Object... args) throws ScriptException, NoSuchMethodException
    {
        // compile and evaluate if necessary
        if (!_evaluated)
            eval();

        Context ctx = Context.enter();
        try
        {
            LOG.debug("Invoking method '" + name + "' in script '" + _path.toString() + "'");
            Object result = _engine.invokeMethod(thiz, name, args);
            if (result == null)
                return null;
            return (T)ScriptUtils.jsToJava(result, resultType);
        }
        finally
        {
            Context.exit();
        }
    }

    public Object invokeFn(String name, Object... args) throws ScriptException, NoSuchMethodException
    {
        return invokeFn(Object.class, name, args);
    }

    public <T> T invokeFn(Class<T> resultType, String name, Object... args) throws ScriptException, NoSuchMethodException
    {
        // compile and evaluate if necessary
        if (!_evaluated)
            eval();

        Context ctx = Context.enter();
        try
        {
            LOG.debug("Invoking method '" + name + "' in script '" + _path.toString() + "'");
            ScriptContext ctxt = getContext();
            Scriptable scope = _engine.getRuntimeScope(ctxt);
            Object result = _engine.invokeMethod(scope, name, args);
            if (result == null)
                return null;
            return (T)ScriptUtils.jsToJava(result, resultType);
        }
        finally
        {
            Context.exit();
        }
    }

    public Object invokeFn(Object thiz, String name, Object... args) throws ScriptException, NoSuchMethodException
    {
        return invokeFn(Object.class, thiz, name, args);
    }
}

class LabKeyModuleSourceProvider extends ModuleSourceProviderBase
{
    /**
     * Caches the last modified date of all top-level Rhino scripts (e.g., scripts included in another script using require()).
     * Rhino manages the compilation and caching of these scripts itself, but asks our code if they're stale (e.g., changed or
     * deleted during the development process). This cache provides an efficient (typically no I/O) way to check both existence
     * and last modified timestamp for these resources.
     *
     * This cache is a bit redundant with the module resource cache (see ModuleResourceResolver), which caches and invalidates
     * the resources themselves, however, it can't currently be used for this staleness check because it doesn't invalidate
     * on modify plus the exists() and lastModified() methods of FileResource access the file system directly.
     */
    static final ModuleResourceCache<Map<Path, Long>> TOP_LEVEL_SCRIPT_CACHE = ModuleResourceCaches.create("Top-level Rhino script cache", new ModuleResourceCacheHandler<Map<Path, Long>>()
    {
        @Override
        public Map<Path, Long> load(Stream<? extends Resource> resources, Module module)
        {
            return unmodifiable(resources
                .filter(getFilter(".js"))
                .collect(Collectors.toMap(Resource::getPath, Resource::getLastModified)));
        }
    }, ResourceRootProvider.getHierarchy(Path.parse(ScriptService.SCRIPTS_DIR)), ResourceRootProvider.QUERY_SUBDIRECTORIES);

    @Override
    protected boolean entityNeedsRevalidation(Object validator)
    {
        boolean isStale = true;

        if (validator instanceof RhinoScriptRef)
        {
            RhinoScriptRef ref = (RhinoScriptRef)validator;
            Long currentLastModified = TOP_LEVEL_SCRIPT_CACHE.getResourceMap(ref.getModule()).get(ref.getPath());

            isStale = (null == currentLastModified || currentLastModified.longValue() != ref.getLastModified());
        }

        return isStale;
    }

    @Override
    protected ModuleSource loadFromUri(URI uri, URI base, Object validator) throws IOException, URISyntaxException
    {
        return load(uri.getPath(), validator);
    }

    @Override
    protected ModuleSource loadFromPrivilegedLocations(String moduleId, Object validator) throws IOException
    {
        return load(moduleId + ".js", validator);
    }

    protected ModuleSource load(String moduleScript, Object validator)
    {
        // load non-relative modules from the root "/scripts" directory
        if ((!moduleScript.startsWith("./") || !moduleScript.startsWith("../")) && !moduleScript.startsWith(ScriptService.SCRIPTS_DIR + "/"))
            moduleScript = "/" + ScriptService.SCRIPTS_DIR + "/" + moduleScript;

        Path path = Path.parse(moduleScript);
        Resource res = ModuleLoader.getInstance().getResource(path);

        if (res == null || !res.isFile())
        {
            return null;
        }

        LOG.debug("Loading require()'ed resource '" + path.toString() + "'");

        RhinoScriptRef ref = new RhinoScriptRef(res);
        try
        {
            return new LabKeyModuleSource(ref);
        }
        catch (URISyntaxException e)
        {
            throw new UnexpectedException(e);
        }
    }

    /**
     * Bridge between Rhino commonjs ModuleSource/validator and LabKey Resource/ResourceRef.
     */
    private static class LabKeyModuleSource extends ModuleSource
    {
        private final RhinoScriptRef _ref;

        /**
         * Creates a new module source.
         */
        public LabKeyModuleSource(RhinoScriptRef ref) throws URISyntaxException
        {
            super(null, null, new URI(ref.getPath().toString()), new URI("module://scripts/"), ref);
            _ref = ref;
        }

        @Override
        public Reader getReader()
        {
            try
            {
                return Readers.getReader(_ref.getResource().getInputStream());
            }
            catch (IOException e)
            {
                LOG.info(e.getMessage());
                // XXX: log to mothership
                return null;
            }
        }

        @Override
        public Object getValidator()
        {
            return super.getValidator();
        }
    }
}

class RhinoEngine extends RhinoScriptEngine
{
    private static final Object sharedTopLevelLock = new Object();
    private static WeakReference<ScriptableObject> sharedTopLevel = null;
    private static SoftCachingModuleScriptProvider _moduleScriptProvider = null;

    protected RhinoEngine()
    {
        super();
        MemTracker.getInstance().put(this);
    }

    public RhinoEngine(ScriptEngineFactory factory)
    {
        this();
        setEngineFactory(factory);
    }

    static void clearTopLevel()
    {
        synchronized (sharedTopLevelLock) {
            if (sharedTopLevel != null)
                sharedTopLevel.clear();
            _moduleScriptProvider = null;
        }
    }

    // Similar to the topLevel scope created in RhinoScriptEngine
    // except it is sealed to prevent modifications to built-in objects
    // or adding any additional objects to the scope.  In addition, the
    // topLevel is cached in a WeakReference so can be shared with other
    // instances of RhinoService and across threads.  The topLevel won't
    // be gc'd until all of the ScriptResourceRef in the SCRIPT_CACHE are gone.
    protected ScriptableObject createTopLevel()
    {
        synchronized (sharedTopLevelLock)
        {
            ScriptableObject topLevel = null;
            if (sharedTopLevel != null)
                topLevel = sharedTopLevel.get();

            if (topLevel == null)
            {
                LOG.info("RhinoEngine.createTopLevel: initialize cache");
                // Create the shared module script cache.
                // This cache is managed by Rhino's module provider implementation.
                ModuleSourceProvider moduleSourceProvider = new LabKeyModuleSourceProvider();
                _moduleScriptProvider = new SoftCachingModuleScriptProvider(moduleSourceProvider);
                
                Context cx = Context.enter();
                cx.setLanguageVersion(Context.VERSION_1_8);

                try
                {
                    /*
                     * RRC - modified this code to register JSAdapter and some functions
                     * directly, without using a separate RhinoTopLevel class
                     */
                    topLevel = new ImporterTopLevel(cx, false /*true*/);
                    //topLevel = new TopLevel(cx, this, true);
                    MemTracker.getInstance().put(topLevel);
                    new LazilyLoadedCtor(topLevel, "JSAdapter",
                        "com.sun.phobos.script.javascript.JSAdapter",
                        false);
                    /*
                    // add top level functions
                    String names[] = { "bindings", "scope", "sync"  };
                    topLevel.defineFunctionProperties(names, RhinoScriptEngine.class, ScriptableObject.DONTENUM);
                    */

                    initHostObjects(topLevel);
                    processAllTopLevelScripts(cx, topLevel);

                    //sealStandardObjects(cx, topLevel);
                    topLevel.sealObject();
                }
                finally
                {
                    Context.exit();
                }
                
                sharedTopLevel = new WeakReference<>(topLevel);
            }

            return topLevel;
        }
    }

    protected void initHostObjects(Scriptable scope)
    {
        try
        {
            ScriptableList.init(scope);
            ScriptableMap.init(scope);
            ScriptableErrors.init(scope);
            ScriptableErrorsList.init(scope);
        }
        catch (Exception e)
        {
            UnexpectedException.rethrow(e);
        }
    }

    protected void processAllTopLevelScripts(Context cx, Scriptable scope)
    {
        try
        {
            ModuleScript global = _moduleScriptProvider.getModuleScript(cx, "global", null, null);
            global.getScript().exec(cx, scope);
        }
        catch (Exception e)
        {
            UnexpectedException.rethrow(e);
        }
    }

    /*
    protected void sealStandardObjects(Context cx, Scriptable scope)
    {
        String[] classes = new String[] {
                "Function", "Object", "Array", "Date", "Error"
        }
        ScriptableObject so = (ScriptableObject)ScriptableObject.getFunctionPrototype(scope);
        so.sealObject();

        so = (ScriptableObject)ScriptableObject.getObjectPrototype(scope);
        so.sealObject();

        so = (ScriptableObject)ScriptableObject.getClassPrototype(scope, "Error");
        so.sealObject();

        so = (ScriptableObject)ScriptableObject.getArrayPrototype(scope);
        so.sealObject();

        so = (ScriptableObject)ScriptableObject.getProperty(scope, "With");
        so.sealObject();
    }
    */

    @Override
    protected Scriptable getRuntimeScope(ScriptContext ctxt)
    {
        // https://developer.mozilla.org/en/Rhino/Scopes_and_Contexts#sharingscopes
        // Create a new scope with shared topLevel as prototype and with no parent scope.
        // The ExternalScriptable puts values into its ScriptContext map so this might
        // not be necessary since the values shouldn't end up in the parent scope.
        Scriptable scriptable = super.getRuntimeScope(ctxt);
        scriptable.setParentScope(null);

        // Install the "require()" function to enable CommonJS module loading
        // from the shared SoftCachingModuleScriptProvider.
        // NOTE: we can't install this in the topLevel since the Require instance
        // holds on to all modules that have been require()'ed.
        Context cx = enterContext();
        try
        {
            Require require = new Require(cx, getTopLevel(), _moduleScriptProvider, null, null, true);
            require.install(scriptable);
        }
        finally
        {
            Context.exit();
        }

        return scriptable;
    }

    @Override
    public Object eval(Reader reader, ScriptContext ctxt) throws ScriptException
    {
        if (SandboxContextFactory.SANDBOX != ContextFactory.getGlobal())
            throw new IllegalStateException();
        return super.eval(reader, ctxt);
    }

    @Override
    public Object eval(String script, ScriptContext ctxt) throws ScriptException
    {
        if (SandboxContextFactory.SANDBOX != ContextFactory.getGlobal())
            throw new IllegalStateException();
        return super.eval(script, ctxt);
    }

    @Override
    public Object invokeMethod(Object thiz, String name, Object... args) throws ScriptException, NoSuchMethodException
    {
        if (SandboxContextFactory.SANDBOX != ContextFactory.getGlobal())
            throw new IllegalStateException();
        return super.invokeMethod(thiz, name, args);
    }

    @Override
    public Object invokeFunction(String name, Object... args) throws ScriptException, NoSuchMethodException
    {
        if (SandboxContextFactory.SANDBOX != ContextFactory.getGlobal())
            throw new IllegalStateException();
        return super.invokeFunction(name, args);
    }

    @Override
    public <T> T getInterface(Class<T> clasz)
    {
        if (SandboxContextFactory.SANDBOX != ContextFactory.getGlobal())
            throw new IllegalStateException();
        return super.getInterface(clasz);
    }

    @Override
    public <T> T getInterface(Object thiz, Class<T> clasz)
    {
        if (SandboxContextFactory.SANDBOX != ContextFactory.getGlobal())
            throw new IllegalStateException();
        return super.getInterface(thiz, clasz);
    }

    @Override
    public CompiledScript compile(String script) throws ScriptException
    {
        if (SandboxContextFactory.SANDBOX != ContextFactory.getGlobal())
            throw new IllegalStateException();
        return super.compile(script);
    }

    @Override
    public CompiledScript compile(Reader script) throws ScriptException
    {
        if (SandboxContextFactory.SANDBOX != ContextFactory.getGlobal())
            throw new IllegalStateException();
        return super.compile(script);
    }
}

/**
 * Defines the Rhino sandbox.
 * http://codeutopia.net/blog/2009/01/02/sandboxing-rhino-in-java/
 */
class SandboxContextFactory extends ContextFactory
{
    private static final Logger log = Logger.getLogger(SandboxContextFactory.class);

    static final ContextFactory SANDBOX = new SandboxContextFactory();

    private static final Set<String> DISALLOWED_METHODS;
    private static final Set<String> ALLOWED_CLASSES;

    static
    {
        HashSet<String> disallowedMethods = new HashSet<>();
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
        
        HashSet<String> allowedClasses = new HashSet<>();
        allowedClasses.add(ArrayList.class.getName());
        allowedClasses.add(Arrays.class.getName());
        allowedClasses.add(BigDecimal.class.getName());
        allowedClasses.add(BigInteger.class.getName());
        allowedClasses.add(BindException.class.getName());
        allowedClasses.add(Boolean.class.getName());
        allowedClasses.add(Byte.class.getName());
        allowedClasses.add(Calendar.class.getName());
        allowedClasses.add(CaseInsensitiveHashMap.class.getName());
        allowedClasses.add(CaseInsensitiveHashSet.class.getName());
        allowedClasses.add(Character.class.getName());
        allowedClasses.add(Collections.class.getName());
        allowedClasses.add(Collections.class.getName() + "$*"); // allow inner-classes
        allowedClasses.add(Date.class.getName());
        allowedClasses.add(java.sql.Date.class.getName());
        allowedClasses.add(Double.class.getName());
        allowedClasses.add(EcmaError.class.getName());
        allowedClasses.add(Errors.class.getName());
        allowedClasses.add(Float.class.getName());
        allowedClasses.add(GregorianCalendar.class.getName());
        allowedClasses.add(HashMap.class.getName());
        allowedClasses.add(HashSet.class.getName());
        allowedClasses.add(Integer.class.getName());
        allowedClasses.add(JavaScriptException.class.getName());
        allowedClasses.add(org.json.JSONObject.class.getName());
        allowedClasses.add(LinkedHashMap.class.getName());
        allowedClasses.add(LinkedHashSet.class.getName());
        allowedClasses.add(LinkedList.class.getName());
        allowedClasses.add(List.class.getName());
        allowedClasses.add(Long.class.getName());
        allowedClasses.add(Math.class.getName());
        allowedClasses.add(Number.class.getName());
        allowedClasses.add(PrintStream.class.getName());
        allowedClasses.add(PrintWriter.class.getName());
        allowedClasses.add(RhinoException.class.getName());
        allowedClasses.add(RowMap.class.getName());
        allowedClasses.add(Short.class.getName());
        allowedClasses.add(SimpleScriptContext.class.getName());
        allowedClasses.add(String.class.getName());
        allowedClasses.add(java.sql.Time.class.getName());
        allowedClasses.add(java.sql.Timestamp.class.getName());
        allowedClasses.add(TreeMap.class.getName());
        allowedClasses.add(TreeSet.class.getName());
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
        Context context = new SandboxContext(this);
        context.setClassShutter(new SandboxShutter());
        context.setWrapFactory(new SandboxWrapFactory());
        context.setInstructionObserverThreshold(30000);
        // Checking stack depth requires opt level -1 (interpreted) so we do our own check in observeInstructionCount
        //context.setMaximumInterpreterStackDepth(1000);
        return context;
    }

    @Override
    protected void observeInstructionCount(Context cx, int instructionCount)
    {
        SandboxContext ctx = (SandboxContext)cx;
        long currentTime = HeartBeat.currentTimeMillis();
        final int timeout = 60;
        if (currentTime - ctx.startTime > timeout*1000)
            Context.reportError("Script execution exceeded " + timeout + " seconds.");
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
            return true;
            /*
            if (ALLOWED_CLASSES.contains(fullClassName))
                return true;

            // allow inner-classes of particular class: e.g., java.util.Collections$*
            int i = fullClassName.indexOf("$");
            if (i > 0)
            {
                if (ALLOWED_CLASSES.contains(fullClassName.substring(0, i+1) + "*"))
                    return true;
            }
            
            log.warn("Rhino sandbox disallowed class: " + fullClassName);
            return false;
            */
        }
    }

    private static class SandboxContext extends Context
    {
        private final long startTime;

        private SandboxContext(SandboxContextFactory factory)
        {
            super(factory);
            setLanguageVersion(Context.VERSION_1_8);
            startTime = HeartBeat.currentTimeMillis();
        }
    }

    private static class SandboxWrapFactory extends WrapFactory
    {
        public SandboxWrapFactory()
        {
            super();
            // ???
            setJavaPrimitiveWrap(false);
        }

        @Override
        public Object wrap(Context cx, Scriptable scope, Object obj, Class<?> staticType)
        {
            // Unwrap JSONArrays to standard arrays first
            if (obj instanceof JSONArray)
                obj = ((JSONArray)obj).toArray();

            if (obj instanceof Map)
                return new ScriptableMap(scope, (Map)obj);
            else if (obj instanceof List)
                return new ScriptableList(scope, (List)obj);
            else if (obj instanceof ValidationException)
                return cx.newObject(scope, ScriptableErrors.CLASSNAME, new Object[] { obj });
            else if (obj instanceof BatchValidationException)
                return cx.newObject(scope, ScriptableErrorsList.CLASSNAME, new Object[] { obj });
            else if (obj instanceof char[])
                return new String((char[])obj);
            else if (obj instanceof Object[])
            {
                Object[] arr = (Object[])obj;
                int len = arr.length;
                Object[] wrapped = new Object[len];
                Class<?> componentType = arr.getClass().getComponentType();
                for (int i = 0; i < len; i++)
                    wrapped[i] = wrap(cx, scope, arr[i], componentType);
                NativeArray jsArray = new NativeArray(wrapped);
                jsArray.setPrototype(ScriptableObject.getClassPrototype(scope, "Array"));
                jsArray.setParentScope(scope);
                return jsArray;
            }

            return super.wrap(cx, scope, obj, staticType);
        }

        @Override
        public Scriptable wrapAsJavaObject(Context cx, Scriptable scope, Object javaObject, Class staticType)
        {
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
