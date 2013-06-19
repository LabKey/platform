/*
 * Copyright (c) 2010 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.files;

/**
 * User: klum
 * Date: Apr 23, 2010
 * Time: 3:34:02 PM
 */
public class FileContentDefaultEmailPref extends FileContentEmailPref
{
    @Override
    public String getId()
    {
        return "FileContentDefaultEmailPref";
    }

    @Override
    public String getDefaultValue()
    {
        return String.valueOf(NONE);
    }

    @Override
    public String getValue(String value, String defaultValue)
    {
        return value;
    }
}
