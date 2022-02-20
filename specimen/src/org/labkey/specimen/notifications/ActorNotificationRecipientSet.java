/*
 * Copyright (c) 2014-2019 LabKey Corporation
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

package org.labkey.specimen.notifications;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.specimen.location.LocationImpl;
import org.labkey.api.specimen.location.LocationManager;
import org.labkey.api.specimen.model.SpecimenRequestActor;
import org.labkey.api.specimen.requirements.SpecimenRequestRequirementProvider;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.HtmlStringBuilder;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.SafeToRender;
import org.labkey.api.view.ActionURL;
import org.labkey.specimen.actions.ShowGroupMembersAction;

/**
 * User: brittp
 * Date: May 4, 2007
 * Time: 3:36:19 PM
 */
public class ActorNotificationRecipientSet extends NotificationRecipientSet
{
    private final SpecimenRequestActor _actor;
    private final LocationImpl _location;

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

    @Override
    public String getShortRecipientDescription()
    {
        StringBuilder shortDesc = new StringBuilder();
        shortDesc.append(getActor().getLabel());
        if (getLocation() != null)
            shortDesc.append(", ").append(getLocation().getDisplayName());
        return shortDesc.toString();
    }

    @Override
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
        SpecimenRequestActor actor = SpecimenRequestRequirementProvider.get().getActor(container, actorId);
        LocationImpl location = locationId >= 0 ? LocationManager.get().getLocation(container, locationId) : null;
        return new ActorNotificationRecipientSet(actor, location);
    }

    private HtmlString getConfigureEmailsLinkHTML(ActionURL currentUrl)
    {
        ActionURL url = ShowGroupMembersAction.getShowGroupMembersURL(
            getActor().getContainer(),
            getActor().getRowId(),
            getLocation() != null ? getLocation().getRowId() : null,
            currentUrl
        );
        return PageFlowUtil.link("Configure Addresses").href(url).getHtmlString();
    }

    public SafeToRender getHtmlDescriptionAndLink(boolean hasEmailAddresses, ActionURL currentUrl)
    {
        HtmlStringBuilder builder = HtmlStringBuilder.of(getShortRecipientDescription());
        if (hasEmailAddresses)
            builder.append(HtmlString.unsafe(PageFlowUtil.helpPopup("Group Members", getEmailAddressesAsString("<br>") + "<br>" +
                getConfigureEmailsLinkHTML(currentUrl), true)));
        else
            builder.append(" ").append(getConfigureEmailsLinkHTML(currentUrl));
        return builder;
    }
}
