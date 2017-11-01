/*
 * Copyright (c) 2017 LabKey Corporation
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
package org.labkey.api.attachments;

import org.labkey.api.data.CacheableWriter;
import org.labkey.api.util.Pair;
import org.springframework.validation.BindException;

import javax.servlet.http.HttpServletResponse;
import java.util.Calendar;
import java.util.GregorianCalendar;

// Abstract action that renders a portal image for a portal selection pages (e.g., GEL, Argos). Modules need an action
// in their own controller that extends this class and handles security, creates the correct attachment parent, and
// specifies the image filename.
public abstract class BaseBackgroundImageAction<FORM> extends BaseDownloadAction<FORM>
{
    @Override
    public void export(FORM form, HttpServletResponse response, BindException errors) throws Exception
    {
        Pair<AttachmentParent, String> attachment = getAttachment(form);

        if (null != attachment)
        {
            CacheableWriter writer = PortalBackgroundImageCache.getImageWriter(attachment.first, attachment.second);

            if (null != writer)
            {
                // review: is this correct?
                Calendar expiration = new GregorianCalendar();
                expiration.add(Calendar.YEAR, 1);
                writer.writeToResponse(response, expiration);
            }
        }
    }
}
