/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

package org.labkey.biotrue.query;

import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.DataRegion;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.view.DataView;

/**
 * User: Karl Lum
 * Date: Apr 3, 2007
 */
public class BtServerView extends QueryView
{
    public BtServerView(UserSchema schema, QuerySettings settings)
    {
        super(schema, settings);
    }

    public DataView createDataView()
    {
        DataView view = super.createDataView();

        if (view.getDataRegion().getButtonBarPosition() != DataRegion.ButtonBarPosition.NONE)
        {
            ButtonBar bar = view.getDataRegion().getButtonBar(DataRegion.MODE_GRID);

            ActionButton adminButton = new ActionButton("admin.view", "Admin", DataRegion.MODE_GRID, ActionButton.Action.LINK);
            adminButton.setDisplayPermission(AdminPermission.class);
            bar.add(adminButton);
            view.getDataRegion().setButtonBarPosition(DataRegion.ButtonBarPosition.TOP);
        }
        view.getDataRegion().setShadeAlternatingRows(true);
        view.getDataRegion().setShowBorders(true);

        return view;
    }
}
