/*
 * Copyright (c) 2010-2017 LabKey Corporation
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

package org.labkey.api.reports.report;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.BooleanFormat;
import org.labkey.api.data.Container;
import org.labkey.api.query.*;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.permissions.ShareReportPermission;
import org.labkey.api.reports.report.view.AjaxRunScriptReportView;
import org.labkey.api.reports.report.view.AjaxScriptReportView.Mode;
import org.labkey.api.reports.report.view.ReportQueryView;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.reports.report.view.ScriptReportBean;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.TabStripView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.ContainerUser;

import java.util.Collections;
import java.util.List;

/*
* User: adam
* Date: Dec 21, 2010
* Time: 7:57:11 PM
*/
public abstract class ScriptReport extends AbstractReport
{
    public static final String TAB_SOURCE = "Source";

    /**
     * Create the query view used to generate the result set that this report operates on.
     */
    protected QueryView createQueryView(ViewContext context, ReportDescriptor descriptor) throws Exception
    {
        final String schemaName = descriptor.getProperty(QueryParam.schemaName.toString());
        final String queryName = descriptor.getProperty(QueryParam.queryName.toString());
        final String viewName = descriptor.getProperty(QueryParam.viewName.toString());
        final String dataRegionName = descriptor.getProperty(QueryParam.dataRegionName.toString());

        if (context != null && schemaName != null)
        {
            UserSchema base = QueryService.get().getUserSchema(context.getUser(), context.getContainer(), schemaName);

            if (base != null)
            {
                QuerySettings settings = base.getSettings(context, dataRegionName);
                settings.setSchemaName(schemaName);
                settings.setQueryName(queryName);
                settings.setViewName(viewName);
                // need to reset the report id since we want to render the data grid, not the report
                settings.setReportId(null);

                UserSchema schema = base.createView(context, settings).getSchema();
                return new ReportQueryView(schema, settings);
            }
        }

        return null;
    }

    public abstract boolean supportsPipeline();

    public String getDownloadDataHelpMessage()
    {
        return "You can download the data via this link to help with the development of your script.";
    }

    // At the moment, only R reports support shared scripts
    public List<Report> getAvailableSharedScripts(ViewContext context, ScriptReportBean bean) throws Exception
    {
        return Collections.emptyList();
    }

    public @Nullable String getEditAreaSyntax()
    {
        return null;
    }

    // When creating a new script report, populate the editarea with this text
    public String getDefaultScript()
    {
        return "";
    }

    public boolean hasClientDependencies()
    {
        return false;
    }

    public @Nullable String getDesignerHelpHtml()
    {
        return null;
    }

    @Override
    public ActionURL getEditReportURL(ViewContext context)
    {
        if (canEdit(context.getUser(), context.getContainer()))
        {
            return ReportUtil.getRunReportURL(context, this, true).addParameter(TabStripView.TAB_PARAM, TAB_SOURCE);
        }
        return null;
    }

    @Override
    public HttpView getRunReportView(ViewContext context) throws Exception
    {
        String tabId = (String)context.get("tabId");

        if (null == tabId)
            tabId = context.getActionURL().getParameter("tabId");

        String webpartString = (String)context.get(Report.renderParam.reportWebPart.name());
        boolean webpart = (null != webpartString && BooleanFormat.getInstance().parseObject(webpartString));

        // Module-based reports are always read-only, but we still allow viewing the report source in the source tab.
        // if tab == "Source" then use update mode, which lets developers edit the source
        // otherwise, if we're a webpart then use view mode
        // otherwise, use viewAndUpdate, which means show the view tab first, but let developers edit the source
        Mode  mode = (TAB_SOURCE.equals(tabId) ? Mode.update : (webpart ? Mode.view : Mode.viewAndUpdate));

        return new AjaxRunScriptReportView(this, mode);
    }

    public boolean canEdit(User user, Container container, List<ValidationError> errors)
    {
        // HACK: We shouldn't be using canEdit permissions to check during view. Rather we should new up
        // a report and check using standard security.
        if (errors.isEmpty() && getDescriptor().isModuleBased())
        {
            return true;
        }

        super.canEdit(user, container, errors);

        if (errors.isEmpty())
        {
            if (user.isDeveloper())
            {
                if (isPrivate() || getDescriptor().hasCustomAccess())
                {
                    if (!container.hasPermission(user, InsertPermission.class))
                        errors.add(new SimpleValidationError("You must be in the Author role to update a private or custom script report."));
                }
            }
            else
                errors.add(new SimpleValidationError("You must be in the Developers groups to update a script report."));
        }
        return errors.isEmpty();
    }

    public boolean canShare(User user, Container container, List<ValidationError> errors)
    {
        super.canShare(user, container, errors);

        if (errors.isEmpty())
        {
            if (isPrivate())
            {
                if (user.isDeveloper())
                {
                    if (!container.hasPermission(user, ShareReportPermission.class))
                        errors.add(new SimpleValidationError("You must be in the Author role to share a private script report."));
                }
                else
                    errors.add(new SimpleValidationError("You must be in the Developers groups to share a private script report."));
            }
        }
        return errors.isEmpty();
    }

    @Override
    public boolean hasContentModified(ContainerUser context)
    {
        // Content modified if change to the "script" config property
        return hasDescriptorPropertyChanged(ScriptReportDescriptor.Prop.script.name());
    }
}
