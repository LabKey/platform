/*
 * Copyright (c) 2026 LabKey Corporation
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
package org.labkey.search.model;

/**
 * Thrown when a Lucene index commit fails. Propagates to the outer indexer loop so that backoff and retry
 * are handled there, consistent with the pattern used by {@link DavCrawler}.
 */
public class IndexCommitException extends RuntimeException
{
    IndexCommitException(Throwable cause)
    {
        super(cause);
    }
}
