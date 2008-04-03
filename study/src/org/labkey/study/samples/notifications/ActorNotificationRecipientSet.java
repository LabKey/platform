package org.labkey.study.samples.notifications;

import org.labkey.study.model.SampleRequestActor;
import org.labkey.study.model.Site;
import org.labkey.study.model.StudyManager;
import org.labkey.study.SampleManager;
import org.labkey.api.data.Container;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.HttpView;
import org.labkey.api.security.User;

import java.sql.SQLException;

/**
 * User: brittp
* Date: May 4, 2007
* Time: 3:36:19 PM
*/
public class ActorNotificationRecipientSet extends NotificationRecipientSet
{
    private SampleRequestActor _actor;
    private Site _site;

    public ActorNotificationRecipientSet(SampleRequestActor actor, Site site)
    {
        super(init(actor, site));
        _actor = actor;
        _site = site;
    }

    public SampleRequestActor getActor()
    {
        return _actor;
    }

    public Site getSite()
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

    public static ActorNotificationRecipientSet getFromFormValue(Container container, String formValue) throws SQLException
    {
        String[] ids = formValue.split(",");
        int actorId = Integer.parseInt(ids[0]);
        int siteId = Integer.parseInt(ids[1]);
        SampleRequestActor actor = SampleManager.getInstance().getRequirementsProvider().getActor(container, actorId);
        Site site = siteId >= 0 ? StudyManager.getInstance().getSite(container, siteId) : null;
        return new ActorNotificationRecipientSet(actor, site);
    }

    public String textLink(String text, String href)
    {
        return "[<a href=\"" + PageFlowUtil.filter(href) + "\">" + text + "</a>]";
    }

    public String getConfigureEmailsLinkHTML()
    {
        String configureMembersURL = "showGroupMembers.view?id=" + getActor().getRowId();
        if (getSite() != null)
            configureMembersURL += "&siteId=" + getSite().getRowId();
        configureMembersURL += "&returnUrl=" + PageFlowUtil.encode(HttpView.currentContext().getActionURL().getLocalURIString());
        return textLink("Configure Addresses", configureMembersURL);
    }
}
