/*
 * Copyright (c) 2015 LabKey Corporation
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
package org.labkey.announcements;

import org.labkey.announcements.query.AnnouncementSchema;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.query.QueryForm;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.view.NavTree;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

/**
 * Created by Marty on 1/19/2015.
 */
public class ToursController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(ToursController.class, GetTourAction.class);

    public ToursController() throws Exception
    {
        setActionResolver(_actionResolver);
    }

    // Anyone with read permission can attempt to view the list.  ToursTable will do further permission checking.
    @RequiresPermissionClass(ReadPermission.class)
    @SuppressWarnings("UnusedDeclaration")
    public class BeginAction extends SimpleViewAction<QueryForm>
    {
        @Override
        public ModelAndView getView(QueryForm queryForm, BindException errors) throws Exception
        {
            queryForm.setSchemaName(AnnouncementSchema.SCHEMA_NAME);
            queryForm.setQueryName(AnnouncementSchema.TOURS_TABLE_NAME);

            QuerySettings settings = queryForm.getQuerySettings();
            settings.setAllowChooseView(false);
            settings.setAllowChooseQuery(false);

            QueryView view = QueryView.create(queryForm, errors);
            view.setShowBorders(true);
            view.setShowSurroundingBorder(true);
            view.setTitle("Tours");

            return view;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }
}
