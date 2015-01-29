package org.labkey.announcements;

import org.labkey.announcements.query.AnnouncementSchema;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.announcements.CommSchema;
import org.labkey.api.query.QueryForm;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

/**
 * Created by Marty on 1/19/2015.
 */
public class ToursController extends SpringActionController
{
    private static final CommSchema _comm = CommSchema.getInstance();
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(ToursController.class, GetTourAction.class);

    public ToursController() throws Exception
    {
        setActionResolver(_actionResolver);
    }

    // Anyone with read permission can attempt to view the list.  AnnouncementWebPart will do further permission checking.  For example,
    //   in a secure message board, those without Editor permissions will only see messages when they are on the member list
    @RequiresPermissionClass(ReadPermission.class)
    public class BeginAction extends SimpleViewAction<QueryForm>
    {


        // Invoked via reflection
        @SuppressWarnings("UnusedDeclaration")
        public BeginAction()
        {
        }

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

        // Called directly by other actions
        public BeginAction(ViewContext ctx)
        {
            setViewContext(ctx);
        }


        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            //return root.addChild(getSettings().getBoardName(), getBeginURL(getContainer()));
            return null;
        }
    }
}
