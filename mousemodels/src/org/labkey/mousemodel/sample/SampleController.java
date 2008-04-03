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
package org.labkey.mousemodel.sample;

import org.apache.beehive.netui.pageflow.Forward;
import org.apache.beehive.netui.pageflow.annotations.Jpf;
import org.apache.commons.lang.StringUtils;
import static org.apache.commons.lang.StringUtils.trimToNull;
import org.apache.struts.action.*;
import org.apache.struts.upload.FormFile;
import org.apache.struts.upload.MultipartRequestHandler;
import org.labkey.api.data.*;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.sample.*;
import org.labkey.api.security.ACL;
import org.labkey.api.util.PageFlowUtil;
import static org.labkey.api.util.PageFlowUtil.filter;
import org.labkey.api.view.*;
import org.labkey.api.view.template.HomeTemplate;
import org.labkey.api.attachments.AttachmentForm;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.StrutsAttachmentFile;
import org.labkey.api.announcements.DiscussionService;
import org.labkey.mousemodel.MouseModelController;
import org.labkey.mousemodel.VelocityView;
import org.labkey.mousemodel.MouseModelController.MouseModelTemplateView;
import org.labkey.mousemodel.mouse.MouseController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.ServletException;
import java.io.PrintWriter;
import java.io.IOException;
import java.net.URI;
import java.sql.SQLException;
import java.util.Map;

