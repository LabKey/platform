/*
 * Copyright (c) 2004-2008 Fred Hutchinson Cancer Research Center
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

package org.labkey.mousemodel.necropsy;

import org.labkey.mousemodel.MouseModelController;
import org.labkey.mousemodel.VelocityView;
import org.labkey.mousemodel.MouseModelController.MouseModelForm;
import org.labkey.mousemodel.MouseModelController.MouseModelTemplateView;
import org.apache.beehive.netui.pageflow.Forward;
import org.apache.beehive.netui.pageflow.annotations.Jpf;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.FastDateFormat;
import org.apache.log4j.Logger;
import org.apache.struts.action.ActionError;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.upload.FormFile;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.StrutsAttachmentFile;
import org.labkey.api.data.*;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.sample.*;
import org.labkey.api.security.ACL;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.common.util.Pair;
import org.labkey.api.view.*;
import org.labkey.api.view.template.HomeTemplate;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.Writer;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Pattern;

@Jpf.Controller(messageBundles = {@Jpf.MessageBundle(bundlePath = "messages.Validation")})
public class NecropsyController extends ViewController
{
    private static int _firstSampleId = 800;
    private static int _firstFinalBleedSampleId = 900;
    private static int _firstSerialBleedSampleId = 950;
    private static int _lastSampleId = 999;
    private static Logger _log = Logger.getLogger(NecropsyController.class);

    public final static int NUMBER_STYLE_MANUAL = 0;
    public final static int NUMBER_STYLE_BARCODE = 1;
    public final static Pattern MANUAL_SAMPLE_NUM_PATTERN = Pattern.compile("\\d\\d\\d[a-z]?");

    public static final int TASK_TYPE_NECROPSY = 1;
    public static final int TASK_TYPE_FINAL_BLEED = 2;
    public static final int TASK_TYPE_SERIAL_BLEED = 3;

    private static final int PREPARATION_ID_FROZEN = 1;
//    private static final int PREPARATION_ID_FIXED=2;

    private static final int MAX_PHOTOS = 10;

    //  private Cage _cage = null;

    protected static String formatDate()
    {
        return formatDate(new Date());
    }


    protected static String formatDate(Date date)
    {
        return FastDateFormat.getInstance("MM/dd/yy").format(date);
    }


    @Jpf.Action
    /**
     * This method represents the point of entry into the pageflow
     */
    protected Forward begin(final MouseModelForm form) throws Exception
    {
        requiresPermission(ACL.PERM_READ);

        VelocityView formView = new VelocityView("/org/labkey/mousemodel/necropsy/startNecropsyForm.vm");
        formView.addObject("modelId", form.getTypedValue("modelId"));
        formView.addObject("action", "addCageNecropsy.post");
        formView.addObject("collectionDate", formatDate());
        formView.setTitle("Add Forms");

        DataRegion drTasks = new DataRegion();
        drTasks.setShowRecordSelectors(true);
        ButtonBar bb = new ButtonBar();
        ActionButton abDelete = new ActionButton("deleteTasks.post", "Delete Forms");
        bb.add(abDelete);
        drTasks.setButtonBar(bb, DataRegion.MODE_GRID);
        DataColumn dataCol = new DataColumn(MouseSchema.getMouseTask().getColumn("TaskTypeId"))
        {
            public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
            {
                int modelId;
                try
                {
                    modelId = MouseModelController.getModelId(form);
                }
                catch(ServletException x)
                {
                    throw new RuntimeException(x);
                }
                int taskType = ((Integer) ctx.get("taskTypeId"));
                switch (taskType)
                {
                    case TASK_TYPE_NECROPSY:
                        out.write("<a href='showNecropsyForm.view?collectionDateString=");
                        out.write(PageFlowUtil.filter(ConvertUtils.convert(ctx.get("taskDate"))));
                        out.write("&mouseEntityId=");
                        out.write(ctx.get("mouseEntityId").toString());
                        out.write("&modelId=" + modelId);
                        if (null != ctx.get("taskId"))
                        {
                            out.write("&taskId=");
                            out.write(((Integer) ctx.get("taskId")).toString());
                        }
                        out.write("'>Necropsy</a>");
                        break;
                    case TASK_TYPE_FINAL_BLEED:
                    case TASK_TYPE_SERIAL_BLEED:
                        out.write("<a href='showBleedForm.view?&collectionDateString=");
                        out.write(PageFlowUtil.filter(ConvertUtils.convert(ctx.get("taskDate"))));
                        out.write("&mouseEntityId=");
                        out.write(ctx.get("mouseEntityId").toString());
                        out.write("&modelId=" + modelId);
                        if (null != ctx.get("taskId"))
                        {
                            out.write("&taskId=");
                            out.write(((Integer) ctx.get("taskId")).toString());
                        }
                        if (taskType == TASK_TYPE_FINAL_BLEED)
                            out.write("&finalBleed=1'>Final Bleed</a>");
                        else
                            out.write("&finalBleed=0'>Serial Bleed</a>");
                        break;
                    default:
                        break;
                }
            }

        };
        dataCol.setCaption("Form Type");
        drTasks.addColumn(dataCol);
        drTasks.addColumns(MouseSchema.getMouseTask().getColumns("mouseEntityId,taskDate,modelId"));
        ((DataColumn) drTasks.getDisplayColumn(1)).setCaption("Mouse");
        drTasks.getDisplayColumn(3).setVisible(false);

        SimpleFilter filter = new SimpleFilter("Complete", Boolean.FALSE);
        filter.addCondition("modelId", MouseModelController.getModelId(form));
        GridView gridViewTasks = new GridView(drTasks);
        gridViewTasks.setFilter(filter);
        gridViewTasks.setTitle("Pending Forms");
        gridViewTasks.setSort(new Sort("taskTypeId,MouseEntityID/MouseNo"));

        HBox box = new HBox(new HttpView[]{gridViewTasks, formView,});
        _renderInTemplate(box, form.getContainer(), MouseModelController.getModelId(form));

        return null;
    }

    @Jpf.Action
    protected Forward deleteTasks(TaskForm form) throws Exception
    {
        //redundant, but defensive
        requiresPermission(ACL.PERM_DELETE);

        form.doDelete();
        return form.getForward("begin", new Pair("modelId", new Integer(MouseModelController.getModelId(form))), true);
    }

    @Jpf.Action
    protected Forward showNecropsyForm(NecropsyForm form) throws Exception
    {
        requiresPermission(ACL.PERM_INSERT);

        VelocityView necropsyView = new VelocityView("/org/labkey/mousemodel/necropsy/Necropsy.vm");
        Mouse mouse = form.getMouse(); //May be null

        necropsyView.addObject("form", form);
        necropsyView.addObject("mouse", mouse);
        necropsyView.addObject("samples", form.getSamples());
//        NamedObjectList objects = MouseSchema.sampleType.getSelectList();
        necropsyView.addObject("sampleTypes", MouseSchema.getSampleType().getSelectList());
        necropsyView.addObject("collectionDate", formatDate(form.getCollectionDate()));
        necropsyView.addObject("taskId", form.getTaskId());
        int modelId = MouseModelController.getModelId(form);
        necropsyView.addObject("modelId", modelId);
        necropsyView.addObject("nextSampleId", SampleManager.getNextSampleId(form.getCollectionDate(), _firstSampleId, _firstFinalBleedSampleId));
        _renderInTemplate(necropsyView, form.getContainer(), modelId);
        return null;
    }

    @Jpf.Action
    protected Forward showNecropsyPhotoForm(NecropsyPhotoForm form) throws Exception
    {
        requiresPermission(ACL.PERM_INSERT);

        VelocityView photoForm = new VelocityView("/org/labkey/mousemodel/necropsy/necropsyPhotoForm.vm");
        int modelId = MouseModelController.getModelId(form);
        photoForm.addObject("modelId", new Integer(modelId));
        photoForm.setTitle("Enter necropsy photos");

        _renderInTemplate(photoForm, form.getContainer(), modelId);

        return null;
    }

    @Jpf.Action
    protected Forward showBleedForm(BleedForm form) throws Exception
    {
        requiresPermission(ACL.PERM_INSERT);

        VelocityView bleedView = new VelocityView("/org/labkey/mousemodel/necropsy/Bleed.vm");
        Mouse mouse = null;
        if (null != form.getMouseEntityId())
            mouse = MouseModelManager.getMouse(form.getMouseEntityId());

        
        //Hack to get bleed types in here. Should be in separate tables
        String[] bleedTypes = {"B0 (pre-treatment)",
                "B1 (4 weeks treatment)\n",
                "B2 (8 weeks treatment)\n",
                "B3 (12 weeks treatment)\n",
                "B4 (terminal bleed)\n",
                "B4 (16 weeks treatment)\n",
                "Post-treatment\n",
                "Pre-treatment\n",
                "Terminal bleed "};
        bleedView.addObject("form", form);
        bleedView.addObject("bleedTypes", bleedTypes);
        bleedView.addObject("mouse", mouse);
        bleedView.addObject("samples", form.getSamples());
        bleedView.addObject("locations", form.getLocations());
        bleedView.addObject("taskId", form.getTaskId());
        Date collectionDate = form.getCollectionDate();
        if (null == collectionDate)
            collectionDate = new Date();

        bleedView.addObject("collectionDate", formatDate(collectionDate));

        if (form.isFinalBleed() || form.isUrineForm())
            bleedView.addObject("nextSampleId", SampleManager.getNextSampleId(collectionDate, _firstFinalBleedSampleId, _firstSerialBleedSampleId));
        else
            bleedView.addObject("nextSampleId", SampleManager.getNextSampleId(collectionDate, _firstSerialBleedSampleId, _lastSampleId));

        _renderInTemplate(bleedView, form.getContainer(), MouseModelController.getModelId(form));
        return null;
    }

    @Jpf.Action(validationErrorForward = @Jpf.Forward(path = "showBleedForm.do", name = "validate"))
    protected Forward submitBleed(BleedForm form) throws Exception
    {
        requiresPermission(ACL.PERM_INSERT);

        String samples[] = form.getSamples();

        Mouse mouse = form.getMouse();
        MouseModel model = MouseModelManager.getModel(mouse.getModelId());
        ExpSampleSet matSource = ExperimentService.get().getSampleSet(model.getMaterialSourceLSID());

        Date necropsyDate = form.getCollectionDate();
        MouseModelManager.beginTransaction();

        String samplePrefix = "";
        if (form.getNumberStyle() == NUMBER_STYLE_MANUAL)
            samplePrefix = SampleManager.formatSampleDate(necropsyDate) + "-";

        int sampleType = form.isUrineForm() ? SampleManager.getUrineSampleType() : SampleManager.getPlasmaSampleType();
        try
        {
            for (int i = 0; i < samples.length; i++)
            {
                if (samples[i] != null && !"".equals(samples[i].trim()))
                {
                    Sample sample = new Sample();
                    sample.setOrganismId(mouse.getEntityId());
                    sample.setCollectionDate(new java.sql.Timestamp(form.getCollectionDate().getTime()));
                    sample.setDescription(form.getDescription());
                    sample.setSampleTypeId(sampleType);
                    sample.setContainer(getContainer().getId());
                    sample.setSampleId(samplePrefix + samples[i]);
                    sample.setLSID(matSource.getMaterialLSIDPrefix() + sample.getSampleId());
                    sample.setFrozen(true);
                    sample.setFixed(false);
                    //If samples are deleted through data controller materials might be out of synch.
                    //Deal with that...
                    ExpMaterial mat = ExperimentService.get().getExpMaterial(sample.getLSID());
                    if (null != mat)
                        ExperimentService.get().deleteMaterialByRowIds(getContainer(), mat.getRowId());
                    SampleManager.insert(form.getUser(), sample);

                    Location location = form.getLocations()[i];
                    if (!PageFlowUtil.empty(location.getFreezer()))
                    {
                        location.setSampleLSID(sample.getLSID());
                        SampleManager.insert(getUser(), location);
                    }
                }
            }

            if (form.isFinalBleed())
            {
                mouse.setBleedOutComplete(true);
                MouseModelManager.updateMouse(getUser(), mouse);
            }

            //TODO: Get rid of table usage. Should hide in manager
            if (null != form.getTaskId())
            {
                Map m = new HashMap();
                m.put("Complete", Boolean.TRUE);
                Table.update(form.getUser(), MouseSchema.getMouseTask(), m, form.getTaskId(), null);
            }
        }
        catch (Exception x)
        {
            MouseModelManager.rollbackTransaction();
            throw x;
        }

        MouseModelManager.commitTransaction();

        ActionURL urlhelp = cloneActionURL();
        urlhelp.setPageFlow("MouseModel-Necropsy").setAction("begin.view");
        urlhelp.deleteParameters();
        urlhelp.replaceParameter("modelId", String.valueOf(MouseModelController.getModelId(form)));
        return new ViewForward(urlhelp);
    }


    @Jpf.Action
    protected Forward submitNecropsyPhotos(NecropsyPhotoForm form) throws Exception
    {
        String[] mouseNumbers = form.getMouseNo();
        FormFile[] formFiles = form.getPhoto();

        for (int i = 0; i < MAX_PHOTOS; i++)
        {
            if (null == mouseNumbers[i] || mouseNumbers[i].length() == 0)
                continue;
            Mouse mouse = MouseModelManager.getMouse(getContainer(), mouseNumbers[i]);
            if (null == mouse)
            {
                return form.getForward("showNecropsyPhotoForm", new Pair[]
                        {
                                new Pair("modelId", form.getModelId()),
                                new Pair("message", "Could not find mouse " + mouseNumbers[i] + ". Some photos not saved.")
                        }, true);
            }

            AttachmentService.get().addAttachments(form.getUser(), mouse, StrutsAttachmentFile.createList(formFiles[i]));
        }

        return form.getForward("showNecropsyPhotoForm", new Pair[]
                {
                        new Pair("modelId", form.getModelId()),
                        new Pair("message", "Photos uploaded successfully")
                }, true);

    }

    @Jpf.Action(validationErrorForward = @Jpf.Forward(path = "begin.do", name = "validate"))
    protected Forward addTasks(CageNecropsyForm form) throws Exception
    {
        requiresPermission(ACL.PERM_INSERT);

        Cage cage = form.getCage();

        SimpleFilter filter = new SimpleFilter("cageId", new Integer(cage.getCageId()));
        Mouse[] mice = (Mouse[]) Table.select(MouseSchema.getMouse(), Table.ALL_COLUMNS, filter, null, Mouse.class);

        MouseSchema.getSchema().getScope().beginTransaction();
        Map m = new HashMap();
        m.put("taskDate", form.getCollectionDate());
        m.put("modelId", form.getModelId());
        m.put("container", form.getContainer().getId());
        try
        {
            for (int i = 0; i < mice.length; i++)
            {
                m.put("MouseEntityId", mice[i].getEntityId());
                if (form.getTaskType() == TASK_TYPE_NECROPSY)
                {
                    m.put("TaskTypeId", new Integer(TASK_TYPE_NECROPSY));
                    Table.insert(form.getUser(), MouseSchema.getMouseTask(), m);
                    m.put("TaskTypeId", new Integer(TASK_TYPE_FINAL_BLEED));
                    Table.insert(form.getUser(), MouseSchema.getMouseTask(), m);
                }
                else
                {
                    m.put("TaskTypeId", new Integer(TASK_TYPE_SERIAL_BLEED));
                    Table.insert(form.getUser(), MouseSchema.getMouseTask(), m);
                }
            }
        }
        catch (Exception x)
        {
            MouseSchema.getSchema().getScope().rollbackTransaction();
            throw x;
        }
        MouseSchema.getSchema().getScope().commitTransaction();

        return form.getForward("begin", new Pair("modelId", form.getModelId()), true);
    }


    @Jpf.Action(validationErrorForward = @Jpf.Forward(path = "showNecropsyForm.do", name = "validate"))
    protected Forward submitNecropsy(NecropsyForm form) throws Exception
    {
        requiresPermission(ACL.PERM_INSERT);

        NecropsySample samples[] = form.getSamples();
        //Expand Sample List for sub-samples (aliquots essentially)
        List sampleList = new ArrayList();
        for (int i = 0; i < samples.length; i++)
        {
            NecropsySample necropsySample = samples[i];
            if (necropsySample.hasSubSamples())
            {
                NecropsySample[] subSamples = necropsySample.getSubSamples();
                for (int j = 0; j < subSamples.length; j++)
                    sampleList.add(subSamples[j]);
                //If sample is fixed store that under a different sample ID.
                if (necropsySample.getFixed())
                {
                    necropsySample.setFrozen(false);
                    sampleList.add(necropsySample);
                }
            }
            else
                sampleList.add(necropsySample);
        }
        samples = (NecropsySample[]) sampleList.toArray(new NecropsySample[sampleList.size()]);

        MouseModel mouseModel = MouseModelManager.getMouseModel(MouseModelController.getModelId(form));
        ExpSampleSet matSource = ExperimentService.get().getSampleSet(mouseModel.getMaterialSourceLSID());

        Mouse mouse = form.getMouse();

        Date necropsyDate = form.getCollectionDate();
        SampleManager.beginTransaction();
        //String samplePrefix = _mouseModel.getName() + "-" + _sampleDateFormat.format(necropsyDate) + "-";
        String samplePrefix = "";
        if (form.getNumberStyle() == NUMBER_STYLE_MANUAL)
            samplePrefix = SampleManager.formatSampleDate(necropsyDate) + "-";
        try
        {
            for (int i = 0; i < samples.length; i++)
            {
                if (samples[i].getSampleId() != null)
                {
                    Sample sample = new Sample();
                    sample.setOrganismId(mouse.getEntityId());
                    sample.setCollectionDate(new java.sql.Timestamp(form.getCollectionDate().getTime()));
                    sample.setDescription(samples[i].getDescription());
                    sample.setSampleTypeId(samples[i].getTissueId());
                    sample.setSampleId(samplePrefix + samples[i].getSampleId());
                    sample.setLSID(matSource.getMaterialLSIDPrefix() + sample.getSampleId());
                    sample.setContainer(form.getContainer().getId());
                    sample.setFrozen(samples[i].getFrozen());
                    sample.setFixed(samples[i].getFixed());
                    //If samples are deleted through data controller materials might be out of synch.
                    //Deal with that...
                    ExpMaterial mat = ExperimentService.get().getExpMaterial(sample.getLSID());
                    if (null != mat)
                        ExperimentService.get().deleteMaterialByRowIds(getContainer(), mat.getRowId());
                    SampleManager.insert(getUser(), sample);


                    if (samples[i].getCell() > 0 && samples[i].getFreezer() != null && !"".equals(samples[i].getFreezer().trim()))
                    {
                        Location location = new Location();
                        location.setSampleLSID(sample.getLSID());
                        location.setCell(samples[i].getCell());
                        location.setBox(samples[i].getBox());
                        location.setFreezer(samples[i].getFreezer());
                        location.setRack(samples[i].getRack());
                        SampleManager.insert(getUser(), location);
                    }
                }
            }

            mouse.setNecropsyComplete(true);
            mouse.setNecropsyAppearance(form.getAppearance());
            mouse.setNecropsyGrossFindings(form.getGrossFindings());
            MouseModelManager.updateMouse(getUser(), mouse);

            if (null != form.getTaskId())
            {
                Map m = new HashMap();
                m.put("Complete", Boolean.TRUE);
                Table.update(form.getUser(), MouseSchema.getMouseTask(), m, form.getTaskId(), null);
            }
        }
        catch (Exception x)
        {
            SampleManager.rollbackTransaction();
            throw x;
        }

        SampleManager.commitTransaction();

        return form.getForward("begin", new Pair("modelId", new Integer(MouseModelController.getModelId(form))), true);
    }

    private void _renderInTemplate(HttpView view, Container c, int modelId) throws Exception
    {
        MouseModelTemplateView tv = new MouseModelTemplateView(modelId, 4, view);
        HomeTemplate template = new HomeTemplate(getViewContext(), getContainer(), tv);
        includeView(template);
    }


    public static class NecropsySample
    {
        private String _sampleId;
        private Integer _tissueId;
        private boolean _fix;
        private boolean _frozen;
        private String _description;
        private String _aliquots;
        private String _box;
        private String _rack;
        private String _freezer; //Should be a drop-down. Make sure we can list this
        private int _cell;


        public String getSampleId()
        {
            return _sampleId;
        }

        public void setSampleId(String sampleId)
        {
            _sampleId = sampleId;
        }

        public Integer getTissueId()
        {
            return _tissueId;
        }

        public void setTissueId(Integer tissueId)
        {
            _tissueId = tissueId;
        }

        public boolean getFixed()
        {
            return _fix;
        }

        public void setFixed(boolean fix)
        {
            _fix = fix;
        }

        public boolean getFrozen()
        {
            return _frozen;
        }

        public void setFrozen(boolean frozen)
        {
            _frozen = frozen;
        }

        public String getDescription()
        {
            return _description;
        }

        public void setDescription(String description)
        {
            _description = description;
        }

        public String getAliquots()
        {
            return _aliquots;
        }

        public void setAliquots(String _aliquots)
        {
            this._aliquots = _aliquots;
        }

        public boolean hasSubSamples()
        {
            return _aliquots != null;
        }

        /**
         * Parse a string of the form a,c-e,j and return an array
         * of strings for each character in the set..
         *
         * @return
         * @throws IllegalArgumentException
         */
        private char[] subSampleIds() throws IllegalArgumentException
        {
            StringBuffer sb = new StringBuffer();
            if (null == _aliquots)
                return null;

            String[] commaSeps = _aliquots.split(",");
            for (int i = 0; i < commaSeps.length; i++)
            {
                String str = commaSeps[i].trim();
                if ("".equals(str))
                    continue;

                if (str.length() == 1)
                {
                    char c = str.charAt(0);
                    if (!Character.isLetter(c))
                        throw new IllegalArgumentException("Bad aliquot: " + str);
                    else
                        sb.append(c);
                }
                else if (str.matches("[a-z]-[a-z]"))
                {
                    char first = str.charAt(0);
                    char last = str.charAt(2);
                    if (first > last)
                    {
                        char temp = first;
                        first = last;
                        last = temp;
                    }
                    for (char c = first; c <= last; c++)
                        sb.append(c);
                }
                else
                    throw new IllegalArgumentException("Bad Aliquot: " + str);
            }

            if (sb.length() == 0)
                return null;

            char[] chars = new char[sb.length()];
            sb.getChars(0, sb.length(), chars, 0);

            return chars;
        }

        public NecropsySample[] getSubSamples()
        {
            char[] subIds = subSampleIds();
            if (null == subIds)
                return null;
            NecropsySample[] samples = new NecropsySample[subIds.length];
            for (int i = 0; i < subIds.length; i++)
            {
                char subId = subIds[i];
                NecropsySample sample = new NecropsySample();
                sample.setDescription(_description);
                //These are always frozen. ONly main sample is fixed
                sample.setFixed(false);
                sample.setFrozen(_frozen);
                sample.setTissueId(_tissueId);
                sample.setSampleId(_sampleId + subId);
                samples[i] = sample;
            }
            return samples;
        }

        public String getBox()
        {
            return _box;
        }

        public void setBox(String _box)
        {
            this._box = _box;
        }

        public String getRack()
        {
            return _rack;
        }

        public void setRack(String _rack)
        {
            this._rack = _rack;
        }

        public String getFreezer()
        {
            return _freezer;
        }

        public void setFreezer(String freezer)
        {
            this._freezer = freezer;
        }

        public Integer getCell()
        {
            return _cell;
        }

        public void setCell(Integer location)
        {
            this._cell = location == null ? 0 : location;
        }
    }

    public static class TaskForm extends TableViewForm
    {
        public TaskForm()
        {
            super(MouseSchema.getMouseTask());
        }
    }

    public static class NecropsyPhotoForm extends ViewForm
    {
        private Integer _modelId;
        private String[] _mouseNo = new String[MAX_PHOTOS];
        private FormFile[] _photo = new FormFile[MAX_PHOTOS];

        public Integer getModelId()
        {
            return _modelId;
        }

        public void setModelId(Integer modelId)
        {
            _modelId = modelId;
        }

        public String[] getMouseNo()
        {
            return _mouseNo;
        }

        public void setMouseNo(String[] mouseNo)
        {
            _mouseNo = mouseNo;
        }

        public FormFile[] getPhoto()
        {
            return _photo;
        }

        public void setPhoto(FormFile[] photo)
        {
            _photo = photo;
        }
    }

    public static class SampleForm extends ViewForm
    {
        private String _sampleId;
        private String _sourceId;
        private Integer _modelId;

        public Integer getModelId()
        {
            return _modelId;
        }

        public void setModelId(Integer modelId)
        {
            _modelId = modelId;
        }

        public String getSampleId()
        {
            return _sampleId;
        }

        public void setSampleId(String sampleId)
        {
            _sampleId = sampleId;
        }

        public String getSourceId()
        {
            return _sourceId;
        }

        public void setSourceId(String sourceId)
        {
            _sourceId = sourceId;
        }
    }

    public static class CageNecropsyForm extends ViewForm
    {
        private String _collectionDateString;
        private String _cageName;
        private Integer _modelId;
        private Cage _cage;
        private int _taskType = TASK_TYPE_NECROPSY;

        public Date getCollectionDate()
        {
            try
            {
                return (Date) ConvertUtils.convert(_collectionDateString, Date.class);
            }
            catch (Exception x)
            {
            }
            return null;
        }

        public String getCollectionDateString()
        {
            return _collectionDateString;
        }

        public void setCollectionDateString(String collectionDate)
        {
            _collectionDateString = collectionDate;
        }

        public String getCageName()
        {
            return _cageName;
        }

        public void setCageName(String cageName)
        {
            _cageName = cageName;
        }

        public Cage getCage()
        {
            return _cage;
        }

        public int getTaskType()
        {
            return _taskType;
        }

        public void setTaskType(int taskType)
        {
            _taskType = taskType;
        }

        public Integer getModelId()
        {
            return _modelId;
        }

        public void setModelId(Integer modelId)
        {
            _modelId = modelId;
        }


        public ActionErrors validate(ActionMapping arg0, HttpServletRequest arg1)
        {
            ActionErrors errors = new ActionErrors();

            Cage[] cages = null;
            if (null != getCageName() && !"".equals(getCageName().trim()))
            {
                SimpleFilter filter = new SimpleFilter("cageName", getCageName());
                filter.addCondition("container", getContainer().getId());
                filter.addCondition("modelId", getModelId());

                try
                {
                    cages = (Cage[]) Table.select(MouseSchema.getCage(), Table.ALL_COLUMNS, filter, null, Cage.class);
                }
                catch (Exception x)
                {
                    errors.add("main", new ActionError("Error", x.getMessage()));
                    return errors;
                }
            }

            if (null != cages && cages.length == 1)
                _cage = cages[0];
            else
                errors.add("main", new ActionError("Error", "Could not find cage " + getCageName() + " in model " + getModelId()));
            return errors;
        }

        public void reset(ActionMapping arg0, HttpServletRequest request)
        {
            super.reset(arg0, request);
            _collectionDateString = formatDate();
        }
    }

    public static class BleedForm extends ViewForm
    {
        public final static int MAX_SAMPLES = 12;
        private String[] _samples = new String[MAX_SAMPLES];
        private String _mouseEntityId = null;
        private String _mouseNo = null;
        private Mouse _mouse = null;
        private String _collectionDateString = null;
        private Integer _taskId = null;
        private int _numberStyle = NUMBER_STYLE_MANUAL;
        private String _description;
        private boolean _isFinalBleed;
        private boolean _isUrineForm;
        private Location[] _locations = new Location[MAX_SAMPLES];
        private String _bleedType;

        public BleedForm()
        {
            for (int i = 0; i < _locations.length; i++)
                _locations[i] = new Location();
        }

        public Mouse getMouse()
        {
            if (null != _mouse)
                return _mouse;

            if (null != _mouseEntityId && !"".equals(_mouseEntityId.trim()))
                _mouse = MouseModelManager.getMouse(_mouseEntityId);
            else if (null != _mouseNo && !"".equals(_mouseNo.trim()))
                _mouse = MouseModelManager.getMouse(getContainer(), _mouseNo);

            return _mouse;
        }

        public String[] getSamples()
        {
            return _samples;
        }

        public void setSamples(String[] samples)
        {
            _samples = samples;
        }

        public String getMouseEntityId()
        {
            return _mouseEntityId;
        }

        public void setMouseEntityId(String mouseEntityId)
        {
            _mouseEntityId = mouseEntityId;
        }

        public Date getCollectionDate()
        {
            try
            {
                return (Date) ConvertUtils.convert(_collectionDateString, Date.class);
            }
            catch (Exception x)
            {
            }
            return null;
        }

        public String getCollectionDateString()
        {
            return _collectionDateString;
        }

        public void setCollectionDateString(String dateStr)
        {
            _collectionDateString = dateStr;
        }

        public Integer getTaskId()
        {
            return _taskId;
        }

        public void setTaskId(Integer taskId)
        {
            _taskId = taskId;
        }

        public int getNumberStyle()
        {
            return _numberStyle;
        }

        public void setNumberStyle(int numberStyle)
        {
            _numberStyle = numberStyle;
        }

        //Bleed type will get stored in descrption column...
        public String getDescription()
        {
            if (null == _description)
                return _bleedType;
            if (null == _bleedType)
                return _description;

            return _bleedType + ", " + _description;
        }

        public void setDescription(String description)
        {
            _description = description;
        }

        public boolean isFinalBleed()
        {
            return _isFinalBleed;
        }

        public void setFinalBleed(boolean finalBleed)
        {
            _isFinalBleed = finalBleed;
            if (finalBleed)
                setBleedType("Terminal Bleed");
        }

        public ActionErrors validate(ActionMapping arg0, HttpServletRequest request)
        {
            ActionErrors errors = new ActionErrors();
            if (null == getMouse())
                errors.add("main", new ActionError("Error", "Could not find mouse " + (_mouseNo != null ? _mouseNo : "")));

            if (null == getCollectionDate())
                errors.add("main", new ActionError("Error", "Valid Date must be supplied"));

            if (_numberStyle == NUMBER_STYLE_MANUAL)
            {
                for (int i = 0; i < _samples.length; i++)
                {
                    if (_samples[i] != null && !"".equals(_samples[i].trim()))
                        if (!MANUAL_SAMPLE_NUM_PATTERN.matcher(_samples[i]).matches())
                            errors.add("main", new ActionError("Error", "Sample '" + _samples[i] + "' is not 3 digit number."));
                }
            }

            String samplePrefix = "";
            if (getNumberStyle() == NUMBER_STYLE_MANUAL)
                samplePrefix = SampleManager.formatSampleDate(getCollectionDate()) + "-";

            HashMap<String, Integer> sampleMap = new HashMap<String,Integer>();
            for (int i = 0; i < _samples.length; i++)
            {
                String sampleId = _samples[i];
                if (PageFlowUtil.empty(sampleId))
                    continue;

                Integer loc = sampleMap.get(sampleId);
                if (null != loc)
                    errors.add("main", new ActionError("Error", "Sample " + sampleId  + " occurs in row " + (loc + 1) + " and " + (i + 1)));
                else
                    sampleMap.put(sampleId, i);

                try
                {
                    Sample samp = SampleManager.getSampleFromId(samplePrefix + sampleId );
                    if (null != samp)
                        errors.add("main", new ActionError("Error", "Sample " + samplePrefix + sampleId  + " already exists"));
                } catch (SQLException e)
                {
                    throw new RuntimeSQLException(e);
                }
            }

            HashMap<String, Integer> locationMap = new HashMap<String,Integer>();
            for (int i = 0; i < _locations.length; i++)
            {
                Location location = _locations[i];
                if (!PageFlowUtil.empty(location.getFreezer()) &&
                        (PageFlowUtil.empty(location.getBox())
                                || PageFlowUtil.empty(location.getRack())
                                || 0 == location.getCell()))
                    errors.add("main", new ActionError("Error", "Bad location for sample " + i));

                if (!PageFlowUtil.empty(location.getFreezer()))
                {
                try
                {
                    String locationKey = location.getFreezer() + "," + location.getRack() + "," + location.getBox() + "," + location.getCell();
                    Integer oldIndex = locationMap.get(locationKey);
                    if (null != oldIndex)
                        errors.add("main", new ActionError("Error", "The location " + locationKey + " is used in row " + (oldIndex + 1) + " and " + (i + 1)));
                    else
                        locationMap.put(locationKey, i);

                    Sample stored = SampleManager.getSampleFromLocation(location.getFreezer(), location.getRack(), location.getBox(), location.getCell());
                    if (null  != stored)
                        errors.add("main", new ActionError("Error", "There is already a sample stored at location " + locationKey));

                } catch (SQLException e)
                {
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                }
                }
            }


            return errors;
        }

        public void reset(ActionMapping arg0, HttpServletRequest request)
        {
            super.reset(arg0, request);
            //Only interested in dates..
            _collectionDateString = formatDate();
        }

        public String getMouseNo()
        {
            return _mouseNo;
        }

        public void setMouseNo(String mouseNo)
        {
            _mouseNo = mouseNo;
        }

        public void setUrineForm(boolean urineForm)
        {
            _isUrineForm = urineForm;
        }

        public boolean isUrineForm()
        {
            return _isUrineForm;
        }

        public Location[] getLocations()
        {
            return _locations;
        }

        public void setLocations(Location[] locations)
        {
            _locations = locations;
        }

        public String getBleedType()
        {
            return _bleedType;
        }

        public void setBleedType(String _bleedType)
        {
            this._bleedType = _bleedType;
        }
    }

    public static class NecropsyForm extends ViewForm
    {
        public final static int MAX_SAMPLES = 20;
        private NecropsySample[] _samples = new NecropsySample[MAX_SAMPLES];
        private String _mouseEntityId = null;
        private String _collectionDateString = null;
        private int _numberStyle = NUMBER_STYLE_MANUAL;
        private Integer _taskId = null;
        private String _mouseNo = null;
        private Mouse _mouse = null;
        private String _appearance;
        private String _grossFindings;

        public NecropsySample[] getSamples()
        {
            return _samples;
        }

        public void setSamples(NecropsySample[] samples)
        {
            _samples = samples;
        }

        public String getMouseEntityId()
        {
            return _mouseEntityId;
        }

        public void setMouseEntityId(String mouseEntityId)
        {
            _mouseEntityId = StringUtils.trimToNull(mouseEntityId);
            _mouse = null;
        }

        public String getMouseNo()
        {
            return _mouseNo;
        }

        public void setMouseNo(String mouseNo)
        {
            _mouseNo = StringUtils.trimToNull(mouseNo);
            _mouse = null;
        }

        public Date getCollectionDate()
        {
            try
            {
                return (Date) ConvertUtils.convert(_collectionDateString, Date.class);
            }
            catch (Exception x)
            {
            }
            return null;
        }

        public String getCollectionDateString()
        {
            return _collectionDateString;
        }

        public void setCollectionDateString(String dateStr)
        {
            _collectionDateString = dateStr;
        }

        public Integer getTaskId()
        {
            return _taskId;
        }

        public void setTaskId(Integer taskId)
        {
            _taskId = taskId;
        }

        public int getNumberStyle()
        {
            return _numberStyle;
        }

        public void setNumberStyle(int numberStyle)
        {
            _numberStyle = numberStyle;
        }

        public ActionErrors validate(ActionMapping arg0, HttpServletRequest request)
        {
            ActionErrors errors = new ActionErrors();
            if (null == getCollectionDate())
                errors.add("main", new ActionError("Error", "Valid collection date must be supplied"));

            if (null == getMouse())
                errors.add("main", new ActionError("Error", "Could not find mouse with name: " + (getMouseNo() == null ? "" : getMouseNo())));

            Date necropsyDate = getCollectionDate();
                    //String samplePrefix = _mouseModel.getName() + "-" + _sampleDateFormat.format(necropsyDate) + "-";
                    //String samplePrefix = _mouseModel.getName() + "-" + _sampleDateFormat.format(necropsyDate) + "-";
                    String samplePrefix = "";
                    if (getNumberStyle() == NUMBER_STYLE_MANUAL)
                        samplePrefix = SampleManager.formatSampleDate(necropsyDate) + "-";



            Map<String, Integer> sampleIdMap = new HashMap<String, Integer>();
            Map<String, Integer> locationMap = new HashMap<String, Integer>();

            for (int i = 0; i < _samples.length; i++)
            {
                NecropsySample sample = _samples[i];
                if (null != sample.getSampleId())
                {
                    Integer oldIndex = sampleIdMap.get(sample.getSampleId());
                    if (null != oldIndex)
                        errors.add("main", new ActionError("Error", "The sample id " + sample.getSampleId() + " is entered in rows " + (oldIndex + 1) + " and " + (i + 1)));
                    else
                        sampleIdMap.put(sample.getSampleId(), i);

                    if (null != sample.getFreezer())
                    {
                        String location = sample.getFreezer() + "," + sample.getRack() + "," + sample.getBox() + "," + sample.getCell();
                        Integer locIndex = locationMap.get(location);
                        if (null != locIndex)
                            errors.add("main", new ActionError("Error", "The location " + location + " is entered in rows " + (locIndex + 1) + " and " + (i + 1)));
                        else
                            locationMap.put(location, i);
                    }

                    if ((null == sample.getTissueId() || sample.getTissueId() == 0))
                        errors.add("main", new ActionError("Error", "In sample " + String.valueOf(i + 1) + " a sample id is assigned, but no tissue type is chosen"));
                    if (!sample.getFixed() && !sample.getFrozen())
                        errors.add("main", new ActionError("Error", "Sample " + String.valueOf(i + 1) + " is not marked as either fixed or frozen."));
                    if (_numberStyle == NUMBER_STYLE_MANUAL)
                    {
                        if (!MANUAL_SAMPLE_NUM_PATTERN.matcher(sample.getSampleId()).matches())
                            errors.add("main", new ActionError("Error", "Sample '" + _samples[i].getSampleId() + "' is not 3 digit number."));

                        if (sample.hasSubSamples())
                            try
                            {
                                char[] chars = sample.subSampleIds();
                            }
                            catch (IllegalArgumentException x)
                            {
                                errors.add("main", new ActionError("Error", "Sample " + String.valueOf(i + 1) + " aliquot is not a valid range of letters. "));
                            }
                    }
                }
                else if (null != sample.getTissueId() && sample.getTissueId().intValue() != 0)
                    errors.add("main", new ActionError("Error", "In sample " + String.valueOf(i + 1) + " a tissue type is chosen, but no sample is assigned."));

                try
                {
                    Sample savedSample = SampleManager.getSampleFromId(samplePrefix + sample.getSampleId());
                    if (null != savedSample)
                        errors.add("main", new ActionError("Error", "Sample " + samplePrefix + sample.getSampleId() + " already exists."));

                    if (!PageFlowUtil.empty(sample.getFreezer()))
                    {
                        savedSample = SampleManager.getSampleFromLocation(sample.getFreezer(), sample.getRack(), sample.getBox(), sample.getCell());
                        if (null != savedSample)
                            errors.add("main", new ActionError("Error", "There is already stored at " + sample.getFreezer() + ", " + sample.getRack() + ", " + sample.getBox() + ", " + sample.getCell()));
                    }
                } catch (SQLException e)
                {
                    throw new RuntimeSQLException(e);
                }

            }


            return errors;
        }

        public void reset(ActionMapping arg0, HttpServletRequest request)
        {
            super.reset(arg0, request);
            for (int i = 0; i < MAX_SAMPLES; i++)
                _samples[i] = new NecropsySample();
            //Only interested in dates..
            _collectionDateString = formatDate();
        }

        public Mouse getMouse()
        {
            if (null != _mouse)
                return _mouse;

            if (null != _mouseEntityId)
                _mouse = MouseModelManager.getMouse(_mouseEntityId);
            if (null != _mouseNo)
                _mouse = MouseModelManager.getMouse(getContainer(), _mouseNo);

            return _mouse;
        }

        public void setMouse(Mouse _mouse)
        {
            this._mouse = _mouse;
        }

        public String getAppearance()
        {
            return _appearance;
        }

        public void setAppearance(String _appearance)
        {
            this._appearance = _appearance;
        }

        public String getGrossFindings()
        {
            return _grossFindings;
        }

        public void setGrossFindings(String _grossFindings)
        {
            this._grossFindings = _grossFindings;
        }
    }

}
