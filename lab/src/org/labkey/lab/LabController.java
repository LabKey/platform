/*
 * Copyright (c) 2011 LabKey Corporation
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

package org.labkey.lab;

import org.labkey.api.action.RedirectAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;


public class LabController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(LabController.class);

    public LabController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class BeginAction extends RedirectAction
    {
        @Override
        public URLHelper getSuccessURL(Object o)
        {
            return PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(getContainer());
        }

        @Override
        public boolean doAction(Object o, BindException errors) throws Exception
        {
            return true;
        }

        @Override
        public void validateCommand(Object target, Errors errors) {}

    }
}