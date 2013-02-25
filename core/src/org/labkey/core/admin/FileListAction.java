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

