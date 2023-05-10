/*
 * Copyright (c) 2012-2018 LabKey Corporation
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
import org.labkey.api.admin.AbstractFolderImportFactory;
import org.labkey.api.admin.FolderArchiveDataTypes;
import org.labkey.api.admin.FolderImportContext;
import org.labkey.api.admin.FolderImporter;
import org.labkey.api.admin.ImportException;
import org.labkey.api.admin.InvalidFileException;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.security.User;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.FolderTab;
import org.labkey.api.view.Portal;
import org.labkey.api.view.WebPartCache;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument.Folder;
import org.labkey.folder.xml.PagesDocument;

import java.util.ArrayList;
import java.util.Arrays;
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


    public static class PageImporter implements FolderImporter
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
        public void process(PipelineJob job, FolderImportContext ctx, VirtualFile root) throws Exception
        {
            if (isValidForImportArchive(ctx))
            {
                Folder.Pages pagesXml = ctx.getXml().getPages();

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

                List<PagesDocument.Pages.Page> pageXmls = new ArrayList<>(Arrays.asList(pagesDocXml.getPages().getPageArray()));
                List<FolderTab> tabs = new ArrayList<>();
                int webpartCount = 0;

                FolderTab defaultTab = ctx.getContainer().getFolderType().getDefaultTab();
                String defaultPageId = defaultTab == null ? Portal.DEFAULT_PORTAL_PAGE_ID : defaultTab.getName();

                dedupePortalTabs(pageXmls, defaultPageId);

                for (PagesDocument.Pages.Page pageXml : pageXmls)
                {
                    // for the study folder type(s), the Overview tab can have a pageId of portal.default
                    String pageId = pageXml.getName();
                    String properties = pageXml.getPropertyString();
                    boolean hidden = pageXml.getHidden();

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
                        webpartCount++;
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

                // Clear the cache one more time - attempt to avoid race condition on TeamCity
                WebPartCache.remove(ctx.getContainer());

                ctx.getLogger().info("Done importing " + pageXmls.size() + " page(s) with " + webpartCount + " webpart(s)");
            }
        }

        private void dedupePortalTabs(List<PagesDocument.Pages.Page> pageXmls, String defaultPageId)
        {
            // See if the folder isn't using the legacy portal definition by default
            if (!defaultPageId.equals(Portal.DEFAULT_PORTAL_PAGE_ID))
            {
                // Need to check and potentially de-dupe the legacy and new default tab, so find them in the list
                PagesDocument.Pages.Page folderDefaultTabXml = null;
                PagesDocument.Pages.Page portalDefaultTabXml = null;
                for (PagesDocument.Pages.Page pageXml : pageXmls)
                {
                    if (Portal.DEFAULT_PORTAL_PAGE_ID.equals(pageXml.getName()))
                    {
                        portalDefaultTabXml = pageXml;
                    }
                    else if (defaultPageId.equals(pageXml.getName()))
                    {
                        folderDefaultTabXml = pageXml;
                    }
                }

                // We found both the legacy name and the default tab for this folder
                if (portalDefaultTabXml != null && folderDefaultTabXml != null)
                {
                    // Issue 47841: if the portalDefaultTabXml contains only menu bar webparts, keep them both
                    if (!portalDefaultTabXml.getWebpartList().isEmpty())
                    {
                        boolean allMenuBarParts = portalDefaultTabXml.getWebpartList().stream().allMatch(webpart -> WebPartFactory.LOCATION_MENUBAR.equalsIgnoreCase(webpart.getLocation()));
                        if (allMenuBarParts)
                            return;
                    }

                    // The folder's default tab is empty
                    if (folderDefaultTabXml.getWebpartArray().length == 0)
                    {
                        // Remove it from the list and repurpose the legacy name
                        pageXmls.remove(folderDefaultTabXml);
                        portalDefaultTabXml.setName(defaultPageId);
                    }
                    else
                    {
                        // Prefer the folder's default tab
                        pageXmls.remove(portalDefaultTabXml);
                    }
                }

                if (portalDefaultTabXml != null && folderDefaultTabXml == null)
                {
                    // We only have the legacy version - rename it to the folder's default
                    portalDefaultTabXml.setName(defaultPageId);
                }
            }
        }

        @Override
        public boolean isValidForImportArchive(FolderImportContext ctx) throws ImportException
        {
            return ctx.getXml() != null && ctx.getXml().getPages() != null;
        }
    }
}
