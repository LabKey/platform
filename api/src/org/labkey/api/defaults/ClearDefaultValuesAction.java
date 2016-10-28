/*
 * Copyright (c) 2009-2016 LabKey Corporation
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

package org.labkey.api.defaults;

import org.labkey.api.exp.property.Domain;
import org.labkey.api.lists.permissions.DesignListPermission;
import org.labkey.api.security.RequiresAnyOf;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.study.permissions.DesignAssayPermission;
import org.labkey.api.view.NavTree;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

/**
 * Removes previously set default values.
 * User: brittp
 * Date: Jan 30, 2009
 */

// Any of one of these permissions is sufficient
@RequiresAnyOf({DesignListPermission.class, DesignAssayPermission.class, AdminPermission.class})
public class ClearDefaultValuesAction extends DefaultValuesAction<DomainIdForm>
{
    public ModelAndView getView(DomainIdForm domainIdForm, boolean reshow, BindException errors) throws Exception
    {
        throw new UnsupportedOperationException("ClearDefaultValuesAction is a post handler only.");
    }

    public boolean handlePost(DomainIdForm domainIdForm, BindException errors) throws Exception
    {
        Domain domain = getDomain(domainIdForm);
        DefaultValueService.get().clearDefaultValues(domainIdForm.getContainer(), domain);
        return true;
    }

    public NavTree appendNavTrail(NavTree root)
    {
        throw new UnsupportedOperationException("ClearDefaultValuesAction is a post handler only.");
    }
}