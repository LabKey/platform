package org.labkey.api.reports;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import java.util.List;

public interface LabkeyScriptEngineManager
{
    ScriptEngine getEngineByName(String shortName);
    List<ScriptEngineFactory> getEngineFactories();
    ScriptEngine getEngineByExtension(String extension);
    @Deprecated
    ScriptEngine getEngineByExtension(String extension, boolean requestRemote, boolean requestDocker);

    void deleteDefinition(ExternalScriptEngineDefinition def);
    ExternalScriptEngineDefinition saveDefinition(ExternalScriptEngineDefinition def);
    boolean isFactoryEnabled(ScriptEngineFactory factory);
    List<ExternalScriptEngineDefinition> getEngineDefinitions();
}
