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

package org.labkey.mousemodel;

import org.apache.beehive.netui.pageflow.Forward;
import org.apache.beehive.netui.pageflow.annotations.Jpf;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.struts.action.ActionError;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionMapping;
import org.labkey.api.data.*;
import org.labkey.api.sample.*;
import org.labkey.api.security.ACL;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.common.util.Pair;
import org.labkey.api.view.*;
import org.labkey.api.view.template.HomeTemplate;
import org.labkey.api.announcements.DiscussionService;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashSet;

@Jpf.Controller(messageBundles = {@Jpf.MessageBundle(bundlePath = "messages.Validation")})
public class MouseModelController extends ViewController
{

    private static final int MICE_PER_CAGE = 5;

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
    protected Forward begin(MouseModelForm form) throws Exception
    {
        requiresPermission(ACL.PERM_READ);

        HttpView v;
        GridView gv = new GridView(getGridRegion(getViewContext()));
        gv.setContainer(form.getContainer());
        if (null != getRequest().getParameter("message"))
        {
            String message = getRequest().getParameter("message");
            HtmlView messageView = new HtmlView("<b>" + PageFlowUtil.filter(message) + "</b><br>");
            v = new VBox(messageView, gv);
        }
        else
            v = gv;

        _renderInTemplate(v, form.getContainer());

        return null;
    }

    @Jpf.Action
    protected Forward showInsert(MouseModelForm form) throws Exception
    {
        requiresPermission(ACL.PERM_INSERT);

        VelocityDataView v = new VelocityDataView(getDefaultRegion(), form, "/org/labkey/mousemodel/insert.vm");
        v.setMode(DataRegion.MODE_INSERT);

        for (DisplayColumn col : v.getDataRegion().getDisplayColumnList())
        {
            if (col.getColumnInfo().getFk() != null)
                col.getColumnInfo().setNullable(false);
        }

        _renderInTemplate(v, form.getContainer());
        return null;
    }

    @Jpf.Action
    protected Forward showUpdate(MouseModelForm form) throws Exception
    {
        requiresPermission(ACL.PERM_UPDATE);

        UpdateView v = new UpdateView(getDefaultRegion(), form, null);
        _renderInTemplate(v, form.getContainer());
        return null;
    }

    @Jpf.Action
    protected Forward showAddPair(BreedingPairForm form) throws Exception
    {
        requiresPermission(ACL.PERM_INSERT);

        DataRegion drAddPair = new DataRegion();
        drAddPair.addColumns(MouseSchema.getBreedingPair().getUserEditableColumns());
        ButtonBar bb = new ButtonBar();
        bb.add(new ActionButton("insertPair.post", "Submit"));
        drAddPair.setButtonBar(bb, DataRegion.MODE_INSERT);
        InsertView v = new InsertView(drAddPair, form, null);
        _renderInTemplate(v, form.getContainer());
        return null;
    }

    @Jpf.Action
    protected Forward showUpdatePair(BreedingPairForm form) throws Exception
    {
        requiresPermission(ACL.PERM_UPDATE);

        DataRegion drAddPair = new DataRegion();
        drAddPair.addColumns(MouseSchema.getBreedingPair().getUserEditableColumns());
        ButtonBar bb = new ButtonBar();
        bb.add(new ActionButton("updatePair.post", "Submit"));
        drAddPair.setButtonBar(bb, DataRegion.MODE_UPDATE);
        UpdateView v = new UpdateView(drAddPair, form, null);
        _renderInTemplate(v, form.getContainer());
        return null;
    }

    @Jpf.Action
    protected Forward updatePair(BreedingPairForm form) throws Exception
    {
        //redundant, but defensive
        requiresPermission(ACL.PERM_UPDATE);

        form.doUpdate();
        return form.getForward("showPairs.view", new Pair<String, Object>("modelId", form.get("modelId")), true);
    }


    @Jpf.Action(validationErrorForward = @Jpf.Forward(path = "showAddPair.do", name = "validate"))
    protected Forward insertPair(BreedingPairForm form) throws Exception
    {
        //redundant, but defensive
        requiresPermission(ACL.PERM_INSERT);

        form.doInsert();
        return form.getForward("showPairs.view", new Pair<String, Object>("modelId", form.get("modelId")), true);
    }

