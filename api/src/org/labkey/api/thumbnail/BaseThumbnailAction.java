/*
 * Copyright (c) 2011-2017 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.ExportAction;
import org.labkey.api.data.CacheableWriter;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.thumbnail.ThumbnailService.ImageType;
import org.labkey.api.view.UnauthorizedException;
import org.springframework.validation.BindException;

import javax.servlet.http.HttpServletResponse;
import java.util.Calendar;
import java.util.GregorianCalendar;

/**
* User: adam
* Date: 10/18/11
* Time: 9:20 AM
*/
public abstract class BaseThumbnailAction<FORM> extends ExportAction<FORM>
{
    @Override
    public void checkPermissions() throws UnauthorizedException
    {
        setUnauthorizedType(UnauthorizedException.Type.sendBasicAuth);
        super.checkPermissions();
    }

    @Override
    protected String getCommandClassMethodName()
    {
        return "getProvider";  // getProvider() method determines the form class
    }

    // Do any additional permissions checks and return the provider (or null, if no thumbnail should be sent)
    public abstract @Nullable ThumbnailProvider getProvider(FORM form) throws Exception;

    @Override
    public void export(FORM form, HttpServletResponse response, BindException errors) throws Exception
    {
        ThumbnailService svc = ServiceRegistry.get().getService(ThumbnailService.class);

        if (null == svc)
            return;

        ThumbnailProvider provider = getProvider(form);

        if (null != provider)
        {
            CacheableWriter writer = svc.getThumbnailWriter(provider, getImageType(form));

            Calendar expiration = new GregorianCalendar();
            expiration.add(Calendar.YEAR, 1);

            writer.writeToResponse(response, expiration);
        }
    }

    // Default to large thumbnails
    protected ImageType getImageType(FORM form)
    {
        return ImageType.Large;
    }
}
