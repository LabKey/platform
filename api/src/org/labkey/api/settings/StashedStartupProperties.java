/*
 * Copyright (c) 2022-2026 LabKey Corporation
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
package org.labkey.api.settings;

public enum StashedStartupProperties implements StartupProperty
{
    homeProjectFolderType("Home project folder type"),
    homeProjectResetPermissions("Reset the home project permissions to remove default assignments given at server install"),
    homeProjectWebparts("Semicolon-delimited list of webpart names to add to the home project"),
    siteAvailableEmailFrom("Site available from address"),
    siteAvailableEmailMessage("Site available message"),
    siteAvailableEmailSubject("Site available subject");

    private final String _description;

    StashedStartupProperties(String description)
    {
        _description = description;
    }

    @Override
    public String getDescription()
    {
        return _description;
    }
}
