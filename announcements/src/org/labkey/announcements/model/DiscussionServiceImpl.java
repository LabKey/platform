package org.labkey.announcements.model;

import org.apache.commons.lang.StringUtils;
import org.labkey.announcements.AnnouncementsController;
import org.labkey.api.announcements.DiscussionService;
import org.labkey.api.data.*;
import org.labkey.api.security.User;
import org.labkey.api.util.AppProps;
import org.labkey.api.view.*;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.Map;

/**
 * User: matthewb
 * Date: Feb 6, 2007
 * Time: 12:37:45 PM
 */
public class DiscussionServiceImpl implements DiscussionService.Service
{
    public WebPartView startDiscussion(Container c, User user, String identifier, ActionURL pageURL, ActionURL cancelURL, String title, String summary, boolean allowMultipleDiscussions)
    {
        if (!allowMultipleDiscussions)
        {
            Announcement[] discussions = getDiscussions(c, identifier);

            if (discussions.length > 0)
                return getDiscussion(c, discussions[0], user);
        }

        String viewTitle = "New Discussion";
        AnnouncementsController.AnnouncementForm form = new AnnouncementsController.AnnouncementForm();
        form.setUser(user);
        form.setContainer(c);
        form.set("title", title);
        form.set("discussionSrcIdentifier", identifier);
        form.set("discussionSrcURL", toSaved(pageURL));
        WebPartView view = new AnnouncementsController.InsertMessageView(form, viewTitle, null, false, cancelURL, true, allowMultipleDiscussions);
        return view;
    }


    public static String toSaved(ActionURL url)
    {
        Container c = ContainerManager.getForPath(url.getExtraPath());
        ActionURL saveURL = url.clone();
        if (null != c)
            saveURL.setExtraPath(c.getId());
        String saved=saveURL.getLocalURIString();

        String contextPath = AppProps.getInstance().getContextPath();
        if (saved.startsWith(contextPath))
            saved = "~" + saved.substring(contextPath.length());
        return saved;
    }


    public static ActionURL fromSaved(String saved)
    {
        if (saved.startsWith("~/"))
            saved = AppProps.getInstance().getContextPath() + saved.substring(1);
        ActionURL url = new ActionURL(saved);
        String id = StringUtils.strip(url.getExtraPath(),"/");
        Container c = ContainerManager.getForId(id);
        if (null != c)
            url.setExtraPath(c.getPath());
        return url;
    }


    public Announcement[] getDiscussions(Container c, String identifier)
    {
        SimpleFilter filter = new SimpleFilter("discussionSrcIdentifier", identifier);
        Announcement[] announcements = AnnouncementManager.getBareAnnouncements(c, filter, new Sort("Created"));
        return announcements;
    }


    public WebPartView getDiscussion(Container c, Announcement ann, User user)
    {
        try
        {
            // NOTE: don't pass in Announcement, it came from getBareAnnouncements()
            AnnouncementsController.ThreadView threadView = new AnnouncementsController.ThreadView(c, user, null, ann.getEntityId());
            return threadView;
        }
        catch (ServletException x)
        {
            return new HtmlView(x.toString());
        }
    }


    public DiscussionService.DiscussionView getDisussionArea(ViewContext context, String objectId, ActionURL pageURL, String newDiscussionTitle, boolean allowMultipleDiscussions, boolean displayFirstDiscussionByDefault)
    {
        Container c = context.getContainer();
        User user = context.getUser();

        // get discussion parameters
        Map<String, String> params = context.getActionURL().getScopeParameters("discussion");
        Announcement[] announcements = getDiscussions(c, objectId);

        int discussionId = 0;
        try
        {
            String id = params.get("id");
            if (null != id)
            {
                discussionId = Integer.parseInt(id);
            }
            else if (displayFirstDiscussionByDefault && params.isEmpty() && announcements.length > 0)
            {
                discussionId = announcements[0].getRowId();
            }
        }
        catch (Exception x) {/* */}
        
        pageURL = pageURL.clone();
        // clean up discussion parameters (in case caller didn't)
        pageURL.deleteScopeParameters("discussion");

        // often, but not necessarily the same as pageURL, assume we want to return to current page
        ActionURL currentURL = context.cloneActionURL().deleteScopeParameters("discussion");
        if (0 != discussionId)
            currentURL.addParameter("discussion.id", "" + discussionId);

        ModelAndView discussionBox = null;
        String focusId = null;
        
        if (params.get("start") != null)
        {
            WebPartView start = startDiscussion(c, user, objectId, pageURL, currentURL, newDiscussionTitle, "", allowMultipleDiscussions);
            String title;

            if (start instanceof AnnouncementsController.ThreadView)
            {
                title = "Discussion";
            }
            else
            {
                title = "Start a new discussion";
                focusId = "body";
            }

            start.setFrame(WebPartView.FrameType.NONE);
            discussionBox = new ThreadWrapper(context, title, start);
        }
        else
        {
            Announcement selected = null;

            WebPartView discussionView = null;
            HttpView respondView = null;

            if (discussionId != 0)
            {
                for (Announcement ann : announcements)
                {
                    if (ann.getRowId() == discussionId)
                    {
                        selected = ann;
                        break;
                    }
                }

                if (selected != null)
                {
                    discussionView = getDiscussion(c, selected, user);
                    discussionView.setFrame(WebPartView.FrameType.NONE);
                    if (params.get("reply") != null)
                    {
                        ((AnnouncementsController.ThreadView)discussionView).getModelBean().isResponse = true;
                        respondView = new AnnouncementsController.RespondView(c, selected, currentURL, true);
                        focusId = "body";
                    }
                }
            }

            if (discussionView != null)
                discussionBox = new ThreadWrapper(context, "Discussion", discussionView, respondView); 
        }

        ModelAndView pickerView = new PickerView(c, currentURL, announcements, null != discussionBox, allowMultipleDiscussions);
        DiscussionService.DiscussionView view = new DiscussionService.DiscussionView(pickerView);

        if (null != discussionBox)
        {
            view.addView(discussionBox);
            view.setFocusId(focusId);
        }

        return view;
    }


