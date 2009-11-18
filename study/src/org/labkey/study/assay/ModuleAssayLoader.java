/*
 * Copyright (c) 2008-2009 LabKey Corporation
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
import org.labkey.api.exp.api.ExpProtocol.AssayDomainTypes;
import org.labkey.api.exp.api.IAssayDomainType;
import org.labkey.study.StudyModule;
import org.labkey.study.assay.xml.DomainDocument;
import org.labkey.study.assay.xml.ProviderDocument;
import org.labkey.study.assay.xml.ProviderType;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;
import org.apache.xmlbeans.XmlError;
import org.fhcrc.cpas.exp.xml.DomainDescriptorType;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * User: kevink
 * Date: Dec 10, 2008 1:34:33 PM
 */
public class ModuleAssayLoader implements ModuleResourceLoader
{
    /*package*/ static final String ASSAY_DIR_NAME = "assay";
    /*package*/ static final String DOMAINS_DIR_NAME = "domains";
    /*package*/ static final String VIEWS_DIR_NAME = "views";

    public Set<String> getModuleDependencies(Module module, File explodedModuleDir)
    {
        File assayDir = new File(explodedModuleDir, ASSAY_DIR_NAME);
        if (assayDir.exists())
            return Collections.singleton(StudyModule.MODULE_NAME);
        return Collections.emptySet();
    }

    public void loadResources(Module module, File explodedModuleDir) throws IOException, ModuleResourceLoadException
    {
        File assayDir = new File(explodedModuleDir, ASSAY_DIR_NAME);
        if (assayDir.exists())
        {
            for (File assayProviderDir : assayDir.listFiles())
            {
                if (!assayProviderDir.isHidden())
                    loadAssayProvider(assayProviderDir);
            }
        }
    }

    private void loadAssayProvider(File assayProviderDir) throws IOException, ModuleResourceLoadException
    {
        String assayName = assayProviderDir.getName();

        File configFile = new File(assayProviderDir, "config.xml");
        ProviderType providerConfig = null;
        if (configFile.canRead())
            providerConfig = parseProvider(configFile);
        if (providerConfig == null)
            providerConfig = ProviderDocument.Factory.newInstance().addNewProvider();

        if (providerConfig.isSetName())
            assayName = providerConfig.getName();
        else
            providerConfig.setName(assayName);

        ModuleAssayProvider assayProvider = new ModuleAssayProvider(assayName, assayProviderDir, providerConfig);

        AssayService.get().registerAssayProvider(assayProvider);
    }

    private ProviderType parseProvider(File configFile) throws IOException, ModuleResourceLoadException
    {
        try
        {
            ProviderDocument doc = ProviderDocument.Factory.parse(configFile);
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
