package org.labkey.filecontent;

import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.*;

import java.beans.PropertyChangeEvent;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;


public class FileContentModule extends DefaultModule
{
    private static final Logger _log = Logger.getLogger(FileContentModule.class);
    public static final String NAME = "FileContent";

    public FileContentModule()
    {
        super(NAME, 2.30, null, false, new FilesWebPart.Factory(HttpView.BODY), new FilesWebPart.Factory("right"));
        addController("filecontent", FileContentController.class);
    }

    @Override
    public Collection<String> getSummary(Container c)
    {
        return Collections.emptyList();
    }

    @Override
    public void startup(ModuleContext moduleContext)
    {
        super.startup(moduleContext);
    }

    @Override
    public Set<DbSchema> getSchemasToTest()
    {
        return PageFlowUtil.set();
    }
}