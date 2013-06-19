/*
 * Copyright (c) 2009-2013 LabKey Corporation
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
package org.labkey.api.webdav;

import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;

import java.util.Date;

/**
 * User: matthewb
 * Date: Dec 24, 2008
 * Time: 8:23:36 PM
 */
public class HistoryImpl implements WebdavResolver.History
{
    int _userId;
    Date _date;
    String _message;
    String _href;

    public HistoryImpl(int userId, Date date, String message, String href)
    {
        _userId = userId;
        _date = date;
        _message = message;
        _href = href;
    }
    
    public User getUser()
    {
        return UserManager.getUser(_userId);
    }

    public Date getDate()
    {
        return _date;
    }

    public String getMessage()
    {
        return _message;
    }

    public String getHref()
    {
        return _href;
    }
}
