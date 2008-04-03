package org.labkey.cabig;

import org.apache.log4j.Logger;
import org.labkey.api.data.DbSchema;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.util.PageFlowUtil;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class caBIGModule extends DefaultModule
{
    private static final Logger _log = Logger.getLogger(caBIGModule.class);
    public static final String NAME = "caBIG";

    public caBIGModule()
    {
        super(NAME, 2.31, null, true);
        addController("cabig", caBIGController.class);
    }

    public void startup(ModuleContext moduleContext)
    {
        super.startup(moduleContext);
        SecurityManager.addViewFactory(new caBIGController.caBIGPermissionsViewFactory());
    }

    public Set<String> getSchemaNames()
    {
        return Collections.singleton("cabig");
    }

    public Set<DbSchema> getSchemasToTest()
    {
        return PageFlowUtil.set(caBIGSchema.getInstance().getSchema());
    }

    public TabDisplayMode getTabDisplayMode()
    {
        return TabDisplayMode.DISPLAY_NEVER;
    }

    @Override
    public Set<String> getModuleDependencies()
    {
        Set<String> result = new HashSet<String>();
        result.add("Experiment");
        result.add("MS2");
        return result;
    }
}