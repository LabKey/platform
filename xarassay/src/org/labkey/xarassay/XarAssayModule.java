package org.labkey.xarassay;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.Module;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.security.User;
import org.labkey.api.study.assay.AssayService;

import java.beans.PropertyChangeEvent;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * User: phussey
 * Date: Sep 17, 2007
 * Time: 12:30:55 AM
 */
public class XarAssayModule extends DefaultModule implements ContainerManager.ContainerListener
{

    public XarAssayModule()
    {
        super("XarAssay", 0.01, null, false);
        addController("XarAssay", XarAssayController.class);
    }

    public void containerCreated(Container c)
    {
    }

    public void containerDeleted(Container c, User user)
    {
    }

    public Collection<String> getSummary(Container c)
    {
        return Collections.emptyList();
    }

    public TabDisplayMode getTabDisplayMode()
    {
        return TabDisplayMode.DISPLAY_NEVER;
    }

    public void propertyChange(PropertyChangeEvent evt)
    {
    }

    public void startup(ModuleContext moduleContext)
    {
        super.startup(moduleContext);
        // add a container listener so we'll know when our container is deleted:
        ContainerManager.addContainerListener(this);

        AssayService.get().registerAssayProvider(new XarAssayProvider());
        AssayService.get().registerAssayProvider(new CptacAssayProvider());
        PipelineService.get().registerPipelineProvider(new XarAssayPipelineProvider());

    }

    public Set<String> getSchemaNames()
    {
        return Collections.emptySet();
    }

}