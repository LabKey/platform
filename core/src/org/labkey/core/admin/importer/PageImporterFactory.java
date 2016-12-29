/*
 * Copyright (c) 2012-2016 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.apache.xmlbeans.XmlObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.AbstractFolderImportFactory;
import org.labkey.api.admin.FolderImporter;
import org.labkey.api.admin.FolderArchiveDataTypes;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.admin.ImportException;
import org.labkey.api.admin.InvalidFileException;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobWarning;
import org.labkey.api.security.User;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.FolderTab;
import org.labkey.api.view.Portal;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument;
import org.labkey.folder.xml.PagesDocument;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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


    private static class _FolderTab extends FolderTab
    {
        _FolderTab(String pageId, int index, String caption)
        {
            super(pageId, caption);
            setCaption(caption);        // Overwrite what parent knows with what we imported or null

            _defaultIndex = index;
        }
        @Override
        public ActionURL getURL(Container container, User user)
        {
            return null;
        }
    }


    public class PageImporter implements FolderImporter<FolderDocument.Folder>
    {
        @Override
        public String getDataType()
        {
            return FolderArchiveDataTypes.WEBPART_PROPERTIES_AND_LAYOUT;
        }

        @Override
        public String getDescription()
        {
            return "pages and webpart properties";
        }

        @Override
        public void process(PipelineJob job, ImportContext<FolderDocument.Folder> ctx, VirtualFile root) throws Exception
        {
            if (isValidForImportArchive(ctx))
            {
                FolderDocument.Folder.Pages pagesXml = ctx.getXml().getPages();

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
                List<FolderTab> tabs = new ArrayList<>();
                for (PagesDocument.Pages.Page pageXml : pageXmls)
                {
                    // for the study folder type(s), the Overview tab can have a pageId of portal.default
                    String pageId = pageXml.getName();
                    String properties = pageXml.getPropertyString();
                    boolean hidden = pageXml.getHidden();

/*                    if (pageId.equals("Overview") && Portal.getParts(ctx.getContainer(), pageId).size() == 0)
                    {
                        pageId = Portal.DEFAULT_PORTAL_PAGE_ID;       // TODO: seems to cause problem with new container tab and import/export features. Check this.
                    }
*/
                    FolderTab tab = new _FolderTab(pageXml.getName(), pageXml.getIndex(), StringUtils.trimToNull(pageXml.getCaption()));
                    tabs.add(tab);

                    PagesDocument.Pages.Page.Webpart[] webpartXmls = pageXml.getWebpartArray();
                    List<Portal.WebPart> webparts = new ArrayList<>();
                    for (PagesDocument.Pages.Page.Webpart webpartXml : webpartXmls)
                    {
                        Portal.WebPart webPart = new Portal.WebPart();
                        webPart.setName(webpartXml.getName());
                        webPart.setIndex(webpartXml.getIndex());
                        webPart.setLocation(webpartXml.getLocation());
                        webPart.setPermanent(webpartXml.getPermanent());
                        webPart.setPermission(webpartXml.getPermission());

                        if(webpartXml.getPermissionContainerPath() != null)
                        {
                            Container permissionContainer = ContainerManager.getForPath(webpartXml.getPermissionContainerPath());
                            if(permissionContainer != null)
                                webPart.setPermissionContainer(permissionContainer);
                        }
                        
                        if (null != webpartXml.getProperties())
                        {
                            PagesDocument.Pages.Page.Webpart.Properties.Property[] propertyXmls = webpartXml.getProperties().getPropertyArray();
                            Map<String, String> propertyMap = new HashMap<>();
                            for (PagesDocument.Pages.Page.Webpart.Properties.Property propertyXml : propertyXmls)
                            {
                                propertyMap.put(propertyXml.getKey(), propertyXml.getValue());
                            }

                            WebPartFactory factory = Portal.getPortalPart(webPart.getName());
                            if (null != factory)
                            {
                                Map<String, String> newPropertyMap = factory.deserializePropertyMap(ctx, propertyMap);
                                for (Map.Entry<String, String> newProperty : newPropertyMap.entrySet())
                                {
                                    webPart.setProperty(newProperty.getKey(), newProperty.getValue());
                                }
                            }
                        }
                        webparts.add(webPart);
                    }
                    Portal.saveParts(ctx.getContainer(), pageId, webparts);
                    Portal.addProperties(ctx.getContainer(), pageId, properties);
                    if(hidden)
                        Portal.hidePage(ctx.getContainer(), pageId);
                }

                if (tabs.size() > 1)
                {
                    Portal.resetPages(ctx.getContainer(), tabs, true);
                }

                ctx.getLogger().info("Done importing " + getDescription());
            }
        }

        @NotNull
        @Override
        public Collection<PipelineJobWarning> postProcess(ImportContext<FolderDocument.Folder> ctx, VirtualFile root) throws Exception
        {
            return Collections.emptyList();
        }

        @Override
        public boolean isValidForImportArchive(ImportContext<FolderDocument.Folder> ctx) throws ImportException
        {
            return ctx.getXml() != null && ctx.getXml().getPages() != null;
        }
    }
}
