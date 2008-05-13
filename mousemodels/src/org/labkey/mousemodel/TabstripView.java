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
package org.labkey.mousemodel;

import org.labkey.common.util.Pair;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.ActionURL;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintWriter;


public class TabstripView extends WebPartView
{
    private Pair[] _tabs = null;
    private int _selected = -1;


    public TabstripView()
    {
        setFrame(FrameType.NONE);
    }


    public TabstripView(Pair[] tabs)
    {
        this();
        setTabs(tabs);
    }


    public Pair[] getTabs()
    {
        return _tabs;
    }

    public void setTabs(Pair[] tabs)
    {
        _tabs = tabs;
    }

    public int getSelected()
    {
        return _selected;
    }

    public void setSelected(int selected)
    {
        _selected = selected;
    }


    @Override
    public void renderView(Object model, PrintWriter out) throws IOException, ServletException
    {
        chooseSelected(getViewContext().getActionURL());
        
        out.write("<table width='100%'><tr>");
        for (int i = 0; i < _tabs.length; i++)
        {
            if (i == _selected)
            {
                out.write("<td class='ms-tabselected'>");
                out.write(_tabs[i].getKey().toString());
                out.write("</td>");
            }
            else
            {
                out.write("<td class='ms-tabinactive'>");
                out.write("<a href='");
                if (_tabs[i].getValue() instanceof ActionURL)
                    out.write(((URLHelper) _tabs[i].getValue()).getLocalURIString());
                else
                    out.write(_tabs[i].getValue().toString());
                out.write("'>");
                out.write(_tabs[i].getKey().toString());
                out.write("</a>");
                out.write("</td>");
            }
        }
        out.write("</tr></table>\n");
    }


    protected void chooseSelected(ActionURL thisurl) throws ServletException
    {
        if (_selected != -1)
            return;

        ActionURL taburl = null;
        int bestMatch = 0;

        boolean matchFlow = false;
        boolean matchAction = false;
        int maxMatchParamCount = 0;

        for (int i = 0; i < _tabs.length; i++)
        {
            Object urlObj = _tabs[i].getValue();
            if (null == urlObj)
                continue;

            try
            {
                if (urlObj instanceof String)
                    taburl = new ActionURL((String) urlObj);
                else
                    taburl = (ActionURL) urlObj;

                if (taburl.getPageFlow().equalsIgnoreCase(thisurl.getPageFlow()))
                {
                    if (!matchFlow)
                    {
                        bestMatch = i;
                        matchFlow = true;
                    }

                    if (taburl.getAction().equals(thisurl.getAction()))
                    {
                        if (!matchAction)
                        {
                            _selected = i;
                            return;
                        }

                        //UNDONE: Should match parameters, but viewURLHelper doesn't have way to enumerate them
                    }
                }
            }
            catch (Exception x)
            {
                throw new ServletException(x);
            }
        }

        _selected = bestMatch;
    }
}
