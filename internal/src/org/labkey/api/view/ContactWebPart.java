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

package org.labkey.api.view;

import org.labkey.api.data.*;
import org.labkey.api.security.ACL;

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
        setTitle("Project Contacts");
    }


    @Override
    public void renderView(Object model, PrintWriter out) throws Exception
    {
        Container c;

        try
        {
            c = getViewContext().getContainer(ACL.PERM_READ);
        }
        catch (UnauthorizedException e)
        {
            String message;

            if (getViewContext().getUser().isGuest())
                message = "Please log in to see this data.";
            else
                message = "You do not have permission to see this data.";

            out.println(message);

            return;
        }

        if (c == null)
            throw new IllegalStateException("There is a problem with this folder. Please see your administrator for assistance.");

        GridView gridView = new GridView(getGridRegionWebPart());
        gridView.setFrame(FrameType.DIV);
        gridView.setCustomizeLinks(getCustomizeLinks());
        gridView.setSort(new Sort("Email"));
        gridView.setContainer(c.getProject());

        if (!getViewContext().getUser().isGuest())
            gridView.setTitleHref(ActionURL.toPathString("User", "showUsers", ""));

        include(gridView);
    }


    private DataRegion getGridRegionWebPart()
    {
        DataRegion rgn = new DataRegion();

        rgn.setColumns(CoreSchema.getInstance().getTableInfoContacts().getColumns("Name,DisplayName,Email,Phone,UserId"));
        DisplayColumn nameDC = rgn.getDisplayColumn("name");
        nameDC.setURL(ActionURL.toPathString("User", "details.view", "") + "?userId=${UserId}");
        nameDC.setHtmlFiltered(false);

        rgn.getDisplayColumn("Email").setURL("mailto:${Email}");
        rgn.getDisplayColumn("UserId").setVisible(false);
        rgn.getDisplayColumn("DisplayName").setCaption("Display Name");

        rgn.setShadeAlternatingRows(true);
        rgn.setShowBorders(true);
        rgn.setButtonBar(ButtonBar.BUTTON_BAR_EMPTY, DataRegion.MODE_GRID);
        return rgn;
    }
}
