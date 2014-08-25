package org.labkey.query.olap;

import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.view.ActionURL;
import org.labkey.query.controllers.OlapController;

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
    final OlapDef _olapDef;

    public CustomOlapSchemaDescriptor(@NotNull OlapDef olapDef)
    {
        super(olapDef.getConfigId(), olapDef.lookupModule());
        _olapDef = olapDef;
    }

    @Override
    public Container getContainer()
    {
        return _olapDef.lookupContainer();
    }

    @Override
    public boolean isEditable()
    {
        return true;
    }

    @Override
    public String getDefinition()
    {
        return _olapDef.getDefinition();
    }

    @Override
    protected InputStream getInputStream() throws IOException
    {
        return IOUtils.toInputStream(getDefinition());
    }

    public ActionURL urlEdit()
    {
        return new ActionURL(OlapController.EditDefinitionAction.class, getContainer()).addParameter("rowId", _olapDef.getRowId());
    }

    public ActionURL urlDelete()
    {
        return new ActionURL(OlapController.DeleteDefinitionAction.class, getContainer()).addParameter("rowId", _olapDef.getRowId());
    }
}
