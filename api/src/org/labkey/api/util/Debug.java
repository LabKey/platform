/*
 * Copyright (c) 2003-2016 Fred Hutchinson Cancer Research Center
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

import java.util.HashMap;
import java.util.Map;

public class Debug
{
    public static int RELEASE = 0;
    public static int DEBUG = 1;
    public static int VERBOSE = 2;
    private static Integer iDEBUG = new Integer(DEBUG);

    private static Map<String, Integer> _map = new HashMap<>();

    static
    {
        String d = System.getProperty("debug");
        if (null == d)
            _map.put(".", iDEBUG);
        else
        {

            String [] classes = d.split(",");
            for (String aClass : classes)
                _map.put(aClass, iDEBUG);
        }
    }

    public static int getLevel(Class clss)
    {
        Integer i = _map.get(clss.getName());
        if (null != i) return i.intValue();
        i = _map.get(clss.getPackage().getName());
        if (null != i) return i.intValue();
        i = _map.get(".");
        if (null != i) return i.intValue();
        return RELEASE;
    }
}