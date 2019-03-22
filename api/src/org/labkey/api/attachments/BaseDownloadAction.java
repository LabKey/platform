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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.ExportAction;
import org.labkey.api.util.Pair;
import org.springframework.validation.BindException;

import javax.servlet.http.HttpServletResponse;

// Abstract action that downloads an attachment associated with an AttachmentParent. Modules need an action in their own controller
// that extends this class and handles security, creates the correct attachment parent, and specifies the attachment filename.
public abstract class BaseDownloadAction<FORM> extends ExportAction<FORM>
{
    @Override
    protected String getCommandClassMethodName()
    {
        return "getAttachment";
    }

    @Override
    public void export(FORM form, HttpServletResponse response, BindException errors) throws Exception
    {
        Pair<AttachmentParent, String> attachment = getAttachment(form);

        if (null != attachment)
            AttachmentService.get().download(response, attachment.first, attachment.second);
    }

    public abstract @Nullable Pair<AttachmentParent, String> getAttachment(FORM form);
}
