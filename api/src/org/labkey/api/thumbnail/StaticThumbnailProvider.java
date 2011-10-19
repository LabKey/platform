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
package org.labkey.api.thumbnail;

/**
 * User: adam
 * Date: 10/7/11
 * Time: 10:55 PM
 */

// Implement this to provide a standard thumbnail, typically an image shared by all objects of the same type. These
// thumbnails are cached in memory (using the cache key), but if not cached the service will block, waiting for the
// provider to return the thumbnail, so they must be retrieved quickly (pulled from the webapp or module resources).
public interface StaticThumbnailProvider
{
    Thumbnail getStaticThumbnail();
    String getStaticThumbnailCacheKey();
}
