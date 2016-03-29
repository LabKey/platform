/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
package org.labkey.api.ehr.dataentry;

import org.labkey.api.ehr.security.EHRRequestPendingInsertPermission;
import org.labkey.api.module.Module;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * User: bimber
 * Date: 4/27/13
 * Time: 12:45 PM
 */
public class RequestForm extends AbstractDataEntryForm
{
    protected RequestForm(DataEntryFormContext ctx, Module owner, String name, String label, String category, List<FormSection> sections)
    {
        super(ctx, owner, name, label, category, sections);
        setJavascriptClass("EHR.panel.RequestDataEntryPanel");
        setStoreCollectionClass("EHR.data.RequestStoreCollection");

        for (FormSection s : getFormSections())
        {
            s.addConfigSource("Request");
        }
    }

    public static RequestForm create(DataEntryFormContext ctx, Module owner, String category, String name, String label, List<FormSection> formSections)
    {
        List<FormSection> sections = new ArrayList<FormSection>();
        sections.add(new RequestFormSection());
        sections.add(new AnimalDetailsFormSection());
        sections.addAll(formSections);

        return new RequestForm(ctx, owner, name, label, category, sections);
    }

    @Override
    protected List<String> getButtonConfigs()
    {
        List<String> defaultButtons = new ArrayList<String>();
        defaultButtons.add("DISCARD");
        defaultButtons.add("REQUEST");
        //defaultButtons.add("APPROVE");

        return defaultButtons;
    }

    @Override
    protected List<String> getMoreActionButtonConfigs()
    {
        List<String> ret = new ArrayList<>();
        ret.add("COPY_REQUEST");
        ret.add("VALIDATEALL");

        return ret;
    }

    @Override
    protected List<Class<? extends Permission>> getAvailabilityPermissions()
    {
        return Collections.<Class<? extends Permission>>singletonList(EHRRequestPendingInsertPermission.class);
    }
}
