/*
 * Copyright (c) 2007-2017 LabKey Corporation
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

package org.labkey.announcements.model;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.announcements.AnnouncementsController;
import org.labkey.api.announcements.DiscussionService;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.UniqueID;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFrame;
import org.labkey.api.view.WebPartView;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * User: matthewb
 * Date: Feb 6, 2007
 * Time: 12:37:45 PM
 */
public class DiscussionServiceImpl implements DiscussionService
{
    public WebPartView startDiscussion(Container c, User user, String identifier, ActionURL pageURL, URLHelper cancelURL, String title, String summary, boolean allowMultipleDiscussions)
    {
        if (!allowMultipleDiscussions)
        {
            List<AnnouncementModel> discussions = getDiscussions(c, identifier);

            if (!discussions.isEmpty())
                return getDiscussion(c, cancelURL, discussions.get(0), user); // TODO: cancelURL is probably not right
        }

        String viewTitle = "New Discussion";
        AnnouncementsController.AnnouncementForm form = new AnnouncementsController.AnnouncementForm();
        form.setUser(user);
        form.setContainer(c);
        form.set("title", title);
        form.set("discussionSrcIdentifier", identifier);
        form.set("discussionSrcURL", toSaved(pageURL));
        return new AnnouncementsController.InsertMessageView(form, viewTitle, null, false, cancelURL, true, allowMultipleDiscussions);
    }


    public static String toSaved(ActionURL url)
    {
        Container c = ContainerManager.getForPath(url.getExtraPath());
        ActionURL saveURL = url.clone();
        if (null != c)
            saveURL.setContainer(c);
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
        String id = StringUtils.strip(url.getExtraPath(), "/");
        Container c = ContainerManager.getForId(id);
        if (null != c)
            url.setContainer(c);
        return url;
    }


