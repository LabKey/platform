package org.labkey.api.view.template;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.clientLibrary.xml.ModeTypeEnum;

import java.util.Set;

public class ContextClientDependency extends ClientDependency
{
    private final Module _module;

    protected ContextClientDependency(@NotNull Module m, ModeTypeEnum.Enum mode)
    {
        super(TYPE.context, mode);
        _module = m;
    }

    @Override
    protected void init()
    {
    }

    @Override
    protected Set<ClientDependency> getUniqueDependencySet(Container c)
    {
        return _module.getClientDependencies(c);
    }

    @Override
    public Set<Module> getRequiredModuleContexts(Container c)
    {
        Set<Module> ret = super.getRequiredModuleContexts(c);
        ret.add(_module);

        return ret;
    }

    @Override
    protected String getUniqueKey()
    {
        return getCacheKey("moduleContext|" + _module.toString(), _mode);
    }

    @Override
    public String getScriptString()
    {
        return _module.getName() + "." + _primaryType.name();
    }
}
