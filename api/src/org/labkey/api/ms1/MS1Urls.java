/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.api.ms1;

import org.labkey.api.action.UrlProvider;
import org.labkey.api.view.ActionURL;
import org.labkey.api.data.Container;

/**
 * UrlProvider for the MS1 controller
 *
 * User: Dave
 * Date: Jan 21, 2008
 * Time: 1:24:02 PM
 */
public interface MS1Urls extends UrlProvider
{
    public ActionURL getPepSearchUrl(Container container);
    public ActionURL getPepSearchUrl(Container container, String sequence);
}
