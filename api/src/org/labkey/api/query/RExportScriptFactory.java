/*
 * Copyright (c) 2009-2014 LabKey Corporation
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
package org.labkey.api.query;

/**
 * User: adam
 * Date: Jan 27, 2009
 * Time: 2:34:48 PM
 */
public class RExportScriptFactory implements ExportScriptFactory
{
    @Override
    public String getScriptType()
    {
        return "r";
    }

    @Override
    public String getMenuText()
    {
        return "R";
    }

    @Override
    public ExportScriptModel getModel(QueryView queryView)
    {
        return new RExportScriptModel(queryView);
    }
}


