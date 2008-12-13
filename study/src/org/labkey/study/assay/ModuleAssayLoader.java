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
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpProtocol.AssayDomainType;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.study.StudyModule;
import org.labkey.study.assay.xml.DomainDocument;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.xmlbeans.XmlException;
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

    private void loadAssayProvider(File assayProviderDir) throws IOException, ModuleResourceLoadException
    {
        File configFile = new File(assayProviderDir, "config.xml");

        String assayName = assayProviderDir.getName();

        ModuleAssayProvider assayProvider = new ModuleAssayProvider(assayName);

        File domainDir = new File(assayProviderDir, "domains");
        if (domainDir.canRead())
        {
            for (AssayDomainType domainType : AssayDomainType.values())
            {
                File domainFile = new File(domainDir, domainType.name().toLowerCase() + ".xml");
                if (!domainFile.canRead())
                    continue;
                DomainDescriptorType xDomain = parseDomain(domainType, domainFile);
                if (xDomain != null)
                    assayProvider.addDomain(domainType, xDomain);

//                File domainFile = new File(domainDir, domainType.name().toLowerCase() + ".tsv");
//                if (!domainFile.canRead())
//                    continue;
//                GWTDomain domain = DomainUtil.fromTsv(domainFile);
            }
        }

        File viewsDir = new File(assayProviderDir, "views");
        if (viewsDir.canRead())
        {
            for (AssayDomainType domainType : new AssayDomainType[] { AssayDomainType.Run, AssayDomainType.Data })
            {
                File viewFile = new File(viewsDir, domainType.name().toLowerCase() + ".html");
                if (!viewFile.canRead())
                    continue;
                assayProvider.addView(domainType, viewFile);
            }
        }

        //assayProvider.validateConfiguration();

        AssayService.get().registerAssayProvider(assayProvider);
    }

    private DomainDescriptorType parseDomain(AssayDomainType domainType, File domainFile) throws IOException, ModuleResourceLoadException
    {
        try
        {
            DomainDocument doc = DomainDocument.Factory.parse(domainFile);
            DomainDescriptorType xDomain = doc.getDomain();
            if (xDomain != null && xDomain.validate())
            {
                if (!xDomain.isSetName())
                    xDomain.setName(domainType.name() + " Fields");

                if (!xDomain.isSetDomainURI())
                    xDomain.setDomainURI(domainType.getLsidTemplate());

                return xDomain;
            }
        }
        catch (XmlException e)
        {
            throw new ModuleResourceLoadException(e);
        }

        return null;
    }

    public static class ModuleAssayConfig
    {
        public String name;
        public String protocolLSIDPrefix;
        public String runLSIDPrefix;
        public DataTypeConfig dataType;

        public boolean canPublish = false;
        public boolean plateBased = false;
    }

    public static class DataTypeConfig
    {
        public String dataLSIDPrefix;
        public String flaggedURL;
        public String unflaggedURL;
        public StringExpressionFactory.StringExpression detailsURL; // StringExpression taking ExpData?

        public DataType createDataType()
        {
            return new DataType(dataLSIDPrefix) {
                @Override
                public URLHelper getDetailsURL(ExpData dataObject)
                {
                    try
                    {
                        if (detailsURL != null && dataObject != null)
                            return new ActionURL(detailsURL.eval(BeanUtils.describe(dataObject)));
                    }
                    catch (Exception e)
                    {
                        // XXX: log to mother
                    }
                    return null;
                }

                @Override
                public String urlFlag(boolean flagged)
                {
                    return flagged ? flaggedURL : unflaggedURL;
                }
            };
        }
    }
}
