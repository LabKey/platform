/*
 * Copyright (c) 2013-2019 LabKey Corporation
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
 * User: bbimber
 * Date: 2/5/12
 * Time: 8:37 PM
 */
public class URLExportScriptFactory implements ExportScriptFactory
{
    @Override
    public String getScriptType()
    {
        return "url";
    }

    @Override
    public String getMenuText()
    {
        return "Stable URL";
    }

    @Override
    public ExportScriptModel getModel(QueryView queryView)
    {
        return new URLExportScriptModel(queryView);
    }
}