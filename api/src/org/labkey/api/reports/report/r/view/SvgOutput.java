/*
 * Copyright (c) 2012-2017 LabKey Corporation
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
package org.labkey.api.reports.report.r.view;

import org.apache.batik.transcoder.TranscoderException;
import org.apache.log4j.Logger;
import org.labkey.api.attachments.DocumentConversionService;
import org.labkey.api.reader.Readers;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.thumbnail.Thumbnail;
import org.labkey.api.thumbnail.ThumbnailOutputStream;
import org.labkey.api.thumbnail.ThumbnailService.ImageType;
import org.labkey.api.view.ViewContext;

import java.io.File;
import java.io.IOException;

/**
 * User: adam
 * Date: 6/4/12
 * Time: 3:15 PM
 */
public class SvgOutput extends HtmlOutput
{
    public static final String ID = "svgout:";

    public SvgOutput()
    {
        super(ID);
    }

    @Override
    protected String getLabel()
    {
        return "SVG output";
    }

    @Override
    public Thumbnail renderThumbnail(ViewContext context) throws IOException
    {
        DocumentConversionService svc = ServiceRegistry.get().getService(DocumentConversionService.class);

        if (null != svc)
        {
            ThumbnailOutputStream os = new ThumbnailOutputStream();

            for (File file : getFiles())
            {
                try
                {
                    svc.svgToPng(Readers.getXmlReader(file), os, ImageType.Large.getHeight());

                    return os.getThumbnail("image/png");
                }
                catch (TranscoderException e)
                {
                    Logger.getLogger(SvgOutput.class).error("Couldn't generate thumbnail", e);
                }
            }
        }
        return null;
    }
}
