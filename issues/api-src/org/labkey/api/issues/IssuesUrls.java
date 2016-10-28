/*
 * Copyright (c) 2010-2016 LabKey Corporation
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

package org.labkey.api.issues;

import org.labkey.api.action.UrlProvider;
import org.labkey.api.data.Container;
import org.labkey.api.view.ActionURL;

/*
* User: adam
* Date: Nov 19, 2010
* Time: 9:38:34 PM
*/
public interface IssuesUrls extends UrlProvider
{
    ActionURL getDetailsURL(Container c);
    ActionURL getInsertURL(Container c, String issueDefName);
    ActionURL getListURL(Container c, String issueDefName);
}