    @Jpf.Action
    protected Forward details(MouseModelForm form) throws Exception
    {
        requiresPermission(ACL.PERM_READ);

        if (!form.isDataLoaded())
            form.refreshFromDb(false);

        Integer modelId = (Integer) form.getTypedValue("modelId");
        MouseModel model = MouseModelManager.getModel(modelId);
        VelocityView dashboardView = new VelocityView("/org/labkey/mousemodel/dashboard.vm");
        dashboardView.addObject("modelId", modelId);
        dashboardView.addObject("container", form.getContainer().getId());
        dashboardView.setTitle("Links");

        VelocityView startNecropsyView = new VelocityView("/org/labkey/mousemodel/necropsy/startNecropsyForm.vm");
        startNecropsyView.addObject("modelId", modelId);
        startNecropsyView.setTitle("Necropsy and Bleed");

        VelocityView locateSampleView = new VelocityView("/org/labkey/mousemodel/locateSampleForm.vm");
        locateSampleView.addObject("modelId", modelId);
        locateSampleView.addObject("materialSourceLSID", model.getMaterialSourceLSID());
        locateSampleView.setTitle("Locate Sample");

        HBox hbox = new HBox(new HttpView[]{dashboardView, startNecropsyView, locateSampleView});
        ModelAndView discussionView = DiscussionService.get().getDisussionArea(
                getViewContext(),
                model.getEntityId(), getViewContext().cloneActionURL(), model.getName(), true, false);

        MouseModelTemplateView v = new MouseModelTemplateView(form, 0, new VBox(hbox, discussionView));

        _renderInTemplate(v, form.getContainer());
        return null;
    }

    @Jpf.Action
    protected Forward showPairs(MouseModelForm form) throws Exception
    {
        requiresPermission(ACL.PERM_READ);

        //Breeding pairs
        DataRegion drPairs = new DataRegion();
        drPairs.addColumns(MouseSchema.getBreedingPair().getColumns("PairName,ModelId,DateJoined"));
        DisplayColumn col = new SimpleDisplayColumn(
                "<a href=\"showAddLitter.view?breedingPairId=${breedingPairId}\"><img border=0 src=\"" + PageFlowUtil.buttonSrc("Add Litter") + "\"></a>");
        col.setDisplayPermission(ACL.PERM_INSERT);
        drPairs.addColumn(col);

        col = drPairs.getDisplayColumn(0);
        col.setURL("showUpdatePair.view?breedingPairId=${breedingPairId}");

        ActionButton addPairButton = new ActionButton("showAddPair");
        addPairButton.setCaption("Add Breeding Pair");
        ActionURL helper = cloneActionURL();
        helper.setAction("showAddPair");
        helper.deleteParameters();
        helper.addParameter("modelId", (String) form.get("modelId"));
        addPairButton.setURL(helper.getLocalURIString());
        addPairButton.setActionType(ActionButton.Action.LINK);
        addPairButton.setDisplayPermission(ACL.PERM_INSERT);
        ButtonBar bb = new ButtonBar();
        bb.add(addPairButton);
        drPairs.setButtonBar(bb, DataRegion.MODE_GRID);
        GridView gridViewPairs = new GridView(drPairs);
        gridViewPairs.setFilter(new SimpleFilter("modelId", getModelId(form)));
        gridViewPairs.setTitle("Breeding Pairs");

        MouseModelTemplateView v = new MouseModelTemplateView(form, 1, gridViewPairs);
        _renderInTemplate(v, form.getContainer());

        return null;
    }

    @Jpf.Action
    protected Forward showAddLitter(ConfirmLitterForm form) throws Exception
    {
        requiresPermission(ACL.PERM_INSERT);

        BreedingPair pair = MouseModelManager.getBreedingPair(form.getBreedingPairId());

        if (null == form.getCages()[0].getCageName() || "".equals(form.getCages()[0].getCageName()))
            form.populateDefaults(pair);

        VelocityView v = new VelocityView("/org/labkey/mousemodel/confirmLitter.vm");
        v.addObject("form", form);
        v.addObject("cages", form.getCages());
        v.addObject("breedingPair", pair);
        MouseModel model = MouseModelManager.getModel(pair.getModelId());
        v.addObject("customColumns", model.getExtraColumnCaptions());
        v.setTitle("Confirm Mice and Cages");

        MouseModelTemplateView vt = new MouseModelTemplateView(pair.getModelId(), 1, v);

        _renderInTemplate(vt, form.getContainer());

        return null;
    }

