/*
 * Copyright (c) 2012-2014 LabKey Corporation
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
package org.labkey.api.visualization;

import org.labkey.api.thumbnail.ThumbnailProvider;

/**
 * User: adam
 * Date: 10/4/12
 * Time: 5:27 AM
 */

// Primarily a marker interface that indicates the provider generates its thumbnail from provided SVG. Used by the
// VisualizationController to generate thumbnails for time and box/scatter charts
public interface SvgThumbnailGenerator extends ThumbnailProvider
{
    public void setSvg(String svg);
}
