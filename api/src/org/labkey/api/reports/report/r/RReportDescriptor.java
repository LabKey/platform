/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.api.reports.report.r;

import org.labkey.api.reports.report.ScriptReportDescriptor;

/**
 * User: Karl Lum
 * Date: Jul 12, 2007
 */
public class RReportDescriptor extends ScriptReportDescriptor
{
    public static final String TYPE = "rReportDescriptor";

    public enum KnitrFormat
    {
        None,
        Html,
        Markdown,
    }

    public static KnitrFormat getKnitrFormatFromString(String s)
    {
        if (s.equalsIgnoreCase(KnitrFormat.Html.name()))
            return KnitrFormat.Html;

        if (s.equalsIgnoreCase(KnitrFormat.Markdown.name()))
            return KnitrFormat.Markdown;

        return KnitrFormat.None;
    }

    public RReportDescriptor()
    {
        super(TYPE);
    }

    public RReportDescriptor(String type)
    {
        super(type);
    }
}
