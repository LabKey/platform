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
