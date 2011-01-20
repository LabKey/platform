/*
 * Copyright (c) 2011 LabKey Corporation
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

package org.labkey.api.reports.report.view;

import org.labkey.api.view.JspView;

/*
* User: adam
* Date: Jan 18, 2011
* Time: 5:20:27 PM
*/
public class AjaxScriptReportView extends JspView<ScriptReportBean>
{
    public AjaxScriptReportView(ScriptReportBean bean) throws Exception
    {
        super("/org/labkey/api/reports/report/view/ajaxScriptReportDesigner.jsp", bean);
        bean.init();
    }
}
