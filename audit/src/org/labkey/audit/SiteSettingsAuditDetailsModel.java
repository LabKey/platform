/*
 * Copyright (c) 2008-2013 LabKey Corporation
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

import org.apache.commons.lang3.time.FastDateFormat;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.security.User;
import org.labkey.api.settings.WriteableAppProps;

import java.util.Date;
import java.util.Map;

/*
 * User: Dave
 * Date: May 28, 2008
 * Time: 11:00:14 AM
 */

public class SiteSettingsAuditDetailsModel
{
    private User _user = null;
    private FastDateFormat _dateFormat = FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss");
    private String _diff;
    private User _createdBy;
    private Date _created;

    public SiteSettingsAuditDetailsModel(String diff, User createdBy, Date created)
    {
        _diff = diff;
        _createdBy = createdBy;
        _created = created;
    }

    public String getDiff()
    {
        if(null == _diff || 0 == _diff.length())
            _diff = "<p>No details were recorded.</p>";
        return _diff;
    }

    public String getWhen()
    {
        if(null == _created)
            return "(unknown)";

        return _dateFormat.format(_created);
    }

    public User getUser()
    {
        if(null != _user)
            return _user;
        
        if(null == _createdBy)
            return null;

        _user = _createdBy;
        return _user;
    }
}