/*
 * Copyright (c) 2019 LabKey Corporation
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

public class HtmlStringBuilder implements HasHtmlString
{
    private final StringBuilder _sb = new StringBuilder();

    // Use of() factory methods
    private HtmlStringBuilder()
    {
    }

    public static HtmlStringBuilder of(String s)
    {
        return new HtmlStringBuilder().append(s);
    }

    public static HtmlStringBuilder of(HtmlString hs)
    {
        return new HtmlStringBuilder().append(hs);
    }

    public static HtmlStringBuilder of(HasHtmlString hhs)
    {
        return new HtmlStringBuilder().append(hhs);
    }

    public HtmlStringBuilder append(String s)
    {
        _sb.append(h(s));
        return this;
    }

    public HtmlStringBuilder append(HtmlString hs)
    {
        _sb.append(hs.toString());
        return this;
    }

    public HtmlStringBuilder append(HasHtmlString hhs)
    {
        _sb.append(hhs.getHtmlString());
        return this;
    }

    @Override
    public HtmlString getHtmlString()
    {
        return HtmlString.unsafe(_sb.toString());
    }

    private static String h(String s)
    {
        return PageFlowUtil.filter(s);
    }
}