    public void deleteDiscussions(Container c, String identifier, User user)
    {
        Announcement[] anns = getDiscussions(c, identifier);
        for (Announcement ann : anns)
        {
            try
            {
                AnnouncementManager.deleteAnnouncement(c, ann.getRowId());
            }
            catch (SQLException x)
            {
                throw new RuntimeSQLException(x);
            }
        }
    }


    public void unlinkDiscussions(Container c, String identifier, User user)
    {
        Announcement[] anns = getDiscussions(c, identifier);
        for (Announcement ann : anns)
        {
            try
            {
                ann.setDiscussionSrcURL(null);
                AnnouncementManager.updateAnnouncement(user, ann);
            }
            catch (SQLException x)
            {
                throw new RuntimeSQLException(x);
            }
        }
    }


    public boolean hasDiscussions(Container container, String identifier)
    {
        return getDiscussions(container, identifier).length > 0;
    }


    public static class AnchorView extends HtmlView
    {
        AnchorView()
        {
            super("<a name=\"discussionArea\"></a>");
        }
    }


    public static class ThreadWrapper extends WebPartView
    {
        VBox _vbox;

        ThreadWrapper(ViewContext context, String caption, HttpView... views)
        {
            _vbox = new VBox();
            for (HttpView v : views)
                if (v != null)
                    _vbox.addView(v);
            _vbox.addView(new AnchorView());
            _vbox.setTitle(caption);
            _vbox.setFrame(WebPartView.FrameType.DIALOG);
            ActionURL closeURL = getCloseURL(context.getActionURL());
            _vbox.addObject("closeURL", closeURL);
        }

        public ThreadWrapper()
        {
            super();
        }

        public void doStartTag(Map context, PrintWriter out)
        {
            out.write("<table><tr><th valign=top width=50px><img src='" + getViewContext().getContextPath() + "/_.gif' width=50 height=1></th><td class=normal>");
        }

        protected void renderView(Object model, HttpServletRequest request, HttpServletResponse response) throws Exception
        {
            _vbox.render(request, response);
        }

        public void doEndTag(Map context, PrintWriter out)
        {
            out.write("</td></tr></table>");
        }
    }


    public static class PickerView extends JspView
    {
        public ActionURL pageURL;
        public ActionURL emailPreferencesURL;
        public ActionURL adminEmailURL;
        public ActionURL customizeURL;
        public Announcement[] announcements;
        public boolean isDiscussionVisible;
        public boolean allowMultipleDiscussions;

        PickerView(Container c, ActionURL pageURL, Announcement[] announcements, boolean isDiscussionVisible, boolean allowMultipleDiscussions)
        {
            super("/org/labkey/announcements/discussionMenu.jsp");
            setFrame(FrameType.NONE);
            this.pageURL = pageURL.clone();
            this.emailPreferencesURL = AnnouncementsController.getEmailPreferencesURL(c, pageURL);
            this.adminEmailURL = AnnouncementsController.getAdminEmailURL(c, pageURL);
            this.customizeURL = AnnouncementsController.getCustomizeURL(c, pageURL);
            this.announcements = announcements;
            this.isDiscussionVisible = isDiscussionVisible;
            this.allowMultipleDiscussions = allowMultipleDiscussions;
        }
    }


    private static ActionURL getCloseURL(ActionURL currentURL)
    {
        ActionURL closeURL = currentURL.clone();
        closeURL.deleteScopeParameters("discussion");
        closeURL.addParameter("discussion.hide", "true");
        return closeURL;
    }
}