    @Jpf.Action(validationErrorForward = @Jpf.Forward(path = "showAddLitter.do", name = "validate"))
    protected Forward confirmLitter(ConfirmLitterForm form) throws Exception
    {
        requiresPermission(ACL.PERM_INSERT);

        Cage[] cages = form.getCages();

        MouseModelManager.beginTransaction();

        try
        {
            Litter litter = new Litter();

            litter.setName(form.getLitterName());
            litter.setMales(form.getCountBySex("M"));
            litter.setFemales(form.getCountBySex("F"));
            litter.setBirthDate(form.getBirthDate());
            litter.setBreedingPairId(form.getBreedingPairId());
            litter.setContainer(form.getContainer().getId());
            litter = MouseModelManager.insertLitter(getUser(), litter);

            for (Cage cage : cages)
            {
                if (cage.getCageName() != null && !"".equals(cage.getCageName().trim()))
                {
                    int cageId;
                    Cage existing = MouseModelManager.getCage(getModelId(form), cage.getCageName());
                    if (null == existing)
                    {
                        MouseModelManager.insertCage(getUser(), cage);
                        cageId = cage.getCageId();
                    } else
                        cageId = existing.getCageId();

                    for (int iMouse = 0; iMouse < MICE_PER_CAGE; iMouse++)
                    {
                        Mouse mouse = cage.getMice()[iMouse];
                        mouse.setCageId(cageId);
                        mouse.setLitterId(litter.getLitterId());
                        if (null != mouse.getToeNo())
                            mouse = MouseModelManager.insertMouse(getUser(), mouse);
                    }
                }
            }
        }
        catch (Exception x)
        {
            MouseModelManager.rollbackTransaction();
            throw x;
        }

        MouseModelManager.commitTransaction();

        return form.getForward("showPairs.view", new Pair<String,Object>("modelId", form.getModelId()), true);
    }


    @Jpf.Action(validationErrorForward = @Jpf.Forward(path = "showUpdate.do", name = "validate"))
    protected Forward update(MouseModelForm form) throws Exception
    {
        //redundant, but defensive
        requiresPermission(ACL.PERM_UPDATE);

        form.doUpdate();
        return form.getPkForward("details");
    }

    private static DataRegion getDefaultRegion()
    {
        DataRegion dr = new DataRegion();
        dr.addColumns(MouseSchema.getMouseModel().getUserEditableColumns());
        return dr;
    }


    private static DataRegion getGridRegion(ViewContext context)
    {
        DataRegion dr = new DataRegion();
        dr.addColumns(MouseSchema.getMouseModel().getColumns("name,mouseStrainId,targetGeneId,tumorType,location,investigator"));

        ActionURL urlhelp = context.cloneActionURL();
        urlhelp.deleteParameters();
        urlhelp.setPageFlow("MouseModel");
        urlhelp.setAction("details");
        dr.getDisplayColumn(0).setURL(urlhelp.getLocalURIString() + "&modelId=${modelId}");

        urlhelp.setPageFlow("Mouse");
        urlhelp.setAction("begin");

        return dr;

    }


    private void _renderInTemplate(HttpView view, Container c) throws Exception
    {
        HomeTemplate template = new HomeTemplate(getViewContext(), getContainer(), view);
        includeView(template);
    }


    @Jpf.Action(validationErrorForward = @Jpf.Forward(path = "showInsert.do", name = "validate"))
    protected Forward insert(MouseModelForm form) throws Exception
    {
        //redundant, but defensive
        requiresPermission(ACL.PERM_INSERT);

        MouseModel model = form.getBean();
        model.setContainer(getContainer().getId());
        MouseModelManager.insertModel(getUser(), model);
        HttpView.throwRedirect(getActionURL().relativeUrl("details.view", "modelId=" + model.getModelId()));

        return null;
    }

    public static class MouseModelForm extends BeanViewForm
    {
        public MouseModelForm()
        {
            super(MouseModel.class, MouseSchema.getMouseModel());
        }

        public MouseModel getBean()
        {
            return (MouseModel) super.getBean();
        }
    }

