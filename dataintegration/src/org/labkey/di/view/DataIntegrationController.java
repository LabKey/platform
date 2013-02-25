package org.labkey.di.view;

import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.NavTree;
import org.labkey.di.pipeline.ETLDescriptor;
import org.labkey.di.pipeline.ETLManager;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.util.List;

/**
 * User: jeckels
 * Date: 2/20/13
 */
public class DataIntegrationController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(DataIntegrationController.class);

    public DataIntegrationController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class BeginAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            List<ETLDescriptor> etls = ETLManager.get().getETLs();
            StringBuilder sb = new StringBuilder();
            for (ETLDescriptor etl : etls)
            {
                sb.append(PageFlowUtil.filter(etl.getName()));
                sb.append("<br/>");
            }

            return new HtmlView(sb.toString());
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }
}
