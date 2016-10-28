/*
 * Copyright (c) 2016 LabKey Corporation
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
package org.labkey.issue.model;

import org.labkey.api.data.Container;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.issues.IssuesListDefProvider;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.issue.IssuesModule;
import org.labkey.issue.query.IssueDefDomainKind;

/**
 * Created by davebradlee on 8/3/16.
 */
public class GeneralIssuesListDefProvider implements IssuesListDefProvider
{
    public String getName()
    {
        return IssueDefDomainKind.NAME;
    }

    public String getLabel()
    {
        return "General Issue Tracker";
    }

    public String getDescription()
    {
        return "General purpose issue tracker";
    }

    public DomainKind getDomainKind()
    {
        return PropertyService.get().getDomainKindByName(IssueDefDomainKind.NAME);
    }

    public boolean isEnabled(Container container)
    {
        Module module = ModuleLoader.getInstance().getModule(IssuesModule.NAME);
        return container.getActiveModules().contains(module);
    }
}
