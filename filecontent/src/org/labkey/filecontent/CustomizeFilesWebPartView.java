/*
 * Copyright (c) 2007 LabKey Corporation
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

package org.labkey.filecontent;

import org.labkey.api.view.JspView;
import org.labkey.api.view.Portal;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;

/**
 * Created by IntelliJ IDEA.
 * User: Mark Igra
 * Date: Jul 16, 2007
 * Time: 2:27:37 PM
 */
public class CustomizeFilesWebPartView extends JspView<FileContentController.CustomizeWebPartForm>
{
    public CustomizeFilesWebPartView(Portal.WebPart currentConfig)
    {
        super("/org/labkey/filecontent/view/customizeWebPart.jsp");
        setModelBean(new FileContentController.CustomizeWebPartForm(currentConfig));
    }
}
