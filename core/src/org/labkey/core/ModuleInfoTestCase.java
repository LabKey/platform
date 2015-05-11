package org.labkey.core;

import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;

import java.util.List;

/**
 * User: kevink
 * Date: 5/11/15
 */
public class ModuleInfoTestCase
{
    @Test
    public void checkLabKeyRequired()
    {
        StringBuilder sb = new StringBuilder();

        for (Module m : ModuleLoader.getInstance().getModules())
        {
            if (!"LabKey Software".equals(m.getOrganization()))
                continue;

            List<String> report = ModuleLoader.getInstance().checkLabKeyModuleInfo(m);
            if (report != null)
                sb.append("\n -- ").append(m.getName()).append(": ").append(StringUtils.join(report, ", "));

        }

        if (sb.length() > 0)
            Assert.fail("Missing expected properties on 'LabKey Software' modules:" + sb.toString());
    }

}

