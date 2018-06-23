/*
 * Copyright (c) 2007-2014 LabKey Corporation
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

import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.URL;
import com.google.gwt.user.client.rpc.RpcRequestBuilder;
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
    public static Object configureEndpoint(Object remoteService, String actionName)
    {
        return configureEndpoint(remoteService, actionName, null);
    }

    public static Object configureEndpoint(Object remoteService, String actionName, String controllerName)
    {
        return configureEndpoint(remoteService, actionName, controllerName, Collections.<String, String>emptyMap());
    }

    public static Object configureEndpoint(Object remoteService, String actionName, String controllerName, Map<String, String> urlParams)
    {
        ServiceDefTarget endpoint = (ServiceDefTarget) remoteService;

        String url;
        if (controllerName == null)
        {
            url = PropertyUtil.getRelativeURL(actionName);
        }
        else
        {
            url = PropertyUtil.getRelativeURL(actionName, controllerName);
        }
        String separator = "?";
        for (String key : urlParams.keySet())
        {
            url += separator;
            separator = "&";
            //issue 14006: changed encodeComponent to encodePathSegment, b/c the former will convert spaces to '+'
            url += URL.encodePathSegment(key) + "=" + URL.encodePathSegment(urlParams.get(key));
        }
        endpoint.setServiceEntryPoint(url);

        RpcRequestBuilder rpc = new RpcRequestBuilder()
        {
            @Override
            protected void doFinish(RequestBuilder rb)
            {
                rb.setHeader("X-LABKEY-CSRF",getCsrfToken());
                super.doFinish(rb);
            }
        };
        endpoint.setRpcRequestBuilder(rpc);

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

    public static native String getCsrfToken() /*-{
        return $wnd.LABKEY.CSRF;
    }-*/;
}