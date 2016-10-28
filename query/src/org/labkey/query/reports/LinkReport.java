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
package org.labkey.query.reports;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.reports.ReportService;
import org.labkey.api.thumbnail.Thumbnail;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.ImageUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.ContainerUser;

import java.net.URL;
import java.net.UnknownHostException;

/**
 * User: kevink
 * Date: 6/21/12
 */
public class LinkReport extends BaseRedirectReport
{
    public static final String TYPE = ReportService.LINK_REPORT_TYPE;

    public String getType()
    {
        return TYPE;
    }

    public String getTypeDescription()
    {
        return "Link Report";
    }

    @Override
    public String getStaticThumbnailPath()
    {
        return "/reports/web.png";
    }

    @Override
    public Thumbnail generateThumbnail(@Nullable ViewContext context)
    {
        URL url = getURL();
        try
        {
            return ImageUtil.webThumbnail(url);
        }
        catch (UnknownHostException uhe)
        {
            Logger.getLogger(LinkReport.class).warn("Error rendering link report thumbnail: " + uhe.getMessage());
        }
        catch (Exception e)
        {
            ExceptionUtil.decorateException(e, ExceptionUtil.ExceptionInfo.ResolveURL, url.toString(), false);
            ExceptionUtil.logExceptionToMothership(null, e);
        }
        
        return null;
    }

    @Override
    public ActionURL getEditReportURL(ViewContext context)
    {
        ActionURL url = new ActionURL(ReportsController.UpdateLinkReportAction.class, context.getContainer());
        url.addParameter("reportId", getReportId().toString());

        return url;
    }

    @Override
    public boolean hasContentModified(ContainerUser context)
    {
        // Content modified if change to the link URL string property
        return hasDescriptorPropertyChanged(REDIRECT_URL);
    }
}
