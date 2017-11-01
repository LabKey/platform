/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

package org.labkey.api.view;

import org.labkey.api.data.*;
import org.labkey.api.security.UserUrls;
import org.labkey.api.util.PageFlowUtil;
import org.springframework.validation.BindException;

import java.io.PrintWriter;

/**
 * User: Mark Igra
 * Date: Jul 28, 2006
 * Time: 4:35:23 PM
 */
public class ContactWebPart extends WebPartView
{
    public ContactWebPart()
    {
        super("Project Contacts");
    }


    @Override
    public void renderView(Object model, PrintWriter out) throws Exception
    {
        Container c = getViewContext().getContainer();

        GridView gridView = new GridView(getGridRegionWebPart(), (BindException)null);
        gridView.setFrame(FrameType.DIV);
        
        gridView.setPortalLinks(getPortalLinks());
        gridView.setSort(new Sort("Email"));
        gridView.setContainer(c.getProject());

        if (!getViewContext().getUser().isGuest())
            gridView.setTitleHref(PageFlowUtil.urlProvider(UserUrls.class).getSiteUsersURL());

        include(gridView);
    }


    private DataRegion getGridRegionWebPart()
    {
        DataRegion rgn = new DataRegion();

        rgn.setColumns(CoreSchema.getInstance().getTableInfoContacts().getColumns("Name,DisplayName,Email,Phone,UserId"));
        DisplayColumn nameDC = rgn.getDisplayColumn("name");
        nameDC.setRequiresHtmlFiltering(false);

        rgn.getDisplayColumn("Email").setURL("mailto:${Email}");
        rgn.getDisplayColumn("UserId").setVisible(false);
        rgn.getDisplayColumn("DisplayName").setCaption("Display Name");

        rgn.setButtonBar(ButtonBar.BUTTON_BAR_EMPTY, DataRegion.MODE_GRID);
        return rgn;
    }
}
