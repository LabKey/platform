/*
 * Copyright (c) 2012 LabKey Corporation
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
package org.labkey.core.admin.importer;

import org.apache.xmlbeans.XmlObject;
import org.labkey.api.admin.AbstractFolderImportFactory;
import org.labkey.api.admin.FolderImporter;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.admin.ImportException;
import org.labkey.api.admin.InvalidFileException;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobWarning;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.api.view.FolderTab;
import org.labkey.api.view.Portal;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument;
import org.labkey.folder.xml.PagesDocument;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: cnathe
 * Date: Jan 17, 2012
 */
public class PageImporterFactory extends AbstractFolderImportFactory
{
    @Override
    public FolderImporter create()
    {
        return new PageImporter();
    }

    @Override
    public int getPriority()
    {
        return 80;
    }

    public class PageImporter implements FolderImporter<FolderDocument.Folder>
    {
        @Override
        public String getDescription()
        {
            return "pages and webpart properties";
        }

        @Override
        public void process(PipelineJob job, ImportContext<FolderDocument.Folder> ctx, VirtualFile root) throws Exception
        {
            FolderDocument.Folder.Pages pagesXml = ctx.getXml().getPages();

            if (null != pagesXml)
            {
                if (null != job)
                    job.setStatus("IMPORT " + getDescription());
                ctx.getLogger().info("Loading " + getDescription());

                PagesDocument pagesDocXml;
                String pagesFileName = pagesXml.getFile();

                try
                {
                    XmlObject xml = root.getXmlBean(pagesFileName);
                    if (xml instanceof PagesDocument)
                    {
                        pagesDocXml = (PagesDocument)xml;
                        XmlBeansUtil.validateXmlDocument(pagesDocXml, pagesFileName);
                    }
                    else
                    {
                        throw new ImportException("Could not find expected file: " + pagesFileName);
                    }
                }
                catch (XmlValidationException e)
                {
                    throw new InvalidFileException(pagesFileName, e);
                }

                PagesDocument.Pages.Page[] pageXmls = pagesDocXml.getPages().getPageArray();
                List<Portal.WebPart> tabs = new ArrayList<Portal.WebPart>();
                for (PagesDocument.Pages.Page pageXml : pageXmls)
                {
                    // for the study folder type(s), the Overview tab can have a pageId of portal.default
                    String pageId = pageXml.getName();
                    if (pageId.equals("Overview") && Portal.getParts(ctx.getContainer(), pageId).size() == 0)
                    {
                        pageId = Portal.DEFAULT_PORTAL_PAGE_ID;
                    }

                    Portal.WebPart tab = new Portal.WebPart();
                    tab.setLocation(FolderTab.LOCATION);
                    tab.setName(pageXml.getName());
                    tab.setIndex(pageXml.getIndex());
                    tabs.add(tab);

                    PagesDocument.Pages.Page.Webpart[] webpartXmls = pageXml.getWebpartArray();
                    List<Portal.WebPart> webparts = new ArrayList<Portal.WebPart>();
                    for (PagesDocument.Pages.Page.Webpart webpartXml : webpartXmls)
                    {
                        Portal.WebPart webPart = new Portal.WebPart();
                        webPart.setName(webpartXml.getName());
                        webPart.setIndex(webpartXml.getIndex());
                        webPart.setLocation(webpartXml.getLocation());
                        webPart.setPermanent(webpartXml.getPermanent());
                        if (null != webpartXml.getProperties())
                        {
                            PagesDocument.Pages.Page.Webpart.Properties.Property[] properyXmls = webpartXml.getProperties().getPropertyArray();
                            Map<String, String> propertyMap = new HashMap<String, String>();
                            for (PagesDocument.Pages.Page.Webpart.Properties.Property properyXml : properyXmls)
                            {
                                propertyMap.put(properyXml.getKey(), properyXml.getValue());
                            }

                            WebPartFactory factory = Portal.getPortalPart(webPart.getName());
                            Map<String, String> newPropertyMap = factory.deserializePropertyMap(ctx, propertyMap);
                            for (Map.Entry<String, String> newProperty : newPropertyMap.entrySet())
                            {
                                webPart.setProperty(newProperty.getKey(), newProperty.getValue());
                            }
                        }
                        webparts.add(webPart);
                    }
                    Portal.saveParts(ctx.getContainer(), pageId, webparts);
                }

                if (tabs.size() > 1)
                {
                    Portal.saveParts(ctx.getContainer(), FolderTab.FOLDER_TAB_PAGE_ID, tabs);
                }

                ctx.getLogger().info("Done importing " + getDescription());
            }
        }

        @Override
        public Collection<PipelineJobWarning> postProcess(ImportContext<FolderDocument.Folder> ctx, VirtualFile root) throws Exception
        {
            return null;
        }

        @Override
        public boolean supportsVirtualFile()
        {
            return true;
        }
    }
}
