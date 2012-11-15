/*
 * Copyright (c) 2007-2012 LabKey Corporation
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

package org.labkey.study.samples.notifications;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.study.Site;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.HttpView;
import org.labkey.study.SampleManager;
import org.labkey.study.model.SampleRequestActor;
import org.labkey.study.model.SiteImpl;
import org.labkey.study.model.StudyManager;

/**
 * User: brittp
* Date: May 4, 2007
* Time: 3:36:19 PM
*/
public class ActorNotificationRecipientSet extends NotificationRecipientSet
{
    private SampleRequestActor _actor;
    private SiteImpl _site;

    public ActorNotificationRecipientSet(SampleRequestActor actor, SiteImpl site)
    {
        super(init(actor, site));
        _actor = actor;
        _site = site;
    }

    public SampleRequestActor getActor()
    {
        return _actor;
    }

    public SiteImpl getSite()
    {
        return _site;
    }

    private static String[] init(SampleRequestActor actor, Site site)
    {
        User[] users = actor.getMembers(site);
        String[] addresses = new String[users.length];
        for (int i = 0; i < users.length; i++)
            addresses[i] = users[i].getEmail();
        return addresses;
    }

    public String getShortRecipientDescription()
    {
        StringBuilder shortDesc = new StringBuilder();
        shortDesc.append(getActor().getLabel());
        if (getSite() != null)
            shortDesc.append(", ").append(getSite().getDisplayName());
        return shortDesc.toString();
    }

    public String getLongRecipientDescription()
    {
        StringBuilder emailList = new StringBuilder(getShortRecipientDescription());
        emailList.append(": ");
        emailList.append(getEmailAddresses(", "));
        return emailList.toString();
    }

    public String getFormValue()
    {
        return getActor().getRowId() + "," + (getSite() != null ? getSite().getRowId() : "-1");
    }

    public static ActorNotificationRecipientSet getFromFormValue(Container container, String formValue)
    {
        String[] ids = formValue.split(",");
        int actorId = Integer.parseInt(ids[0]);
        int siteId = Integer.parseInt(ids[1]);
        SampleRequestActor actor = SampleManager.getInstance().getRequirementsProvider().getActor(container, actorId);
        SiteImpl site = siteId >= 0 ? StudyManager.getInstance().getSite(container, siteId) : null;
        return new ActorNotificationRecipientSet(actor, site);
    }

    public String getConfigureEmailsLinkHTML()
    {
        String configureMembersURL = "showGroupMembers.view?id=" + getActor().getRowId();
        if (getSite() != null)
            configureMembersURL += "&siteId=" + getSite().getRowId();
        configureMembersURL += "&returnUrl=" + PageFlowUtil.encode(HttpView.currentContext().getActionURL().getLocalURIString());
        return PageFlowUtil.textLink("Configure Addresses", configureMembersURL);
    }
}
