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
import org.labkey.api.query.ValidationException;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.model.ReportPropsManager;
import org.labkey.api.thumbnail.ThumbnailService;

public class ReportThumbnailLarge extends ReportThumbnail
{
    public ReportThumbnailLarge(Container container, Report report)
    {
        super(container, report);
    }

    @Override
    public String getFilename()
    {
        return ThumbnailService.ImageType.Large.getFilename();
    }

    @Override
    public String getThumbnailType()
    {
        return (String) ReportPropsManager.get().getPropertyValue(report.getDescriptor().getEntityId(), container, "thumbnailType");
    }

    @Override
    public void setAutoThumbnailType()
    {
        if (null == getThumbnailType())
        {
            try
            {
                ReportPropsManager.get().setPropertyValue(report.getDescriptor().getEntityId(), container, "thumbnailType",
                        DataViewProvider.EditInfo.ThumbnailType.AUTO.name());
            }
            catch (ValidationException e)
            {
                // it shouldn't be fatal if we couldn't roundtrip the thumbnail
            }
        }
    }

    @Override
    public boolean shouldSerialize()
    {
        String type = getThumbnailType();

        // auto-generated thumbnails may not have a thumbnail type but we still need to serialize them
        if (type == null)
            return true;

        // otherwise serialize AUTO or CUSTOM
        return !type.equalsIgnoreCase(DataViewProvider.EditInfo.ThumbnailType.NONE.name());
    }

    @Override
    public boolean shouldDeserialize()
    {
        String type = getThumbnailType();

        // we always setAutoThumbnail so by the time we get to import, we only need to check
        // for non-null values != NONE
        if (type == null)
            return false;

        return !type.equalsIgnoreCase(DataViewProvider.EditInfo.ThumbnailType.NONE.name());
    }
}
