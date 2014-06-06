/*
 * Copyright (c) 2008-2014 LabKey Corporation
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
package org.labkey.wiki;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.User;

/**
 * User: adam
 * Date: Nov 5, 2008
 * Time: 10:52:38 AM
 */
public class WikiContainerListener extends ContainerManager.AbstractContainerListener
{
    // Note: Attachments are purged by AttachmentServiceImpl.containerDeleted()
    public void containerDeleted(Container c, User user)
    {
        WikiManager.get().purgeContainer(c);
    }
}
