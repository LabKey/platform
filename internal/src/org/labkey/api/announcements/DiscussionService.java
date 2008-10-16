/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.api.announcements;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.view.*;
import org.labkey.api.util.URLHelper;
import org.springframework.web.servlet.ModelAndView;

/**
 * Created by IntelliJ IDEA.
 *
 * Discussion service is a wrapper for the Announcements controller used as a forum for discussing objects/pages
 *
 * CONSIDER: ideas for future extensions
 *      + understand versioning (e.g. for wiki pages)
 *      + implement a resolver interface for generating links 
 *
 * User: matthewb
 * Date: Feb 1, 2007
 * Time: 2:33:22 PM
 */
public class DiscussionService
{
    private static Service _serviceImpl = null;

    public interface Service
    {
        /**
         * @param c
         * @param user
         * @param identifier
         * @param pageURL      persistent URL to link back to original page, additional parameter discussionId will automatically be added
         * @param title
         * @param summary
         * @param allowMultipleDiscussions
         * @return WebPartView with a form to start a new discussion, will post directly to Announcements controller
         */
        public WebPartView startDiscussion(Container c, User user, String identifier, ActionURL pageURL, URLHelper cancelURL, String title, String summary, boolean allowMultipleDiscussions);

        /** show links, and forms, do it all (wrapper for other methods)
         * @param displayFirstDiscussionByDefault   if true and no discussion parameters are present, display the first
         *                                          discussion associated with this object.
         */
        public DiscussionView getDisussionArea(ViewContext context, String objectId, ActionURL pageURL, String newDiscussionTitle, boolean allowMultipleDiscussions, boolean displayFirstDiscussionByDefault);

        public DiscussionView getDisussionArea(Container c, User user, URLHelper currentURL, String objectId, ActionURL pageURL, String newDiscussionTitle, boolean allowMultipleDiscussions, boolean displayFirstDiscussionByDefault);

        public void deleteDiscussions(Container container, String identifier, User user);

        public boolean hasDiscussions(Container container, String identifier);

        public void unlinkDiscussions(Container container, String identifier, User user);
    }

    /* CONSIDER: provide for resolvers rather than (or in addition to) hardcoded url back links
    public interface Resolver
    {
        public ActionURL resolve(Container c, String id, String url)
    }
    registerResolver(String name, Resolver resolver);
    */

    public static void register(Service serviceImpl)
    {
        if (_serviceImpl != null)
            throw new IllegalStateException("Service has already been set.");
        _serviceImpl = serviceImpl;
    }

    public static Service get()
    {
        if (_serviceImpl == null)
            throw new IllegalStateException("Service has not been set.");
        return _serviceImpl;
    }

    public static class DiscussionView extends VBox
    {
        private String _focusId;

        public DiscussionView(ModelAndView... views)
        {
            super(views);
        }

        public String getFocusId()
        {
            return _focusId;
        }

        public void setFocusId(String focusId)
        {
            _focusId = focusId;
        }
    }
}
