/*
 * Copyright (c) 2012-2017 LabKey Corporation
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
package org.labkey.core.admin.writer;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.admin.BaseFolderWriter;
import org.labkey.api.admin.FolderArchiveDataTypes;
import org.labkey.api.admin.FolderWriter;
import org.labkey.api.admin.FolderWriterFactory;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.data.Container;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.Portal;
import org.labkey.api.view.Portal.WebPart;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument;
import org.labkey.folder.xml.PagesDocument;

import java.util.List;
import java.util.Map;


/**
 * User: cnathe
 * Date: Jan 17, 2012
 */
public class PageWriterFactory implements FolderWriterFactory
{
    private static final String FILENAME = "pages.xml";

    @Override
    public FolderWriter create()
    {
        return new PageWriter();
    }

    public class PageWriter extends BaseFolderWriter
    {
        public String getDataType()
        {
            return FolderArchiveDataTypes.WEBPART_PROPERTIES_AND_LAYOUT;
        }

        public void write(Container c, ImportContext<FolderDocument.Folder> ctx, VirtualFile root) throws Exception
        {
            FolderDocument.Folder folderXml = ctx.getXml();
            FolderDocument.Folder.Pages folderPagesXML = folderXml.addNewPages();
            folderPagesXML.setFile(PageWriterFactory.FILENAME);

            PagesDocument pagesDocXML = PagesDocument.Factory.newInstance();
            PagesDocument.Pages pagesXML = pagesDocXML.addNewPages();

            Map<String,Portal.PortalPage> tabs = Portal.getPages(ctx.getContainer(), true);
            if (tabs.size() == 0)
            {
                // if there are no tabs, try getting webparts for the default page ID
                PagesDocument.Pages.Page pageXml = pagesXML.addNewPage();
                pageXml.setName(Portal.DEFAULT_PORTAL_PAGE_ID);
                addWebPartsToPage(ctx, pageXml, Portal.getParts(ctx.getContainer(), Portal.DEFAULT_PORTAL_PAGE_ID));
            }
            else
            {
                for (Portal.PortalPage tab : tabs.values())
                {
                    PagesDocument.Pages.Page pageXml = pagesXML.addNewPage();
                    pageXml.setIndex(tab.getIndex());
                    pageXml.setName(tab.getPageId());
                    pageXml.setPropertyString(tab.getProperties());     // For custom tab
                    pageXml.setHidden(tab.isHidden());
                    if (null != StringUtils.trimToNull(tab.getCaption()))
                        pageXml.setCaption(tab.getCaption());

                    // for the study folder type(s), the Overview tab can have a pageId of portal.default
                    List<WebPart> portalPageParts = Portal.getParts(ctx.getContainer(), tab.getPageId());

                    addWebPartsToPage(ctx, pageXml, portalPageParts);
                }
            }

            root.saveXmlBean(FILENAME, pagesDocXML);
        }


        public void addWebPartsToPage(ImportContext ctx, PagesDocument.Pages.Page pageXml, List<WebPart> webpartsInPage)
        {
            for (WebPart webPart : webpartsInPage)
            {
                WebPartFactory factory = null;

                if (webPart.getPropertyMap().size() > 0)
                {
                    factory = Portal.getPortalPart(webPart.getName());
                }

                if (factory != null && !factory.includeInExport(ctx, webPart))
                {
                    return;
                }

                PagesDocument.Pages.Page.Webpart webpartXml = pageXml.addNewWebpart();
                webpartXml.setName(webPart.getName());
                webpartXml.setIndex(webPart.getIndex());
                webpartXml.setLocation(webPart.getLocation());
                webpartXml.setPermanent(webPart.isPermanent());

                if (webPart.getPermission() != null)
                    webpartXml.setPermission(webPart.getPermission());

                if (webPart.getPermissionContainer() != null)
                    webpartXml.setPermissionContainerPath(webPart.getPermissionContainer().getPath());

                if (webPart.getPropertyMap().size() > 0)
                {
                    if (null != factory)        // old old webpart could have been left behind and have no factory
                    {
                        Map<String, String> props = factory.serializePropertyMap(ctx, webPart.getPropertyMap());

                        PagesDocument.Pages.Page.Webpart.Properties propertiesXml = webpartXml.addNewProperties();
                        for (Map.Entry<String, String> prop : props.entrySet())
                        {
                            // This will filter out internal parameters that were previously saved with webpart properties,
                            // such as X-LABKEY-CSRF, #30532.
                            if (PageFlowUtil.isInternalParameter(prop.getKey()))
                                continue;

                            PagesDocument.Pages.Page.Webpart.Properties.Property propertyXml = propertiesXml.addNewProperty();
                            propertyXml.setKey(prop.getKey());
                            propertyXml.setValue(prop.getValue());
                        }
                    }
                }
            }
        }
    }
}
