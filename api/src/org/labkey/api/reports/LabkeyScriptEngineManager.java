package org.labkey.api.reports;

import org.labkey.api.security.User;

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

    void deleteDefinition(User user, ExternalScriptEngineDefinition def);
    ExternalScriptEngineDefinition saveDefinition(User user, ExternalScriptEngineDefinition def);
    boolean isFactoryEnabled(ScriptEngineFactory factory);
    List<ExternalScriptEngineDefinition> getEngineDefinitions();
    ExternalScriptEngineDefinition getEngineDefinition(String name, ExternalScriptEngineDefinition.Type type);

    ExternalScriptEngineDefinition createEngineDefinition();
}
