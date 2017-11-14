/*
 * Copyright (c) 2011 LabKey Corporation
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
 * Indicates that something about the full-text search engine or indexer is messed up.
 * User: adam
 * Date: Mar 10, 2011
 */

import org.labkey.api.util.MinorConfigurationException;

public class SearchMisconfiguredException extends MinorConfigurationException
{
    public SearchMisconfiguredException()
    {
        super("The search index is misconfigured. An administrator will need to correct this via the full-text search configuration page.");
    }
}
