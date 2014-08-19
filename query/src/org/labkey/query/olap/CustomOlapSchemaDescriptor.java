package org.labkey.query.olap;

import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;

import java.io.IOException;
import java.io.InputStream;

/**
 * User: kevink
 * Date: 8/16/14
 *
 * High-level representation of a custom olap definition while
 * the OlapDef represents a row in the query.OlapDef table.
 *
 * CONSIDER: Currently, the custom olap def must be associated with a module for {@link org.labkey.api.module.Module#getOlapSchemaInfo()} info.
 */
public class CustomOlapSchemaDescriptor extends OlapSchemaDescriptor
{
    final Container _container;
    final String _definition;

    public CustomOlapSchemaDescriptor(@NotNull String id, @NotNull Module module, @NotNull Container c, @NotNull String definition)
    {
        super(id, module);
        _container = c;
        _definition = definition;
    }

    public CustomOlapSchemaDescriptor(@NotNull OlapDef olapDef)
    {
        super(olapDef.getConfigId(), olapDef.lookupModule());
        _container = olapDef.lookupContainer();
        _definition = olapDef.getDefinition();
    }

    @Override
    public Container getContainer()
    {
        return _container;
    }

    @Override
    public boolean isEditable()
    {
        return true;
    }

    @Override
    public String getDefinition()
    {
        return _definition;
    }

    @Override
    protected InputStream getInputStream() throws IOException
    {
        return IOUtils.toInputStream(_definition);
    }
}
