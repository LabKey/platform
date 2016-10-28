/*
 * Copyright (c) 2011-2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.filecontent.message;

import org.apache.commons.lang3.math.NumberUtils;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.files.FileContentDefaultEmailPref;
import org.labkey.api.message.settings.AbstractConfigTypeProvider;
import org.labkey.api.notification.EmailService;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.filecontent.FileContentController;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;

import java.util.Set;

/**
 * User: klum
 * Date: Jan 19, 2011
 * Time: 3:01:52 PM
 */
public class FileEmailConfig extends AbstractConfigTypeProvider
{
    public static final String TYPE = "files";
    public static int NO_EMAIL = 512;
    public static int SHORT_DIGEST = 513;
    public static int DAILY_DIGEST = 514;

    @Override
    public String getType()
    {
        return TYPE;
    }

    @Override
    public String getName()
    {
        // appears in the config tab
        return getType();
    }

    @Override
    protected int getDefaultEmailOption(Container c)
    {
        String pref = EmailService.get().getDefaultEmailPref(c, new FileContentDefaultEmailPref());
        return NumberUtils.toInt(pref);
    }

    @Override
    protected ActionURL getSetDefaultPrefURL(Container c)
    {
        return new ActionURL(FileContentController.SetDefaultEmailPrefAction.class, c);
    }

    @Override
    public void validateCommand(ViewContext context, Errors errors)
    {
        Set<String> selected = DataRegionSelection.getSelected(context, false);

        if (selected.isEmpty())
            errors.reject(SpringActionController.ERROR_MSG, "There are no users selected for this update.");
    }

    @Override
    public boolean handlePost(ViewContext context, BindException errors) throws Exception
    {
        return false;
    }
}
