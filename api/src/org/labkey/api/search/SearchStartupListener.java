/*
 * Copyright (c) 2025-2026 LabKey Corporation
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
package org.labkey.api.search;

/**
 * Defines a listener interface for events related to the completion of search indexing startup/initialization.
 * Implementations of this interface can define specific actions to be executed once the search startup process
 * is completed during the application's startup or initialization phase.
 */
public interface SearchStartupListener
{
    String getName();

    void indexStartupComplete();
}
