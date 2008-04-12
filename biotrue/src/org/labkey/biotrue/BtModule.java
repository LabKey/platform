package org.labkey.biotrue;

import org.labkey.api.data.DbSchema;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.biotrue.controllers.BtController;
import org.labkey.biotrue.controllers.BtOverviewWebPart;
import org.labkey.biotrue.datamodel.BtManager;
import org.labkey.biotrue.query.BtSchema;
import org.labkey.biotrue.task.BtThreadPool;
import org.labkey.biotrue.task.ScheduledTask;

import java.util.Collections;
import java.util.Set;

public class BtModule extends DefaultModule
{
    static public final String NAME = "BioTrue";
    public BtModule()
    {
        super(NAME, 8.10, null, true, BtOverviewWebPart.FACTORY);
        addController("biotrue", BtController.class);
        DefaultSchema.registerProvider("biotrue", BtSchema.PROVIDER);
    }
    
    public Set<String> getSchemaNames()
    {
        return Collections.singleton("biotrue");
    }

    public Set<DbSchema> getSchemasToTest()
    {
        return PageFlowUtil.set(BtManager.get().getSchema());
    }

    public void startup(ModuleContext moduleContext)
    {
        BtThreadPool.get();
        ScheduledTask.startTimer();
    }
}