@Jpf.Controller(messageBundles = {@Jpf.MessageBundle(bundlePath = "messages.Validation")})
public class SampleController extends ViewController
{
    private DataRegion getDataRegion()
    {
        DataRegion region = new DataRegion();

        region.addColumns(MouseSchema.getSample().getUserEditableColumns());
        return region;
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
    protected Forward begin(SampleForm form) throws Exception
    {
        requiresPermission(ACL.PERM_READ);

        DataRegion rgn = new DataRegion();
        rgn.setColumns(MouseSchema.getMouseSample().getColumns("MouseEntityId,LSID,SampleId,SampleType"));
        rgn.addColumn(new MouseController.TrueFalseColumn(MouseSchema.getMouseSample().getColumn("FrozenUsed"), "Used", "Unused"));
        rgn.addColumn(new MouseController.ControlColumn(MouseSchema.getMouseSample()));
        rgn.addColumns(MouseSchema.getMouseSample().getColumns("MouseNo,Sex,Weeks,Description,Fixed,Frozen,Freezer,Rack,Box,Cell"));
        rgn.getDisplayColumn(0).setVisible(false);
        rgn.getDisplayColumn(1).setVisible(false);
        rgn.setShowRecordSelectors(true);
        rgn.setFixedWidthColumns(false);
        ActionURL urlhelp = cloneActionURL();

        urlhelp.deleteParameters();
        urlhelp.setAction("details");
        rgn.getDisplayColumn(2).setURL(urlhelp.getLocalURIString() + "&LSID=${LSID}&modelId=" + MouseModelController.getModelId(form));


        urlhelp.setPageFlow("MouseModel-Mouse");
        urlhelp.setAction("details");
        rgn.getDisplayColumn(4).setURL(urlhelp.getLocalURIString() + "&entityId=${mouseEntityId}");

        ButtonBar bb = new ButtonBar()
                .add(ActionButton.BUTTON_DELETE)
                .add(new ActionButton("markUsedRows.view", "Mark Used"))
                .add(new ActionButton("reboxSamples.view?modelId=" + MouseModelController.getModelId(form), "Move a Box", DataRegion.MODE_ALL, ActionButton.Action.LINK));
        rgn.setButtonBar(bb);

        GridView gridViewSamples = new GridView(rgn);
        gridViewSamples.setFilter(new SimpleFilter("ModelId", MouseModelController.getModelId(form)));
        gridViewSamples.setSort(new Sort("+SampleId"));
        gridViewSamples.setTitle("Samples");

        VelocityView locateSampleView = new VelocityView("/org/labkey/mousemodel/locateSampleForm.vm");
        locateSampleView.addObject("modelId", MouseModelController.getModelId(form));
        locateSampleView.setTitle("Locate Sample");

        _renderInTemplate(new VBox(locateSampleView, gridViewSamples), MouseModelController.getModelId(form));

        return null;
    }

    @Jpf.Action
    protected Forward locateSample(SampleForm form) throws Exception
    {
        requiresPermission(ACL.PERM_READ);
        //Try a few strategies to locate a sample.
        //SampleId might be correct, might be partial (no model name)
        //Also include a hack for BDI bar-code style, which shows the week of the month
        //instead of the day

        Sample sample = form.getBean();
        String sampleId = sample.getSampleId();
        Sample[] samples = null;
        ActionURL url = cloneActionURL();
        url.deleteParameter("sampleId");

        if (null != sampleId)
        {
            sample = SampleManager.getSampleFromId(sampleId);
            if (null != sample)
                samples = new Sample[] { sample };
            else
                samples = SampleManager.getSampleFromBDIBarCode(sampleId);
        }
        if (null == samples || samples.length == 0)
        {
            VelocityView locateSampleView = new VelocityView("/org/labkey/mousemodel/locateSampleForm.vm");
            String message;
            if (null == sampleId)
                message = "Please enter a sample Id";
            else
                message = "Sample " + sampleId + " not found.";
            locateSampleView.addObject("message", message);
            locateSampleView.addObject("modelId", MouseModelController.getModelId(form));
            locateSampleView.setTitle("Locate Sample");

            return _renderInTemplate(locateSampleView, form);
        }
        else if (samples.length == 1)
            return new ViewForward(url.setAction("details.view").replaceParameter("LSID", samples[0].getLSID()));
        else
            return _renderInTemplate(new SampleListView(samples), form);

    }

    private static class SampleListView extends WebPartView
    {
        private Sample[] samples;
        public SampleListView(Sample[] samples)
        {
            super("Multiple samples found. Click on a sample to view.");
            this.samples = samples;
        }

        @Override
        protected void renderView(Object model, PrintWriter out) throws Exception
        {
            if (null == samples || samples.length == 0)
                out.write("No samples found.");
            else
            {
                ActionURL url = currentContext().cloneActionURL();
                url.setAction("details.view");
                out.printf("<table><tr><td class='header'>Sample</td></tr>\n");
                for (Sample samp : samples)
                {
                    url.replaceParameter("LSID", samp.getLSID());
                    out.printf("<tr><td class='ms-vb'><a href='%s'>%s<a></td></tr>", url.getLocalURIString(), samp.getSampleId());
                }
                out.printf("</table>\n");
            }
        }

    }

    @Jpf.Action
    protected Forward details(SampleForm form) throws Exception
    {
        requiresPermission(ACL.PERM_READ);

        Sample sample = form.getBean();
        String lsid = sample.getLSID();
        if (null == lsid)
        {
            MouseModel model = MouseModelController.getModel(form);
            ExpSampleSet matSource = ExperimentService.get().getSampleSet(model.getMaterialSourceLSID());
            String prefix = matSource.getMaterialLSIDPrefix();
            lsid = prefix + sample.getSampleId();
        }

        int modelId = MouseModelController.getModelId(form);
        MouseModel model = MouseModelManager.getMouseModel(modelId);
        //Reselect to get the whole thing...
        sample = SampleManager.getSample(lsid);
        if (null == sample)
        {
            VelocityView locateSampleView = new VelocityView("/org/labkey/mousemodel/locateSampleForm.vm");
            locateSampleView.addObject("message", "Sample " + lsid + " not found.");
            locateSampleView.addObject("modelId", modelId);
            locateSampleView.setTitle("Locate Sample");

            _renderInTemplate(locateSampleView, form);
            return null;
        }

        form.setBean(sample);
        DataRegion dr = getDataRegion();

        ActionURL toggleUsedURL = cloneActionURL().setAction("toggleUsed.view");
        ActionButton usedButton = new ActionButton("toggleUsed", sample.getFrozenUsed() ? "Mark Unused" : "Mark Used");
        usedButton.setURL(toggleUsedURL.getLocalURIString());
        usedButton.setActionType(ActionButton.Action.LINK);
        usedButton.setDisplayPermission(ACL.PERM_UPDATE);
        ActionButton showMouseButton = new ActionButton("showMouse", "Show Mouse");
        showMouseButton.setActionType(ActionButton.Action.LINK);

        showMouseButton.setURL(getActionURL().relativeUrl("details.view", PageFlowUtil.map("modelId", modelId, "entityId", "${organismId}"), "MouseModel-Mouse", true));
        ButtonBar bb = new ButtonBar()
                .add(ActionButton.BUTTON_SHOW_UPDATE)
                .add(ActionButton.BUTTON_SHOW_GRID)
                .add(usedButton)
                .add(showMouseButton);
        dr.setButtonBar(bb);
        DetailsView detailsView = new DetailsView(dr, form);
        detailsView.setTitle("Sample");

        DataRegion locationRegion = new DataRegion();
        locationRegion.addColumns(MouseSchema.getLocation().getColumns("Freezer,Rack,Box,Cell"));
        locationRegion.setButtonBar(ButtonBar.BUTTON_BAR_EMPTY);

        DetailsView locationView = new DetailsView(locationRegion, sample.getLSID());
        locationView.setTitle("Location");

        SlidesView slidesView = new SlidesView(model, sample, getViewContext());
        HttpView discussionView = DiscussionService.get().getDisussionArea(getViewContext(), sample.getLSID(), getViewContext().cloneActionURL(), sample.getSampleId(), true, false);

        VBox vbox = new VBox(new HBox(new HttpView[]{detailsView, locationView}), slidesView, discussionView);
        _renderInTemplate(vbox, modelId);

        return null;
    }

    @Jpf.Action
    protected Forward download(AttachmentForm form) throws IOException, ServletException, SQLException
    {
        requiresPermission(ACL.PERM_READ);

        Slide slide = SampleManager.getSlide(getContainer(), form.getEntityId());

        if (null == slide)
            HttpView.throwNotFound("Unable to find slide");

        AttachmentService.get().download(getResponse(), slide, form.getName());

        return null;
    }


    @Jpf.Action
    protected Forward toggleUsed(SampleForm form) throws Exception
    {
        requiresPermission(ACL.PERM_UPDATE);
        Sample sample = form.getBean();
        if (null == sample || null == StringUtils.trimToNull(sample.getLSID()))
            throw new IllegalArgumentException("No sample supplied");
        Sample findSample = SampleManager.getSample(sample.getLSID());
        if (null == findSample || null == StringUtils.trimToNull(findSample.getLSID()))
            throw new IllegalArgumentException("Could not find the sample " + sample.getLSID());
        sample = findSample;

        sample.setFrozenUsed(!sample.getFrozenUsed());
        SampleManager.update(getUser(), sample);

        return new ViewForward(cloneActionURL().setAction("details"));
    }

    @Jpf.Action
    protected Forward markUsedRows(SampleForm form) throws Exception
    {
        requiresPermission(ACL.PERM_UPDATE);

        ActionURL forwardUrl = cloneActionURL().setAction("begin.view");
        forwardUrl.addParameter("modelId", String.valueOf(MouseModelController.getModelId(form)));
        forwardUrl.addParameter(DataRegion.LAST_FILTER_PARAM, "true");
        ViewForward forward = new ViewForward(forwardUrl);

        String[] selectedRows = form.getSelectedRows();
        if (null == selectedRows || selectedRows.length == 0)
            return forward;

        for (String lsid : selectedRows)
        {
            Sample sample = SampleManager.getSample(lsid);
            if (null == sample || null == StringUtils.trimToNull(sample.getLSID()))
                throw new IllegalArgumentException("Could not find the sample " + lsid);

            sample.setFrozenUsed(true);
            SampleManager.update(getUser(), sample);
        }

        return forward;
    }


    @Jpf.Action
    protected Forward showUpdate(SampleForm form) throws Exception
    {
        requiresPermission(ACL.PERM_UPDATE);

        DataRegion dr = getDataRegion();
        DataColumn lsidCol = (DataColumn) dr.getDisplayColumn("LSID");
        lsidCol.setEditable(false);
        DataColumn sampleIdCol = (DataColumn) dr.getDisplayColumn("SampleId");
        sampleIdCol.setEditable(false);
        DataColumn organismIdCol = (DataColumn) dr.getDisplayColumn("OrganismId");
        organismIdCol.setEditable(false);
        UpdateView updateView = new UpdateView(dr, form, null);
        updateView.setTitle("Update Sample");
        _renderInTemplate(updateView, form);
        return null;
    }

    @Jpf.Action(validationErrorForward = @Jpf.Forward(path = "showUpdate.do", name = "validate"))
    protected Forward update(SampleForm form) throws Exception
    {
        //redundant, but defensive
        requiresPermission(ACL.PERM_UPDATE);

        form.doUpdate();
        return form.getPkForward("details.view");
    }

    @Jpf.Action(validationErrorForward = @Jpf.Forward(path = "showUpdate.do", name = "validate"))
    protected Forward delete(SampleForm form) throws Exception
    {
        requiresPermission(ACL.PERM_DELETE);
        String[] selectedRows = form.getSelectedRows();
        if (null != selectedRows)
            for (String selectedRow : selectedRows)
                SampleManager.deleteSample(selectedRow, getContainer());
        ActionURL helper = cloneActionURL();
        helper.setAction("begin.view");
        helper.replaceParameter(DataRegion.LAST_FILTER_PARAM, "1");
        URI uri = new URI(helper.getLocalURIString());
        return new Forward(uri, true);
    }

    @Jpf.Action
    protected Forward showInsertSlides(SlideForm form) throws Exception
    {
        requiresPermission(ACL.PERM_INSERT);

        HttpView insertSlideView = new VelocityView("/org/labkey/mousemodel/sample/insertSlide.vm");
        insertSlideView.addObject("form", form);
        insertSlideView.addObject("stains", MouseSchema.getStain().getSelectList());

        if (null != StringUtils.trimToNull(form.getSampleId()))
        {
            MouseModel model = MouseModelManager.getMouseModel(form.getModelId());
            Sample sample = SampleManager.getSample(form.getSampleId(), model);
            if (null != sample)
                insertSlideView = new VBox(insertSlideView, new SlidesView(model, sample, getViewContext()));
        }

        _renderInTemplate(insertSlideView, form.getModelId());

        return null;
    }

    @Jpf.Action
    protected Forward insertSlides(SlideForm form) throws Exception
    {
        requiresPermission(ACL.PERM_INSERT);

        MouseModel mouseMod = MouseModelManager.getModel(form.modelId);
        if (null == mouseMod)
        {
            form.message = "Mouse model " + form.getModelId() + " not found please conntact administrator.";
            return showInsertSlides(form);
        }

        Sample sample = SampleManager.getSample(form.getSampleId(), mouseMod);
        if (null == sample)
        {
            form.message = "Sample " + form.getSampleId() + " not found in mouse model. " + mouseMod.getName() + ". Samples must be entered in database before submitting slides";
            return showInsertSlides(form);
        }

        Slide slide = new Slide();
        slide.setNotes(form.getNotes());
        slide.setSampleLSID(sample.getLSID());
        slide.setContainer(getContainer().getId());
        slide.setStainId(form.getStainId());

        MultipartRequestHandler handler = form.getMultipartRequestHandler();
        if (null == handler)
        {
            form.message = "Attached files not found.";
            return showInsertSlides(form);
        }
        else
        {
            Map fileMap = form.getMultipartRequestHandler().getFileElements();
            FormFile[] formFiles = (FormFile[]) fileMap.values().toArray(new FormFile[fileMap.size()]);

            if (formFiles.length > 0 && StringUtils.trimToNull(formFiles[0].getFileName()) != null && formFiles[0].getFileSize() > 0)
                SampleManager.insertSlide(getUser(), slide, StrutsAttachmentFile.createList(formFiles[0]));
            else
            {
                form.message = "Attached files not found.";
                return showInsertSlides(form);
            }
        }

        ActionURL url = cloneActionURL().setAction("showInsertSlides.view")
                .addParameter("modelId", String.valueOf(form.getModelId()))
                .addParameter("sampleId", form.getSampleId())
                .addParameter("success", String.valueOf(Boolean.TRUE));

        return new ViewForward(url, true);
    }

    @Jpf.Action
    protected Forward deleteSlide(EntityForm form) throws Exception
    {
        requiresPermission(ACL.PERM_DELETE);

        Slide slide = SampleManager.getSlide(getContainer(), form.getEntityId());

        if (null == slide)
            return _renderInTemplate(new HtmlView("Slide not found"), MouseModelController.getModelId(form));

        SampleManager.deleteSlide(getContainer(), slide);

        ActionURL url = cloneActionURL();
        url.setAction("details");
        url.addParameter("LSID", slide.getSampleLSID());
        if (null != form.getModelId())
            url.replaceParameter("modelId", String.valueOf(form.getModelId()));

        return new ViewForward(url, true);
    }

    public static class EntityForm extends ViewForm
    {
        private String entityId;
        private Integer modelId;

        public String getEntityId()
        {
            return entityId;
        }

        public void setEntityId(String entityId)
        {
            this.entityId = entityId;
        }

        public Integer getModelId()
        {
            return modelId;
        }

        public void setModelId(Integer modelId)
        {
            this.modelId = modelId;
        }
    }

    @Jpf.Action
    protected Forward reboxSamples(ReboxSampleForm form) throws Exception
    {
        requiresPermission(ACL.PERM_UPDATE);

        if (null == trimToNull(form.getOldFreezer()) || null == trimToNull(form.getOldRack()))
        {
            String outputStr = "<form action=''>Fill in all fields below. All fields are case sensitive. <table><tr><td>Old Freezer</td><td><input name=oldFreezer value='%s'></td>\n" +
                    "<td>New Freezer</td><td><input name=newFreezer value='%s'></td></tr>\n" +
                    "<tr><td>Old Rack</td><td><input name=oldRack value='%s'></td><td>New Rack</td><td><input name=newRack value='%s'></td></tr>\n"+
                    "<tr><td>Old Box</td><td><input name=oldBox value='%s'></td><td>New Box</td><td><input name=newBox value='%s'></td></tr>\n" +
                    "</table><input type=image src='%s'></form>";
            HtmlView htmlView = new HtmlView("Move a box", outputStr, filter(form.getOldFreezer()), filter(form.getNewFreezer()), filter(form.getOldRack()), filter(form.getNewRack()), filter(form.getOldBox()), filter(form.getNewBox()), PageFlowUtil.buttonSrc("Submit"));
            return renderInTemplate(htmlView, getContainer(), "Move a box");
        }

        int numRows = SampleManager.moveBox(getContainer(), form.getOldFreezer(), form.getNewFreezer(), form.getOldRack(), form.getNewRack(), form.getOldBox(), form.getNewBox());
        HtmlView view = new HtmlView("Box Moved", "%d samples moved. <a href='begin.view?_lastfilter=1&modelId=%d'>View Samples</a>", numRows, MouseModelController.getModelId(form));

        _renderInTemplate(view, form);

        return null;
    }

    public static class ReboxSampleForm extends ViewForm
    {
        private String oldFreezer;
        private String oldRack;
        private String oldBox;
        private String newFreezer;
        private String newRack;
        private String newBox;
        private int modelId;

        public String getOldFreezer()
        {
            return oldFreezer;
        }

        public void setOldFreezer(String oldFreezer)
        {
            this.oldFreezer = oldFreezer;
        }

        public String getOldRack()
        {
            return oldRack;
        }

        public void setOldRack(String oldRack)
        {
            this.oldRack = oldRack;
        }

        public String getOldBox()
        {
            return oldBox;
        }

        public void setOldBox(String oldBox)
        {
            this.oldBox = oldBox;
        }

        public String getNewFreezer()
        {
            return newFreezer;
        }

        public void setNewFreezer(String newFreezer)
        {
            this.newFreezer = newFreezer;
        }

        public String getNewRack()
        {
            return newRack;
        }

        public void setNewRack(String newRack)
        {
            this.newRack = newRack;
        }

        public String getNewBox()
        {
            return newBox;
        }

        public void setNewBox(String newBox)
        {
            this.newBox = newBox;
        }

        public int getModelId()
        {
            return modelId;
        }

        public void setModelId(int modelId)
        {
            this.modelId = modelId;
        }
    }

    @Jpf.Action
    protected Forward showEnterSampleLocations(EnterSamplesForm form) throws Exception
    {
        requiresPermission(ACL.PERM_INSERT);

        VelocityView view = new VelocityView("/org/labkey/mousemodel/sample/EnterSampleLocations.vm");
        if ("true".equals(getRequest().getParameter("success")))
            view.addObject("message", "Locations entered successfully");
        view.addObject("form", form);
        MouseModel model = MouseModelController.getModel(form);
        view.addObject("modelId", model.getModelId());
        view.addObject("materialSourceLSID", model.getMaterialSourceLSID());

        _renderInTemplate(view, model.getModelId());

        return null;
    }

    @Jpf.Action(validationErrorForward = @Jpf.Forward(path = "showEnterSampleLocations.do", name = "validate"))
    protected Forward submitSampleLocations(EnterSamplesForm form) throws Exception
    {
        requiresPermission(ACL.PERM_INSERT);
        Location [] locations = form.getLocations();
        try
        {
            SampleManager.beginTransaction();
            for (int i = 0; i < locations.length; i++)
            {
                Location location = locations[i];
                String sampleId = form.getSampleIds()[i];
                if (sampleId == null || sampleId.trim().length() == 0)
                    continue;

                ExpSampleSet matSource = form.getMaterialSource();
                String sampleLsid = matSource.getMaterialLSIDPrefix() + sampleId;
                location.setSampleLSID(sampleLsid);
                Location curLocation = SampleManager.getLocation(sampleLsid);
                if (null == curLocation)
                    SampleManager.insert(getUser(), location);
                else
                    SampleManager.update(getUser(), location);

            }
            SampleManager.commitTransaction();
        }
        catch (Exception x)
        {
            SampleManager.rollbackTransaction();
            throw x;
        }


        HttpView.throwRedirect(getViewContext().getActionURL().relativeUrl("showEnterSampleLocations", "modelId=" + MouseModelController.getModelId(form) + "&success=true"));
        return null;
    }

    public static class EnterSamplesForm extends ViewForm
    {
        public static int MAX_LOCATIONS = 20;
        private ExpSampleSet materialSource;

        private Location[] locations = new Location[MAX_LOCATIONS];
        private String[] sampleIds = new String[MAX_LOCATIONS];

        public EnterSamplesForm()
        {
            for (int i = 0; i < locations.length; i++)
                locations[i] = new Location();
        }

        public void setLocations(Location[] locations)
        {
            this.locations = locations;
        }

        public Location[] getLocations()
        {
            return locations;
        }

        @Override
        public void reset(ActionMapping actionMapping, HttpServletRequest servletRequest)
        {
            super.reset(actionMapping, servletRequest);
            locations = new Location[MAX_LOCATIONS];
            for (int i = 0; i < locations.length; i++)
                locations[i] = new Location();
        }

        @Override
        public ActionErrors validate(ActionMapping actionMapping, HttpServletRequest httpServletRequest)
        {
            for (int i = 0; i < locations.length; i++)
            {
                Location location = locations[i];
                String sampleId = sampleIds[i];
                if (sampleId != null && sampleId.trim().length() > 0)
                {
                    if (location.getCell() == 0)
                        addActionError("No cell set for sample " + (i + 1));
                    if (location.getBox() == null || "".equals(location.getBox()))
                        addActionError("No box set for sample " + (i + 1));
                    if (location.getRack() == null || "".equals(location.getRack()))
                        addActionError("No rack set for sample " + (i + 1));

                    try
                    {
                        Sample samp = SampleManager.getSampleFromLocation(location.getFreezer(), location.getRack(), location.getBox(), location.getCell());
                        if (null != samp)
                            addActionError("Sample " + samp.getSampleId() + " already exists at " + location.getFreezer() + "," + location.getRack() + "," + location.getBox() + "," + location.getCell());
                    } catch (SQLException e)
                    {
                        //Skip
                    }
                }
            }

            return getActionErrors();
        }


        public void setMaterialSourceLSID(String sampleSource)
        {
            this.materialSource = ExperimentService.get().getSampleSet(sampleSource);
        }

        public String getMaterialSourceLSID()
        {
            return materialSource != null ? materialSource.getLSID() : null;
        }

        public ExpSampleSet getMaterialSource()
        {
            return materialSource;
        }

        public String getSampleId(int index)
        {
            return sampleIds[index];
        }
        
        public String[] getSampleIds()
        {
            return sampleIds;
        }

        public void setSampleIds(String[] sampleIds)
        {
            this.sampleIds = sampleIds;
        }
    }

    private Forward _renderInTemplate(HttpView view, ViewForm form) throws Exception
    {
        return _renderInTemplate(view, MouseModelController.getModelId(form));
    }

    private Forward _renderInTemplate(HttpView view, int modelId) throws Exception
    {
        MouseModelTemplateView modelTemplate = new MouseModelTemplateView(modelId, 3, view);
        HomeTemplate template = new HomeTemplate(getViewContext(), getContainer(), modelTemplate);
        includeView(template);

        return null;
    }


    public static class SlideForm extends ViewForm
    {
        private int modelId;
        private String notes;
        private String sampleId;
        private String message;
        private int stainId;
        private boolean success;

        public SlideForm()
        {
        }

        public SlideForm(MouseModel model)
        {
            this.modelId = model.getModelId();
        }
        
        public int getModelId()
        {
            return modelId;
        }

        public void setModelId(int modelId)
        {
            this.modelId = modelId;
        }

        public String getNotes()
        {
            return notes;
        }

        public void setNotes(String notes)
        {
            this.notes = notes;
        }

        public String getSampleId()
        {
            return sampleId;
        }

        public void setSampleId(String sampleId)
        {
            this.sampleId = sampleId;
        }

        public String getMessage()
        {
            return message;
        }

        public void setMessage(String message)
        {
            this.message = message;
        }

        public int getStainId()
        {
            return stainId;
        }

        public void setStainId(int stainId)
        {
            this.stainId = stainId;
        }

        public boolean isSuccess()
        {
            return success;
        }

        public void setSuccess(boolean success)
        {
            this.success = success;
        }
    }

    public static class SampleForm extends BeanViewForm<Sample>
    {
        public SampleForm()
        {
            super(Sample.class, MouseSchema.getSample());
        }
    }

    public static class SlidesView extends JspView
    {
        private SlidesView(MouseModel model, ViewContext context)
        {
            super("/org/labkey/mousemodel/sample/slides.jsp");
            addObject("modelId", model.getModelId());
            if (context.getContainer().hasPermission(context.getUser(),  ACL.PERM_DELETE))
            {
                ActionURL deleteURL = context.cloneActionURL();
                deleteURL.setPageFlow("MouseModel-Sample").setAction("deleteSlide");
                deleteURL.deleteParameters();
                deleteURL.replaceParameter("modelId", String.valueOf(model.getModelId()));
                addObject("deleteURL", deleteURL);
            }
        }

        public SlidesView(MouseModel model, Sample sample, ViewContext context) throws SQLException
        {
            this(model, context);
            SimpleFilter slideFilter = new SimpleFilter("lsid", sample.getLSID());
            Map[] slides = Table.select(MouseSchema.getMouseSlide(), Table.ALL_COLUMNS, slideFilter, null, Map.class);
            addObject("slides", slides);
            setTitle("Slides for Sample " + sample.getSampleId());
            addObject("sampleId", sample.getSampleId());
        }

        public SlidesView(MouseModel model, Mouse mouse, ViewContext context) throws SQLException
        {
            this(model, context);
            String entityId = mouse.getEntityId();
            setTitle("Slides for Mouse " + mouse.getMouseNo());
            SimpleFilter slideFilter = new SimpleFilter("mouseEntityId", entityId);
            Map[] slides = Table.select(MouseSchema.getMouseSlide(), Table.ALL_COLUMNS, slideFilter, null, Map.class);
            addObject("slides", slides);
        }
    }
}
