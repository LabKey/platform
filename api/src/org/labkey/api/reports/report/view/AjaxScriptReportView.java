/*
 * Copyright (c) 2011-2012 LabKey Corporation
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
import org.labkey.api.security.User;
import org.labkey.api.util.UniqueID;
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
        create(true, false, false, true),
        update(true, false, false, true),
        viewAndUpdate(true, false, false, false),   // Same as update, except we display the view tab initially
        view(false, true, true, false);

        private final boolean _allowSourceAndHelp;
        private final boolean _allowMultiplePerPage;
        private final boolean _readOnly;
        private final boolean _preferSourceTab;

        Mode(boolean allowSourceAndHelp, boolean allowMultiplePerPage, boolean readOnly, boolean preferSourceTab)
        {
            _allowSourceAndHelp = allowSourceAndHelp;
            _allowMultiplePerPage = allowMultiplePerPage;
            _readOnly = readOnly;
            _preferSourceTab = preferSourceTab;
        }

        public boolean showSourceAndHelp(User user)
        {
            return _allowSourceAndHelp && user.isDeveloper();
        }

        public String getUniqueID()
        {
            // Use simple element ids for create/update (easier for saving & testing), but tack on a unique integer to
            // every id when viewing a report, since the element ids are global and multiple reports could be rendered
            // on the same page.
            return (_allowMultiplePerPage ? "_" + UniqueID.getServerSessionScopedUID() : "");
        }

        public boolean isReadOnly()
        {
            return _readOnly;
        }

        public boolean preferSourceTab()
        {
            return _preferSourceTab;
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
        bean.init(getViewContext(), mode);
    }
}
