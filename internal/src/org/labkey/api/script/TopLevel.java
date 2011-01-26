package org.labkey.api.script;

import org.mozilla.javascript.*;
import org.mozilla.javascript.tools.shell.Global;

/**
 * User: kevink
 */
public class TopLevel extends ImporterTopLevel
{
    protected TopLevel() { }

    public TopLevel(Context cx, RhinoEngine engine, boolean sealed)
    {
        super(cx, sealed);
        init(cx, engine);
    }

    public void init(Context cx, RhinoEngine engine)
    {
        String[] names = {
            "require",
        };
        defineFunctionProperties(names, TopLevel.class, ScriptableObject.DONTENUM);
    }

    /*
    public static Object require(Context cx, Scriptable thisObj,
                               Object[] args, Function funObj)
    {
        if (args == null || args.length < 1)
            throw Context.reportRuntimeError("require() requires a module id argument");

        String id = (String)Context.jsToJava(args[0], String.class);
        id = resolveId(cx, thisObj, id);

        RhinoEngine engine;
        engine.loadModule(cx, (String)args[0], moduleScope);
        return module.getExports();
    }

    public static String resolveId(Context cx, Scriptable thisObj, String id)
    {
        if (id.startsWith("./") || id.startsWith("../"))
        {
            String moduleId = getModuleId(thisObj);
            if (moduleId == null)
                throw Context.reportRuntimeError("Can't resolve relative module id from current scope");

            
        }
    }
    */
}
