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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.report.ReportIdentifier;
import org.labkey.api.view.JspView;

/*
* User: adam
* Date: Jan 18, 2011
* Time: 5:20:27 PM
*/
public class AjaxScriptReportView extends JspView<ScriptReportBean>
{
    public enum Mode
    {
        create(true, true, false, false),
        update(true, false, false, false),
        view(false, false, true, true);

        private final boolean _showSource;
        private final boolean _showHelp;
        private final boolean _allowsMultiplePerPage;
        private final boolean _readOnly;

        Mode(boolean showSource, boolean showHelp, boolean allowsMultiplePerPage, boolean readOnly)
        {
            _showSource = showSource;
            _showHelp = showHelp;
            _allowsMultiplePerPage = allowsMultiplePerPage;
            _readOnly = readOnly;
        }

        public boolean showSource()
        {
            return _showSource;
        }

        public boolean showHelp()
        {
            return _showHelp;
        }

        public boolean allowsMultiplePerPage()
        {
            return _allowsMultiplePerPage;
        }

        public boolean isReadOnly()
        {
            return _readOnly;
        }
    }

    protected Report _report;
    protected ReportIdentifier _reportId;

    public AjaxScriptReportView(@Nullable Report report, ScriptReportBean bean, Mode mode) throws Exception
    {
        super("/org/labkey/api/reports/report/view/ajaxScriptReportDesigner.jsp", bean);

        _report = report;

        if (_report != null)
        {
            _reportId = _report.getDescriptor().getReportId();
        }

        init(bean, mode);
    }

    protected void init(ScriptReportBean bean, Mode mode) throws Exception
    {
        bean.init(mode);
    }
}
