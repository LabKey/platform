/*
 * Copyright (c) 2014-2026 LabKey Corporation
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
package org.labkey.api.study;

// Only one option now, but keep the enum in case we add some variant in the future
public enum StudySnapshotType
{
    publish("Published", "Publish Study");

    private final String _title;
    private final String _jobDescription;

    StudySnapshotType(String title, String jobDescription)
    {
        _title = title;
        _jobDescription = jobDescription;
    }

    public String getTitle()
    {
        return _title;
    }
    public String getJobDescription()
    {
        return _jobDescription;
    }
}
