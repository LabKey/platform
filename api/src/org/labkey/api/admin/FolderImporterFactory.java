/*
 * Copyright (c) 2012-2016 LabKey Corporation
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
package org.labkey.api.admin;

/**
 * User: cnathe
 * Date: Jan 18, 2012
 */
public interface FolderImporterFactory
{
    int DEFAULT_PRIORITY = 50;

    FolderImporter create();

    /* priority allows importers to be ordered relative to each other in ascending order. 0 would be the highest priority */
    int getPriority();
}
