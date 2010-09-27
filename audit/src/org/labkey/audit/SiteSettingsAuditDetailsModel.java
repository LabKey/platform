/*
 * Copyright (c) 2008 LabKey Corporation
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

import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.settings.WriteableAppProps;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.apache.commons.lang.time.FastDateFormat;

import java.util.Map;

/*
 * User: Dave
 * Date: May 28, 2008
 * Time: 11:00:14 AM
 */

public class SiteSettingsAuditDetailsModel
{
    private AuditLogEvent _event = null;
    private Map<String,Object> _eventProps = null;
    private User _user = null;
    private FastDateFormat _dateFormat = FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss");

    public SiteSettingsAuditDetailsModel()
    {
    }

    public SiteSettingsAuditDetailsModel(AuditLogEvent event, Map<String,Object> eventProps)
    {
        assert null != event;
        assert null != eventProps;

        _event = event;
        _eventProps = eventProps;
    }

    public AuditLogEvent getEvent()
    {
        return _event;
    }

    public Map<String, Object> getEventProps()
    {
        return _eventProps;
    }

    public String getDiff()
    {
        String diff = (String)(_eventProps.get(AuditLogService.get().getPropertyURI(WriteableAppProps.AUDIT_EVENT_TYPE, WriteableAppProps.AUDIT_PROP_DIFF)));
        if(null == diff || 0 == diff.length())
            diff = "<p>No details were recorded.</p>";
        return diff;
    }

    public String getWhen()
    {
        if(null == _event)
            return "(unknown)";

        return _dateFormat.format(_event.getCreated());
    }

    public User getUser()
    {
        if(null != _user)
            return _user;
        
        if(null == _event)
            return null;

        _user = _event.getCreatedBy();
        return _user;
    }
}