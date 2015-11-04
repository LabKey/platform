
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

package org.labkey.api.script;

import com.sun.phobos.script.util.ExtendedScriptException;
import org.apache.log4j.Logger;
import org.labkey.api.util.ExceptionUtil;
import org.mozilla.javascript.*;

import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

/**
 * Represents compiled JavaScript code.
 *
 * @author Mike Grogan
 * @version 1.0
 * @since 1.6
 */
// kevink: Essentially the same as the original, with changes marked with kevink
final class RhinoCompiledScript extends CompiledScript
{
    private final Logger _log = Logger.getLogger(RhinoCompiledScript.class);

    private RhinoScriptEngine engine;
    private Script script;
    private final static boolean DEBUG = RhinoScriptEngine.DEBUG;

    RhinoCompiledScript(RhinoScriptEngine engine, Script script) {
        this.engine = engine;
        this.script = script;
    }

    public Object eval(ScriptContext context) throws ScriptException
    {

        Object result = null;
        Context cx = RhinoScriptEngine.enterContext();
        try {

            Scriptable scope = engine.getRuntimeScope(context);
            Object ret = script.exec(cx, scope);
            result = engine.unwrapReturnValue(ret);
        } catch (JavaScriptException jse) {
            _log.debug(jse);
            int line = (line = jse.lineNumber()) == 0 ? -1 : line;
            Object value = jse.getValue();
            String str = (value != null && value.getClass().getName().equals("org.mozilla.javascript.NativeError") ?
                          value.toString() :
                          jse.toString());
            // kevink: supress mothership logging.
            ScriptException ex = new ExtendedScriptException(jse, str, jse.sourceName(), line);
            ExceptionUtil.decorateException(ex, ExceptionUtil.ExceptionInfo.SkipMothershipLogging, "true", true);
            throw ex;
        } catch (RhinoException re) {
            _log.debug(re);
            int line = (line = re.lineNumber()) == 0 ? -1 : line;
            // kevink: supress mothership logging.
            ScriptException ex = new ExtendedScriptException(re, re.toString(), re.sourceName(), line);
            ExceptionUtil.decorateException(ex, ExceptionUtil.ExceptionInfo.SkipMothershipLogging, "true", true);
            throw ex;
        } finally {
            Context.exit();
        }

        return result;
    }

    public ScriptEngine getEngine() {
        return engine;
    }

}
