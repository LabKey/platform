/*
 * Copyright (c) 2015-2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
            if (!"LabKey".equals(m.getOrganization()))
                continue;

            List<String> report = ModuleLoader.getInstance().checkLabKeyModuleInfo(m);
            if (report != null)
                sb.append("\n -- ").append(m.getName()).append(": ").append(StringUtils.join(report, ", "));

        }

        if (sb.length() > 0)
            Assert.fail("Missing expected properties on 'LabKey' modules:" + sb.toString());
    }

}

