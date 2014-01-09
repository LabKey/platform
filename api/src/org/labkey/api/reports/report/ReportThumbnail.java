/*
 * Copyright (c) 2006-2014 LabKey Corporation
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

package org.labkey.api.reports.report;

import org.labkey.api.data.Container;
import org.labkey.api.reports.Report;

/**
 * User: dax
 * Date: 12/12/13
 */

/**
 * Helper for serializing/deserializing thumbnails and custom icons
 */
public abstract class ReportThumbnail
{
    final Report report;
    final Container container;

    public ReportThumbnail(Container container, Report report)
    {
        this.report = report;
        this.container = container;
    }
    public void setAutoThumbnailType()
    {
    }
    public abstract String getThumbnailType();
    public abstract String getFilename();
    public abstract boolean shouldSerialize();
    public abstract boolean shouldDeserialize();
}
