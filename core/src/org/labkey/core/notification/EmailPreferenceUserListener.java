package org.labkey.core.notification;

import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;

import java.beans.PropertyChangeEvent;

public class EmailPreferenceUserListener implements UserManager.UserListener
{
    @Override
    public void userAddedToSite(User user)
    {
    }

    @Override
    public void userDeletedFromSite(User user)
    {
        //when user is deleted from site, remove any corresponding record from EmailPrefs table.
        EmailPreferenceConfigManager.deleteUserEmailPref(user, null);
    }

    @Override
    public void userAccountDisabled(User user)
    {
    }

    @Override
    public void userAccountEnabled(User user)
    {
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt)
    {
    }
}
