/*
 * Copyright (c) 2016-2017 LabKey Corporation
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
package org.labkey.bigiron;

import org.labkey.api.action.ExportAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.SqlScriptRunner;
import org.labkey.api.module.AllowedDuringUpgrade;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.AdminOperationsPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.bigiron.mssql.GroupConcatInstallationManager;
import org.springframework.validation.BindException;

import javax.servlet.http.HttpServletResponse;

/**
 * Created by Josh on 11/17/2016.
 */
public class BigIronController extends SpringActionController
{
    private static ActionResolver _actionResolver = new DefaultActionResolver(BigIronController.class);

    public BigIronController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresPermission(AdminOperationsPermission.class)
    @AllowedDuringUpgrade
    public class DownloadGroupConcatInstallScriptAction extends ExportAction<Object>
    {
        @Override
        public void export(Object o, HttpServletResponse response, BindException errors) throws Exception
        {
            SqlScriptRunner.SqlScript installScript = GroupConcatInstallationManager.get().getInstallScript();
            response.setCharacterEncoding(StringUtilsLabKey.DEFAULT_CHARSET.name());
            PageFlowUtil.streamFileBytes(response, "groupConcatInstall.sql", installScript.getContents().getBytes(StringUtilsLabKey.DEFAULT_CHARSET), true);
        }
    }
}
