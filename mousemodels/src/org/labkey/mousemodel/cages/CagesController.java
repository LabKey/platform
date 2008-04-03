/*
 * Copyright (c) 2003-2005 Fred Hutchinson Cancer Research Center
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

package org.labkey.mousemodel.cages;

import org.labkey.mousemodel.MouseModelController;
import org.labkey.mousemodel.MouseModelController.MouseModelTemplateView;
import org.labkey.mousemodel.necropsy.NecropsyController;
import org.labkey.api.data.*;
import org.labkey.api.sample.Cage;
import org.labkey.api.sample.MouseSchema;
import org.labkey.api.security.ACL;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.*;
import org.labkey.api.view.template.HomeTemplate;
import org.apache.beehive.netui.pageflow.annotations.Jpf;
import org.apache.beehive.netui.pageflow.Forward;

@Jpf.Controller(messageBundles = {@Jpf.MessageBundle(bundlePath = "messages.Validation")})
public class CagesController extends ViewController
{
    private static DbSchema _dbSchema = MouseSchema.getSchema();
    private static SqlDialect _dialect = _dbSchema.getSqlDialect();
    public static TableInfo _tinfo = MouseSchema.getCage();
    public static DataRegion _region = new DataRegion();

    static
    {
        _region.addColumns(_tinfo.getUserEditableColumns());
    }

    // Uncomment this declaration to access Global.app.
    //
    //     protected global.Global globalApp;
    //

    // For an example of page flow exception handling see the example "catch" and "exception-handler"
    // annotations in {project}/WEB-INF/src/global/Global.app


    @Jpf.Action
    /**
     * This method represents the point of entry into the pageflow
     */
    protected Forward begin(CageForm form) throws Exception
    {
        requiresPermission(ACL.PERM_READ);

        //Cages
        SimpleDisplayColumn col = null;
        DataRegion drCages = new DataRegion();
        drCages.addColumns(_tinfo.getColumns("CageName,Sex"));
        drCages.getDisplayColumn(0).setWidth("80px");
        drCages.getDisplayColumn(1).setWidth("40px");
        ActionURL urlhelp = cloneActionURL();
        urlhelp.deleteParameters();
        urlhelp.setPageFlow("MouseModel-Necropsy").setAction("addTasks");
        urlhelp.replaceParameter("modelId", (String) form.get("modelId"));
        urlhelp.replaceParameter("taskType", String.valueOf(NecropsyController.TASK_TYPE_SERIAL_BLEED));

        col = new SimpleDisplayColumn(
                "<a href=\"" + urlhelp.getLocalURIString() + "&cageName=${cageName}\"><img border=0 src=\"" + PageFlowUtil.buttonSrc("Serial Bleed") + "\"></a>");
        col.setDisplayPermission(ACL.PERM_UPDATE);
        drCages.addColumn(col);

        urlhelp.replaceParameter("taskType", String.valueOf(NecropsyController.TASK_TYPE_NECROPSY));
        col = new SimpleDisplayColumn(
                "<a href=\"" + urlhelp.getLocalURIString() + "&cageName=${cageName}\"><img border=0 src=\"" + PageFlowUtil.buttonSrc("Necropsy") + "\"></a>");
        col.setDisplayPermission(ACL.PERM_UPDATE);
        drCages.addColumn(col);
        drCages.setButtonBar(ButtonBar.BUTTON_BAR_EMPTY, DataRegion.MODE_GRID);

        GridView gridViewCages = new GridView(drCages);
        gridViewCages.setTitle("Cages");
        SimpleFilter filter = new SimpleFilter("modelId", new Integer(MouseModelController.getModelId(form)));
        filter.addCondition("necropsyComplete", new Integer(0));
        gridViewCages.setFilter(filter);


        _renderInTemplate(gridViewCages, form);

        return null;
    }

    @Jpf.Action
    protected Forward details(CageForm form) throws Exception
    {
        requiresPermission(ACL.PERM_READ);

        DetailsView detailsView = new DetailsView(_region, form);
        detailsView.setTitle("Cage");
        _renderInTemplate(detailsView, form);

        return null;
    }

    @Jpf.Action
    protected Forward showInsert(CageForm form) throws Exception
    {
        requiresPermission(ACL.PERM_INSERT);

        InsertView insertView = new InsertView(_region, null);
        insertView.setTitle("Add New Cage");
        _renderInTemplate(insertView, form);
        return null;
    }

    @Jpf.Action
    protected Forward showUpdate(CageForm form) throws Exception
    {
        requiresPermission(ACL.PERM_UPDATE);

        UpdateView updateView = new UpdateView(_region, form, null);
        updateView.setTitle("Update Cage");
        _renderInTemplate(updateView, form);
        return null;
    }

    @Jpf.Action(validationErrorForward = @Jpf.Forward(path = "showInsert.do", name = "validate"))
    protected Forward insert(CageForm form) throws Exception
    {
        //redundant, but defensive
        requiresPermission(ACL.PERM_INSERT);

        form.doInsert();
        return form.getPkForward("details.view");
    }

    @Jpf.Action(validationErrorForward = @Jpf.Forward(path = "showUpdate.do", name = "validate"))
    protected Forward update(CageForm form) throws Exception
    {
        //redundant, but defensive
        requiresPermission(ACL.PERM_UPDATE);

        form.doUpdate();
        return form.getPkForward("details.view");
    }

    @Jpf.Action(validationErrorForward = @Jpf.Forward(path = "showUpdate.do", name = "validate"))
    protected Forward delete(CageForm form) throws Exception
    {
        //redundant, but defensive
        requiresPermission(ACL.PERM_DELETE);

        form.doDelete();
        return form.getPkForward("begin.view");
    }

    private void _renderInTemplate(HttpView view, CageForm form) throws Exception
    {
        MouseModelTemplateView modelTemplate = new MouseModelTemplateView(MouseModelController.getModelId(form), 2, view);
        NavTrailConfig trailConfig = new NavTrailConfig(getViewContext()).setTitle("Cages");
        HomeTemplate template = new HomeTemplate(getViewContext(), getContainer(), modelTemplate, trailConfig);
        includeView(template);
    }

    public static class CageForm extends BeanViewForm
    {
        public CageForm()
        {
            super(Cage.class, CagesController._tinfo);
        }
    }

}
