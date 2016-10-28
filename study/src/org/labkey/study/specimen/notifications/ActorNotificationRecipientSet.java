/*
 * Copyright (c) 2007-2016 LabKey Corporation
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

package org.labkey.study.specimen.notifications;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.study.SpecimenManager;
import org.labkey.study.controllers.specimen.ShowGroupMembersAction;
import org.labkey.study.model.SpecimenRequestActor;
import org.labkey.study.model.LocationImpl;
import org.labkey.study.model.StudyManager;

/**
 * User: brittp
* Date: May 4, 2007
* Time: 3:36:19 PM
*/
public class ActorNotificationRecipientSet extends NotificationRecipientSet
{
    private SpecimenRequestActor _actor;
    private LocationImpl _location;

    public ActorNotificationRecipientSet(SpecimenRequestActor actor, LocationImpl location)
    {
        super();
        _actor = actor;
        _location = location;

        User[] users = actor.getMembers(location);
        String[] addresses = new String[users.length];
        boolean[] addressInactive = new boolean[users.length];
        for (int i = 0; i < users.length; i++)
        {
            addresses[i] = users[i].getEmail();
            addressInactive[i] = !users[i].isActive();
        }

        setEmailAddresses(addresses, addressInactive);
    }

    public SpecimenRequestActor getActor()
    {
        return _actor;
    }

    public LocationImpl getLocation()
    {
        return _location;
    }

    public String getShortRecipientDescription()
    {
        StringBuilder shortDesc = new StringBuilder();
        shortDesc.append(getActor().getLabel());
        if (getLocation() != null)
            shortDesc.append(", ").append(getLocation().getDisplayName());
        return shortDesc.toString();
    }

    public String getLongRecipientDescription()
    {
        StringBuilder emailList = new StringBuilder(getShortRecipientDescription());
        emailList.append(": ");
        emailList.append(getEmailAddressesAsString(", "));
        return emailList.toString();
    }

    public String getFormValue()
    {
        return getActor().getRowId() + "," + (getLocation() != null ? getLocation().getRowId() : "-1");
    }

    public static ActorNotificationRecipientSet getFromFormValue(Container container, String formValue)
    {
        String[] ids = formValue.split(",");
        int actorId = Integer.parseInt(ids[0]);
        int locationId = Integer.parseInt(ids[1]);
        SpecimenRequestActor actor = SpecimenManager.getInstance().getRequirementsProvider().getActor(container, actorId);
        LocationImpl location = locationId >= 0 ? StudyManager.getInstance().getLocation(container, locationId) : null;
        return new ActorNotificationRecipientSet(actor, location);
    }

    public String getConfigureEmailsLinkHTML()
    {
        URLHelper url = new ActionURL(ShowGroupMembersAction.class, getActor().getContainer());
        url.addParameter("id", Integer.toString(getActor().getRowId()));
        if (getLocation() != null)
            url.addParameter("locationId", Integer.toString(getLocation().getRowId()));
        url.addParameter("returnUrl", HttpView.currentContext().getActionURL().getLocalURIString());
        return PageFlowUtil.textLink("Configure Addresses", url);
    }

    public String getHtmlDescriptionAndLink(boolean hasEmailAddresses)
    {
        StringBuilder stringBuilder = new StringBuilder(PageFlowUtil.filter(getShortRecipientDescription()));
        if (hasEmailAddresses)
            stringBuilder.append(PageFlowUtil.helpPopup("Group Members", getEmailAddressesAsString("<br>") + "<br>" +
                    getConfigureEmailsLinkHTML(), true));
        else
            stringBuilder.append(" " + getConfigureEmailsLinkHTML());
        return stringBuilder.toString();
    }
}
