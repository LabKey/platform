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
package org.labkey.specimen.writer;

import org.labkey.api.specimen.SpecimenMigrationService;

public class SpecimenArchiveDataTypes
{
    public static final String SCHEMA_FILENAME = "specimens_metadata.xml";
    public static final String SPECIMEN_SETTINGS =  "Specimen Settings";
    public static final String SPECIMENS = SpecimenMigrationService.SPECIMENS_ARCHIVE_TYPE;

    private SpecimenArchiveDataTypes()
    {
    }
}
