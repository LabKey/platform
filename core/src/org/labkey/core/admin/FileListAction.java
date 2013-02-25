/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.core.admin;

import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;
import org.labkey.core.query.CoreQuerySchema;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

@RequiresSiteAdmin
public class FileListAction extends SimpleViewAction
{
    @Override
    public ModelAndView getView(Object o, BindException errors) throws Exception
    {
        ViewContext context = getViewContext();
        QuerySettings settings = new QuerySettings(context, QueryView.DATAREGIONNAME_DEFAULT, CoreQuerySchema.FILES_TABLE_NAME);
        UserSchema schema = QueryService.get().getUserSchema(context.getUser(), context.getContainer(), "core");
        QueryView view = schema.createView(context, settings, errors);
        return view;
    }

    @Override
    public NavTree appendNavTrail(NavTree root)
    {
        return PageFlowUtil.urlProvider(AdminUrls.class).appendAdminNavTrail(root, "File List", null);
    }
}

