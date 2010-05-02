package org.labkey.api.script;

import javax.script.ScriptContext;
import javax.script.ScriptException;
import java.util.Map;

/**
 * A compiled JavaScript and execution context.
 */
public interface ScriptReference
{
    /**
     * The context in which the script will run when {@link #eval()} or {@link #invoke()} is called.
     */
    public ScriptContext getContext();

    /**
     * The eval family of methods will run the compiled script in the execution context.  Passing in
     * the map bindings is the same as setting the bindings in the ScriptContext.ENGINE_SCOPE.
     * @return
     * @throws ScriptException
     */
    public Object eval() throws ScriptException;
    public Object eval(Map<String, ?> bindings) throws ScriptException;
    public <T> T eval(Class<T> resultType) throws ScriptException;
    public <T> T eval(Class<T> resultType, Map<String, ?> bindings) throws ScriptException;

    /**
     * Checks if a JavaScript function is in scope after {@link eval()}uating the compiled script.
     * @param name
     * @return
     * @throws ScriptException
     */
    public boolean hasFn(String name) throws ScriptException;

    /**
     * Calls a JavaScript function after {@link eval()}uating the compiled script.  These are loose
     * functions defined in the top-level of the script and not methods of objects.
     * @param name
     * @param args
     * @return
     * @throws ScriptException
     * @throws NoSuchMethodException
     */
    public Object invokeFn(String name, Object... args) throws ScriptException, NoSuchMethodException;
    public <T> T invokeFn(Class<T> resultType, String name, Object... args) throws ScriptException, NoSuchMethodException;
}