    public static int getModelId(ViewForm form) throws ServletException
    {
        Integer modelId = null;

        if (form instanceof TableViewForm)
            modelId = (Integer) ((TableViewForm) form).getTypedValue("modelId");

        if (null == modelId)
        {
            String modelIdStr = form.getRequest().getParameter("modelId");
            if (null != modelIdStr && modelIdStr.length() > 0)
                modelId = new Integer(modelIdStr);
        }

        //if we found one, save it away in the session
        if (null != modelId)
            form.getRequest().getSession().setAttribute("modelId", modelId);
        else //last resort, use the session
            modelId = (Integer) form.getRequest().getSession().getAttribute("modelId");

        if (null == modelId)
            HttpView.throwRedirect(form.getContext().getActionURL().relativeUrl("begin", "message=Your session may have timed out. Please select a mouse model.", "MouseModel"));

        return modelId;
    }

    public static MouseModel getModel(ViewForm form) throws ServletException
    {
        return MouseModelManager.getModel(getModelId(form));
    }


    public static class MouseModelDetailsView extends GroovyView
    {
        MouseModel _model;

        public MouseModelDetailsView(MouseModelForm form)
        {
            super("/org/labkey/mousemodel/details.gm");
            if (form.isDataLoaded())
                _model = form.getBean();
            else
                _model = MouseModelManager.getModel(form.getBean().getModelId());
        }

        public MouseModelDetailsView(int modelId)
        {
            super("/org/labkey/mousemodel/details.gm");
            _model = MouseModelManager.getModel(modelId);
        }

        @Override
        protected void prepareWebPart(Object model) throws ServletException
        {
            addObject("model", _model); // mouse model! not view model
            super.prepareWebPart(model);
        }
    }


    public static class MouseModelTemplateView extends WebPartView
    {
        MouseModelDetailsView _detailsView = null;
        HttpView _includeView = null;
        TabstripView _tabView = null;
        int _modelId = 0;
        int _tabIndex = 0;

        private Pair[] getTabs()
        {
            ActionURL urlhelper = getViewContext().cloneActionURL();
            urlhelper.deleteParameters();
            urlhelper.replaceParameter("modelId", (String) String.valueOf(_modelId));
            Pair[] tabs = new Pair[]
                    {
                            new Pair("Dashboard", urlhelper.setPageFlow("MouseModel").setAction("details").clone()),
                            new Pair("Breeding Pairs", urlhelper.setPageFlow("MouseModel").setAction("showPairs").clone()),
                            new Pair("Mice", urlhelper.setPageFlow("MouseModel-Mouse").setAction("begin").clone()),
                            new Pair("Samples", urlhelper.setPageFlow("MouseModel-Sample").setAction("begin").clone()),
                            new Pair("Sample Collection", urlhelper.setPageFlow("MouseModel-Necropsy").setAction("begin").clone())
                    };

            return tabs;
        }

        public MouseModelTemplateView(MouseModelForm form, int tabIndex, HttpView includeView) throws ServletException
        {
            setFrame(FrameType.NONE);
            _detailsView = new MouseModelDetailsView(form);
            _modelId = getModelId(form);
            _tabIndex = tabIndex;
            _includeView = includeView;
        }

        public MouseModelTemplateView(int mouseModelId, int tabIndex, HttpView includeView)
        {
            setFrame(FrameType.NONE);
            _modelId = mouseModelId;
            _tabIndex = tabIndex;
            _includeView = includeView;
        }

        protected void prepareWebPart(Object model) throws ServletException
        {
            if (null == _detailsView)
                _detailsView = new MouseModelDetailsView(_modelId);
            _tabView = new TabstripView();
            _tabView.setTabs(getTabs());
            _tabView.setSelected(_tabIndex);
        }

        protected void renderView(Object model, PrintWriter out) throws Exception
        {
            include(_detailsView);
            out.println("<div />");
            include(_tabView);
            out.println("<div />");
            include(_includeView);
        }
    }


    public static class MouseModelView extends WebPartView
    {
        private Container _c;

        public MouseModelView()
        {
            setTitle("Mouse Models");
        }

        private static DataRegion getGridRegion(ViewContext context)
        {
            DataRegion dr = new DataRegion();
            dr.addColumns(MouseSchema.getMouseModel().getColumns("name,mouseStrainId,targetGeneId,tumorType,location,investigator"));

            ActionURL urlhelp = context.cloneActionURL();
            urlhelp.deleteParameters();
            urlhelp.setPageFlow("MouseModel");
            urlhelp.setAction("details");
            dr.getDisplayColumn(0).setURL(urlhelp.getLocalURIString() + "&modelId=${modelId}");

            urlhelp.setPageFlow("MouseModel-Mouse");
            urlhelp.setAction("begin");
            String url = urlhelp.getLocalURIString() + "&modelId=${modelId}";

            SimpleDisplayColumn linkCol = new SimpleDisplayColumn();
            linkCol.setDisplayHtml("<a href='" + url + "'>View Mice</a>");
            dr.addColumn(linkCol);

            dr.setButtonBar(ButtonBar.BUTTON_BAR_EMPTY, DataRegion.MODE_GRID);
            return dr;
        }


