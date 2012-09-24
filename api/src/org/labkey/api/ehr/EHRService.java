package org.labkey.api.ehr;

import org.labkey.api.module.Module;
import org.labkey.api.resource.Resource;

import java.util.List;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 9/14/12
 * Time: 4:44 PM
 */
abstract public class EHRService
{
    static EHRService instance;

    public static EHRService get()
    {
        return instance;
    }

    static public void setInstance(EHRService instance)
    {
        EHRService.instance = instance;
    }

    abstract public void registerModule(Module module);

    abstract public Set<Module> getRegisteredModules();

    abstract public void registerTriggerScript(Resource script);

    abstract public List<Resource> getExtraTriggerScripts();
}
