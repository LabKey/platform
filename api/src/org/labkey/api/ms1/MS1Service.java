/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.api.ms1;

import org.labkey.api.data.TableInfo;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;

/**
 * MS1 Module Service
 *
 * User: Dave
 * Date: Oct 26, 2007
 * Time: 10:26:10 AM
 */
public interface MS1Service
{
    public static final String DB_SCHEMA_NAME = "ms1";
    public static final String PUBLIC_SCHEMA_NAME = "ms1";

    public enum Tables
    {
        Features,
        Files,
        Scans,
        PeakFamilies,
        PeaksToFamilies,
        Peaks,
        Software,
        SoftwareParams,
        Calibrations;
        
        public String getFullName()
        {
            return DB_SCHEMA_NAME + "." + this.name();
        }
    }

    TableInfo createFeaturesTableInfo(User user, Container container);
    TableInfo createFeaturesTableInfo(User user, Container container, boolean includePepFk);
}