    public @NotNull List<AnnouncementModel> getDiscussions(Container c, String identifier)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("discussionSrcIdentifier"), identifier);
        return AnnouncementManager.getAnnouncements(c, filter, new Sort("Created"));
    }

    public @NotNull Collection<AnnouncementModel> getDiscussions(Container c, String[] identifiers)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("discussionSrcIdentifier"), Arrays.asList(identifiers), CompareType.IN);
        return AnnouncementManager.getAnnouncements(c, filter, new Sort("Created"));
    }

    public WebPartView getDiscussion(Container c, URLHelper currentURL, AnnouncementModel ann, User user)
    {
        try
        {
            // NOTE: don't pass in AnnouncementModel, it came from getBareAnnouncements()
            return new AnnouncementsController.ThreadView(c, currentURL, user, null, ann.getEntityId());
        }
        catch (ServletException x)
        {
            return new HtmlView(x.toString());
        }
    }


    public DiscussionService.DiscussionView getDiscussionArea(ViewContext context, String objectId, ActionURL pageURL, String newDiscussionTitle, boolean allowMultipleDiscussions, boolean displayFirstDiscussionByDefault)
    {
        Container c = context.getContainer();
        User user = context.getUser();
        ActionURL currentURL = context.getActionURL();

        return getDiscussionArea(c, user, currentURL, objectId, pageURL, newDiscussionTitle, allowMultipleDiscussions, displayFirstDiscussionByDefault);
    }

    public DiscussionService.DiscussionView getDiscussionArea(Container c, User user, URLHelper currentURL, String objectId, ActionURL pageURL, String newDiscussionTitle, boolean allowMultipleDiscussions, boolean displayFirstDiscussionByDefault)
    {
        if(!LookAndFeelProperties.getInstance(c).isDiscussionEnabled()){
            return null;
        }
        // get discussion parameters
        Map<String, String> params = currentURL.getScopeParameters("discussion");
        List<AnnouncementModel> discussions = getDiscussions(c, objectId);

        int discussionId = 0;
        try
        {
            String id = params.get("id");
            if (null != id)
            {
                discussionId = Integer.parseInt(id);
            }
            else if (displayFirstDiscussionByDefault && params.isEmpty() && discussions.size() > 0)
            {
                discussionId = discussions.get(0).getRowId();
            }
        }
        catch (Exception x) {/* */}
        
        // often, but not necessarily the same as pageURL, assume we want to return to current page
        URLHelper adjustedCurrentURL = currentURL.clone().deleteScopeParameters("discussion");
        if (0 != discussionId)
            adjustedCurrentURL.addParameter("discussion.id", "" + discussionId);

        ThreadWrapper discussionBox = null;
        String focusId = null;
        
        if (params.get("start") != null)
        {
            pageURL = pageURL.clone();
            // clean up discussion parameters (in case caller didn't)
            pageURL.deleteScopeParameters("discussion");

            WebPartView start = startDiscussion(c, user, objectId, pageURL, adjustedCurrentURL, newDiscussionTitle, "", allowMultipleDiscussions);
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
            discussionBox = new ThreadWrapper(adjustedCurrentURL, title, start);
        }
        else
        {
            AnnouncementModel selected = null;

            WebPartView discussionView = null;
            HttpView respondView = null;

            if (discussionId != 0)
            {
                for (AnnouncementModel ann : discussions)
                {
                    if (ann.getRowId() == discussionId)
                    {
                        selected = ann;
                        break;
                    }
                }

                if (selected != null)
                {
                    discussionView = getDiscussion(c, adjustedCurrentURL, selected, user);
                    discussionView.setFrame(WebPartView.FrameType.NONE);
                    if (params.get("reply") != null)
                    {
                        ((AnnouncementsController.ThreadView)discussionView).getModelBean().isResponse = true;
                        respondView = new AnnouncementsController.RespondView(c, selected, adjustedCurrentURL, true);
                        focusId = "body";
                    }
                }
            }

            if (discussionView != null)
                discussionBox = new ThreadWrapper(adjustedCurrentURL, "Discussion", discussionView, respondView);
        }

        if (discussionBox == null)
            discussionBox = new ThreadWrapper(currentURL, "Discussion");

        ModelAndView pickerView = new PickerView(c, discussionBox.getId(), adjustedCurrentURL, newDiscussionTitle, discussions, true, allowMultipleDiscussions, objectId);
        DiscussionService.DiscussionView view = new DiscussionService.DiscussionView(pickerView);

        view.addView(discussionBox);
        view.addClientDependencies(discussionBox.getClientDependencies());
        view.setFocusId(focusId);

        return view;
    }


    public void deleteDiscussions(Container c, User user, String... identifiers)
    {
        Collection<AnnouncementModel> discussions = getDiscussions(c, identifiers);
        for (AnnouncementModel ann : discussions)
        {
            AnnouncementManager.deleteAnnouncement(c, ann.getRowId());
        }
    }

    @Override
    public void deleteDiscussions(Container container, User user, Collection<String> identifiers)
    {
        Collection<AnnouncementModel> discussions = getDiscussions(container, identifiers.toArray(new String[identifiers.size()]));
        for (AnnouncementModel ann : discussions)
        {
            AnnouncementManager.deleteAnnouncement(container, ann.getRowId());
        }
    }

    public void unlinkDiscussions(Container c, String identifier, User user)
    {
        Collection<AnnouncementModel> discussions = getDiscussions(c, identifier);
        for (AnnouncementModel ann : discussions)
        {
            try
            {
                ann.setDiscussionSrcURL(null);
                AnnouncementManager.updateAnnouncement(user, ann, Collections.emptyList());
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }
    }


    public boolean hasDiscussions(Container container, String identifier)
    {
        return !getDiscussions(container, identifier).isEmpty();
    }


    public DiscussionService.Settings getSettings(Container container)
    {
        try
        {
            return AnnouncementManager.getMessageBoardSettings(container);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    public void setSettings(Container container, DiscussionService.Settings settings)
    {
        try
        {
            AnnouncementManager.saveMessageBoardSettings(container, settings);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
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
        String _id;
        String _class = "labkey-hidden";
        VBox _vbox;

        ThreadWrapper(URLHelper currentURL, String caption, HttpView... views)
        {
            super(FrameType.NONE);
            _vbox = new VBox();
            for (HttpView v : views)
            {
                if (v != null)
                {
                    _vbox.addView(v);
                    _class = "";
                }
            }
            _vbox.addView(new AnchorView());
            _vbox.setTitle(caption);
            _vbox.setFrame(WebPartView.FrameType.DIALOG);
            URLHelper closeURL = getCloseURL(currentURL);
            _vbox.setCloseURL(closeURL);

            _id = "discussionBox" + UniqueID.getRequestScopedUID(HttpView.currentRequest());

            for (HttpView view : views)
            {
                if (view != null)
                    addClientDependencies(view.getClientDependencies());
            }
        }

        public ThreadWrapper()
        {
            super(FrameType.NONE);
        }

        String getId()
        {
            return _id;
        }

        protected void renderView(Object model, HttpServletRequest request, HttpServletResponse response) throws Exception
        {
            _vbox.render(request, response);
        }


        @Override
        public WebPartFrame getWebPartFrame()
        {
            return new WebPartFrame()
            {
                @Override
                public void doStartTag(PrintWriter out)
                {
                    out.write("<div id=\"" + _id + "\" class=\"" + _class + "\"><table><tr><th valign=top width=50px><img src='" + getViewContext().getContextPath() + "/_.gif' width=50 height=1></th><td>");
                }

                @Override
                public void doEndTag(PrintWriter out)
                {
                    out.write("</td></tr></table></div>");
                }
            };
        }
    }


    public static class PickerView extends JspView
    {
        final public String discussionAreaId;
        final public URLHelper pageURL;
        final public ActionURL emailPreferencesURL;
        final public ActionURL adminEmailURL;
        final public ActionURL customizeURL;
        final public @NotNull List<AnnouncementModel> discussions;
        final public boolean isDiscussionVisible;
        final public boolean allowMultipleDiscussions;
        final public String title;

        PickerView(Container c, String discussionAreaId, URLHelper pageURL, String title, @NotNull List<AnnouncementModel> discussions, boolean isDiscussionVisible, boolean allowMultipleDiscussions, String objectId)
        {
            super("/org/labkey/announcements/discussionMenu.jsp");
            setFrame(FrameType.NONE);
            this.discussionAreaId = discussionAreaId;
            this.pageURL = pageURL.clone();
            this.title = title;
            this.emailPreferencesURL = AnnouncementsController.getEmailPreferencesURL(c, pageURL, objectId);
            this.adminEmailURL = AnnouncementsController.getAdminEmailURL(c, pageURL);
            this.customizeURL = AnnouncementsController.getAdminURL(c, pageURL);
            this.discussions = discussions;
            this.isDiscussionVisible = isDiscussionVisible;
            this.allowMultipleDiscussions = allowMultipleDiscussions;
        }
    }


    private static URLHelper getCloseURL(URLHelper currentURL)
    {
        URLHelper closeURL = currentURL.clone();
        closeURL.deleteScopeParameters("discussion");
        closeURL.addParameter("discussion.hide", "true");
        return closeURL;
    }
}
