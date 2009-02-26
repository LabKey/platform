/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.api.data;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * User: jgarms
 * Date: Jan 14, 2009
 */
public class QcUtil
{
    private static final String[] QC_STATES = new String[] {"Q", "N"};

    private static final String Q_TEXT = "This value has been flagged as failing QC";
    private static final String N_TEXT = "This value is missing";

    private QcUtil() {}

    public static Set<String> getQcValues(Container c)
    {
        assert c != null : "Attempt to get QC values without a container";
        return new HashSet<String>(Arrays.asList(QC_STATES));
    }

    /**
     * Allows nulls and ""
     */
    public static boolean isValidQcValue(String value, Container c)
    {
        if (value == null || "".equals(value))
            return true;
        return isQcValue(value, c);
    }

    public static boolean isQcValue(String value, Container c)
    {
        return getQcValues(c).contains(value);
    }

    public static String getQcLabel(String qcValue)
    {
        if ("N".equals(qcValue))
            return N_TEXT;
        if ("Q".equals(qcValue))
            return Q_TEXT;
        return "";
    }
}
