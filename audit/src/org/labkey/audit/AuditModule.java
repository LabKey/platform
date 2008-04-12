package org.labkey.audit;

import org.apache.log4j.Logger;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.security.User;
import org.labkey.api.util.ContainerUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.audit.model.LogManager;
import org.labkey.audit.query.AuditQuerySchema;

import java.beans.PropertyChangeEvent;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class AuditModule extends DefaultModule implements ContainerManager.ContainerListener
{
    public static final String NAME = "Audit";
    private static final Logger _log = Logger.getLogger(AuditModule.class);

    public AuditModule()
    {
        super(NAME, 8.10, null, true);
        AuditLogService.registerProvider(new AuditLogImpl());
    }

    public void containerCreated(Container c)
    {
    }

    public void containerDeleted(Container c, User user)
    {
/*
        try {
            ContainerUtil.purgeTable(LogManager.get().getTinfoAuditLog(), c, "ContainerId");
        }
        catch (SQLException x)
        {
            _log.error("Error occured deleting reports for container", x);
        }
*/
    }

    public TabDisplayMode getTabDisplayMode()
    {
        return TabDisplayMode.DISPLAY_NEVER;
    }

    public Collection<String> getSummary(Container c)
    {
        return Collections.emptyList();
    }

    public void propertyChange(PropertyChangeEvent evt)
    {
    }

    public void startup(ModuleContext moduleContext)
    {
        super.startup(moduleContext);
        // add a container listener so we'll know when our container is deleted:
        ContainerManager.addContainerListener(this);

        //AuditLogService.registerProvider(new AuditLogImpl());
        AuditQuerySchema.register();
    }

    public Set<String> getSchemaNames()
    {
        return Collections.singleton("audit");
    }

    public Set<DbSchema> getSchemasToTest()
    {
        return PageFlowUtil.set(AuditSchema.getInstance().getSchema());
    }

    public Set<String> getModuleDependencies()
    {
        return Collections.singleton("Experiment"); 
    }
}