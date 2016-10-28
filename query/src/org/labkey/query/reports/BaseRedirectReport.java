/*
 * Copyright (c) 2012-2016 LabKey Corporation
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
package org.labkey.query.reports;

import org.labkey.api.data.Container;
import org.labkey.api.query.SimpleValidationError;
import org.labkey.api.query.ValidationError;
import org.labkey.api.reports.report.RedirectReport;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.InsertPermission;

import java.util.List;

/**
 * User: kevink
 * Date: 6/21/12
 */
public abstract class BaseRedirectReport extends RedirectReport
{
    public boolean canEdit(User user, Container container, List<ValidationError> errors)
    {
        super.canEdit(user, container, errors);

        if (errors.isEmpty() && !getDescriptor().isShared())
        {
            if (!container.hasPermission(user, InsertPermission.class))
                errors.add(new SimpleValidationError("You must be in the Author role to update a private attachment report."));
        }
        return errors.isEmpty();
    }

    public boolean canDelete(User user, Container container, List<ValidationError> errors)
    {
        super.canDelete(user, container, errors);

        if (errors.isEmpty())
        {
            if (isPrivate())
            {
                if (!container.hasPermission(user, InsertPermission.class))
                    errors.add(new SimpleValidationError("You must be in the Author role to delete a private attachment report."));
            }
        }
        return errors.isEmpty();
    }
}
