/*
 * Copyright (c) 2011-2014 LabKey Corporation
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
package org.labkey.announcements.config;

import org.apache.commons.lang3.math.NumberUtils;
import org.labkey.announcements.AnnouncementsController;
import org.labkey.announcements.model.AnnouncementManager;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.message.settings.AbstractConfigTypeProvider;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;

import java.util.Set;

public class AnnouncementEmailConfig extends AbstractConfigTypeProvider
{
    public static final String TYPE = "messages";

    @Override
    public String getType()
    {
        return TYPE;
    }

    public String getName()
    {
        // appears in the config tab
        return getType();
    }

    @Override
    protected ActionURL getSetDefaultPrefURL(Container c)
    {
        return new ActionURL(AnnouncementsController.SetEmailDefault.class, c);
    }

    @Override
    protected int getDefaultEmailOption(Container c)
    {
        return AnnouncementManager.getDefaultEmailOption(c);
    }

    @Override
    public void validateCommand(ViewContext context, Errors errors)
    {
        if (!DataRegionSelection.hasSelected(context))
            errors.reject(SpringActionController.ERROR_MSG, "There are no users selected for this update.");
    }

    @Override
    public boolean handlePost(ViewContext context, BindException errors) throws Exception
    {
        Object selectedOption = context.get("selectedEmailOption");
        // Only supports container-level subscriptions for this bulk UI
        String srcIdentifier = context.getContainer().getId();

        if (selectedOption != null)
        {
            int newOption = NumberUtils.toInt((String)selectedOption);
            for (Integer selected : DataRegionSelection.getSelectedIntegers(context, true))
            {
                User projectUser = UserManager.getUser(selected);
                int currentEmailOption = AnnouncementManager.getUserEmailOption(context.getContainer(), projectUser, srcIdentifier);

                //has this projectUser's option changed? if so, update
                //creating new record in EmailPrefs table if there isn't one, or deleting if set back to folder default
                if (currentEmailOption != newOption)
                {
                    AnnouncementManager.saveEmailPreference(context.getUser(), context.getContainer(), projectUser, newOption, srcIdentifier);
                }
            }
            return true;
        }
        return false;
    }

}