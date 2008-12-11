/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

package org.labkey.study.assay;

import org.labkey.api.module.ModuleResourceLoader;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleResourceLoadException;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.Handler;
import org.labkey.api.exp.Lsid;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.MothershipReport;
import org.labkey.api.view.ActionURL;
import org.labkey.study.StudyModule;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.DynaBean;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.lang.reflect.InvocationTargetException;

/**
 * User: kevink
 * Date: Dec 10, 2008 1:34:33 PM
 */
public class ModuleAssayLoader implements ModuleResourceLoader
{
    public Set<String> getModuleDependencies(Module module, File explodedModuleDir)
    {
        File assayDir = new File(explodedModuleDir, "assay");
        if (assayDir.exists())
            return Collections.singleton(StudyModule.MODULE_NAME);
        return Collections.emptySet();
    }

    public void loadResources(Module module, File explodedModuleDir) throws IOException, ModuleResourceLoadException
    {
        File assayDir = new File(explodedModuleDir, "assay");
        if (assayDir.exists())
        {
            for (File assayProviderDir : assayDir.listFiles())
                loadAssayProvider(assayProviderDir);
        }
    }

    private void loadAssayProvider(File assayProviderDir)
    {
        String assayName = assayProviderDir.getName();
        ModuleAssayProvider assayProvider = new ModuleAssayProvider(assayName);
        AssayService.get().registerAssayProvider(assayProvider);
    }

}
