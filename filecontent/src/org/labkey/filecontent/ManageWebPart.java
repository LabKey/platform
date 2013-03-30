/*
 * Copyright (c) 2009-2013 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.files.view.FilesWebPart;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Apr 15, 2009
 * Time: 12:47:02 PM
 */
public class ManageWebPart extends FilesWebPart
{
    public ManageWebPart(Container c)
    {
        super(c);
    }

    public ManageWebPart(Container c, String fileSet)
    {
        super(c, fileSet, null);
    }

}
