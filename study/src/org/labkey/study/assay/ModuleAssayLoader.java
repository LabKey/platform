/*
 * Copyright (c) 2008-2014 LabKey Corporation
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

import org.apache.xmlbeans.XmlException;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleResourceLoadException;
import org.labkey.api.resource.Resource;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.study.StudyModule;
import org.labkey.study.assay.xml.ProviderDocument;
import org.labkey.study.assay.xml.ProviderType;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Set;

/**
 * User: kevink
 * Date: Dec 10, 2008 1:34:33 PM
 */

// TODO: Temporary: switch to a ModuleResourceCache
public class ModuleAssayLoader
{
    public static final String DOMAINS_DIR_NAME = "domains";

    @NotNull
    public Set<String> getModuleDependencies(Module module, File explodedModuleDir)
    {
        // NOTE: Can't use Module's resource resolver yet since the module hasn't been initialized.
        File assayDir = new File(explodedModuleDir, AssayService.ASSAY_DIR_NAME);
        if (assayDir.exists())
            return Collections.singleton(StudyModule.MODULE_NAME);
        return Collections.emptySet();
    }

    public Collection<AssayProvider> getAssayProviders(Module module)
    {
        Collection<AssayProvider> ret = new LinkedList<>();
        Resource assayDir = module.getModuleResource(AssayService.ASSAY_DIR_NAME);
        if (assayDir != null && assayDir.exists() && assayDir.isCollection())
        {
            for (Resource assayProviderDir : assayDir.list())
            {
                if (!assayProviderDir.isCollection())
                    continue;

                // A config.xml file is required to define the file-based module assay.
                Resource configFile = assayProviderDir.find("config.xml");
                if (configFile == null || !configFile.isFile())
                    continue;

                try
                {
                    ret.add(loadAssayProvider(module, assayProviderDir, configFile));
                }
                catch (Exception e)
                {
                    ExceptionUtil.logExceptionToMothership(null, e);
                }
            }
        }
        return ret;
    }

    private AssayProvider loadAssayProvider(Module module, Resource assayProviderDir, Resource configFile) throws IOException, ModuleResourceLoadException
    {
        String assayName = assayProviderDir.getName();

        ProviderType providerConfig = parseProvider(configFile);
        if (providerConfig == null)
            providerConfig = ProviderDocument.Factory.newInstance().addNewProvider();

        if (providerConfig.isSetName())
            assayName = providerConfig.getName();
        else
            providerConfig.setName(assayName);

        return new ModuleAssayProvider(assayName, module, assayProviderDir, providerConfig);
    }

    private ProviderType parseProvider(Resource configFile) throws IOException, ModuleResourceLoadException
    {
        try
        {
            ProviderDocument doc = ProviderDocument.Factory.parse(configFile.getInputStream());
            if (doc != null)
                return doc.getProvider();
        }
        catch (XmlException e)
        {
            throw new ModuleResourceLoadException(e);
        }
        return null;
    }
}
