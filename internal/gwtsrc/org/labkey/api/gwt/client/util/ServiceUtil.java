/*
 * Copyright (c) 2007-2011 LabKey Corporation
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

package org.labkey.api.gwt.client.util;

import com.google.gwt.http.client.URL;
import com.google.gwt.user.client.rpc.ServiceDefTarget;
import com.google.gwt.user.client.ui.RootPanel;

import java.util.Collections;
import java.util.Map;

/**
 * User: brittp
 * Date: Feb 2, 2007
 * Time: 11:35:54 AM
 */
public class ServiceUtil
{
    public static Object configureEndpoint(Object remoteService, String action)
    {
        return configureEndpoint(remoteService, action, null);
    }

    public static Object configureEndpoint(Object remoteService, String action, String pageflow)
    {
        return configureEndpoint(remoteService, action, pageflow, Collections.<String, String>emptyMap());
    }

    public static Object configureEndpoint(Object remoteService, String action, String pageflow, Map<String, String> urlParams)
    {
        ServiceDefTarget endpoint = (ServiceDefTarget) remoteService;
        String url;
        if (pageflow == null)
        {
            url = PropertyUtil.getRelativeURL(action);
        }
        else
        {
            url = PropertyUtil.getRelativeURL(action, pageflow);
        }
        String separator = "?";
        for (String key : urlParams.keySet())
        {
            url += separator;
            separator = "&";
            url += URL.encodeComponent(key) + "=" + URL.encodeComponent(urlParams.get(key));
        }
        endpoint.setServiceEntryPoint(url);
        return remoteService;
    }

    public static RootPanel findRootPanel(String classname)
    {
        int index = classname.indexOf(".client.");
        if (index != -1)
        {
            classname = classname.substring(0, index) + classname.substring(index + ".client.".length() - 1);
        }
        return RootPanel.get(classname + "-Root");
    }
}
