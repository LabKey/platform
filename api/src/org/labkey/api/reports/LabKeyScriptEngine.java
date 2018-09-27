package org.labkey.api.reports;

import javax.script.ScriptEngine;

public interface LabKeyScriptEngine extends ScriptEngine
{
    boolean isSandboxed();
}
