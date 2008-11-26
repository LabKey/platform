/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.api.study.query;

import org.labkey.api.data.*;
import org.labkey.api.exp.api.ContainerFilter;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.ChartQueryReport;
import org.labkey.api.reports.report.RReport;
import org.labkey.api.security.ACL;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayPublishService;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.RunDataTable;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ViewContext;
import org.labkey.common.util.Pair;

/**
 * User: brittp
 * Date: Jun 29, 2007
 * Time: 11:13:44 AM
 */
public class RunDataQueryView extends AssayBaseQueryView
{
    private boolean includeSubfolders;

    public RunDataQueryView(ExpProtocol protocol, ViewContext context, QuerySettings settings)
    {
        super(protocol, context, settings);
        setViewItemFilter(new ReportService.ItemFilter() {
            public boolean accept(String type, String label)
            {
                if (RReport.TYPE.equals(type)) return true;
                if (ChartQueryReport.TYPE.equals(type)) return true;
                return false;
            }
        });
    }

    public DataView createDataView()
    {
        DataView view = super.createDataView();
        view.getRenderContext().setBaseSort(new Sort(AssayService.get().getProvider(_protocol).getDataRowIdFieldKey().toString()));
        view.getDataRegion().addHiddenFormField("rowId", "" + _protocol.getRowId());
        view.getDataRegion().addHiddenFormField("returnURL", getViewContext().getActionURL().toString());
        if (showControls())
        {
            if (!AssayPublishService.get().getValidPublishTargets(getUser(), ACL.PERM_INSERT).isEmpty())
            {
                ButtonBar bbar = view.getDataRegion().getButtonBar(DataRegion.MODE_GRID);

                AssayProvider provider = AssayService.get().getProvider(_protocol);

                if (provider.allowUpload(getUser(), getContainer(), _protocol))
                {
                    ActionButton uploadRuns = new ActionButton(provider.getUploadWizardURL(getContainer(), _protocol).getLocalURIString(),
                            "Upload Runs", DataRegion.MODE_GRID, ActionButton.Action.GET);
                    bbar.add(uploadRuns);
                }

                ActionURL publishURL = AssayService.get().getProtocolURL(getContainer(), _protocol, "publishStart");
                for (Pair<String, String> param : publishURL.getParameters())
                {
                    if (!"rowId".equalsIgnoreCase(param.getKey()))
                        view.getDataRegion().addHiddenFormField(param.getKey(), param.getValue());
                }
                publishURL.deleteParameters();

                if (provider.canPublish())
                {
                    ActionButton publishButton = new ActionButton(publishURL.getLocalURIString(),
                            "Copy Selected to Study", DataRegion.MODE_GRID, ActionButton.Action.POST);
                    publishButton.setDisplayPermission(ACL.PERM_INSERT);
                    publishButton.setScript("return verifySelected(this.form, \"" + publishURL.getLocalURIString() + "\", \"post\", \"data rows\")");
                    publishButton.setActionType(ActionButton.Action.POST);

                    bbar.add(publishButton);
                }
                view.getDataRegion().setButtonBar(bbar);
            }
        }
        else
            view.getDataRegion().setButtonBar(ButtonBar.BUTTON_BAR_EMPTY);
        return view;
    }

    protected TSVGridWriter.ColumnHeaderType getColumnHeaderType()
    {
        return TSVGridWriter.ColumnHeaderType.caption;
    }

    public boolean isIncludeSubfolders()
    {
        return includeSubfolders;
    }

    public void setIncludeSubfolders(boolean includeSubfolders)
    {
        this.includeSubfolders = includeSubfolders;
    }

    @Override
    protected TableInfo createTable()
    {
        RunDataTable table = (RunDataTable)super.createTable();
        if (includeSubfolders)
            table.setContainerFilter(ContainerFilter.CURRENT_AND_SUBFOLDERS);
        return table;
    }
}