        @Override
        public void prepareWebPart(Object model)
        {
            if (null == _c)
            {
                String path = (String) getViewContext().get("path");
                if (null == path)
                    path = getViewContext().getActionURL().getExtraPath();
                _c = ContainerManager.getForPath(path);
            }

            if (null == _c || !_c.hasPermission(getViewContext().getUser(), ACL.PERM_READ))
                return;

            ActionURL urlhelp = getViewContext().cloneActionURL();
            urlhelp.setAction((String)null);
            urlhelp.setPageFlow("MouseModel");
            urlhelp.deleteParameters();
            getGridRegion(getViewContext()).setPageFlowUrl(urlhelp.getURIString());
            if (_c.hasPermission(getViewContext().getUser(), ACL.PERM_UPDATE))
                setTitleHref(ActionURL.toPathString("MouseModel", "begin", _c.getPath()));
        }


        @Override
        public void renderView(Object model, PrintWriter out) throws Exception
        {
            try
            {
                if (null == _c || !_c.hasPermission(getViewContext().getUser(), ACL.PERM_READ))
                {
                    if (getViewContext().getUser().isGuest())
                        out.println("<span class=\"normal\">Please log in to see this data.</span>");
                    else
                        out.println("<span class=\"normal\">You do not have permission to see this data.</span>");
                }
                else
                {
                    getViewContext().getContainer(ACL.PERM_READ);
                    getGridRegion(getViewContext()).renderTable(getViewContext(), out, null, null);
                }
            }
            catch (SQLException x)
            {
                out.print(x);
            }
        }
    }

    public static class BreedingPairForm extends TableViewForm
    {
        public BreedingPairForm()
        {
            super(MouseSchema.getBreedingPair());
        }
    }

    public static class LitterForm extends BeanViewForm
    {
        public LitterForm()
        {
            super(Litter.class, MouseSchema.getLitter());
        }
    }

    public static class ConfirmLitterForm extends ViewForm
    {
        private static final int MAX_CAGES = 6;
        private Cage[] _cages = new Cage[MAX_CAGES];
        private int _modelId = 0;
        private String litterName;
        private String birthDateString;
        private Timestamp birthDate;
        private int breedingPairId;

        public Cage[] getCages()
        {
            return _cages;
        }

        public void setCages(Cage[] cages)
        {
            _cages = cages;
        }

        public int getModelId()
        {
            return _modelId;
        }

        public void setModelId(int modelId)
        {
            _modelId = modelId;
        }

        public int getCountBySex(String sex)
        {
            int num = 0;
            for (int i = 0; i < _cages.length; i++)
                if (sex.equalsIgnoreCase(_cages[i].getSex()))
                    num += _cages[i].getMouseCount();

            return num;
        }

        public void populateDefaults(BreedingPair breedingPair) throws SQLException
        {
            Cage[] cages = new Cage[MAX_CAGES];
            setBreedingPairId(breedingPair.getBreedingPairId());

            for (int i = 0; i < cages.length; i++)
            {

                Mouse[] mice = new Mouse[MICE_PER_CAGE];
                for (int j = 0; j < mice.length; j++)
                {
                    Mouse mouse = new Mouse();
                    mouse.setModelId(breedingPair.getModelId());
                    mice[j] = mouse;
                }
                cages[i] = new Cage();
                cages[i].setMice(mice);
            }

            String[] names = MouseModelManager.getNextCageNames(breedingPair.getModelId(), 1);
            cages[0].setCageName(names[0]);
            setCages(cages);
        }

