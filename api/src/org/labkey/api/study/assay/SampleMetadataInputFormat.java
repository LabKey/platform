/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.api.study.assay;

/**
 * Enum to describe how sample metadata is captured during
 * data import.
 */
public enum SampleMetadataInputFormat
{
    MANUAL("Manual"),                               // form based manual entry
    FILE_BASED("File Upload (metadata only)"),           // metadata is provided from a file (separate from the run data file)
    COMBINED("Combined File Upload (metadata & run data)");     // metadata and run data are combined in a single file

    private String _label;

    SampleMetadataInputFormat(String label)
    {
        _label = label;
    }

    public String getLabel()
    {
        return _label;
    }
}
