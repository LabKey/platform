/*
 * Copyright (c) 2008-2016 LabKey Corporation
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
package org.labkey.audit;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.util.DateUtil;

import java.util.Date;

/*
 * User: Dave
 * Date: May 28, 2008
 * Time: 11:00:14 AM
 */

public class SiteSettingsAuditDetailsModel
{
    private final Container _c;
    private final String _diff;
    private final User _createdBy;
    private final Date _created;

    public SiteSettingsAuditDetailsModel(Container c, String diff, User createdBy, Date created)
    {
        _c = c;
        _diff = StringUtils.isEmpty(diff) ? "<p>No details were recorded.</p>" : diff;
        _createdBy = createdBy;
        _created = created;
    }

    public String getDiff()
    {
        return _diff;
    }

    public String getWhen()
    {
        if(null == _created)
            return "(unknown)";

        return DateUtil.formatDateTime(_c, _created);
    }

    public User getCreatedBy()
    {
        return _createdBy;
    }
}