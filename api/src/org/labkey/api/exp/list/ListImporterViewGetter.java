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

package org.labkey.api.exp.list;

import org.labkey.api.view.GWTView;

import java.util.Map;

/*
* User: adam
* Date: Nov 21, 2009
* Time: 11:53:16 AM
*/

// Temporary hack.  TODO: Extract GWTList module out of GWTExperiment
public class ListImporterViewGetter
{
    private static Class _clazz;

    public static void setClass(Class clazz)
    {
        _clazz = clazz;
    }

    public static GWTView getView(Map<String, String> props)
    {
        return new GWTView(_clazz, props);
    }
}
