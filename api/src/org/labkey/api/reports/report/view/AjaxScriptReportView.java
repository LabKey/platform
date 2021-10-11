/*
 * Copyright (c) 2011-2019 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.moduleeditor.api.ModuleEditorService;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.report.ModuleReportDescriptor;
import org.labkey.api.reports.report.ReportIdentifier;
import org.labkey.api.reports.report.ReportUrls;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.HtmlStringBuilder;
import org.labkey.api.util.Pair;
import org.labkey.api.util.UniqueID;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.ViewContext;

import java.io.File;
import java.util.Map;

import static org.labkey.api.util.PageFlowUtil.urlProvider;

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

        public boolean showSourceAndHelp(ViewContext context)
        {
            //TODO: should this check engine.isSandboxed?
            return _allowSourceAndHelp && context.getUser().isAnalyst();
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

    protected final Report _report;
    protected final ReportIdentifier _reportId;

    public AjaxScriptReportView(@Nullable Report report, ScriptReportDesignBean bean, Mode mode) throws Exception
    {
        super("/org/labkey/api/reports/report/view/ajaxScriptReportDesigner.jsp", bean);

        _report = report;
        _reportId = null==report ? null : _report.getDescriptor().getReportId();

        init(bean, mode);

        /* add warnings to editor */
        Report r = bean.getReport(bean.getViewContext());
        if (null != r)
        {
            // module based report warning
            if (r.getDescriptor().isModuleBased() && r.canEdit(bean.getUser(), bean.getContainer()))
            {
                ModuleReportDescriptor mrd = (ModuleReportDescriptor) r.getDescriptor();
                org.labkey.api.module.Module m = mrd.getModule();
                File f = ModuleEditorService.get().getFileForModuleResource(m, mrd.getSourceFile().getPath());
                if (null != f)
                {
                    HtmlStringBuilder moduleWarning = HtmlStringBuilder.of()
                            .append("This report is defined in the '" + m.getName() + "' module in directory '" + f.getParent() + "'.")
                            .append(HtmlString.BR)
                            .append("Changes to this report will be reflected in all usages across different folders on the server.")
                            .append(HtmlString.BR);
                    bean.addWarning(moduleWarning.getHtmlString());
                }
            }

            // external editor warning
            Pair<ActionURL, Map<String, Object>> externalEditorSettings = urlProvider(ReportUrls.class).urlAjaxExternalEditScriptReport(getViewContext(), r);
            if (null != externalEditorSettings)
            {
                Map<String, Object> externalConfig = externalEditorSettings.getValue();
                if (!StringUtils.isBlank((String) externalConfig.get("warningMsg")))
                {
                    // This seems very round about, can't AjaxScriptReportView do this?
                    bean.addWarning((String) externalConfig.get("warningMsg"));
                }
            }
        }
    }

    protected void init(ScriptReportDesignBean bean, Mode mode) throws Exception
    {
        bean.init(getViewContext(), mode);
    }
}
