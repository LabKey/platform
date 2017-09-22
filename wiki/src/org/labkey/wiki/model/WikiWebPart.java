/*
 * Copyright (c) 2007-2017 LabKey Corporation
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

package org.labkey.wiki.model;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.view.NotFoundException;

import java.util.Map;

/**
 * User: adam
 * Date: Aug 11, 2007
 * Time: 3:30:55 PM
 */
public class WikiWebPart extends BaseWikiView
{
    public WikiWebPart(int webPartId, Map<String, String> props)
    {
        super();
        _webPartId = webPartId;

        // webPartContainer and name will be null in the new webpart case
        String containerId = props.get("webPartContainer");
        Container c = (null != containerId ? ContainerManager.getForId(props.get("webPartContainer")) : getViewContext().getContainer());
        if (null == c)
            throw new NotFoundException("The requested wiki page does not exist in the specified container!");

        String name = props.get("name");
        name = (name != null) ? name : "default";

        init(c, name);

        // display edit pencil in frameless webpart
        setShowFloatingCustomBtn(true);
    }
}
