package org.labkey.query.olap;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Entity;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.query.persist.QueryManager;

/**
 * User: kevink
 * Date: 8/18/14
 *
 * Represents a row in the {@link QueryManager#getTableInfoQueryDef()} table.
 * @see org.labkey.query.olap.CustomOlapSchemaDescriptor
 */
public class OlapDef extends Entity
{
    private String _name;
    private String _module;
    private String _description;
    private String _definition;

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public String getModule()
    {
        return _module;
    }

    public void setModule(String module)
    {
        _module = module;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public String getDefinition()
    {
        return _definition;
    }

    public void setDefinition(String definition)
    {
        _definition = definition;
    }

    @Nullable
    public final Module lookupModule()
    {
        return ModuleLoader.getInstance().getModule(_module);
    }

    public String getConfigId()
    {
        return OlapSchemaCacheHandler.createOlapCacheKey(lookupModule(), getName());
    }
}
