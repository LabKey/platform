/*
 * Copyright (c) 2009-2016 LabKey Corporation
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
 * Factory for generating script snippets in a given client language that know how to fetch the data currently
 * being shown by a specific {@link QueryView}.
 * User: adam
 * Date: Jan 27, 2009
 */
public interface ExportScriptFactory
{
    String getScriptType();
    String getMenuText();
    ExportScriptModel getModel(QueryView queryView);
}