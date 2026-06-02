/*
 * Copyright (c) 2021-2026 LabKey Corporation
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
package org.labkey.specimen.importer;

import org.labkey.api.specimen.SpecimenSchema;

// Extracted from SpecimenImporter to ease the specimen module migration
public class ImportTypes
{
    private ImportTypes()
    {
    }

    public static final String DATETIME_TYPE = "SpecimenImporter/DateTime";
    public static final String DURATION_TYPE = "SpecimenImporter/TimeOnlyDate";
    public static final String NUMERIC_TYPE = "NUMERIC(15,4)";
    public static final String BOOLEAN_TYPE = SpecimenSchema.get().getSqlDialect().getBooleanDataType();
    public static final String BINARY_TYPE = SpecimenSchema.get().getSqlDialect().getBinaryDataType();
}
