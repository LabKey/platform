/*
 * Copyright (c) 2011 LabKey Corporation
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
package org.labkey.core.admin;

import org.labkey.api.action.FormViewAction;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.NavTree;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

/**
 * User: jeckels
 * Date: Sep 23, 2011
 */
public class InstallSettingsAction extends FormViewAction<FileSettingsForm>
{
    @Override
    public void validateCommand(FileSettingsForm target, Errors errors)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ModelAndView getView(FileSettingsForm fileSettingsForm, boolean reshow, BindException errors) throws Exception
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean handlePost(FileSettingsForm fileSettingsForm, BindException errors) throws Exception
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public URLHelper getSuccessURL(FileSettingsForm fileSettingsForm)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public NavTree appendNavTrail(NavTree root)
    {
        throw new UnsupportedOperationException();
    }
}
