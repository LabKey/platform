package org.labkey.study.controllers;

import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.study.Study;
import org.labkey.api.util.GUID;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.Portal;
import org.labkey.api.view.VBox;
import org.labkey.api.view.template.HomeTemplate;
import org.labkey.api.view.template.PageConfig;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: marki
 *
 * This is a class used for a prototype user interface where each study can have multiple pages attached to it.
 * Page are portal pages keyed by id's stored in the PropertyManager. This is NOT a recommended way to store pages but
 * I was trying for minimal changes to project controller 
 */
public class StudyPagesController extends BaseStudyController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(StudyPagesController.class);

    public StudyPagesController()
    {
        setActionResolver(_actionResolver);
    }

    public enum Page
    {
        SHORTCUTS("Shortcuts"),
        SPECIMENS("Specimens"),
        DATA_ANALYSIS("Data Analysis");

        private final String _caption;
        Page(String caption)
        {
            this._caption = caption;
        }

        public String getCaption()
        {
            return _caption;
        }

        public ActionURL getURL(Container c)
        {
            ActionURL actionUrl = new ActionURL(PageAction.class, c);
            actionUrl.addParameter("pageName", this.toString());
            
            return actionUrl;
        }
    }

    public static class PageForm
    {
        private String _pageName;

        public String getPageName()
        {
            return _pageName;
        }

        public void setPageName(String pageName)
        {
            _pageName = pageName;
        }

        public String getPageCaption() {
            Page page = Page.valueOf(_pageName);
            if (null == page)
                return _pageName;
            else
                return page.getCaption();
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class PageAction extends SimpleViewAction<PageForm>
    {
        private static final String STUDY_PAGES = "StudyPages";
        private PageForm _page;

        @Override
        public ModelAndView getView(PageForm pageForm, BindException errors) throws Exception
        {
            Container c = getViewContext().getContainer();
            _page = pageForm;
            String containerId = c.getId();
            Map<String, String> properties = PropertyManager.getProperties(containerId, STUDY_PAGES);
            String pageGuid = properties.get(pageForm.getPageName());
            if (null == pageGuid)
            {
                properties = PropertyManager.getWritableProperties(containerId, STUDY_PAGES, true);
                pageGuid = GUID.makeGUID();
                properties.put(pageForm.getPageName(), pageGuid);
                PropertyManager.saveProperties(properties);
            }

            PageConfig page = getPageConfig();
            page.setTitle(pageForm.getPageCaption());

            HttpView template = new HomeTemplate(getViewContext(), c, new VBox(), page, new NavTree[0]);
            Portal.populatePortalView(getViewContext(), pageGuid, template);

            getPageConfig().setTemplate(PageConfig.Template.None);
            return template;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            Container c = getViewContext().getContainer();
            try
            {
                root.addChild(getStudy(false, c).getDisplayString(), getStudyOverviewURL());
                root.addChild(_page.getPageCaption(), getViewContext().getActionURL());
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }

            return root;
        }
    }

}
