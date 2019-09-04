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
package org.labkey.timeline.view;

import org.labkey.timeline.TimelineSettings;
import org.labkey.api.view.JspView;

/*
* User: Mark Igra
* Date: Jul 8, 2008
* Time: 9:47:39 AM
*/
public class TimelineView extends JspView<TimelineSettings>
{
    public TimelineView(TimelineSettings timelineSettings)
    {
        super("/org/labkey/timeline/view/timeline.jsp", timelineSettings);
        setTitle(timelineSettings.getWebPartTitle());
    }
}