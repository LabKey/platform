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
    ScriptContext getContext();

    /**
     * The eval family of methods will run the compiled script in the execution context.  Passing in
     * the map bindings is the same as setting the bindings in the ScriptContext.ENGINE_SCOPE.
     */
    Object eval() throws ScriptException;
    Object eval(Map<String, ?> bindings) throws ScriptException;
    <T> T eval(Class<T> resultType) throws ScriptException;
    <T> T eval(Class<T> resultType, Map<String, ?> bindings) throws ScriptException;

    /** True if the script has been evaluated sucessfully. */
    boolean evaluated();

    /**
     * Checks if a JavaScript function is in scope after {@link eval()}uating the compiled script.
     * The scope is the top-level script context.
     */
    boolean hasFn(String name) throws ScriptException;

    /**
     * Checks if a JavaScript function is in scope after {@link eval()}uating the compiled script.
     * @param thiz The scope to interrogate (must be a Rhino Scriptable object.)
     */
    boolean hasFn(Object thiz, String name) throws ScriptException;

    /**
     * Calls a JavaScript function after {@link eval()}uating the compiled script.  These are loose
     * functions defined in the top-level of the script and not methods of objects.
     */
    Object invokeFn(String name, Object... args) throws ScriptException, NoSuchMethodException;
    <T> T invokeFn(Class<T> resultType, String name, Object... args) throws ScriptException, NoSuchMethodException;

    /**
     * Calls a JavaScript function after {@link eval()}uating the compiled script.  These are loose
     * functions defined in the top-level of the script and not methods of objects.
     * @param thiz The scope to invoke the function in.
     */
    Object invokeFn(Object thiz, String name, Object... args) throws ScriptException, NoSuchMethodException;
    <T> T invokeFn(Class<T> resultType, Object thiz, String name, Object... args) throws ScriptException, NoSuchMethodException;
}
