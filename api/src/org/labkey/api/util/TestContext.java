/*
 * Copyright (c) 2004-2016 Fred Hutchinson Cancer Research Center
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
package org.labkey.api.util;

import org.labkey.api.security.User;

import javax.servlet.http.HttpServletRequest;

/**
 * User: mbellew
 * Date: Feb 24, 2004
 * Time: 12:31:33 PM
 */
public class TestContext
{
    private static ThreadLocal<TestContext> local = new ThreadLocal<>();

    private HttpServletRequest _request;
    private User _user;


    public static TestContext get()
    {
        return local.get();
    }


    public static void setTestContext(HttpServletRequest request, User user)
    {
        TestContext t = new TestContext();
        t.setUser(user);
        t.setRequest(request);
        local.set(t);
    }


    public User getUser()
    {
        return _user;
    }


    void setUser(User user)
    {
        _user = user;
    }


    public HttpServletRequest getRequest()
    {
        return _request;
    }


    public void setRequest(HttpServletRequest request)
    {
        _request = request;
    }
}
