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

package org.labkey.api.reports.report;

/**
 * User: dax
 * Date: 12/12/13
 */

import org.labkey.api.data.Container;
import org.labkey.api.data.views.DataViewProvider;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.model.ReportPropsManager;
import org.labkey.api.thumbnail.ThumbnailService;

public class ReportThumbnailSmall extends ReportThumbnail
{
    public ReportThumbnailSmall(Container container, Report report)
    {
        super(container, report);
    }

    @Override
    public String getFilename()
    {
        return ThumbnailService.ImageType.Small.getFilename();
    }

    @Override
    public String getThumbnailType()
    {
        return (String) ReportPropsManager.get().getPropertyValue(report.getDescriptor().getEntityId(), container, "iconType");
    }

    @Override
    public boolean shouldSerialize()
    {
        String type = getThumbnailType();

        if (type == null)
            return false;

        return type.equalsIgnoreCase(DataViewProvider.EditInfo.ThumbnailType.CUSTOM.name());
    }

    @Override
    public boolean shouldDeserialize()
    {
        return shouldSerialize();
    }
}
