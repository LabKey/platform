/*
 * Copyright (c) 2004-2008 Fred Hutchinson Cancer Research Center
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
package org.labkey.api.view;

import org.apache.beehive.netui.pageflow.Forward;
import org.labkey.api.data.Container;

import java.net.URISyntaxException;
import java.net.URI;


public class ViewForward extends Forward
{
    ActionURL _url;


    public ViewForward(ActionURL url)
    {
        this(url, true);
    }


    public ViewForward(ActionURL url, boolean redirect)
    {
        super(_toString(url, redirect), redirect);
        _url = url;
    }


    private static String _toString(ActionURL url, boolean redirect)
    {
        //noinspection PointlessBooleanExpression
        boolean asForward = redirect==false;
        String s;
        s = url.getLocalURIString(asForward);
        return s;
    }


    @Deprecated
    public ViewForward(String pageFlow, String action, Container c)
    {
        this(pageFlow, action, c.getPath());
    }


    @Deprecated
    public ViewForward(String pageFlow, String action, String extraPath)
    {
        this(new ActionURL(pageFlow, action, extraPath), true);
    }


    public ViewForward(String str) throws URISyntaxException
    {
        super(new URI(str), true);
    }


    public String toString()
    {
        return String.valueOf(_url);
    }
}