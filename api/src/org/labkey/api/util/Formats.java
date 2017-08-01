/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.settings.FolderSettingsCache;

import java.text.DecimalFormat;

/**
 * User: adam
 * Date: Jul 27, 2006
 * Time: 4:05:45 PM
 */
public class Formats
{
    public static DecimalFormat f0 = new DecimalFormat("0");
    public static DecimalFormat f1 = new DecimalFormat("0.0");
    public static DecimalFormat f2 = new DecimalFormat("0.00");
    public static DecimalFormat fv2 = new DecimalFormat("#.##");
    public static DecimalFormat f3 = new DecimalFormat("0.000");
    public static DecimalFormat fv3 = new DecimalFormat("#.###");
    public static DecimalFormat f4 = new DecimalFormat("0.0000");
    public static DecimalFormat fv4 = new DecimalFormat("#.####");
    public static DecimalFormat signf4 = new DecimalFormat("+0.0000;-0.0000");
    public static DecimalFormat percent = new DecimalFormat("0%");
    public static DecimalFormat percent1 = new DecimalFormat("0.0%");
    public static DecimalFormat percent2 = new DecimalFormat("0.00%");
    public static DecimalFormat commaf0 = new DecimalFormat("#,##0");
    public static DecimalFormat commaf3 = new DecimalFormat("#,##0.###");
    public static DecimalFormat chargeFilter = new DecimalFormat("0.#");

    public static String getDecimalFormatDocumentationURL()
    {
        return HelpTopic.getJDKJavaDocLink(DecimalFormat.class);
    }

    /** Get the default number format string to use in this Container */
    public static String getNumberFormatString(Container c)
    {
        return FolderSettingsCache.getDefaultNumberFormat(c);
    }

    /** Format number using folder-specified default pattern */
    public static String formatNumber(Container c, Number n)
    {
        String formatString = getNumberFormatString(c);

        if (null != formatString)
            return new DecimalFormat(formatString).format(n);
        else
            return n.toString();
    }
}
