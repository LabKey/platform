/*
 * Copyright (c) 2017 LabKey Corporation
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
package org.labkey.api.module;

/**
 * Created by adam on 5/27/2017.
 */
public enum TomcatVersion
{
    UNKNOWN(false, null),
    TOMCAT_7(true, "getMaxActive"),
    TOMCAT_8(true, "getMaxTotal"),
    TOMCAT_9(true, "getMaxTotal");

    private final boolean _supported;
    private final String _maxTotalMethodName;

    TomcatVersion(boolean supported, String maxTotalMethodName)
    {
        _supported = supported;
        _maxTotalMethodName = maxTotalMethodName;
    }

    public boolean isSupported()
    {
        return _supported;
    }

    public String getMaxTotalMethodName()
    {
        return _maxTotalMethodName;
    }

    public static TomcatVersion get(int majorVersion)
    {
        switch (majorVersion)
        {
            case (7): return TomcatVersion.TOMCAT_7;
            case (8): return TomcatVersion.TOMCAT_8;
            case (9): return TomcatVersion.TOMCAT_9;
            default: return TomcatVersion.UNKNOWN;
        }
    }
}