        /**
         * Validate errors.  Also, as much as possible fill in the proper values for the mice from the information
         * in the cage.
         */
        public ActionErrors validate(ActionMapping arg0, HttpServletRequest request)
        {
            ActionErrors errors = new ActionErrors();
            MouseModel model = MouseModelManager.getModel(getModelId());
            BreedingPair breedingPair = MouseModelManager.getBreedingPair(getBreedingPairId());
            if (null == model)
            {
                errors.add("main", new ActionError("Couldn't find mouse model."));
                return errors;
            }
            if (null == breedingPair)
            {
                errors.add("main", new ActionError("Error", "Couldn't find breeding pair."));
                return errors;
            }

            if (null == birthDateString || birthDateString.length() == 0)
                errors.add("main", new ActionError("Error", "Please supply a birth date for litter"));
            else
                try
                {
                    birthDate = (Timestamp) ConvertUtils.convert(birthDateString, Timestamp.class);
                }
                catch (Exception x)
                {
                    errors.add("main", new ActionError("Error", "Could not convert"));
                }

            if (null == litterName || litterName.length() == 0)
                errors.add("main", new ActionError("Error", "Please supply a litter name"));
            else
            {
                Litter litter = MouseModelManager.getLitter(getBreedingPairId(), getLitterName());
                if (null != litter)
                    errors.add("main", new ActionError("Error", "There is already a litter named" + getLitterName()));
            }

            Cage[] cages = getCages();
            for (int i = 0; i < MAX_CAGES; i++)
            {
                Cage cage = cages[i];
                if (null != cage.getCageName() && cage.getCageName().length() > 0)
                {
                    Mouse[] mice = cage.getMice();
                    String sex = cage.getSex();
                    if (null == sex)
                    {
                        errors.add("main", new ActionError("Error", "Sex is not set for cage " + cage.getCageName()));
                        continue;
                    }
                    sex = sex.toUpperCase();
                    cage.setSex(sex);

                    HashSet toeSet = new HashSet();
                    for (int j = 0; j < MICE_PER_CAGE; j++)
                        if (null != mice[j].getToeNo())
                        {
                            Mouse mouse = mice[j];
                            if (toeSet.contains(mouse.getToeNo()))
                                errors.add("main", new ActionError("Error", "Toe Number " + mouse.getToeNo() + " is duplicated in cage " + cage.getCageName()));
                            else
                            {
                                toeSet.add(mouse.getToeNo());
                                mouse.setSex(sex);
                                mouse.setBirthDate(birthDate);
                                String breedingPairName = "";
                                if (null != breedingPair && null != breedingPair.getPairName())
                                    breedingPairName = breedingPair.getPairName();
                                String mouseName = model.getName() + "-" + breedingPairName + litterName + "-" + cage.getCageName() + mouse.getToeNo();
                                mouse.setMouseNo(mouseName);
                                mouse.setModelId(model.getModelId());
                                mouse.setBleedOutComplete(false);
                                mouse.setNecropsyComplete(false);
                                mouse.setContainer(getContainer().getId());
                            }
                        }

                    int miceInCage = toeSet.size();

                    if (miceInCage == 0)
                    {
                        errors.add("main", new ActionError("Error", "No mice in cage " + cage.getCageName() + ". Remove the cage name to indicate the cage is not used"));
                        continue;
                    }

                    if (!"M".equalsIgnoreCase(sex) && !"F".equalsIgnoreCase(sex))
                        errors.add("main", new ActionError("Error", "Sex must be male or female for cage" + cage.getCageName()));

                    Cage existing = MouseModelManager.getCage(getModelId(), cage.getCageName());
                    if (null == existing)
                    {
                        cage.setContainer(getContainer().getId());
                        cage.setModelId(getModelId());
                    }
                }

            }

            return errors;
        }


        public void reset(ActionMapping arg0, HttpServletRequest request)
        {
            super.reset(arg0, request);
            for (int i = 0; i < MAX_CAGES; i++)
            {
                _cages[i] = new Cage();
                Mouse[] mice = new Mouse[MICE_PER_CAGE];
                for (int j = 0; j < MICE_PER_CAGE; j++)
                    mice[j] = new Mouse();
                _cages[i].setMice(mice);
            }
        }

        public String getLitterName()
        {
            return litterName;
        }

        public void setLitterName(String litterName)
        {
            this.litterName = litterName;
        }

        public String getBirthDateString()
        {
            return birthDateString;
        }

        public void setBirthDateString(String birthDateString)
        {
            this.birthDateString = birthDateString;
        }

        public Timestamp getBirthDate()
        {
            return birthDate;
        }

        public void setBirthDate(Timestamp birthDate)
        {
            this.birthDate = birthDate;
        }

        public int getBreedingPairId()
        {
            return breedingPairId;
        }

        public void setBreedingPairId(int breedingPairId)
        {
            this.breedingPairId = breedingPairId;
        }
    }
}
