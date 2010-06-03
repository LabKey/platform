/*
 * Copyright (c) 2007-2010 LabKey Corporation
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

package org.labkey.experiment.controllers.exp;

import jxl.*;
import jxl.read.biff.BiffException;
import jxl.write.*;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import org.labkey.api.action.*;
import org.labkey.api.data.*;
import org.labkey.api.exp.*;
import org.labkey.api.exp.api.*;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.exp.query.ExpInputTable;
import org.labkey.api.exp.xar.LsidUtils;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineUrls;
import org.labkey.api.pipeline.PipelineRootContainerTree;
import org.labkey.api.pipeline.browse.PipelinePathForm;
import org.labkey.api.query.*;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.MapTabLoader;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.security.*;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.study.ParticipantVisit;
import org.labkey.api.study.actions.UploadWizardAction;
import org.labkey.api.util.*;
import org.labkey.api.view.*;
import org.labkey.experiment.*;
import org.labkey.experiment.api.*;
import org.labkey.experiment.controllers.property.PropertyController;
import org.labkey.experiment.pipeline.ExperimentPipelineJob;
import org.labkey.experiment.samples.UploadMaterialSetForm;
import org.labkey.experiment.samples.UploadSamplesHelper;
import org.labkey.experiment.xar.XarExportSelection;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.validation.ObjectError;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.ModelAndView;

import javax.imageio.ImageIO;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.Boolean;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * User: jeckels
 * Date: Dec 13, 2007
 */
public class ExperimentController extends SpringActionController
{
    private static final Logger _log = Logger.getLogger(ExperimentController.class);
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(ExperimentController.class);

    public static final String GUEST_DIRECTORY_NAME = "guest";

    public ExperimentController()
    {
        setActionResolver(_actionResolver);
    }

    public static void ensureCorrectContainer(Container requestContainer, ExpObject object, ViewContext viewContext) throws ServletException
    {
        Container objectContainer = object.getContainer();
        if (!requestContainer.equals(objectContainer))
        {
            ActionURL url = viewContext.cloneActionURL();
            url.setContainer(objectContainer);
            HttpView.throwRedirect(url);
        }
    }

    private NavTree appendRootNavTrail(NavTree root)
    {
        root.addChild("Experiment", ExperimentUrlsImpl.get().getOverviewURL(getContainer()));
        return root;
    }

    @ActionNames("begin,gridView")
    @RequiresPermissionClass(ReadPermission.class)
    public class BeginAction extends ShowRunsAction
    {
        public VBox getView(Object o, BindException errors) throws Exception
        {
            VBox result = super.getView(o, errors);
            RunGroupWebPart runGroups = new RunGroupWebPart(getViewContext(), false);
            runGroups.showHeader();
            result.addView(runGroups);

            result.addView(new ProtocolWebPart(false, getViewContext()));
            result.addView(new SampleSetWebPart(false, getViewContext()));

            return result;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendRootNavTrail(root);
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class ShowRunsAction extends SimpleViewAction
    {
        public VBox getView(Object o, BindException errors) throws Exception
        {
            Set<ExperimentRunType> types = ExperimentService.get().getExperimentRunTypes(getContainer());
            ChooseExperimentTypeBean bean = new ChooseExperimentTypeBean(types, ExperimentRunType.getSelectedFilter(types, getViewContext().getRequest().getParameter("experimentRunFilter")), getViewContext().getActionURL().clone(), Collections.<ExpProtocol>emptyList());
            JspView chooserView = new JspView<ChooseExperimentTypeBean>("/org/labkey/experiment/experimentRunQueryHeader.jsp", bean);

            ExperimentRunListView view = ExperimentService.get().createExperimentRunWebPart(getViewContext(), bean.getSelectedFilter());
            return new VBox(chooserView, view);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendRootNavTrail(root).addChild("Experiment Runs");
        }
    }

    @RequiresPermissionClass(ReadPermission.class) @ActionNames("showRunGroups, showExperiments")
    public class ShowRunGroupsAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new RunGroupWebPart(getViewContext(), false);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendRootNavTrail(root).addChild("Run Groups");
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class CreateHiddenRunGroupAction extends ApiAction<SimpleApiJsonForm>
    {
        public ApiResponse execute(SimpleApiJsonForm form, BindException errors) throws Exception
        {
            List<ExpRun> runs = new ArrayList<ExpRun>();
            JSONArray runIds = form.getJsonObject().getJSONArray("runIds");
            for (int i = 0; i < runIds.length(); i++)
            {
                runs.add(ExperimentServiceImpl.get().getExpRun(runIds.getInt(i)));
            }
            if (runs.isEmpty())
            {
                HttpView.throwNotFound();
            }
            ExpExperiment group = ExperimentService.get().createHiddenRunGroup(getContainer(), getUser(), runs.toArray(new ExpRun[runs.size()]));
            ApiSimpleResponse response = new ApiSimpleResponse();
            response.putBean(group, "rowId", "LSID", "name", "hidden");
            return response;
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class DetailsAction extends SimpleViewAction<ExpObjectForm>
    {
        private ExpExperimentImpl _experiment;

        public ModelAndView getView(ExpObjectForm form, BindException errors) throws Exception
        {
            Container c = getContainer();

            _experiment = ExperimentServiceImpl.get().getExpExperiment(form.getRowId());
            if (_experiment == null)
            {
                throw new NotFoundException("Could not find an experiment with RowId " + form.getRowId());
            }

            if (!_experiment.getContainer().equals(getViewContext().getContainer()))
            {
                HttpView.throwRedirect(getViewContext().cloneActionURL().setContainer(_experiment.getContainer()));
            }

            CustomPropertiesView customPropertiesView = new CustomPropertiesView(_experiment.getLSID(), getViewContext().cloneActionURL(), c);

            DetailsView detailsView = new DetailsView(new DataRegion(), _experiment.getRowId());
            detailsView.getDataRegion().setTable(ExperimentServiceImpl.get().getTinfoExperiment());
            detailsView.getDataRegion().addColumns(ExperimentServiceImpl.get().getTinfoExperiment(), "RowId,Name,ContactId,ExperimentDescriptionURL,Hypothesis,Comments");
            detailsView.getDataRegion().getDisplayColumn(0).setVisible(false);
            detailsView.getDataRegion().getDisplayColumn(2).setWidth("60%");

            ButtonBar bb = new ButtonBar();
            bb.setStyle(ButtonBar.Style.separateButtons);
            ActionButton b = new ActionButton(ExperimentUrlsImpl.get().getShowUpdateURL(_experiment), "Edit");
            b.setDisplayPermission(UpdatePermission.class);
            bb.add(b);
            detailsView.getDataRegion().setButtonBar(bb);
            if (_experiment.getBatchProtocol() != null)
            {
                detailsView.setTitle("Batch Details");
            }
            else
            {
                detailsView.setTitle("Run Group Details");
            }

            VBox vbox = new VBox();
            vbox.addView(new StandardAndCustomPropertiesView(detailsView, customPropertiesView));

            List<ExpProtocol> protocols = _experiment.getAllProtocols();

            Set<ExperimentRunType> types = new TreeSet<ExperimentRunType>(ExperimentService.get().getExperimentRunTypes(getContainer()));
            ExperimentRunType selectedType = ExperimentRunType.getSelectedFilter(types, getViewContext().getRequest().getParameter("experimentRunFilter"));

            ChooseExperimentTypeBean bean = new ChooseExperimentTypeBean(types, selectedType, getViewContext().getActionURL().clone(), protocols);
            JspView chooserView = new JspView<ChooseExperimentTypeBean>("/org/labkey/experiment/experimentRunQueryHeader.jsp", bean);

            ExperimentRunListView runListView = ExperimentRunListView.createView(getViewContext(), bean.getSelectedFilter(), true);
            runListView.getRunTable().setExperiment(_experiment);
            runListView.setShowRemoveFromExperimentButton(true);
            runListView.setShowDeleteButton(true);
            runListView.setShowAddToRunGroupButton(true);
            runListView.setShowExportButtons(true);
            runListView.setShowMoveRunsButton(true);
            chooserView.setTitle("Experiment Runs");
            vbox.addView(chooserView);
            vbox.addView(runListView);

            return vbox;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendRootNavTrail(root).addChild("Run Groups", ExperimentUrlsImpl.get().getShowExperimentsURL(getContainer())).addChild(_experiment.getName());
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class ListMaterialSourcesAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            SampleSetWebPart view = new SampleSetWebPart(false, getViewContext());
            view.setFrame(WebPartView.FrameType.NONE);
            view.setSampleSetError(getViewContext().getRequest().getParameter("sampleSetError"));

            return view;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendRootNavTrail(root).addChild("Sample Sets");
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class ShowMaterialSourceAction extends SimpleViewAction<ExpObjectForm>
    {
        private ExpSampleSetImpl _source;

        public ModelAndView getView(ExpObjectForm form, BindException errors) throws Exception
        {
            _source = ExperimentServiceImpl.get().getSampleSet(form.getRowId());
            if (_source == null && form.getLsid() != null)
            {
                if (form.getLsid().equalsIgnoreCase("Material") || form.getLsid().equalsIgnoreCase("Sample"))
                {
                    // Not a real sample set - just show all the materials instead
                    HttpView.throwRedirect(new ActionURL(ShowAllMaterialsAction.class, getContainer()));
                }
                // Check if the URL specifies the LSID, and stick the bean back into the form
                _source = ExperimentServiceImpl.get().getSampleSet(form.getLsid());
            }

            if (_source == null)
            {
                HttpView.throwNotFound();
            }

            ensureCorrectContainer(getContainer(), _source, getViewContext());

            SamplesSchema schema = new SamplesSchema(getUser(), getContainer());
            QuerySettings settings = new QuerySettings(getViewContext(), "Material");
            settings.setAllowChooseQuery(false);
            settings.setSchemaName(schema.getSchemaName());
            settings.setQueryName(_source.getName());
            final String sourceName = _source.getName();
            QueryView queryView = new QueryView(schema, settings, errors)
            {
                protected void populateButtonBar(DataView view, ButtonBar bar)
                {
                    super.populateButtonBar(view, bar);

                    ActionURL deleteMaterialUrl = new ActionURL(DeleteMaterialByRowIdAction.class, getContainer());
                    deleteMaterialUrl.addParameter("returnURL", getViewContext().getActionURL().toString());
                    ActionButton deleteMaterial = new ActionButton("", "Delete");
                    deleteMaterial.setURL(deleteMaterialUrl);
                    deleteMaterial.setActionType(ActionButton.Action.POST);
                    deleteMaterial.setDisplayPermission(DeletePermission.class);
                    deleteMaterial.setRequiresSelection(true);
                    bar.add(deleteMaterial);

                    ActionURL urlDeriveSamples = new ActionURL(DeriveSamplesChooseTargetAction.class, getContainer());
                    ActionButton deriveButton = new ActionButton("", "Derive Samples");
                    deriveButton.setURL(urlDeriveSamples);
                    deriveButton.setActionType(ActionButton.Action.POST);
                    deriveButton.setDisplayPermission(InsertPermission.class);
                    deriveButton.setRequiresSelection(true);
                    bar.add(deriveButton);
                }
            };
            queryView.setShowRecordSelectors(getContainer().hasPermission(getUser(), DeletePermission.class) || getContainer().hasPermission(getUser(), InsertPermission.class));
            queryView.setShowBorders(true);
            queryView.setShadeAlternatingRows(true);

            queryView.setTitle("Sample Set Contents");

            DetailsView detailsView = new DetailsView(getMaterialSourceRegion(getViewContext(), true), _source.getRowId());
            detailsView.getDataRegion().getDisplayColumn("Name").setURL(null);
            detailsView.getDataRegion().getDisplayColumn("LSID").setVisible(false);
            detailsView.getDataRegion().getDisplayColumn("MaterialLSIDPrefix").setVisible(false);
            detailsView.setTitle("Sample Set Properties");
            detailsView.getDataRegion().getButtonBar(DataRegion.MODE_DETAILS).setStyle(ButtonBar.Style.separateButtons);

            if (!ExperimentService.get().ensureDefaultSampleSet().equals(_source))
            {
                ActionButton updateButton = new ActionButton(ShowUpdateMaterialSourceAction.class, "Edit Set", DataRegion.MODE_DETAILS, ActionButton.Action.GET);
                updateButton.setDisplayPermission(UpdatePermission.class);
                detailsView.getDataRegion().getButtonBar(DataRegion.MODE_DETAILS).add(updateButton);

                ActionURL editTypeURL = getViewContext().cloneActionURL();
                editTypeURL.setAction(ExperimentController.EditSampleSetTypeAction.class);
                ActionButton editTypeButton = new ActionButton(editTypeURL.toString(), "Edit Fields", DataRegion.MODE_DETAILS);
                editTypeButton.setURL(editTypeURL);
                editTypeButton.setDisplayPermission(UpdatePermission.class);
                detailsView.getDataRegion().getButtonBar(DataRegion.MODE_DETAILS).add(editTypeButton);

                ActionButton deleteButton = new ActionButton(ExperimentController.DeleteMaterialSourceAction.class, "Delete Set", DataRegion.MODE_DETAILS, ActionButton.Action.POST);
                deleteButton.setDisplayPermission(DeletePermission.class);
                ActionURL deleteURL = new ActionURL(ExperimentController.DeleteMaterialSourceAction.class, getViewContext().getContainer());
                deleteURL.addParameter("singleObjectRowId", _source.getRowId());
                deleteURL.addParameter("returnURL", ExperimentUrlsImpl.get().getShowSampleSetListURL(getViewContext().getContainer()).toString());

                deleteButton.setURL(deleteURL);
                deleteButton.setActionType(ActionButton.Action.LINK);
                detailsView.getDataRegion().getButtonBar(DataRegion.MODE_DETAILS).add(deleteButton);

            }

            if (_source.canImportMoreSamples())
            {
                ActionURL urlUploadSamples = new ActionURL(ShowUploadMaterialsAction.class, getViewContext().getContainer());
                urlUploadSamples.addParameter("name", sourceName);
                urlUploadSamples.addParameter("importMoreSamples", "true");
                ActionButton uploadButton = new ActionButton(urlUploadSamples.toString(), "Import More Samples", DataRegion.MODE_ALL, ActionButton.Action.LINK);
                uploadButton.setDisplayPermission(UpdatePermission.class);
                detailsView.getDataRegion().getButtonBar(DataRegion.MODE_DETAILS).add(uploadButton);
            }

            return new VBox(detailsView, queryView);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            ActionURL url = new ActionURL(ListMaterialSourcesAction.class, getContainer());
            return appendRootNavTrail(root).addChild("Sample Sets", url).addChild("Sample Set " + _source.getName());
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class ShowAllMaterialsAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            ExpSchema schema = new ExpSchema(getUser(), getContainer());
            QuerySettings settings = new QuerySettings(getViewContext(), "Materials");
            settings.setSchemaName(schema.getSchemaName());
            settings.setAllowChooseQuery(false);
            settings.setQueryName(ExpSchema.TableType.Materials.toString());
            QueryView view = new QueryView(schema, settings, errors)
            {
                protected void populateButtonBar(DataView view, ButtonBar bar)
                {
                    super.populateButtonBar(view, bar);

                    ActionURL urlDeriveSamples = new ActionURL(DeriveSamplesChooseTargetAction.class, getContainer());
                    ActionButton deriveButton = new ActionButton("", "Derive Samples");
                    deriveButton.setURL(urlDeriveSamples);
                    deriveButton.setActionType(ActionButton.Action.POST);
                    deriveButton.setDisplayPermission(InsertPermission.class);
                    deriveButton.setRequiresSelection(true);
                    bar.add(deriveButton);
                }
            };
            view.setShadeAlternatingRows(true);
            view.setShowBorders(true);
            view.setShowDetailsColumn(false);
            view.setShowRecordSelectors(true);
            return view;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendRootNavTrail(root).addChild("All Materials");
        }
    }

    @RequiresPermissionClass(InsertPermission.class)
    @ApiVersion(9.2)
    @RequiresLogin
    public class SaveMaterialsAction extends ApiAction<SaveMaterialsForm>
    {
        public ApiResponse execute(SaveMaterialsForm form, BindException errors) throws Exception
        {
            UploadMaterialSetForm uploadForm = new UploadMaterialSetForm();
            uploadForm.setContainer(getContainer());
            uploadForm.setUser(getUser());
            uploadForm.setName(form.getName());
            uploadForm.setImportMoreSamples(true);
            uploadForm.setParentColumn(-1);
            uploadForm.setInsertUpdateChoice(UploadMaterialSetForm.InsertUpdateChoice.insertOrUpdate.name());
            uploadForm.setCreateNewSampleSet(false);
            uploadForm.setLoader(new MapTabLoader(form.getMaterials()));

            UploadSamplesHelper helper = new UploadSamplesHelper(uploadForm);
            helper.uploadMaterials();

            return new ApiSimpleResponse();
        }
    }

    public static final class SaveMaterialsForm implements ApiJsonForm
    {
        private JSONObject jsonObj;

        public void setJsonObject(JSONObject jsonObj)
        {
            this.jsonObj = jsonObj;
        }

        public String getName()
        {
            return jsonObj.getString("name");
        }

        public List<Map<String, Object>> getMaterials()
        {
            JSONArray materials = jsonObj.getJSONArray("materials");
            List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
            for (int i=0; i<materials.length(); i++)
            {
                Map<String, Object> props = materials.getJSONObject(i).getJSONObject("properties");
                result.add(props);
            }
            return result;
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class ShowMaterialAction extends SimpleViewAction<ExpObjectForm>
    {
        private ExpMaterialImpl _material;

        public ModelAndView getView(ExpObjectForm form, BindException errors) throws Exception
        {
            Container c = getContainer();
            _material = ExperimentServiceImpl.get().getExpMaterial(form.getRowId());
            if (_material == null && form.getLsid() != null)
            {
                _material = ExperimentServiceImpl.get().getExpMaterial(form.getLsid());
            }
            if (_material == null)
            {
                HttpView.throwNotFound("Could not find a material with RowId " + form.getRowId());
            }

            ensureCorrectContainer(getContainer(), _material, getViewContext());

            ExpRunImpl run = _material.getRun();
            ExpProtocol sourceProtocol = _material.getSourceProtocol();
            ExpProtocolApplication sourceProtocolApplication = _material.getSourceApplication();

            DataRegion dr = new DataRegion();
            dr.addColumns(ExperimentServiceImpl.get().getTinfoMaterial().getUserEditableColumns());
            dr.removeColumns("RowId", "RunId", "LSID", "SourceApplicationId", "CpasType");

            //dr.addColumns(extraProps);
            dr.addDisplayColumn(new ExperimentRunDisplayColumn(run, "Source Experiment Run"));
            dr.addDisplayColumn(new ProtocolDisplayColumn(sourceProtocol, "Source Protocol"));
            dr.addDisplayColumn(new ProtocolApplicationDisplayColumn(sourceProtocolApplication, "Source Protocol Application"));
            dr.addDisplayColumn(new LineageGraphDisplayColumn(_material, run));
            dr.addDisplayColumn(new SampleSetDisplayColumn(_material));

            //TODO: Can't yet edit materials uploaded from a material source
            dr.setButtonBar(new ButtonBar());
            DetailsView detailsView = new DetailsView(dr, _material.getRowId());
            detailsView.setTitle("Standard Properties");

            CustomPropertiesView cpv = new CustomPropertiesView(_material.getLSID(), getViewContext().cloneActionURL(), c);

            VBox vbox = new VBox(new StandardAndCustomPropertiesView(detailsView, cpv));

            List<ExpMaterial> materialsToInvestigate = new ArrayList<ExpMaterial>();
            final List<ExpRun> successorRuns = new ArrayList<ExpRun>();
            materialsToInvestigate.add(_material);
            Set<ExpMaterial> investigatedMaterials = new HashSet<ExpMaterial>();
            while (!materialsToInvestigate.isEmpty())
            {
                ExpMaterial m = materialsToInvestigate.remove(0);
                if (investigatedMaterials.add(m))
                {
                    for (ExpRun r : ExperimentService.get().getRunsUsingMaterials(m.getRowId()))
                    {
                        successorRuns.add(r);
                        materialsToInvestigate.addAll(r.getMaterialOutputs());
                    }
                }
                if (successorRuns.size() > 1000)
                {
                    break;
                }
            }

            StringBuilder updateLinks = new StringBuilder();
            ExpSampleSet ss = _material.getSampleSet();
            if (ss != null && ss.getContainer() != null && ss.getContainer().hasPermission(getUser(), UpdatePermission.class))
            {
                // XXX: ridiculous amount of work to get a update url expression for the sample set's table.
                UserSchema samplesSchema = QueryService.get().getUserSchema(getUser(), ss.getContainer(), "Samples");
                QueryDefinition queryDef = samplesSchema.getQueryDefForTable(ss.getName());
                StringExpression expr = queryDef.urlExpr(QueryAction.updateQueryRow, ss.getContainer());
                if (expr != null)
                {
                    String url = expr.eval(Collections.singletonMap("RowId", _material.getRowId()));
                    updateLinks.append("[<a href=\"").append(url).append("\">edit</a>] ");
                }
            }

            if (getContainer().hasPermission(getUser(), InsertPermission.class))
            {
                ActionURL deriveURL = new ActionURL(DeriveSamplesChooseTargetAction.class, getContainer());
                deriveURL.addParameter("rowIds", _material.getRowId());

                updateLinks.append("[<a href=\"").append(deriveURL).append("\">derive samples from this sample</a>] ");
            }

            vbox.addView(new HtmlView(updateLinks.toString()));

            ExperimentRunListView runListView = ExperimentRunListView.createView(getViewContext(), ExperimentRunType.ALL_RUNS_TYPE, true);
            runListView.setShowRecordSelectors(false);
            runListView.getRunTable().setRuns(successorRuns);
            runListView.getRunTable().setContainerFilter(new ContainerFilter.AllFolders(getUser()));
            runListView.setAllowableContainerFilterTypes(ContainerFilter.Type.Current, ContainerFilter.Type.CurrentAndSubfolders, ContainerFilter.Type.AllFolders);
            runListView.setTitle("Runs using this material or a derived material");

            Set<ExpMaterial> parentMaterials = getParentMaterials();
            QueryView parentSamplesView = createMaterialsView(parentMaterials, "parentMaterials", "Parent Samples");
            vbox.addView(parentSamplesView);

            Set<ExpMaterial> childMaterials = getChildMaterials();

            QueryView childSamplesView = createMaterialsView(childMaterials, "childMaterials", "Child Samples");
            vbox.addView(childSamplesView);

            vbox.addView(runListView);

            return vbox;
        }

        private QueryView createMaterialsView(final Set<ExpMaterial> materials, String dataRegionName, String title)
        {
            // Strip out materials in folders that the user can't see - this lets us avoid a container filter that
            // enforces the permissions when we do the query
            String typeName = null;
            boolean sameType = true;
            for (Iterator<ExpMaterial> iter = materials.iterator(); iter.hasNext(); )
            {
                ExpMaterial material = iter.next();
                if (!material.getContainer().hasPermission(getUser(), ReadPermission.class))
                {
                    iter.remove();
                }

                String type = material.getCpasType();
                if (sameType)
                {
                    if (typeName == null)
                        typeName = type;
                    else if (!typeName.equals(type))
                    {
                        typeName = null;
                        sameType = false;
                    }
                }
            }
            final ExpSampleSet ss;
            if (sameType && typeName != null && !"Material".equals(typeName) && !"Sample".equals(typeName))
                ss = ExperimentService.get().getSampleSet(typeName);
            else
                ss = null;

            QuerySettings settings = new QuerySettings(getViewContext(), dataRegionName);
            UserSchema schema;
            if (ss == null)
            {
                schema = new ExpSchema(getUser(), getContainer());
                settings.setQueryName(ExpSchema.TableType.Materials.toString());
            }
            else
            {
                schema = new SamplesSchema(getUser(), getContainer());
                settings.setQueryName(ss.getName());
            }
            settings.setSchemaName(schema.getSchemaName());
            settings.setAllowChooseQuery(false);
            QueryView materialsView = new QueryView(schema, settings, null)
            {
                protected TableInfo createTable()
                {
                    ExpMaterialTable table = ExperimentServiceImpl.get().createMaterialTable(ExpSchema.TableType.Materials.toString(), getSchema());
                    table.setMaterials(materials);
                    table.populate(ss, false);
                    // We've already set an IN clause that restricts us to showing just data that we have permission
                    // to view
                    table.setContainerFilter(ContainerFilter.EVERYTHING);

                    List<FieldKey> defaultVisibleColumns = new ArrayList<FieldKey>();
                    if (ss == null)
                    {
                        // The table columns without any of the active SampleSet property columns
                        defaultVisibleColumns.add(FieldKey.fromParts(ExpMaterialTable.Column.Name));
                        defaultVisibleColumns.add(FieldKey.fromParts(ExpMaterialTable.Column.SampleSet));
                        defaultVisibleColumns.add(FieldKey.fromParts(ExpMaterialTable.Column.Flag));
                    }
                    else
                    {
                        defaultVisibleColumns.addAll(table.getDefaultVisibleColumns());
                    }
                    defaultVisibleColumns.add(FieldKey.fromParts(ExpMaterialTable.Column.Created));
                    defaultVisibleColumns.add(FieldKey.fromParts(ExpMaterialTable.Column.CreatedBy));
                    defaultVisibleColumns.add(FieldKey.fromParts(ExpMaterialTable.Column.Run));
                    table.setDefaultVisibleColumns(defaultVisibleColumns);
                    return table;
                }
            };
            materialsView.setAllowableContainerFilterTypes();
            materialsView.setShowBorders(true);
            materialsView.setShowDetailsColumn(false);
            materialsView.setShowExportButtons(false);
            materialsView.setShadeAlternatingRows(true);
            materialsView.setButtonBarPosition(DataRegion.ButtonBarPosition.BOTTOM);
            materialsView.setTitle(title);
            return materialsView;
        }

        private Set<ExpMaterial> getChildMaterials() throws SQLException
        {
            if (isUnknownMaterial(_material))
            {
                return Collections.emptySet();
            }
            List<ExpRun> runsToInvestigate = new ArrayList<ExpRun>();
            runsToInvestigate.addAll(Arrays.asList(ExperimentServiceImpl.get().getRunsUsingMaterials(_material.getRowId())));
            runsToInvestigate.remove(_material.getRun());
            Set<ExpMaterial> result = new HashSet<ExpMaterial>();
            Set<ExpRun> investigatedRuns = new HashSet<ExpRun>();

            while (!runsToInvestigate.isEmpty())
            {
                ExpRun childRun = runsToInvestigate.remove(0);
                if (!investigatedRuns.contains(childRun))
                {
                    investigatedRuns.add(childRun);

                    List<ExpMaterial> materialOutputs = removeUnknownMaterials(childRun.getMaterialOutputs());
                    result.addAll(materialOutputs);

                    for (ExpMaterial materialOutput : materialOutputs)
                    {
                        runsToInvestigate.addAll(Arrays.asList(ExperimentServiceImpl.get().getRunsUsingMaterials(materialOutput.getRowId())));
                    }

                    runsToInvestigate.addAll(ExperimentServiceImpl.get().getRunsUsingDatas(childRun.getDataOutputs()));
                }
            }
            result.remove(_material);
            return result;
        }

        private boolean isUnknownMaterial(ExpMaterial material)
        {
            return "Unknown".equals(material.getName()) &&
                    ParticipantVisit.ASSAY_RUN_MATERIAL_NAMESPACE.equals(material.getLSIDNamespacePrefix());
        }

        private List<ExpMaterial> removeUnknownMaterials(Iterable<ExpMaterial> materials)
        {
            // Filter out the generic unknown material, which is just a placeholder and doesn't represent a real
            // parent
            ArrayList<ExpMaterial> result = new ArrayList<ExpMaterial>();
            for (ExpMaterial material : materials)
            {
                if (!isUnknownMaterial(material))
                {
                    result.add(material);
                }
            }
            return result;
        }

        private Set<ExpMaterial> getParentMaterials()
        {
            if (isUnknownMaterial(_material))
            {
                return Collections.emptySet();
            }
            List<ExpRun> runsToInvestigate = new ArrayList<ExpRun>();
            ExpRun parentRun = _material.getRun();
            if (parentRun != null)
            {
                runsToInvestigate.add(parentRun);
            }
            Set<ExpRun> investigatedRuns = new HashSet<ExpRun>();
            final Set<ExpMaterial> parentMaterials = new HashSet<ExpMaterial>();
            while (!runsToInvestigate.isEmpty())
            {
                ExpRun predecessorRun = runsToInvestigate.remove(0);
                investigatedRuns.add(predecessorRun);

                for (ExpData d : predecessorRun.getDataInputs().keySet())
                {
                    ExpRun dRun = d.getRun();
                    if (dRun != null && !investigatedRuns.contains(dRun))
                    {
                        runsToInvestigate.add(dRun);
                    }
                }
                for (ExpMaterial m : removeUnknownMaterials(predecessorRun.getMaterialInputs().keySet()))
                {
                    ExpRun mRun = m.getRun();
                    if (mRun != null)
                    {
                        if (!investigatedRuns.contains(mRun))
                        {
                            runsToInvestigate.add(mRun);
                        }
                    }
                    parentMaterials.add(m);
                }
            }
            return parentMaterials;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root = appendRootNavTrail(root);
            root.addChild("Sample Sets", ExperimentUrlsImpl.get().getShowSampleSetListURL(getContainer()));
            ExpSampleSet sampleSet = _material.getSampleSet();
            if (sampleSet != null)
            {
                root.addChild(sampleSet.getName(), ExperimentUrlsImpl.get().getShowSampleSetURL(sampleSet));
            }
            root.addChild("Sample " + _material.getName());
            return root;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class ShowRunGraphAction extends AbstractShowRunAction
    {
        protected HttpView createLowerView(ExpRunImpl experimentRun, BindException errors)
        {
            return new VBox(
                    new ToggleRunView(experimentRun, false, true, true),
                    new ExperimentRunGraphView(experimentRun, false));
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class DownloadGraphAction extends SimpleViewAction<ExperimentRunForm>
    {
        public ModelAndView getView(ExperimentRunForm form, BindException errors) throws Exception
        {
            boolean detail = form.isDetail();
            String focus = form.getFocus();

            ExpRunImpl experimentRun = form.lookupRun();
            ensureCorrectContainer(getContainer(), experimentRun, getViewContext());

            ExperimentRunGraph.RunGraphFiles files;
            try
            {
                files = ExperimentRunGraph.generateRunGraph(getViewContext(), experimentRun, detail, focus);
            }
            catch (ExperimentException e)
            {
                PageFlowUtil.streamTextAsImage(getViewContext().getResponse(), "ERROR: " + e.getMessage(), 600, 150, java.awt.Color.RED);
                return null;
            }

            try
            {
                PageFlowUtil.streamFile(getViewContext().getResponse(), files.getImageFile().getAbsolutePath(), false);
            }
            catch (FileNotFoundException e)
            {
                getViewContext().getResponse().sendRedirect(getViewContext().getRequest().getContextPath() + "/Experiment/ExperimentRunNotFound.gif");
            }
            finally
            {
                files.release();
            }
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            throw new UnsupportedOperationException();
        }
    }

    private abstract class AbstractShowRunAction extends SimpleViewAction<ExperimentRunForm>
    {
        private ExpRunImpl _experimentRun;

        public ModelAndView getView(ExperimentRunForm form, BindException errors) throws Exception
        {
            _experimentRun = form.lookupRun();
            ensureCorrectContainer(getContainer(), _experimentRun, getViewContext());

            VBox vbox = new VBox();

            JspView<ExpRun> detailsView = new JspView<ExpRun>("/org/labkey/experiment/ExperimentRunDetails.jsp", _experimentRun);
            detailsView.setTitle("Standard Properties");

            CustomPropertiesView cpv = new CustomPropertiesView(_experimentRun.getLSID(), getViewContext().cloneActionURL(), getContainer());

            vbox.addView(new StandardAndCustomPropertiesView(detailsView, cpv));
            vbox.addView(new ExperimentRunGroupsView(getUser(), getContainer(), _experimentRun, getViewContext().getActionURL()));
            vbox.addView(createLowerView(_experimentRun, errors));
            return vbox;
        }

        protected abstract HttpView createLowerView(ExpRunImpl experimentRun, BindException errors);

        public NavTree appendNavTrail(NavTree root)
        {
            return appendRootNavTrail(root).addChild("Experiment Run " + _experimentRun.getName());
        }
    }

    public static class ToggleRunExperimentMembershipForm
    {
        private int _runId;
        private int _experimentId;
        private boolean _included;

        public int getRunId()
        {
            return _runId;
        }

        public void setRunId(int runId)
        {
            _runId = runId;
        }

        public int getExperimentId()
        {
            return _experimentId;
        }

        public void setExperimentId(int experimentId)
        {
            _experimentId = experimentId;
        }

        public boolean isIncluded()
        {
            return _included;
        }

        public void setIncluded(boolean included)
        {
            _included = included;
        }
    }

    @RequiresPermissionClass(UpdatePermission.class)
    public class ToggleRunExperimentMembershipAction extends SimpleViewAction<ToggleRunExperimentMembershipForm>
    {
        public NavTree appendNavTrail(NavTree root)
        {
            throw new UnsupportedOperationException();
        }

        public ModelAndView getView(ToggleRunExperimentMembershipForm form, BindException errors) throws Exception
        {
            ExpRun run = ExperimentService.get().getExpRun(form.getRunId());
            // Check if the user has permission to see this run
            if (run == null || !run.getContainer().hasPermission(getUser(), ReadPermission.class))
            {
                return HttpView.throwNotFound();
            }

            ExpExperiment exp = ExperimentService.get().getExpExperiment(form.getExperimentId());
            if (exp == null)
            {
                return HttpView.throwNotFound();
            }
            ExpExperiment[] experiments = ExperimentService.get().getExperiments(run.getContainer(), getUser(), true, false);
            // Check if this
            if (!Arrays.asList(experiments).contains(exp))
            {
                return HttpView.throwNotFound();
            }
            if (!exp.getContainer().hasPermission(getUser(), UpdatePermission.class))
            {
                return HttpView.throwUnauthorized();
            }

            if (form.isIncluded())
            {
                exp.addRuns(getViewContext().getUser(), run);
            }
            else
            {
                exp.removeRun(getViewContext().getUser(), run);
            }

            return null;
        }
    }

    public class ToggleRunView extends HtmlView
    {
        public ToggleRunView(ExpRun expRun, boolean showGraphSummary, boolean showGraphDetail, boolean showText)
        {
            super(null);
            StringBuilder sb = new StringBuilder();
            if (showGraphSummary)
            {
                sb.append("[<a href=\"");
                sb.append(ExperimentUrlsImpl.get().getRunGraphURL(expRun));
                sb.append("\">graph summary view</a>] ");
            }
            else
            {
                sb.append("[<strong>graph summary view</strong>] ");
            }
            if (showGraphDetail)
            {
                sb.append("[<a href=\"");
                sb.append(ExperimentUrlsImpl.get().getRunGraphDetailURL(expRun));
                sb.append("\">graph detail view</a>] ");
            }
            else
            {
                sb.append("[<strong>graph detail view</strong>] ");
            }
            if (showText)
            {
                sb.append("[<a href=\"");
                sb.append(ExperimentUrlsImpl.get().getRunTextURL(expRun));
                sb.append("\">text view</a>] ");
            }
            else
            {
                sb.append("[<strong>text view</strong>] ");
            }
            File runRoot = expRun.getFilePathRoot();
            if (runRoot != null && NetworkDrive.exists(runRoot))
            {
                if (!runRoot.isDirectory())
                {
                    runRoot = runRoot.getParentFile();
                }
                PipeRoot pipelineRoot = PipelineService.get().findPipelineRoot(expRun.getContainer());
                if (pipelineRoot != null)
                {
                    if (URIUtil.isDescendant(pipelineRoot.getUri(), runRoot.toURI()))
                    {
                        String path = runRoot.toURI().toString().substring(pipelineRoot.getUri().toString().length());
                        sb.append("[<a href=\"");
                        sb.append(PageFlowUtil.urlProvider(PipelineUrls.class).urlBrowse(expRun.getContainer(), null, path));
                        sb.append("\">files view</a>] ");
                    }
                }
            }

            sb.append("[<a onclick=\"document.getElementById('exportFilesForm').submit(); return false;\">download all files</a>]");
            sb.append("<form id=\"exportFilesForm\" method=\"post\" action=\"");
            sb.append(new ActionURL(ExportRunFilesAction.class, expRun.getContainer()));
            sb.append("\"><input type=\"hidden\" value=\"ExportSingleRun\" name=\"");
            sb.append(DataRegionSelection.DATA_REGION_SELECTION_KEY);
            sb.append("\" /><input type=\"hidden\" name=\"");
            sb.append(DataRegion.SELECT_CHECKBOX_NAME);
            sb.append("\" value=\"");
            sb.append(expRun.getRowId());
            sb.append("\" /><input type=\"hidden\" name=\"zipFileName\" value=\"");
            sb.append(PageFlowUtil.filter(expRun.getName()));
            sb.append(".zip\" /></form>");

            setHtml(sb.toString());
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class ShowRunTextAction extends AbstractShowRunAction
    {
        protected HttpView createLowerView(ExpRunImpl expRun, BindException errors)
        {
            JspView<ExpRun> applicationsView = new JspView<ExpRun>("/org/labkey/experiment/ProtocolApplications.jsp", expRun);
            applicationsView.setFrame(WebPartView.FrameType.TITLE);
            applicationsView.setTitle("Protocol Applications");

            HtmlView toggleView = new ToggleRunView(expRun, true, true, false);

            QuerySettings runDataInputsSettings = new QuerySettings(getViewContext(), "RunDataInputs", ExpSchema.TableType.DataInputs.name());
            UsageQueryView runDataInputsView = new UsageQueryView("Data Inputs", getViewContext(), expRun, ExpProtocol.ApplicationType.ExperimentRun, runDataInputsSettings, errors);
            runDataInputsView.setButtonBarPosition(DataRegion.ButtonBarPosition.NONE);

            QuerySettings runDataOutputsSettings = new QuerySettings(getViewContext(), "RunDataOutputs", ExpSchema.TableType.DataInputs.name());
            UsageQueryView runDataOutputsView = new UsageQueryView("Data Outputs", getViewContext(), expRun, ExpProtocol.ApplicationType.ExperimentRunOutput, runDataOutputsSettings, errors);

            QuerySettings runMaterialInputsSetting = new QuerySettings(getViewContext(), "RunMaterialInputs", ExpSchema.TableType.MaterialInputs.name());
            UsageQueryView runMaterialInputsView = new UsageQueryView("Material Inputs", getViewContext(), expRun, ExpProtocol.ApplicationType.ExperimentRun, runMaterialInputsSetting, errors);
            runMaterialInputsView.setButtonBarPosition(DataRegion.ButtonBarPosition.NONE);

            QuerySettings runMaterialOutputsSettings = new QuerySettings(getViewContext(), "RunMaterialOutputs", ExpSchema.TableType.MaterialInputs.name());
            UsageQueryView runMaterialOutputsView = new UsageQueryView("Material Outputs", getViewContext(), expRun, ExpProtocol.ApplicationType.ExperimentRunOutput, runMaterialOutputsSettings, errors);

            HBox inputsView = new HBox(runDataInputsView, runMaterialInputsView);
            HBox outputsView = new HBox(runDataOutputsView, runMaterialOutputsView);

            return new VBox(toggleView, inputsView, outputsView, applicationsView);
        }
    }

    private static class UsageQueryView extends QueryView
    {
        private final ExpRun _run;
        private final ExpProtocol.ApplicationType _type;

        public UsageQueryView(String title, ViewContext context, ExpRun run, ExpProtocol.ApplicationType type,
                                   QuerySettings settings, BindException errors)
        {
            super(new ExpSchema(context.getUser(), context.getContainer()), settings, errors);
            setTitle(title);
            setButtonBarPosition(DataRegion.ButtonBarPosition.BOTTOM);
            setFrame(FrameType.TITLE);
            settings.setAllowChooseQuery(false);
            _run = run;
            _type = type;
            setShowExportButtons(false);
            setShowPagination(false);
            setAllowableContainerFilterTypes(Collections.<ContainerFilter.Type>emptyList());
        }

        @Override
        protected TableInfo createTable()
        {
            ExpInputTable tableInfo = (ExpInputTable)super.createTable();
            tableInfo.setRun(_run, _type);
            return tableInfo;
        }

    }

    @RequiresPermissionClass(ReadPermission.class)
    public class ShowRunGraphDetailAction extends AbstractShowRunAction
    {
        protected HttpView createLowerView(ExpRunImpl run, BindException errors)
        {
            ExperimentRunGraphView gw = new ExperimentRunGraphView(run, true);
            if (null != getViewContext().getActionURL().getParameter("focus"))
                gw.setFocus(getViewContext().getActionURL().getParameter("focus"));
            return new VBox(new ToggleRunView(run, true, false, true), gw);
        }
    }

    private abstract class AbstractDataAction extends SimpleViewAction<DataForm>
    {
        protected ExpDataImpl _data;

        public final ModelAndView getView(DataForm form, BindException errors) throws Exception
        {
            _data = form.lookupData();
            if (_data == null)
            {
                HttpView.throwNotFound("Could not find a data with RowId " + form.getRowId());
            }

            ensureCorrectContainer(getContainer(), _data, getViewContext());
            return getDataView(form, errors);
        }

        protected abstract ModelAndView getDataView(DataForm form, BindException errors) throws Exception;

        public NavTree appendNavTrail(NavTree root)
        {
            return appendRootNavTrail(root).addChild("Data File " + _data.getName());
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class ShowDataAction extends AbstractDataAction
    {
        public ModelAndView getDataView(DataForm form, BindException errors) throws Exception
        {
            Container c = getContainer();
            String relativePath = null;
            if (c.hasPermission(getUser(), InsertPermission.class))
            {
                PipeRoot root = PipelineService.get().findPipelineRoot(c);
                if (root != null)
                {
                    URI uri = root.getUri();
                    if (uri != null)
                    {
                        File rootFile = new File(uri);
                        File dataFile = _data.getFile();
                        if (dataFile != null)
                        {
                            File dataParent = dataFile.getParentFile();
                            while (dataParent != null)
                            {
                                if (dataParent.equals(rootFile))
                                {
                                    relativePath = FileUtil.relativizeUnix(rootFile, dataFile.getParentFile(), true);
                                    break;
                                }
                                dataParent = dataParent.getParentFile();
                            }
                        }
                    }
                }
            }

            ExpRun run = _data.getRun();
            ExpProtocol sourceProtocol = _data.getSourceProtocol();
            ExpProtocolApplication sourceProtocolApplication = _data.getSourceApplication();

            DataRegion dr = new DataRegion();
            dr.addColumns(ExperimentServiceImpl.get().getTinfoData().getUserEditableColumns());
            dr.removeColumns("DataFileUrl", "RowId", "RunId", "LSID", "CpasType", "SourceApplicationId");
            dr.addDisplayColumn(new DataFileURLDisplayColumn(_data));
            dr.addDisplayColumn(new ExperimentRunDisplayColumn(run, "Source Experiment Run"));
            dr.addDisplayColumn(new ProtocolDisplayColumn(sourceProtocol, "Source Protocol"));
            dr.addDisplayColumn(new ProtocolApplicationDisplayColumn(sourceProtocolApplication, "Source Protocol Application"));
            dr.addDisplayColumn(new LineageGraphDisplayColumn(_data, run));
            DetailsView detailsView = new DetailsView(dr, _data.getRowId());
            detailsView.setTitle("Standard Properties");
            ButtonBar bb = new ButtonBar();
            bb.setStyle(ButtonBar.Style.separateButtons);

            ActionURL viewDataURL = _data.findDataHandler().getContentURL(getContainer(), _data);
            if (viewDataURL != null)
            {
                bb.add(new ActionButton("View data", viewDataURL));
            }

            if (_data.isFileOnDisk())
            {
                bb.add(new ActionButton("View file", ExperimentUrlsImpl.get().getShowFileURL(c, _data, true)));
                bb.add(new ActionButton("Download file", ExperimentUrlsImpl.get().getShowFileURL(c, _data, false)));

                if (getContainer().hasPermission(getUser(), InsertPermission.class))
                {
                    ActionURL browseURL = PageFlowUtil.urlProvider(PipelineUrls.class).urlBrowse(getContainer(), getViewContext().getActionURL().toString(), relativePath);
                    bb.add(new ActionButton("Browse in pipeline", browseURL));
                }
            }
            dr.setButtonBarPosition(DataRegion.ButtonBarPosition.BOTTOM);
            dr.setButtonBar(bb);

            CustomPropertiesView cpv = new CustomPropertiesView(_data.getLSID(), getViewContext().cloneActionURL(), c);


            ExperimentRunListView runListView = ExperimentRunListView.createView(getViewContext(), ExperimentRunType.ALL_RUNS_TYPE, true);
            runListView.getRunTable().setInputData(_data);
            runListView.getRunTable().setContainerFilter(new ContainerFilter.AllFolders(getUser()));
            runListView.setTitle("Runs using this data as an input");
            VBox vbox = new VBox(new StandardAndCustomPropertiesView(detailsView, cpv), runListView);

            if (_data.isInlineImage() && _data.isFileOnDisk())
            {
                ActionURL showFileURL = new ActionURL(ShowFileAction.class, getContainer()).addParameter("rowId", _data.getRowId());
                HtmlView imageView = new HtmlView("<img src=\"" + showFileURL + "\"/>");
                return new VBox(vbox, imageView);
            }
            return vbox;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class ShowFileAction extends AbstractDataAction
    {
        protected ModelAndView getDataView(DataForm form, BindException errors) throws IOException
        {
            String dataURL = _data.getDataFileUrl();
            if (dataURL == null)
            {
                HttpView.throwNotFound("Unable to find file for " + _data.getName());
            }
            URI dataURI;
            try
            {
                dataURI = new URI(dataURL);
            }
            catch (URISyntaxException use)
            {
                throw new UnexpectedException(use);
            }

            File realContent = new File(dataURI);
            if (!realContent.exists() || !realContent.isFile())
            {
                HttpView.throwNotFound("Data file, " + realContent + ", does not exist on disk");
            }

            try
            {
                boolean inline = _data.isInlineImage() || form.isInline() || "inlineImage".equalsIgnoreCase(form.getFormat());
                if (_data.isInlineImage() && form.getMaxDimension() != null && _data.isFileOnDisk())
                {
                    ByteArrayOutputStream bOut = new ByteArrayOutputStream();
                    BufferedImage image = ImageIO.read(_data.getDataFile());
                    int imageMax = Math.max(image.getHeight(), image.getWidth());
                    if (imageMax > form.getMaxDimension().intValue())
                    {
                        double scale = (double)form.getMaxDimension().intValue() / (double)imageMax;
                        ImageUtil.resizeImage(image, bOut, scale, 1);
                        PageFlowUtil.streamFileBytes(getViewContext().getResponse(), realContent.getName(), bOut.toByteArray(), !inline);
                        return null;
                    }
                }

                String lowerCaseFileName = realContent.getName().toLowerCase();
                boolean extended = "jsonTSVExtended".equalsIgnoreCase(form.getFormat());
                if ("jsonTSV".equalsIgnoreCase(form.getFormat()) || extended)
                {
                    JSONArray sheetsArray = new JSONArray();
                    if (lowerCaseFileName.endsWith(".xls"))
                    {
                        FileInputStream fIn = null;
                        try
                        {
                            fIn = new FileInputStream(realContent);
                            WorkbookSettings settings = new WorkbookSettings();
                            settings.setGCDisabled(true);
                            Workbook workbook;
                            try
                            {
                                workbook = Workbook.getWorkbook(fIn, settings);
                            }
                            catch (BiffException e)
                            {
                                throw new NotFoundException("Unable to parse file as Excel data: " + e);
                            }
                            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++)
                            {
                                JSONArray rowsArray = new JSONArray();
                                Sheet sheet = workbook.getSheet(sheetIndex);
                                for (int rowIndex = 0; rowIndex < sheet.getRows(); rowIndex++)
                                {
                                    Cell[] rowCells = sheet.getRow(rowIndex);
                                    JSONArray rowArray = new JSONArray();
                                    for (Cell cell : rowCells)
                                    {
                                        Object value;
                                        String formattedValue;
                                        String formatString = null;
                                        JSONObject metadataMap = new JSONObject();
                                        if (cell instanceof NumberCell)
                                        {
                                            NumberCell numberCell = (NumberCell) cell;
                                            value = numberCell.getValue();
                                            java.text.NumberFormat numberFormat = numberCell.getNumberFormat();
                                            formattedValue = numberFormat.format(numberCell.getValue());
                                            if (numberCell.getCellFormat().getFormat().getFormatString() != null &&
                                                !"".equals(numberCell.getCellFormat().getFormat().getFormatString()) &&
                                                numberFormat instanceof DecimalFormat)
                                            {
                                                formatString = ((DecimalFormat)numberFormat).toPattern();
                                            }
                                        }
                                        else if (cell instanceof BooleanCell)
                                        {
                                            BooleanCell booleanCell = (BooleanCell) cell;
                                            value = booleanCell.getValue();
                                            formattedValue = value.toString();
                                        }
                                        else if (cell instanceof DateCell)
                                        {
                                            DateCell dateCell = (DateCell) cell;
                                            value = dateCell.getDate();
                                            formattedValue = dateCell.getDateFormat().format(dateCell.getDate());
                                            java.text.DateFormat dateFormat = dateCell.getDateFormat();
                                            metadataMap.put("timeOnly", dateCell.isTime());
                                            if (dateCell.getCellFormat().getFormat().getFormatString() != null &&
                                                !"".equals(dateCell.getCellFormat().getFormat().getFormatString()) &&
                                                dateFormat instanceof SimpleDateFormat)
                                            {
                                                formatString = ((SimpleDateFormat)dateFormat).toPattern();
                                            }
                                        }
                                        else
                                        {
                                            value = cell.getContents();
                                            if ("".equals(value))
                                            {
                                                value = null;
                                            }
                                            formattedValue = cell.getContents();
                                        }
                                        if (extended && cell.getCellFormat() != null)
                                        {
                                            metadataMap.put("value", value);
                                            if (formatString != null && !"".equals(formatString))
                                            {
                                                metadataMap.put("formatString", formatString);
                                            }
                                            metadataMap.put("formattedValue", formattedValue);
                                            rowArray.put(metadataMap);
                                        }
                                        else
                                        {
                                            rowArray.put(value);
                                        }
                                    }
                                    rowsArray.put(rowArray);
                                }
                                JSONObject sheetJSON = new JSONObject();
                                sheetJSON.put("name", sheet.getName());
                                sheetJSON.put("data", rowsArray);
                                sheetsArray.put(sheetJSON);
                            }
                        }
                        finally
                        {
                            if (fIn != null)
                                try { fIn.close(); } catch (IOException e) { /* fall through */ }
                        }
                    }
                    else if (lowerCaseFileName.endsWith(".tsv") || lowerCaseFileName.endsWith(".txt") || lowerCaseFileName.endsWith(".csv"))
                    {
                        TabLoader tabLoader = new TabLoader(realContent);
                        if (lowerCaseFileName.endsWith(".csv"))
                        {
                            tabLoader.parseAsCSV();
                        }
                        ColumnDescriptor[] cols = tabLoader.getColumns();
                        JSONArray rowsArray = new JSONArray();
                        JSONArray headerArray = new JSONArray();
                        for (ColumnDescriptor col : cols)
                        {
                            if (extended)
                            {
                                JSONObject valueObject = new JSONObject();
                                valueObject.put("value", col.name);
                                headerArray.put(valueObject);
                            }
                            else
                            {
                                headerArray.put(col.name);
                            }
                        }
                        rowsArray.put(headerArray);
                        for (Map<String, Object> rowMap : tabLoader)
                        {
                            JSONArray rowArray = new JSONArray();
                            for (ColumnDescriptor col : cols)
                            {
                                Object value = rowMap.get(col.name);
                                if (extended)
                                {
                                    JSONObject valueObject = new JSONObject();
                                    valueObject.put("value", value);
                                    rowArray.put(valueObject);
                                }
                                else
                                {
                                    rowArray.put(value);
                                }
                            }
                            rowsArray.put(rowArray);
                        }

                        JSONObject sheetJSON = new JSONObject();
                        sheetJSON.put("name", "flat");
                        sheetJSON.put("data", rowsArray);
                        sheetsArray.put(sheetJSON);
                    }
                    else
                    {
                        throw new FileNotFoundException("Unable to convert file " + realContent + " to tsv");
                    }
                    ApiJsonWriter writer = new ApiJsonWriter(getViewContext().getResponse());
                    JSONObject workbookJSON = new JSONObject();
                    workbookJSON.put("fileName", realContent.getName());
                    workbookJSON.put("sheets", sheetsArray);
                    writer.write(new ApiSimpleResponse(workbookJSON));
                    return null;
                }

                PageFlowUtil.streamFile(getViewContext().getResponse(), realContent.getAbsolutePath(), !inline);
            }
            catch (IOException e)
            {
                ApiJsonWriter writer = new ApiJsonWriter(getViewContext().getResponse());
                try
                {
                    writer.write(e);
                }
                catch (IllegalStateException ise)
                {
                    // Most likely that a disconnected client caused the IOException writing back the response
                }
            }

            return null;
        }
    }

    public static class ConvertArraysToExcelForm
    {
        private String _json;

        public String getJson()
        {
            return _json;
        }

        public void setJson(String json)
        {
            _json = json;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class ConvertArraysToExcelAction extends ExportAction<ConvertArraysToExcelForm>
    {
        public void export(ConvertArraysToExcelForm form, HttpServletResponse response, BindException errors) throws Exception
        {
            try
            {
                if (form.getJson() == null)
                {
                    throw new NotFoundException("Unable to convert to Excel - no spreadsheet data given");
                }
                JSONObject rootObject = new JSONObject(form.getJson());
                JSONArray sheetsArray = rootObject.getJSONArray("sheets");

                String filename = rootObject.has("fileName") ? rootObject.getString("fileName") : "ExcelExport.xls";

                WorkbookSettings settings = new WorkbookSettings();
                settings.setArrayGrowSize(300000);
                WritableWorkbook workbook = Workbook.createWorkbook(response.getOutputStream(), settings);

                SimpleDateFormat dateFormat = new SimpleDateFormat(JSONObject.JAVASCRIPT_DATE_FORMAT);

                for (int sheetIndex = 0; sheetIndex < sheetsArray.length(); sheetIndex++)
                {
                    JSONObject sheetObject = sheetsArray.getJSONObject(sheetIndex);
                    String sheetName = sheetObject.has("name") ? sheetObject.getString("name") : "Sheet" + sheetIndex;
                    sheetName = ExcelWriter.cleanSheetName(sheetName);
                    WritableSheet sheet = workbook.createSheet(sheetName, sheetIndex);

                    WritableCellFormat defaultFormat = new WritableCellFormat();
                    WritableCellFormat defaultDateFormat = new WritableCellFormat(new DateFormat(DateUtil.getStandardDateFormatString()));
                    WritableCellFormat errorFormat = new WritableCellFormat();
                    errorFormat.setBackground(jxl.format.Colour.RED);

                    JSONArray rowsArray = sheetObject.getJSONArray("data");
                    for (int rowIndex = 0; rowIndex < rowsArray.length(); rowIndex++)
                    {
                        JSONArray rowArray = rowsArray.getJSONArray(rowIndex);
                        for (int colIndex = 0; colIndex < rowArray.length(); colIndex++)
                        {
                            Object value = rowArray.get(colIndex);
                            WritableCell cell = null;
                            JSONObject metadataObject = null;
                            WritableCellFormat cellFormat = defaultFormat;
                            if (value instanceof JSONObject)
                            {
                                metadataObject = (JSONObject)value;
                                value = metadataObject.get("value");
                            }
                            if (value instanceof java.lang.Number)
                            {
                                cell = new jxl.write.Number(colIndex, rowIndex, ((java.lang.Number) value).doubleValue());
                                if (metadataObject != null && metadataObject.has("formatString"))
                                {
                                    cellFormat = new WritableCellFormat(new NumberFormat(metadataObject.getString("formatString")));
                                }
                            }
                            else if (value instanceof Boolean)
                            {
                                cell = new jxl.write.Boolean(colIndex, rowIndex, ((Boolean) value).booleanValue());
                            }
                            else if (value instanceof String)
                            {
                                try
                                {
                                    // JSON has no date literal syntax so try to parse all Strings as dates
                                    Date d = dateFormat.parse((String)value);
                                    try
                                    {
                                        if (metadataObject != null && metadataObject.has("formatString"))
                                        {
                                            cellFormat = new WritableCellFormat(new DateFormat(metadataObject.getString("formatString")));
                                        }
                                        else
                                        {
                                            cellFormat = defaultDateFormat;
                                        }
                                        boolean timeOnly = metadataObject != null && metadataObject.has("timeOnly") && Boolean.TRUE.equals(metadataObject.get("timeOnly"));
                                        cell = new DateTime(colIndex, rowIndex, d, cellFormat, timeOnly);
                                    }
                                    catch (IllegalArgumentException e)
                                    {
                                        // Invalid date format
                                        cellFormat = errorFormat;
                                        cell = new Label(colIndex, rowIndex, e.getMessage());
                                    }
                                }
                                catch (ParseException e)
                                {
                                    // Not a date
                                    cell = new Label(colIndex, rowIndex, (String)value);
                                }
                            }
                            else if (value != null)
                            {
                                cell = new Label(colIndex, rowIndex, value.toString());
                            }
                            if (cell != null)
                            {
                                cell.setCellFormat(cellFormat);
                                sheet.addCell(cell);
                            }
                        }
                    }
                }

                response.setContentType("application/vnd.ms-excel");
                response.setHeader("Content-disposition", "attachment; filename=\"" + filename +"\"");
                response.setHeader("Pragma", "private");
                response.setHeader("Cache-Control", "private");
                workbook.write();
                workbook.close();
            }
            catch (JSONException e)
            {
                HttpView errorView = ExceptionUtil.getErrorView(HttpServletResponse.SC_BAD_REQUEST, "Failed to convert to Excel - invalid input", e, getViewContext().getRequest(), false);
                errorView.render(getViewContext().getRequest(), getViewContext().getResponse());
            }
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class ShowApplicationAction extends SimpleViewAction<ExpObjectForm>
    {
        private ExpProtocolApplicationImpl _app;
        private ExpRun _run;

        public ModelAndView getView(ExpObjectForm form, BindException errors) throws Exception
        {
            _app = ExperimentServiceImpl.get().getExpProtocolApplication(form.getRowId());
            if (_app == null)
            {
                HttpView.throwNotFound("Could not find Protocol Application");
            }
            _run = _app.getRun();
            if (_run == null)
            {
                HttpView.throwNotFound("No experiment run associated with Protocol Application");
            }
            ensureCorrectContainer(getContainer(), _app, getViewContext());

            ExpProtocol protocol = _app.getProtocol();

            DataRegion dr = new DataRegion();
            dr.addColumns(ExperimentServiceImpl.get().getTinfoProtocolApplication().getUserEditableColumns());
            DetailsView detailsView = new DetailsView(dr, form.getRowId());
            dr.removeColumns("RunId", "ProtocolLSID", "RowId", "LSID");
            dr.addDisplayColumn(new ExperimentRunDisplayColumn(_run));
            dr.addDisplayColumn(new ProtocolDisplayColumn(protocol));
            dr.addDisplayColumn(new LineageGraphDisplayColumn(_app, _run));
            detailsView.setTitle("Protocol Application");
            dr.setButtonBar(ButtonBar.BUTTON_BAR_EMPTY);

            Container c = getContainer();
            ApplicationOutputGrid outMGrid = new ApplicationOutputGrid(c, _app.getRowId(), ExperimentServiceImpl.get().getTinfoMaterial());
            ApplicationOutputGrid outDGrid = new ApplicationOutputGrid(c, _app.getRowId(), ExperimentServiceImpl.get().getTinfoData());
            Map<String, AbstractParameter> map = new HashMap<String, AbstractParameter>();
            for (ProtocolApplicationParameter param : ExperimentService.get().getProtocolApplicationParameters(_app.getRowId()))
            {
                map.put(param.getOntologyEntryURI(), param);
            }

            JspView<Map<String, ? extends AbstractParameter>> paramsView = new JspView<Map<String, ? extends AbstractParameter>>("/org/labkey/experiment/Parameters.jsp", map);
            paramsView.setTitle("Protocol Application Parameters");

            return new VBox(detailsView, paramsView, outMGrid, outDGrid);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendRootNavTrail(root).addChild("Experiment Run", ExperimentUrlsImpl.get().getRunGraphDetailURL(_run)).addChild("Protocol Application " + _app.getName());
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class ShowProtocolGridAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new ProtocolWebPart(false, getViewContext());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendRootNavTrail(root).addChild("Protocols");
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class ProtocolDetailsAction extends SimpleViewAction<ExpObjectForm>
    {
        private ExpProtocol _protocol;

        public ModelAndView getView(ExpObjectForm form, BindException errors) throws Exception
        {
            _protocol = ExperimentService.get().getExpProtocol(form.getRowId());
            if (_protocol == null)
            {
                _protocol = ExperimentServiceImpl.get().getExpProtocol(form.getLSID());
            }

            if (_protocol == null)
            {
                HttpView.throwNotFound("Unable to find a matching protocol");
            }
            ensureCorrectContainer(getContainer(), _protocol, getViewContext());

            JspView<ExpProtocol> detailsView = new JspView<ExpProtocol>("/org/labkey/experiment/ProtocolDetails.jsp", _protocol);
            detailsView.setTitle("Standard Properties");

            CustomPropertiesView cpv = new CustomPropertiesView(_protocol.getLSID(), getViewContext().cloneActionURL(), getContainer());
            ProtocolParametersView parametersView = new ProtocolParametersView(_protocol);
            ProtocolListView listView = new ProtocolListView(_protocol, getContainer());

            ExpSchema schema = new ExpSchema(getUser(), getContainer());
            ExperimentRunListView runView = new ExperimentRunListView(schema, ExperimentRunListView.getRunListQuerySettings(schema, getViewContext(), ExpSchema.TableType.Runs.name(), true), ExperimentRunType.ALL_RUNS_TYPE)
            {
                public DataView createDataView()
                {
                    DataView result = super.createDataView();
                    result.getRenderContext().setBaseFilter(new SimpleFilter("Protocol/LSID", _protocol.getLSID()));
                    return result;
                }
            };

            runView.setTitle("Runs Using This Protocol");

            return new VBox(new StandardAndCustomPropertiesView(detailsView, cpv), parametersView, listView, runView);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendRootNavTrail(root).addChild("Protocols", ExperimentUrlsImpl.get().getProtocolGridURL(getContainer())).addChild("Protocol: " + _protocol.getName());
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class ProtocolPredecessorsAction extends SimpleViewAction
    {
        private ExpProtocol _parentProtocol;
        private ProtocolActionStepDetail _actionStep;

        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            ActionURL url = getViewContext().getActionURL();

            String parentProtocolLSID = url.getParameter("ParentLSID");
            int actionSequence = 0;
            try
            {
                actionSequence = Integer.parseInt(url.getParameter("Sequence"));
            }
            catch (NumberFormatException e)
            {
                HttpView.throwNotFound("Could not find SequenceId " + url.getParameter("Sequence"));
            }

            _parentProtocol = ExperimentService.get().getExpProtocol(parentProtocolLSID);
            if (_parentProtocol == null)
            {
                HttpView.throwNotFound("Unable to find a matching protocol");
            }

            ensureCorrectContainer(getContainer(), _parentProtocol, getViewContext());

            _actionStep = ExperimentServiceImpl.get().getProtocolActionStepDetail(parentProtocolLSID, actionSequence);

            if (_actionStep == null)
            {
                throw new NotFoundException("Unable to find a matching protocol action step");
            }

            ExpProtocol childProtocol = ExperimentService.get().getExpProtocol(_actionStep.getChildProtocolLSID());

            JspView<ExpProtocol> detailsView = new JspView<ExpProtocol>("/org/labkey/experiment/ProtocolDetails.jsp", childProtocol);
            detailsView.setTitle("Standard Properties");

            CustomPropertiesView cpv = new CustomPropertiesView(childProtocol.getLSID(), getViewContext().cloneActionURL(), getContainer());

            ProtocolParametersView parametersView = new ProtocolParametersView(childProtocol);
            ProtocolSuccessorPredecessorView predecessorView = new ProtocolSuccessorPredecessorView(parentProtocolLSID, actionSequence, getContainer(), "PredecessorChildLSID", "PredecessorSequence", "ActionSequence", "Protocol Predecessors");
            ProtocolSuccessorPredecessorView successorView = new ProtocolSuccessorPredecessorView(parentProtocolLSID, actionSequence, getContainer(), "ChildProtocolLSID", "ActionSequence", "PredecessorSequence", "Protocol Successors");
            return new VBox(new StandardAndCustomPropertiesView(detailsView, cpv), parametersView, predecessorView, successorView);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendRootNavTrail(root).
                    addChild("Protocols", ExperimentUrlsImpl.get().getProtocolGridURL(getContainer())).
                    addChild("Parent Protocol", ExperimentUrlsImpl.get().getProtocolDetailsURL(_parentProtocol)).
                    addChild("Protocol: " + _actionStep.getName());
        }
    }

    public static class DataForm
    {
        private boolean _inline;
        private int _rowId;
        private String _lsid;
        private Integer _maxDimension;
        private String _format;

        public boolean isInline()
        {
            return _inline;
        }

        public void setInline(boolean inline)
        {
            _inline = inline;
        }

        public int getRowId()
        {
            return _rowId;
        }

        public void setRowId(int rowId)
        {
            _rowId = rowId;
        }

        public String getLsid()
        {
            return _lsid;
        }

        public void setLsid(String lsid)
        {
            _lsid = lsid;
        }

        public ExpDataImpl lookupData()
        {
            ExpDataImpl result = ExperimentServiceImpl.get().getExpData(getRowId());
            if (result == null && getLsid() != null)
            {
                result = ExperimentServiceImpl.get().getExpData(getLsid());
            }
            return result;
        }

        public Integer getMaxDimension()
        {
            return _maxDimension;
        }

        public void setMaxDimension(Integer maxDimension)
        {
            _maxDimension = maxDimension;
        }

        public String getFormat()
        {
            return _format;
        }

        public void setFormat(String format)
        {
            _format = format;
        }
    }

    public static class ExpObjectForm
    {
        private int _rowId;
        private String _lsid;

        public String getLsid()
        {
            return _lsid;
        }

        public void setLsid(String lsid)
        {
            _lsid = lsid;
        }

        public String getLSID()
        {
            return getLsid();
        }

        public void setLSID(String lsid)
        {
            setLsid(lsid);
        }

        public int getRowId()
        {
            return _rowId;
        }

        public void setRowId(int rowId)
        {
            _rowId = rowId;
        }
    }

    @RequiresPermissionClass(DeletePermission.class)
    public class DeleteSelectedExpRunsAction extends AbstractDeleteAction
    {
        public DeleteSelectedExpRunsAction()
        {
            super();
        }

        public ModelAndView getView(DeleteForm deleteForm, boolean reshow, BindException errors) throws Exception
        {
            List<ExpRun> runs = new ArrayList<ExpRun>();
            for (int runId : deleteForm.getIds(false))
            {
                ExpRun run = ExperimentService.get().getExpRun(runId);
                if (run != null)
                {
                    runs.add(run);
                }
            }

            return new ConfirmDeleteView("run", "showRunGraph", runs, deleteForm);
        }

        protected void deleteObjects(DeleteForm deleteForm) throws SQLException, ExperimentException
        {
            ExperimentService.get().deleteExperimentRunsByRowIds(getContainer(), getUser(), deleteForm.getIds(true));
        }
    }

    public static class DeleteRunForm
    {
        private int _runId;

        public int getRunId()
        {
            return _runId;
        }

        public void setRunId(int runId)
        {
            _runId = runId;
        }
    }

    /** Separate delete action from the client API */
    @RequiresPermissionClass(DeletePermission.class)
    public class DeleteRunAction extends MutatingApiAction<DeleteRunForm>
    {
        public ApiResponse execute(DeleteRunForm form, BindException errors) throws Exception
        {
            ExpRun run = ExperimentService.get().getExpRun(form.getRunId());
            if (run == null)
            {
                throw new NotFoundException("Could not find run with ID " + form.getRunId());
            }
            if (!run.getContainer().hasPermission(getUser(), DeletePermission.class))
            {
                throw new UnauthorizedException("Not permitted");
            }
            run.delete(getUser());
            return new ApiSimpleResponse("success", true);
        }
    }

    private abstract class AbstractDeleteAction extends FormViewAction<DeleteForm>
    {
        public void validateCommand(DeleteForm target, Errors errors)
        {
        }

        public boolean handlePost(DeleteForm deleteForm, BindException errors) throws Exception
        {
            if (!deleteForm.isForceDelete())
            {
                return false;
            }
            else
            {
                deleteObjects(deleteForm);
                return true;
            }
        }

        public ActionURL getSuccessURL(DeleteForm deleteForm)
        {
            if (deleteForm.getReturnURL() != null)
            {
                return new ActionURL(deleteForm.getReturnURL());
            }
            return ExperimentUrlsImpl.get().getOverviewURL(getContainer());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendRootNavTrail(root).addChild("Confirm Deletion");
        }

        protected abstract void deleteObjects(DeleteForm deleteForm) throws SQLException, ExperimentException, ServletException;
    }

    @RequiresPermissionClass(DeletePermission.class)
    public class DeleteProtocolByRowIdsAction extends AbstractDeleteAction
    {
        public DeleteProtocolByRowIdsAction()
        {
            super();
        }

        public ModelAndView getView(DeleteForm deleteForm, boolean reshow, BindException errors) throws Exception
        {
            List<? extends ExpRun> runs = ExperimentService.get().getExpRunsForProtocolIds(false, deleteForm.getIds(false));
            List<ExpProtocol> protocols = new ArrayList<ExpProtocol>();
            for (int protocolId : deleteForm.getIds(false))
            {
                ExpProtocol protocol = ExperimentService.get().getExpProtocol(protocolId);
                if (protocol != null)
                {
                    protocols.add(protocol);
                }
            }

            return new ConfirmDeleteView("Protocol", "protocolDetails", protocols, deleteForm, runs);
        }

        protected void deleteObjects(DeleteForm deleteForm) throws SQLException, ExperimentException
        {
            ExperimentService.get().deleteProtocolByRowIds(getContainer(), getUser(), deleteForm.getIds(true));
        }
    }

    @RequiresPermissionClass(DeletePermission.class)
    public class DeleteMaterialByRowIdAction extends AbstractDeleteAction
    {
        public DeleteMaterialByRowIdAction()
        {
            super();
        }

        protected void deleteObjects(DeleteForm deleteForm) throws SQLException, ExperimentException, ServletException
        {
            for (ExpRun run : getRuns(deleteForm))
            {
                if (!run.getContainer().hasPermission(getUser(), DeletePermission.class))
                {
                    HttpView.throwUnauthorized();
                }
            }

            for (ExpMaterial expMaterial : getMaterials(deleteForm, true))
            {
                expMaterial.delete(getUser());
            }
        }

        public ModelAndView getView(DeleteForm deleteForm, boolean reshow, BindException errors) throws Exception
        {
            List<ExpMaterial> materials = getMaterials(deleteForm, false);
            List<ExpRun> runs = getRuns(deleteForm);
            return new ConfirmDeleteView("Sample", "showMaterial", materials, deleteForm, runs);
        }

        private List<ExpRun> getRuns(DeleteForm deleteForm)
                throws SQLException
        {
            ExpRun[] runs = ExperimentService.get().getRunsUsingMaterials(deleteForm.getIds(false));
            return ExperimentService.get().runsDeletedWithInput(runs);
        }

        private List<ExpMaterial> getMaterials(DeleteForm deleteForm, boolean clear)
        {
            List<ExpMaterial> materials = new ArrayList<ExpMaterial>();
            for (int materialId : deleteForm.getIds(clear))
            {
                ExpMaterial material = ExperimentService.get().getExpMaterial(materialId);
                if (material != null)
                {
                    materials.add(material);
                }
            }
            return materials;
        }
    }

    @RequiresPermissionClass(DeletePermission.class)
    public class DeleteSelectedDataAction extends AbstractDeleteAction
    {
        public DeleteSelectedDataAction()
        {
            super();
        }

        protected void deleteObjects(DeleteForm deleteForm) throws ExperimentException, ServletException
        {
            for (ExpData data : getDatas(deleteForm, true))
            {
                data.delete(getViewContext().getUser());
            }
        }

        public ModelAndView getView(DeleteForm deleteForm, boolean reshow, BindException errors) throws Exception
        {
            List<ExpData> datas = getDatas(deleteForm, false);
            List<? extends ExpRun> runs = ExperimentService.get().getRunsUsingDatas(datas);

            return new ConfirmDeleteView("Data", "showData", datas, deleteForm, runs);
        }

        private List<ExpData> getDatas(DeleteForm deleteForm, boolean clear)
        {
            List<ExpData> datas = new ArrayList<ExpData>();
            for (int dataId : deleteForm.getIds(clear))
            {
                ExpData data = ExperimentService.get().getExpData(dataId);
                if (data != null)
                {
                    datas.add(data);
                }
            }
            return datas;
        }
    }

    @RequiresPermissionClass(DeletePermission.class)
    public class DeleteSelectedExperimentsAction extends AbstractDeleteAction
    {
        public DeleteSelectedExperimentsAction()
        {
            super();
        }

        protected void deleteObjects(DeleteForm deleteForm) throws SQLException, ExperimentException, ServletException
        {
            for (ExpExperiment exp : lookupExperiments(deleteForm))
            {
                exp.delete(getUser());
            }
        }

        public ModelAndView getView(DeleteForm deleteForm, boolean reshow, BindException errors) throws Exception
        {
            List<ExpExperiment> experiments = lookupExperiments(deleteForm);

            List<ExpRun> runs = new ArrayList<ExpRun>();
            boolean allBatches = true;
            for (ExpExperiment experiment : experiments)
            {
                // Deleting a batch also deletes all of its runs
                if (experiment.getBatchProtocol() != null)
                {
                    runs.addAll(Arrays.asList(experiment.getRuns()));
                }
                else
                {
                    allBatches = false;
                }
            }

            return new ConfirmDeleteView(allBatches ? "batch" : "run group", "details", experiments, deleteForm, runs);
        }

        private List<ExpExperiment> lookupExperiments(DeleteForm deleteForm)
        {
            List<ExpExperiment> experiments = new ArrayList<ExpExperiment>();
            for (int experimentId : deleteForm.getIds(false))
            {
                ExpExperiment experiment = ExperimentService.get().getExpExperiment(experimentId);
                if (experiment != null)
                {
                    experiments.add(experiment);
                }
            }
            return experiments;
        }
    }

    @RequiresPermissionClass(DeletePermission.class)
    public class DeleteMaterialSourceAction extends AbstractDeleteAction
    {
        public DeleteMaterialSourceAction()
        {
            super();
        }

        protected void deleteObjects(DeleteForm deleteForm) throws SQLException, ExperimentException, ServletException
        {
            List<ExpSampleSet> sampleSets = getSampleSets(deleteForm);
            if (!ensureCorrectContainer(sampleSets))
            {
                HttpView.throwUnauthorized();
            }
            for (ExpRun run : getRuns(sampleSets))
            {
                if (!run.getContainer().hasPermission(getUser(), DeletePermission.class))
                {
                    HttpView.throwUnauthorized();
                }
            }
            for (ExpSampleSet source : sampleSets)
            {
                ExperimentService.get().deleteSampleSet(source.getRowId(), getContainer(), getUser());
            }
        }

        public ModelAndView getView(DeleteForm deleteForm, boolean reshow, BindException errors) throws Exception
        {
            List<ExpSampleSet> sampleSets = getSampleSets(deleteForm);
            ExpSampleSet defaultSampleSet = ExperimentService.get().ensureDefaultSampleSet();
            if (sampleSets.contains(defaultSampleSet))
            {
                HttpView.throwRedirect(ExperimentUrlsImpl.get().getShowSampleSetListURL(getContainer(), "You cannot delete the default sample set."));
            }


            if (!ensureCorrectContainer(sampleSets))
            {
                HttpView.throwRedirect(ExperimentUrlsImpl.get().getShowSampleSetListURL(getContainer(), "To delete a sample set, you must be in its folder or project."));
            }

            return new ConfirmDeleteView("Sample Set", "showMaterialSource", sampleSets, deleteForm, getRuns(sampleSets));
        }

        private List<ExpSampleSet> getSampleSets(DeleteForm deleteForm)
        {
            List<ExpSampleSet> sources = new ArrayList<ExpSampleSet>();
            for (int rowId : deleteForm.getIds(false))
            {
                ExpSampleSet sampleSet = ExperimentServiceImpl.get().getSampleSet(rowId);
                if (sampleSet != null)
                {
                    sources.add(sampleSet);
                }
            }
            return sources;
        }

        private boolean ensureCorrectContainer(List<ExpSampleSet> sampleSets)
        {
            for (ExpSampleSet source : sampleSets)
            {
                Container sourceContainer = source.getContainer();
                if (!sourceContainer.equals(getContainer()))
                {
                    return false;
                }
            }
            return true;
        }

        private List<ExpRun> getRuns(List<ExpSampleSet> sampleSets) throws SQLException
        {
            if (sampleSets.size() > 0)
            {
                ExpRun[] runArray = ExperimentService.get().getRunsUsingSampleSets(sampleSets.toArray(new ExpSampleSet[sampleSets.size()]));
                return ExperimentService.get().runsDeletedWithInput(runArray);
            }
            else
            {
                return Collections.emptyList();
            }
        }
    }

    public static class DeleteForm extends ViewForm implements DataRegionSelection.DataSelectionKeyForm
    {
        private ReturnURLString _returnURL;
        private boolean _forceDelete;
        private String _dataRegionSelectionKey;
        private Integer _singleObjectRowId;

        public int[] getIds(boolean clear)
        {
            if (_singleObjectRowId != null)
            {
                return new int[] { _singleObjectRowId.intValue() };
            }
            return PageFlowUtil.toInts(DataRegionSelection.getSelected(getViewContext(), clear));
        }

        public Integer getSingleObjectRowId()
        {
            return _singleObjectRowId;
        }

        public void setSingleObjectRowId(Integer singleObjectRowId)
        {
            _singleObjectRowId = singleObjectRowId;
        }

        public ReturnURLString getReturnURL()
        {
            return _returnURL;
        }

        public void setReturnURL(ReturnURLString returnURL)
        {
            _returnURL = returnURL;
        }

        public boolean isForceDelete()
        {
            return _forceDelete;
        }

        public void setForceDelete(boolean forceDelete)
        {
            _forceDelete = forceDelete;
        }

        public String getDataRegionSelectionKey()
        {
            return _dataRegionSelectionKey;
        }

        public void setDataRegionSelectionKey(String dataRegionSelectionKey)
        {
            _dataRegionSelectionKey = dataRegionSelectionKey;
        }
    }

    @RequiresPermissionClass(UpdatePermission.class)
    public class ShowUpdateMaterialSourceAction extends SimpleViewAction<MaterialSourceForm>
    {
        private ExpSampleSet _sampleSet;

        public ModelAndView getView(MaterialSourceForm form, BindException errors) throws Exception
        {
            _sampleSet = ExperimentService.get().getSampleSet(form.getBean().getRowId());

            if (_sampleSet.equals(ExperimentService.get().ensureDefaultSampleSet()))
            {
                HttpView.throwUnauthorized("Cannot edit default sample set");
            }

            return new UpdateView(getMaterialSourceRegion(getViewContext(), false), form, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendRootNavTrail(root).addChild("Sample Sets", ExperimentUrlsImpl.get().getShowSampleSetListURL(getContainer())).addChild("Sample Set " + _sampleSet.getName());
        }
    }

    private DataRegion getMaterialSourceRegion(ViewContext model, boolean detailsView) throws Exception
    {
        TableInfo tableInfo = ExperimentServiceImpl.get().getTinfoMaterialSource();
        DataRegion dr = new DataRegion();
        dr.setName("MaterialsSource");
        dr.setSelectionKey(DataRegionSelection.getSelectionKey(tableInfo.getSchema().getName(), tableInfo.getName(), "SampleSets", dr.getName()));
        dr.addColumns(tableInfo.getUserEditableColumns());
        dr.getDisplayColumn(0).setVisible(false);

        dr.getDisplayColumn("idcol1").setVisible(false);
        dr.getDisplayColumn("idcol2").setVisible(false);
        dr.getDisplayColumn("idcol3").setVisible(false);
        dr.getDisplayColumn("lsid").setVisible(false);
        dr.getDisplayColumn("materiallsidprefix").setVisible(false);
        dr.getDisplayColumn("parentcol").setVisible(false);

        ActionURL url = new ActionURL(ExperimentController.ShowMaterialSourceAction.class, model.getContainer());
        dr.getDisplayColumn(1).setURL(url.toString() + "rowId=${RowId}");
        dr.setShowRecordSelectors(getContainer().hasOneOf(getUser(), DeletePermission.class, UpdatePermission.class));
        dr.addDisplayColumn(0, new ActiveSampleSetColumn(model.getContainer()));

        ButtonBar bb = new ButtonBar();

        SampleSetWebPart.populateButtonBar(model, bb, detailsView);

        dr.setButtonBar(bb);

        return dr;

    }

    private static final class ActiveSampleSetColumn extends SimpleDisplayColumn
    {
        private final ExpSampleSet _activeSampleSet;

        public ActiveSampleSetColumn(Container c) throws SQLException
        {
            _activeSampleSet = ExperimentService.get().lookupActiveSampleSet(c);
            setCaption("Active");
        }

        public void renderDetailsCellContents(RenderContext ctx, Writer out) throws IOException
        {
            renderGridCellContents(ctx, out);
        }

        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            if (_activeSampleSet != null && _activeSampleSet.getLSID().equals(ctx.getRow().get("lsid")))
            {
                out.write("<b>Yes</b>");
            }
            else
            {
                out.write("No");
            }
        }
    }


    @RequiresPermissionClass(InsertPermission.class)
    public class ShowInsertMaterialSourceAction extends SimpleViewAction<MaterialSourceForm>
    {
        public ModelAndView getView(MaterialSourceForm form, BindException errors) throws Exception
        {
            return new InsertView(getMaterialSourceRegion(getViewContext(), false), form, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendRootNavTrail(root).addChild("Sample Sets", ExperimentUrlsImpl.get().getShowSampleSetListURL(getContainer())).addChild("Insert Sample Set");
        }
    }

    @RequiresPermissionClass(UpdatePermission.class)
    public class UpdateMaterialSourceAction extends FormHandlerAction<MaterialSourceForm>
    {
        private MaterialSource _source;

        public void validateCommand(MaterialSourceForm target, Errors errors)
        {
        }

        public boolean handlePost(MaterialSourceForm form, BindException errors) throws Exception
        {
            _source = form.getBean();
            ExpSampleSet oldSampleSet = ExperimentService.get().getSampleSet(_source.getLSID());
            if (oldSampleSet == null || !getContainer().equals(oldSampleSet.getContainer()))
            {
                HttpView.throwNotFound("MaterialSource with LSID " + _source.getLSID());
            }
            Table.update(getUser(), ExperimentService.get().getTinfoMaterialSource(), form.getTypedValues(), _source.getRowId());
            ExperimentServiceImpl.get().clearCaches();
            return true;
        }

        public ActionURL getSuccessURL(MaterialSourceForm materialSourceForm)
        {
            return ExperimentUrlsImpl.get().getShowSampleSetURL(ExperimentService.get().getSampleSet(_source.getRowId()));
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class EditSampleSetTypeAction extends SimpleViewAction<MaterialSourceForm>
    {
        public ModelAndView getView(MaterialSourceForm form, BindException errors) throws Exception
        {
            ExpSampleSet ss = ExperimentService.get().getSampleSet(form.getBean().getRowId());
            if (ExperimentService.get().ensureDefaultSampleSet().equals(ss))
            {
                HttpView.throwUnauthorized("Cannot edit default sample set");
            }
            if (ss == null)
            {
                return HttpView.throwNotFound("Could not find sample set with rowId " + form.getBean().getRowId());
            }
            HttpView.throwRedirect(ss.getType().urlEditDefinition(false, false, false));
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            throw new UnsupportedOperationException();
        }
    }

    public static class MaterialSourceForm extends BeanViewForm<MaterialSource>
    {
        public MaterialSourceForm()
        {
            super(MaterialSource.class, ExperimentService.get().getTinfoMaterialSource());
        }
    }

    @RequiresPermissionClass(UpdatePermission.class)
    public class ShowUploadMaterialsAction extends SimpleViewAction<UploadMaterialSetForm>
    {
        ExpSampleSet _ss;

        @Override
        public void validate(UploadMaterialSetForm form, BindException errors)
        {
            if (StringUtils.isNotEmpty(form.getName()))
            {
                String materialSourceLsid = ExperimentService.get().getSampleSetLsid(form.getName(), getContainer()).toString();
                _ss = ExperimentService.get().getSampleSet(materialSourceLsid);
            }
        }

        public ModelAndView getView(UploadMaterialSetForm form, BindException errors) throws ServletException
        {
            if (isPost())
            {
                if (StringUtils.isEmpty(form.getName()) || form.getName() == null)
                {
                    errors.reject(ERROR_MSG, "You must supply a name for the sample set");
                }
                else
                {
                    if (!form.isImportMoreSamples() && null != _ss)
                    {
                        errors.addError(new FormattedError("A sample set with that name already exists.  If you would like to import samples that set, go here:  " +
                                "<a href=" + getViewContext().getActionURL() + "name=" + form.getName() + "&importMoreSamples=true>Import More Samples</a>"));
                    }
                    if (form.isImportMoreSamples() && form.getInsertUpdateChoice() == null)
                    {
                        errors.reject(ERROR_MSG, "Please select how to deal with duplicates.");
                    }
                }

                if (errors.getErrorCount() == 0)
                {
                    try
                    {
                        UploadSamplesHelper helper = new UploadSamplesHelper(form);
                        Pair<MaterialSource, List<ExpMaterial>> pair = helper.uploadMaterials();
                        MaterialSource newSource = pair.first;

                        ExpSampleSet activeSampleSet = ExperimentService.get().lookupActiveSampleSet(getContainer());
						ExpSampleSet newSampleSet = ExperimentService.get().getSampleSet(newSource.getRowId());

                        if (activeSampleSet == null)
                        {
                            ExperimentService.get().setActiveSampleSet(getContainer(), newSampleSet);
                        }
                        HttpView.throwRedirect(ExperimentUrlsImpl.get().getShowSampleSetURL(newSampleSet));
                    }
                    catch (ExperimentException e)
                    {
                        errors.reject(ERROR_MSG, e.getMessage());
                    }
                    catch (ValidationException e)
                    {
                        errors.reject(ERROR_MSG, e.getMessage());
                    }
                    catch (IOException e)
                    {
                        errors.reject(ERROR_MSG, e.getMessage());
                    }
                }
            }
            return new JspView<UploadMaterialSetForm>("/org/labkey/experiment/uploadMaterials.jsp", form, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            NavTree nav = appendRootNavTrail(root).addChild("Sample Sets", ExperimentUrlsImpl.get().getShowSampleSetListURL(getContainer()));
            if (_ss != null)
                nav.addChild(_ss.getName(), ExperimentUrlsImpl.get().getShowSampleSetURL(_ss));
            nav.addChild("Import Sample Set");
            return nav;
        }
    }

    @RequiresPermissionClass(UpdatePermission.class)
    public class SetActiveSampleSetAction extends FormHandlerAction
    {
        public void validateCommand(Object target, Errors errors)
        {
        }

        public boolean handlePost(Object o, BindException errors) throws Exception
        {
            String rowId = null;
            if (getViewContext().getRequest().getParameter("rowid") != null)
            {
                rowId = getViewContext().getRequest().getParameter("rowid");
            }
            else if (getViewContext().getRequest().getParameter("rowId") != null)
            {
                rowId = getViewContext().getRequest().getParameter("rowId");
            }
            else
            {
                Set<String> selectedIds = DataRegionSelection.getSelected(getViewContext(), true);
                if (selectedIds != null && selectedIds.size() == 1)
                {
                    rowId = selectedIds.iterator().next();
                }
            }

            ExpSampleSet sampleSet = null;
            if (rowId != null)
                sampleSet = ExperimentService.get().getSampleSet(Integer.parseInt(rowId));

            String name = getViewContext().getRequest().getParameter("name");
            if (sampleSet == null && name != null && name.trim().length() > 0)
                sampleSet = ExperimentService.get().getSampleSet(getContainer(), name.trim());

            if (sampleSet != null)
            {
                ExperimentService.get().setActiveSampleSet(getContainer(), sampleSet);
            }
            return true;
        }

        public ActionURL getSuccessURL(Object o)
        {
            String referrer = getViewContext().getRequest().getHeader("Referer");
            if (referrer != null)
            {
                return new ActionURL(referrer);
            }
            return ExperimentUrlsImpl.get().getShowSampleSetListURL(getContainer());
        }
    }

    @RequiresPermissionClass(InsertPermission.class)
    public class ShowAddXarFileAction extends FormViewAction<Object>
    {
        public URLHelper getSuccessURL(Object o)
        {
            return PageFlowUtil.urlProvider(PipelineUrls.class).urlBegin(getContainer());
        }

        public void validateCommand(Object target, Errors errors)
        {
        }

        public ModelAndView getView(Object o, boolean reshow, BindException errors) throws Exception
        {
            if (!hasValidPipelineURI(getContainer()))
            {
                return new NoPipelineRootSetView(getContainer(), "upload a XAR");
            }

            return new JspView<Object>("/org/labkey/experiment/addXarFile.jsp", null, errors);
        }

        public boolean handlePost(Object o, BindException errors) throws Exception
        {
            if (!(getViewContext().getRequest() instanceof MultipartHttpServletRequest))
                throw new IllegalStateException("Expected MultipartHttpServletRequest when posting files.");

            if (!hasValidPipelineURI(getContainer()))
            {
                return false;
            }

            MultipartFile formFile = getFileMap().get("uploadFile");
            if (formFile == null)
            {
                errors.addError(new LabkeyError("No file was posted by the browser."));
                return false;
            }

            byte[] bytes = formFile.getBytes();
            if (bytes.length == 0)
            {
                errors.addError(new LabkeyError("No file was posted by the browser."));
                return false;
            }

            File systemDir = PipelineService.get().findPipelineRoot(getContainer()).ensureSystemDirectory();
            File uploadDir = new File(systemDir, "UploadedXARs");
            uploadDir.mkdirs();
            if (!uploadDir.isDirectory())
            {
                errors.addError(new LabkeyError("Unable to create a 'system/UploadedXARs' directory under the pipeline root"));
                return false;
            }
            String userDirName = getUser().getEmail();
            if (userDirName == null || userDirName.length() == 0)
            {
                userDirName = GUEST_DIRECTORY_NAME;
            }
            File userDir = new File(uploadDir, userDirName);
            userDir.mkdirs();
            if (!userDir.isDirectory())
            {
                errors.addError(new LabkeyError("Unable to create an 'UploadedXARs/" + userDirName + "' directory under the pipeline root"));
                return false;
            }

            File xarFile = new File(userDir, formFile.getOriginalFilename());
            OutputStream out = null;
            try
            {
                out = new BufferedOutputStream(new FileOutputStream(xarFile));
                out.write(bytes);
            }
            catch (IOException e)
            {
                errors.addError(new LabkeyError("Unable to write uploaded XAR file to " + xarFile.getPath()));
                return false;
            }
            finally
            {
                if (out != null)
                { //noinspection EmptyCatchBlock
                    try { out.close(); } catch (IOException e) {}
                }
            }

            ExperimentPipelineJob job = new ExperimentPipelineJob(getViewBackgroundInfo(), xarFile,
                    "Uploaded file", true);
            PipelineService.get().queueJob(job);

            return true;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendRootNavTrail(root).addChild("Upload a .xar or .xar.xml file from your browser");
        }
    }

    @RequiresPermissionClass(UpdatePermission.class)
    public class ShowUpdateAction extends SimpleViewAction<ExperimentForm>
    {
        public ModelAndView getView(ExperimentForm form, BindException errors) throws Exception
        {
            form.refreshFromDb();
            Experiment exp = form.getBean();
            if (exp == null)
            {
                return HttpView.throwNotFound();
            }
            ensureCorrectContainer(getContainer(), ExperimentService.get().getExpExperiment(exp.getRowId()), getViewContext());

            return new ExperimentUpdateView(new DataRegion(), form, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendRootNavTrail(root).addChild("Update Run Group");
        }
    }

    @RequiresPermissionClass(UpdatePermission.class)
    public class UpdateAction extends FormHandlerAction<ExperimentForm>
    {
        private Experiment _exp;
        public void validateCommand(ExperimentForm target, Errors errors)
        {}

        public boolean handlePost(ExperimentForm form, BindException errors) throws Exception
        {
            form.doUpdate();
            _exp = form.getBean();
            return true;
        }

        public ActionURL getSuccessURL(ExperimentForm experimentForm)
        {
            return ExperimentUrlsImpl.get().getExperimentDetailsURL(getContainer(), ExperimentService.get().getExpExperiment(_exp.getRowId()));
        }
    }

    public static class ExportBean
    {
        private final LSIDRelativizer _selectedRelativizer;
        private final XarExportType _selectedExportType;
        private final String _fileName;
        private final String _dataRegionSelectionKey;
        private final String _error;
        private final Integer _expRowId;
        private final Integer _protocolId;
        private final ActionURL _postURL;
        private final Set<String> _roles;

        public ExportBean(LSIDRelativizer selectedRelativizer, XarExportType selectedExportType, String fileName, ExportOptionsForm form, Set<String> roles, ActionURL postURL)
        {
            _selectedRelativizer = selectedRelativizer;
            _selectedExportType = selectedExportType;
            _fileName = fileName;
            _dataRegionSelectionKey = form.getDataRegionSelectionKey();
            _error = form.getError();
            _expRowId = form.getExpRowId();
            _postURL = postURL;
            _roles = roles;
            _protocolId = form.getProtocolId();
        }

        public LSIDRelativizer getSelectedRelativizer()
        {
            return _selectedRelativizer;
        }

        public XarExportType getSelectedExportType()
        {
            return _selectedExportType;
        }

        public String getError()
        {
            return _error;
        }

        public String getFileName()
        {
            return _fileName;
        }

        public Set<String> getRoles()
        {
            return _roles;
        }

        public String getDataRegionSelectionKey()
        {
            return _dataRegionSelectionKey;
        }

        public ActionURL getPostURL()
        {
            return _postURL;
        }

        public Integer getProtocolId()
        {
            return _protocolId;
        }

        public Integer getExpRowId()
        {
            return _expRowId;
        }
    }


    private String fixupExportName(String runName)
    {
        runName = runName.replace('/', '-');
        runName = runName.replace('\\', '-');
        return runName;
    }

    public static class ExportOptionsForm extends ExperimentRunListForm
    {
        private String _error;
        private String _exportType;
        private String _lsidOutputType;
        private String _xarFileName;
        private String _zipFileName;
        private String _fileExportType;
        private Integer _protocolId;
        private String[] _roles = new String[0];

        public String getError()
        {
            return _error;
        }

        public void setError(String error)
        {
            _error = error;
        }

        public String getExportType()
        {
            return _exportType;
        }

        public String getLsidOutputType()
        {
            return _lsidOutputType;
        }

        public String getFileExportType()
        {
            return _fileExportType;
        }

        public void setFileExportType(String fileExportType)
        {
            _fileExportType = fileExportType;
        }

        public String getXarFileName()
        {
            return _xarFileName;
        }

        public void setXarFileName(String xarFileName)
        {
            _xarFileName = xarFileName;
        }

        public String getZipFileName()
        {
            return _zipFileName;
        }

        public void setZipFileName(String zipFileName)
        {
            _zipFileName = zipFileName;
        }

        public void setExportType(String exportType)
        {
            _exportType = exportType;
        }

        public void setLsidOutputType(String lsidOutputType)
        {
            _lsidOutputType = lsidOutputType;
        }

        public Integer getProtocolId()
        {
            return _protocolId;
        }

        public void setProtocolId(Integer protocolId)
        {
            _protocolId = protocolId;
        }

        public String[] getRoles()
        {
            return _roles;
        }

        public void setRoles(String[] roles)
        {
            _roles = roles;
        }

        public List<ExpProtocol> lookupProtocols(ViewContext context, boolean clearSelection)
        {
            List<ExpProtocol> protocols = new ArrayList<ExpProtocol>();

            if (_protocolId != null)
            {
                ExpProtocol protocol = ExperimentService.get().getExpProtocol(_protocolId.intValue());
                if (protocol == null || !protocol.getContainer().equals(context.getContainer()))
                {
                    HttpView.throwNotFound();
                }
                protocols.add(protocol);
                return protocols;
            }

            Set<String> protocolIds = DataRegionSelection.getSelected(context, clearSelection);
            for (String protocolId : protocolIds)
            {
                try
                {
                    ExpProtocol protocol = ExperimentService.get().getExpProtocol(Integer.parseInt(protocolId));
                    if (protocol == null || !protocol.getContainer().equals(context.getContainer()))
                    {
                        HttpView.throwNotFound();
                    }
                    protocols.add(protocol);
                }
                catch (NumberFormatException e)
                {
                    HttpView.throwNotFound("Invalid protocol id: " + protocolId);
                }
            }
            if (protocols.isEmpty())
            {
                HttpView.throwNotFound("No protocols selected");
            }
            return protocols;
        }
    }

    private ActionURL exportXAR(XarExportSelection selection, String lsidRelativizerName, String exportTypeName, String fileName)
            throws SQLException, ExperimentException, ServletException, IOException
    {
        final LSIDRelativizer lsidRelativizer;
        final XarExportType exportType;
        if (lsidRelativizerName == null)
        {
            lsidRelativizer = LSIDRelativizer.FOLDER_RELATIVE;
        }
        else
        {
            lsidRelativizer = LSIDRelativizer.valueOf(lsidRelativizerName);
        }
        if (exportTypeName == null)
        {
            exportType = XarExportType.BROWSER_DOWNLOAD;
        }
        else
        {
            exportType = XarExportType.valueOf(exportTypeName);
        }

        if (fileName == null || fileName.equals(""))
        {
            fileName = "export.xar";
        }
        fileName = fixupExportName(fileName);
        String xarXmlFileName = null;
        if (fileName.endsWith(".xar") || fileName.endsWith(".XAR") || fileName.endsWith("Xar"))
            xarXmlFileName = fileName + ".xml";

        switch (exportType)
        {
            case BROWSER_DOWNLOAD:
                XarExporter exporter = new XarExporter(lsidRelativizer, selection, xarXmlFileName, null);

                getViewContext().getResponse().setContentType("application/zip");
                getViewContext().getResponse().setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
                getViewContext().getResponse().setHeader("Pragma", "private");
                getViewContext().getResponse().setHeader("Cache-Control", "private");

                exporter.write(getViewContext().getResponse().getOutputStream());
                return null;
            case PIPELINE_FILE:
                PipeRoot pipeRoot = PipelineService.get().findPipelineRoot(getContainer());
                File pipeRootDir = pipeRoot == null ? null : pipeRoot.getRootPath();
                if (pipeRootDir == null || !pipeRootDir.exists())
                {
                    throw new IllegalStateException("You must set a valid pipeline root before you can export a XAR to it.");
                }
                XarExportPipelineJob job = new XarExportPipelineJob(getViewBackgroundInfo(), pipeRootDir, fileName, lsidRelativizer, selection, xarXmlFileName);
                PipelineService.get().queueJob(job);
                return PageFlowUtil.urlProvider(PipelineUrls.class).urlReferer(getContainer());
            default:
                throw new IllegalArgumentException("Unknown export type: " + exportType);
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class ExportProtocolsAction extends AbstractExportAction
    {
        @Override
        public ModelAndView getView(ExportOptionsForm form, boolean reshow, BindException errors) throws Exception
        {
            handlePost(form, errors);
            return null;
        }

        public boolean handlePost(ExportOptionsForm form, BindException errors) throws Exception
        {
            List<ExpProtocol> protocols = form.lookupProtocols(getViewContext(), false);

            int[] ids = new int[protocols.size()];
            for (int i = 0; i < ids.length; i++)
            {
                ids[i] = protocols.get(i).getRowId();
            }
            XarExportSelection selection = new XarExportSelection();
            selection.addProtocolIds(ids);

            exportXAR(selection, form.getLsidOutputType(), form.getExportType(), form.getXarFileName());

            if (form.getDataRegionSelectionKey() != null)
            {
                // Clear the selection
                form.lookupProtocols(getViewContext(), true);
            }
            return true;
        }
    }

    public abstract class AbstractExportAction extends FormViewAction<ExportOptionsForm>
    {
        protected ActionURL _resultURL;

        public void validateCommand(ExportOptionsForm target, Errors errors)
        {
        }

        public ActionURL getSuccessURL(ExportOptionsForm exportOptionsForm)
        {
            return _resultURL;
        }

        public ModelAndView getSuccessView(ExportOptionsForm exportOptionsForm)
        {
            return null;
        }

        public ModelAndView getView(ExportOptionsForm exportOptionsForm, boolean reshow, BindException errors) throws Exception
        {
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class ExportRunsAction extends AbstractExportAction
    {
        public boolean handlePost(ExportOptionsForm form, BindException errors) throws Exception
        {
            Set<String> runIds = DataRegionSelection.getSelected(getViewContext(), true);
            if (runIds == null || runIds.isEmpty())
            {
                HttpView.throwNotFound();
            }

            try
            {
                int[] ids = PageFlowUtil.toInts(runIds);
                for (int id : ids)
                {
                    ExpRun run = ExperimentService.get().getExpRun(id);
                    if (run == null || !run.getContainer().hasPermission(getUser(), ReadPermission.class))
                    {
                        HttpView.throwNotFound("Could not find run " + id);
                    }
                }

                XarExportSelection selection = new XarExportSelection();
                if (form.getExpRowId() != null)
                {
                    ExpExperiment experiment = ExperimentService.get().getExpExperiment(form.getExpRowId().intValue());
                    if (experiment != null && !experiment.getContainer().hasPermission(getUser(), ReadPermission.class))
                    {
                        HttpView.throwNotFound("Run group " + form.getExpRowId());
                    }
                    selection.addExperimentIds(experiment.getRowId());
                }
                selection.addRunIds(ids);

                _resultURL = exportXAR(selection, form.getLsidOutputType(), form.getExportType(), form.getXarFileName());
                return true;
            }
            catch (NumberFormatException e)
            {
                HttpView.throwNotFound(runIds.toString());
                return true;
            }
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class ExportRunFilesAction extends AbstractExportAction
    {
        public boolean handlePost(ExportOptionsForm form, BindException errors) throws Exception
        {
            Set<String> runIds = DataRegionSelection.getSelected(getViewContext(), true);
            if (runIds == null || runIds.isEmpty())
            {
                HttpView.throwNotFound();
            }

            try
            {
                int[] ids = PageFlowUtil.toInts(runIds);
                for (int id : ids)
                {
                    ExpRun run = ExperimentService.get().getExpRun(id);
                    if (run == null || !run.getContainer().hasPermission(getUser(), ReadPermission.class))
                    {
                        HttpView.throwNotFound("Could not find run " + id);
                    }
                }

                XarExportSelection selection = new XarExportSelection();
                selection.setIncludeXarXml(false);
                if ("role".equalsIgnoreCase(form.getFileExportType()))
                {
                    selection.addRoles(form.getRoles());
                }
                selection.addRunIds(ids);

                _resultURL = exportXAR(selection, null, null, form.getZipFileName());
                return true;
            }
            catch (NumberFormatException e)
            {
                HttpView.throwNotFound(runIds.toString());
                return true;
            }
        }
    }

    public static class ExperimentRunListForm implements DataRegionSelection.DataSelectionKeyForm
    {
        private String _dataRegionSelectionKey;
        private Integer _expRowId;

        public String getDataRegionSelectionKey()
        {
            return _dataRegionSelectionKey;
        }

        public void setDataRegionSelectionKey(String key)
        {
            _dataRegionSelectionKey = key;
        }

        public Integer getExpRowId()
        {
            return _expRowId;
        }

        public void setExpRowId(Integer expRowId)
        {
            _expRowId = expRowId;
        }

        public ExpExperiment lookupExperiment()
        {
            return getExpRowId() == null ? null : ExperimentService.get().getExpExperiment(getExpRowId().intValue());
        }
    }

    private void addSelectedRunsToExperiment(ExpExperiment exp)
            throws SQLException
    {
        int[] runIds = PageFlowUtil.toInts(DataRegionSelection.getSelected(getViewContext(), true));
        List<ExpRun> runs = new ArrayList<ExpRun>();
        for (int runId : runIds)
        {
            ExpRun run = ExperimentServiceImpl.get().getExpRun(runId);
            if (run != null)
            {
                runs.add(run);
            }
        }
        exp.addRuns(getUser(), runs.toArray(new ExpRun[runs.size()]));
    }


    @RequiresPermissionClass(InsertPermission.class)
    public class AddRunsToExperimentAction extends FormHandlerAction<ExperimentRunListForm>
    {
        public void validateCommand(ExperimentRunListForm target, Errors errors)
        {
        }

        public boolean handlePost(ExperimentRunListForm form, BindException errors) throws Exception
        {
            addSelectedRunsToExperiment(form.lookupExperiment());
            return true;
        }

        public ActionURL getSuccessURL(ExperimentRunListForm form)
        {
            return ExperimentUrlsImpl.get().getExperimentDetailsURL(getContainer(), form.lookupExperiment());
        }
    }

    @RequiresPermissionClass(DeletePermission.class)
    public class RemoveSelectedExpRunsAction extends FormHandlerAction<ExperimentRunListForm>
    {
        public void validateCommand(ExperimentRunListForm target, Errors errors)
        {
        }

        public boolean handlePost(ExperimentRunListForm form, BindException errors) throws Exception
        {
            Set<String> runIds = DataRegionSelection.getSelected(getViewContext(), true);
            ExpExperiment exp = form.lookupExperiment();
            if (exp == null || !exp.getContainer().equals(getContainer()))
            {
                HttpView.throwNotFound("Experiment " + form.getExpRowId());
            }
            for (int runId : PageFlowUtil.toInts(runIds))
            {
                ExpRun run = ExperimentService.get().getExpRun(runId);
                if (run == null || !run.getContainer().equals(getContainer()))
                {
                    throw new NotFoundException("Run " + runId);
                }
                exp.removeRun(getUser(), run);
            }
            return true;
        }

        public ActionURL getSuccessURL(ExperimentRunListForm form)
        {
            return ExperimentUrlsImpl.get().getExperimentDetailsURL(getContainer(), form.lookupExperiment());
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class ResolveLSIDAction extends SimpleViewAction<LsidForm>
    {
        public ModelAndView getView(LsidForm form, BindException errors) throws Exception
        {
            String message = "";

            if (!PageFlowUtil.empty(form.getLsid()))
            {
                String url = LsidManager.get().getDisplayURL(Lsid.canonical(form.getLsid()));
                if (null == url)
                    message = "Could not map lsid to URL";
                else
                    HttpView.throwRedirect(url);
            }

            String html = message + "<form action=\"" + getViewContext().cloneActionURL().setAction(ResolveLSIDAction.class) + "\">" +
                    " Lsid <input type=text name=lsid value=\"" +
                    (form.getLsid() == null ? "" : PageFlowUtil.filter(form.getLsid())) + "\">" +
                    PageFlowUtil.generateSubmitButton("Go", "", "size=\"60\"") + "</form>";

            return new HtmlView("Enter LSID", html);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Resolve LSID");
        }
    }

    public static class LsidForm
    {
        private String _lsid = null;

        public void setLsid(String lsid)
        {
            _lsid = lsid;
        }

        public String getLsid()
        {
            return _lsid;
        }
    }

    public static class SetFlagForm extends LsidForm
    {
        private String _comment;
        private boolean _redirect = true;

        public String getComment()
        {
            return _comment;
        }

        public void setComment(String comment)
        {
            _comment = comment;
        }

        public boolean isRedirect()
        {
            return _redirect;
        }

        public void setRedirect(boolean redirect)
        {
            _redirect = redirect;
        }
    }

    /** Check for update on the object itself */
    @RequiresNoPermission
    public class SetFlagAction extends SimpleViewAction<SetFlagForm>
    {
        public ModelAndView getView(SetFlagForm form, BindException errors) throws Exception
        {
            String lsid = form.getLsid();
            if (lsid == null)
                return HttpView.throwNotFound();
            ExpObject obj = ExperimentService.get().findObjectFromLSID(lsid);
            if (obj == null)
                return HttpView.throwNotFound();
            Container container = obj.getContainer();
            if (!container.hasPermission(getUser(), UpdatePermission.class))
                HttpView.throwUnauthorized();

            if (!container.hasPermission(getUser(), UpdatePermission.class))
                HttpView.throwUnauthorized();
            obj.setComment(getUser(), form.getComment());

            if (form.isRedirect())
                HttpView.throwRedirect(obj.urlFlag(!StringUtils.isEmpty(form.getComment())));
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            throw new UnsupportedOperationException();
        }
    }

    @RequiresPermissionClass(InsertPermission.class)
    public class DeriveSamplesChooseTargetAction extends SimpleViewAction<DeriveMaterialForm>
    {
        private List<ExpMaterial> _materials;

        public NavTree appendNavTrail(NavTree root)
        {
            root = appendRootNavTrail(root);
            root.addChild("Sample Sets", ExperimentUrlsImpl.get().getShowSampleSetListURL(getContainer()));
            ExpSampleSet sampleSet = _materials != null && _materials.size() > 0 ? _materials.get(0).getSampleSet() : null;
            if (sampleSet != null)
            {
                root.addChild(sampleSet.getName(), ExperimentUrlsImpl.get().getShowSampleSetURL(sampleSet));
            }
            root.addChild("Derive Samples");
            return root;
        }

        @Override
        public void validate(DeriveMaterialForm form, BindException errors)
        {
            _materials = form.lookupMaterials();
            if (_materials.isEmpty())
            {
                HttpView.throwNotFound("Could not find any matching materials");
            }
        }

        public ModelAndView getView(DeriveMaterialForm form, BindException errors) throws Exception
        {
            if (!_materials.get(0).getContainer().equals(getContainer()))
            {
                ActionURL redirectURL = getViewContext().cloneActionURL().setContainer(_materials.get(0).getContainer());
                HttpView.throwRedirect(redirectURL);
            }

            Container c = getContainer();
            HttpView view;

            PipeRoot root = PipelineService.get().findPipelineRoot(c);
            if (root == null || !NetworkDrive.exists(root.getRootPath()))
            {
                ActionURL pipelineURL = PageFlowUtil.urlProvider(PipelineUrls.class).urlSetup(c);
                view = new HtmlView("You must <a href=\"" + pipelineURL + "\">configure a valid pipeline root for this folder</a> before deriving samples.");
            }
            else
            {
                Set<String> materialInputRoles = new TreeSet<String>();
                materialInputRoles.addAll(ExperimentService.get().getMaterialInputRoles(getContainer()));
                Map<ExpMaterial, String> materialsWithRoles = new LinkedHashMap<ExpMaterial, String>();
                for (ExpMaterial material : _materials)
                {
                    materialsWithRoles.put(material, null);
                }

                List<ExpSampleSet> sampleSets = getUploadableSampleSets();

                DeriveSamplesChooseTargetBean bean = new DeriveSamplesChooseTargetBean(sampleSets, materialsWithRoles, form.getOutputCount(), materialInputRoles, null);
                view = new JspView<DeriveSamplesChooseTargetBean>("/org/labkey/experiment/deriveSamplesChooseTarget.jsp", bean);
            }
            return view;
        }
    }

    public static class DeriveSamplesChooseTargetBean
    {
        private List<ExpSampleSet> _sampleSets;
        private Map<ExpMaterial, String> _sourceMaterials;
        private final int _sampleCount;
        private final Collection<String> _inputRoles;
        private DerivedSamplePropertyHelper _propertyHelper;

        public static final String CUSTOM_ROLE = "--CUSTOM--";

        public DeriveSamplesChooseTargetBean(List<ExpSampleSet> sampleSets, Map<ExpMaterial, String> sourceMaterials, int sampleCount, Collection<String> inputRoles, DerivedSamplePropertyHelper helper)
        {
            _sampleSets = sampleSets;
            _sourceMaterials = sourceMaterials;
            _sampleCount = sampleCount;
            _inputRoles = inputRoles;
            _propertyHelper = helper;
        }

        public DerivedSamplePropertyHelper getPropertyHelper()
        {
            return _propertyHelper;
        }

        public int getSampleCount()
        {
            return _sampleCount;
        }

        public Map<ExpMaterial, String> getSourceMaterials()
        {
            return _sourceMaterials;
        }

        public List<ExpSampleSet> getSampleSets()
        {
            return _sampleSets;
        }

        public Collection<String> getInputRoles()
        {
            return _inputRoles;
        }
    }

    private List<ExpSampleSet> getUploadableSampleSets()
    {
        List<ExpSampleSet> sampleSets = new ArrayList<ExpSampleSet>(Arrays.asList(ExperimentService.get().getSampleSets(getContainer(), getViewContext().getUser(), true)));
        Iterator<ExpSampleSet> iter = sampleSets.iterator();
        while (iter.hasNext())
        {
            ExpSampleSet sampleSet = iter.next();
            if (!sampleSet.canImportMoreSamples())
            {
                iter.remove();
            }
        }
        return sampleSets;
    }

    @RequiresPermissionClass(InsertPermission.class)
    public class DescribeDerivedSamplesAction extends SimpleViewAction<DeriveMaterialForm>
    {
        List<ExpMaterial> _materials;

        public NavTree appendNavTrail(NavTree root)
        {
            root = appendRootNavTrail(root);
            root.addChild("Sample Sets", ExperimentUrlsImpl.get().getShowSampleSetListURL(getContainer()));
            ExpSampleSet sampleSet = _materials != null && _materials.size() > 0 ? _materials.get(0).getSampleSet() : null;
            if (sampleSet != null)
            {
                root.addChild(sampleSet.getName(), ExperimentUrlsImpl.get().getShowSampleSetURL(sampleSet));
            }
            root.addChild("Derive Samples");
            return root;
        }

        @Override
        public void validate(DeriveMaterialForm form, BindException errors)
        {
            _materials = form.lookupMaterials();
            if (_materials.isEmpty())
            {
                HttpView.throwNotFound("Could not find any matching materials");
            }
        }

        public ModelAndView getView(DeriveMaterialForm form, BindException errors) throws Exception
        {
            Container c = getContainer();

            ExpSampleSet sampleSet = ExperimentService.get().getSampleSet(form.getTargetSampleSetId());

            InsertView insertView = new InsertView(new DataRegion(), errors);

            DerivedSamplePropertyHelper helper = new DerivedSamplePropertyHelper(sampleSet, form.getOutputCount(), c, getUser());
            helper.addSampleColumns(insertView, getViewContext().getUser());

            int[] rowIds = form.getRowIds();
            for (int i = 0; i < rowIds.length; i++)
            {
                insertView.getDataRegion().addHiddenFormField("rowIds", Integer.toString(rowIds[i]));
                insertView.getDataRegion().addHiddenFormField("inputRole" + i, form.getInputRole(i) == null ? "" : form.getInputRole(i));
                insertView.getDataRegion().addHiddenFormField("customRole" + i, form.getCustomRole(i) == null ? "" : form.getCustomRole(i));
            }

            insertView.getDataRegion().addHiddenFormField("targetSampleSetId", Integer.toString(form.getTargetSampleSetId()));
            insertView.getDataRegion().addHiddenFormField("outputCount", Integer.toString(form.getOutputCount()));
            insertView.setInitialValues(getViewContext().getRequest().getParameterMap());
            ButtonBar bar = new ButtonBar();
            bar.setStyle(ButtonBar.Style.separateButtons);
            ActionButton submitButton = new ActionButton("deriveSamples.view", "Submit");
            submitButton.setActionType(ActionButton.Action.POST);
            bar.add(submitButton);
            insertView.getDataRegion().setButtonBar(bar);
            insertView.setTitle("Output Samples");

            Map<ExpMaterial, String> materialsWithRoles = new LinkedHashMap<ExpMaterial, String>();
            List<ExpMaterial> materials = form.lookupMaterials();
            for (int i = 0; i < materials.size(); i++)
            {
                materialsWithRoles.put(materials.get(i), form.determineLabel(i));
            }

            DeriveSamplesChooseTargetBean bean = new DeriveSamplesChooseTargetBean(getUploadableSampleSets(), materialsWithRoles, form.getOutputCount(), Collections.<String>emptyList(), helper);
            JspView<DeriveSamplesChooseTargetBean> view = new JspView<DeriveSamplesChooseTargetBean>("/org/labkey/experiment/summarizeMaterialInputs.jsp", bean);
            view.setTitle("Input Samples");

            return new VBox(view, insertView);
        }
    }

    @RequiresPermissionClass(InsertPermission.class)
    public class DeriveSamplesAction extends SimpleViewAction<DeriveMaterialForm>
    {
        private DescribeDerivedSamplesAction _action;

        public ModelAndView getView(DeriveMaterialForm form, BindException errors) throws Exception
        {
            List<ExpMaterial> materials = form.lookupMaterials();

            Map<ExpMaterial, String> inputMaterials = new LinkedHashMap<ExpMaterial, String>();
            int unknownMaterialCount = 0;
            for (int i = 0; i < materials.size(); i++)
            {
                String inputRole = form.determineLabel(i);
                if (inputRole == null || "".equals(inputRole))
                {
                    inputRole = "Material" + (unknownMaterialCount == 0 ? "" : Integer.toString(unknownMaterialCount + 1));
                    unknownMaterialCount++;
                }
                inputMaterials.put(materials.get(i), inputRole);
            }

            ExpSampleSet sampleSet = ExperimentService.get().getSampleSet(form.getTargetSampleSetId());

            Map<ExpMaterial, String> outputMaterials = new HashMap<ExpMaterial, String>();

            DerivedSamplePropertyHelper helper = new DerivedSamplePropertyHelper(sampleSet, form.getOutputCount(), getContainer(), getUser());

            boolean valid = true;
            for (Map.Entry<String, Map<DomainProperty, String>> entry : helper.getPostedPropertyValues(getViewContext().getRequest()).entrySet())
                valid = UploadWizardAction.validatePostedProperties(entry.getValue(), errors) && valid;
            if (!valid)
                return redirectError(form, errors);

            Map<String, Map<DomainProperty, String>> allProperties;
            try
            {
                allProperties = helper.getSampleProperties(getViewContext().getRequest());
            }
            catch (DuplicateMaterialException e)
            {
                errors.addError(new ObjectError(ColumnInfo.propNameFromName(e.getColName()), null, null, ERROR_MSG + " " + e.getMessage()));
                return redirectError(form, errors);
            }

            int i = 0;
            for (Map.Entry<String, Map<DomainProperty, String>> entry : allProperties.entrySet())
            {
                Map<DomainProperty, String> props = entry.getValue();
                String name = helper.determineMaterialName(props);
                ExpMaterial outputMaterial = ExperimentService.get().createExpMaterial(getContainer(), entry.getKey(), name);
                if (sampleSet != null)
                {
                    outputMaterial.setCpasType(sampleSet.getLSID());
                }
                outputMaterial.save(getUser());

                if (sampleSet != null)
                {
                    for (Map.Entry<DomainProperty, String> propertyEntry : entry.getValue().entrySet())
                        outputMaterial.setProperty(getUser(), propertyEntry.getKey().getPropertyDescriptor(), propertyEntry.getValue());
                }

                outputMaterials.put(outputMaterial, helper.getSampleNames().get(i++));
            }

            ExperimentService.get().deriveSamples(inputMaterials, outputMaterials, getViewBackgroundInfo(), _log);

            HttpView.throwRedirect(ExperimentUrlsImpl.get().getShowSampleURL(getContainer(), outputMaterials.keySet().iterator().next()));
            return null;
        }

        private ModelAndView redirectError(DeriveMaterialForm form, BindException errors)
                throws Exception
        {
            _action = new DescribeDerivedSamplesAction();
            _action.setViewContext(getViewContext());
            return _action.getView(form, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return _action.appendNavTrail(root);
        }
    }

    public static class DeriveMaterialForm implements HasViewContext
    {
        private int _outputCount = 1;
        private int _targetSampleSetId;
        private int[] _rowIds;
        private String _name;

        private ViewContext _context;

        public void setViewContext(ViewContext context)
        {
            _context = context;
        }

        public ViewContext getViewContext()
        {
            return _context;
        }

        public List<ExpMaterial> lookupMaterials()
        {
            List<ExpMaterial> result = new ArrayList<ExpMaterial>();
            for (int rowId : getRowIds())
            {
                ExpMaterial material = ExperimentService.get().getExpMaterial(rowId);
                if (material != null)
                {
                    if (material.getContainer().hasPermission(_context.getUser(), ReadPermission.class))
                    {
                        result.add(material);
                    }
                    else
                    {
                        HttpView.throwUnauthorized();
                    }
                }
                else
                {
                    HttpView.throwNotFound();
                }
            }
            Collections.sort(result, new Comparator<ExpMaterial>()
            {
                public int compare(ExpMaterial o1, ExpMaterial o2)
                {
                    return o1.getName().compareTo(o2.getName());
                }
            });
            return result;
        }

        public String getName()
        {
            return _name;
        }

        public void setName(String name)
        {
            _name = name;
        }

        public int[] getRowIds()
        {
            if (_rowIds == null)
            {
                _rowIds = PageFlowUtil.toInts(DataRegionSelection.getSelected(getViewContext(), true));
            }
            return _rowIds;
        }

        public void setRowIds(int[] rowIds)
        {
            _rowIds = rowIds;
        }

        public int getOutputCount()
        {
            return _outputCount;
        }

        public void setOutputCount(int outputCount)
        {
            _outputCount = outputCount;
        }

        public int getTargetSampleSetId()
        {
            return _targetSampleSetId;
        }

        public void setTargetSampleSetId(int targetSampleSetId)
        {
            _targetSampleSetId = targetSampleSetId;
        }

        public String getInputRole(int i)
        {
            return _context.getRequest().getParameter("inputRole" + i);
        }

        public String getCustomRole(int i)
        {
            return _context.getRequest().getParameter("customRole" + i);
        }

        public String determineLabel(int index)
        {
            String result = getInputRole(index);
            if (DeriveSamplesChooseTargetBean.CUSTOM_ROLE.equals(result))
            {
                result = getCustomRole(index);
            }
            if (result != null)
            {
                result = result.trim();
            }
            return result;
        }
    }

    public static class CreateExperimentForm extends ExperimentForm implements DataRegionSelection.DataSelectionKeyForm
    {
        private String _returnURL;
        private boolean _addSelectedRuns;
        private String _dataRegionSelectionKey;

        public String getReturnURL()
        {
            return _returnURL;
        }

        public void setReturnURL(String returnURL)
        {
            _returnURL = returnURL;
        }

        public boolean isAddSelectedRuns()
        {
            return _addSelectedRuns;
        }

        public void setAddSelectedRuns(boolean addSelectedRuns)
        {
            _addSelectedRuns = addSelectedRuns;
        }

        public String getDataRegionSelectionKey()
        {
            return _dataRegionSelectionKey;
        }

        public void setDataRegionSelectionKey(String dataRegionSelectionKey)
        {
            _dataRegionSelectionKey = dataRegionSelectionKey;
        }
    }

    @RequiresPermissionClass(InsertPermission.class) @ActionNames("createRunGroup, createExperiment")
    public class CreateRunGroupAction extends SimpleViewAction<CreateExperimentForm>
    {
        public ModelAndView getView(CreateExperimentForm form, BindException errors) throws Exception
        {
            // HACK - convert ExperimentForm to not be a BeanViewForm
            form.setReturnURL(getViewContext().getRequest().getParameter("returnURL"));
            form.setAddSelectedRuns("true".equals(getViewContext().getRequest().getParameter("addSelectedRuns")));
            form.setDataRegionSelectionKey(getViewContext().getRequest().getParameter(DataRegionSelection.DATA_REGION_SELECTION_KEY));
            if ("POST".equalsIgnoreCase(getViewContext().getRequest().getMethod()) && !"true".equals(getViewContext().getRequest().getParameter("noPost")))
            {
                Experiment exp = form.getBean();
                if (exp.getName() == null || exp.getName().trim().length() == 0)
                {
                    errors.reject(ERROR_MSG, "You must specify a name for the experiment");
                }

                String lsid;
                int suffix = 1;
                do
                {
                    String template = "urn:lsid:" + XarContext.LSID_AUTHORITY_SUBSTITUTION + ":Experiment.Folder-" + XarContext.CONTAINER_ID_SUBSTITUTION + ":" + exp.getName();
                    if (suffix > 1)
                    {
                        template = template + suffix;
                    }
                    suffix++;
                    lsid = LsidUtils.resolveLsidFromTemplate(template, new XarContext("Experiment Creation", getContainer(), getUser()), "Experiment");
                }
                while (ExperimentService.get().getExpExperiment(lsid) != null);
                exp.setLSID(lsid);
                exp.setContainer(getContainer());

                if (errors.getErrorCount() == 0)
                {
                    ExpExperimentImpl wrapper = new ExpExperimentImpl(exp);
                    wrapper.save(getUser());

                    if (form.isAddSelectedRuns())
                    {
                        addSelectedRunsToExperiment(wrapper);
                    }

                    if (form.getReturnURL() != null)
                    {
                        HttpView.throwRedirect(form.getReturnURL());
                    }
                    HttpView.throwRedirect(ExperimentUrlsImpl.get().getShowExperimentsURL(getContainer()));
                }
            }

            DataRegion drg = new DataRegion();

            drg.addHiddenFormField("returnURL", getViewContext().getRequest().getParameter("returnURL"));
            drg.addHiddenFormField("addSelectedRuns", java.lang.Boolean.toString("true".equals(getViewContext().getRequest().getParameter("addSelectedRuns"))));
            form.setDataRegionSelectionKey(getViewContext().getRequest().getParameter(DataRegionSelection.DATA_REGION_SELECTION_KEY));
            for (String rowId : DataRegionSelection.getSelected(getViewContext(), false))
            {
                drg.addHiddenFormField(DataRegion.SELECT_CHECKBOX_NAME, rowId);
            }
            drg.addHiddenFormField(DataRegionSelection.DATA_REGION_SELECTION_KEY, getViewContext().getRequest().getParameter(DataRegionSelection.DATA_REGION_SELECTION_KEY));

            drg.addColumns(ExperimentServiceImpl.get().getTinfoExperiment(), "RowId,Name,LSID,ContactId,ExperimentDescriptionURL,Hypothesis,Comments,Created");

            drg.setFixedWidthColumns(false);
            DisplayColumn col = drg.getDisplayColumn("RowId");
            col.setVisible(false);
            drg.getDisplayColumn("LSID").setVisible(false);
            drg.getDisplayColumn("Created").setVisible(false);

            ButtonBar bb = new ButtonBar();
            bb.setStyle(ButtonBar.Style.separateButtons);
            ActionButton insertButton = new ActionButton(new ActionURL(CreateRunGroupAction.class, getContainer()), "Submit");
            bb.add(insertButton);

            drg.setButtonBar(bb);

            return new InsertView(drg, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Create Run Group");
        }
    }

    public static class MoveRunsForm implements DataRegionSelection.DataSelectionKeyForm
    {
        private String _targetContainerId;
        private String _dataRegionSelectionKey;

        public String getDataRegionSelectionKey()
        {
            return _dataRegionSelectionKey;
        }

        public void setDataRegionSelectionKey(String key)
        {
            _dataRegionSelectionKey = key;
        }

        public String getTargetContainerId()
        {
            return _targetContainerId;
        }

        public void setTargetContainerId(String targetContainerId)
        {
            _targetContainerId = targetContainerId;
        }
    }

    @RequiresPermissionClass(DeletePermission.class)
    public class MoveRunsLocationAction extends SimpleViewAction<MoveRunsForm>
    {
        public ModelAndView getView(MoveRunsForm form, BindException errors) throws Exception
        {
            ActionURL moveURL = new ActionURL(MoveRunsAction.class, getContainer());
            PipelineRootContainerTree ct = new PipelineRootContainerTree(getUser(), moveURL)
            {
                protected void renderCellContents(StringBuilder html, Container c, ActionURL url, boolean hasRoot)
                {
                    if (hasRoot && !c.equals(getContainer()))
                    {
                        html.append("<a href=\"javascript:moveTo('");
                        html.append(c.getId());
                        html.append("')\">");
                    }
                    html.append(PageFlowUtil.filter(c.getName()));
                    if (hasRoot)
                    {
                        html.append("</a>");
                    }
                }
            };
            ct.setInitialLevel(1);

            MoveRunsBean bean = new MoveRunsBean(ct, form.getDataRegionSelectionKey());
            return new JspView<MoveRunsBean>("/org/labkey/experiment/moveRunsLocation.jsp", bean);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Move Runs");
        }
    }



    @RequiresPermissionClass(DeletePermission.class)
    public class MoveRunsAction extends FormHandlerAction<MoveRunsForm>
    {
        private Container _targetContainer;
        public void validateCommand(MoveRunsForm target, Errors errors)
        {
        }

        public boolean handlePost(MoveRunsForm form, BindException errors) throws Exception
        {
            _targetContainer = ContainerManager.getForId(form.getTargetContainerId());
            if (_targetContainer == null || !_targetContainer.hasPermission(getUser(), InsertPermission.class))
            {
                HttpView.throwUnauthorized();
            }

            Set<String> runIds = DataRegionSelection.getSelected(getViewContext(), true);
            List<ExpRun> runs = new ArrayList<ExpRun>();
            for (String runId : runIds)
            {
                try
                {
                    ExpRun run = ExperimentService.get().getExpRun(Integer.parseInt(runId));
                    if (run != null)
                    {
                        runs.add(run);
                    }
                }
                catch (NumberFormatException e) {}
            }

            ViewBackgroundInfo info = getViewBackgroundInfo();
            info.setContainer(_targetContainer);

            try
            {
                ExperimentService.get().moveRuns(info, getContainer(), runs);
            }
            catch (IOException e)
            {
                HttpView.throwNotFound("Failed to initialize move. Check that the pipeline root is configured correctly. " + e);
            }
            return true;
        }

        public ActionURL getSuccessURL(MoveRunsForm form)
        {
            return PageFlowUtil.urlProvider(PipelineUrls.class).urlBegin(_targetContainer);
        }
    }

    public static class ShowExternalDocsForm
    {
        private String _objectURI;
        private String _propertyURI;

        public String getObjectURI()
        {
            return _objectURI;
        }

        public void setObjectURI(String objectURI)
        {
            _objectURI = objectURI;
        }

        public String getPropertyURI()
        {
            return _propertyURI;
        }

        public void setPropertyURI(String propertyURI)
        {
            _propertyURI = propertyURI;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class ShowExternalDocsAction extends SimpleViewAction<ShowExternalDocsForm>
    {
        public ModelAndView getView(ShowExternalDocsForm form, BindException errors) throws Exception
        {
            Map<String, ObjectProperty> props = OntologyManager.getPropertyObjects(getContainer(), form.getObjectURI());
            ObjectProperty prop = props.get(form.getPropertyURI());
            if (prop == null || !getContainer().equals(prop.getContainer()))
            {
                throw new NotFoundException();
            }
            URI uri = new URI(prop.getStringValue());
            File f = new File(uri);
            if (!f.exists())
            {
                throw new NotFoundException();
            }

            PageFlowUtil.streamFile(getViewContext().getResponse(), f.getAbsolutePath(), false);
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class ShowGraphMoreListAction extends SimpleViewAction<ExperimentRunForm>
    {
        private ExperimentRunForm _form;
        public ModelAndView getView(ExperimentRunForm form, BindException errors) throws Exception
        {
            _form = form;
            return new GraphMoreGrid(getContainer(), errors, getViewContext().getActionURL());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild(new NavTree("Experiments", ExperimentUrlsImpl.get().getShowExperimentsURL(getViewContext().getContainer())));
            ExpRun run = ExperimentService.get().getExpRun(_form.getRowId());
            if (run != null)
            {
                root.addChild(new NavTree("Experiment Run", ExperimentUrlsImpl.get().getRunGraphURL(_form.lookupRun())));
            }
            root.addChild(new NavTree("Selected Protocol Applications"));
            return root;
        }
    }

    public static File getPipelineRoot(Container c) throws SQLException
    {
        PipeRoot pr = PipelineService.get().findPipelineRoot(c);
        if (pr == null)
            return null;

        URI uri = pr.getUri();
        if (uri == null)
        {
            return null;
        }
        File rootFile = new File(uri);
        NetworkDrive.ensureDrive(rootFile.getAbsolutePath());
        return rootFile;
    }

    public static boolean hasValidPipelineURI(Container c) throws SQLException
    {
        File rootFile = getPipelineRoot(c);
        return rootFile != null && NetworkDrive.exists(rootFile) && rootFile.isDirectory();
    }

    @RequiresPermissionClass(InsertPermission.class)
    public class ImportXarFileAction extends SimpleViewAction<PipelinePathForm>
    {
        public ModelAndView getView(PipelinePathForm form, BindException errors) throws Exception
        {
            for (File f : form.getValidatedFiles(getContainer()))
            {
                if (f.isFile())
                {
                    ExperimentPipelineJob job = new ExperimentPipelineJob(getViewBackgroundInfo(), f, "Experiment Import", false);
                    PipelineService.get().queueJob(job);
                }
                else
                {
                    throw new NotFoundException("Expected a file but found a directory: " + f.getName());
                }
            }
            
            return HttpView.redirect(getContainer().getStartURL(getViewContext().getUser()));
        }

        public NavTree appendNavTrail(NavTree root)
        {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * User: jeckels
     * Date: Jan 27, 2008
     */
    public static class ExperimentUrlsImpl implements ExperimentUrls
    {
        public ActionURL getOverviewURL(Container c)
        {
            return new ActionURL(BeginAction.class, c);
        }

        public ActionURL getExperimentDetailsURL(Container c, ExpExperiment expExperiment)
        {
            return new ActionURL(DetailsAction.class, c).addParameter("rowId", expExperiment.getRowId());
        }

        public ActionURL getShowSampleURL(Container c, ExpMaterial material)
        {
            return new ActionURL(ShowMaterialAction.class, c).addParameter("rowId", material.getRowId());
        }

        public ActionURL getExportProtocolURL(Container container, ExpProtocol protocol)
        {
            return new ActionURL(ExperimentController.ExportProtocolsAction.class, container).
                    addParameter("protocolId", protocol.getRowId()).
                    addParameter("xarFileName", protocol.getName() + ".xar");
        }

        public ActionURL getMoveRunsLocationURL(Container container)
        {
            return new ActionURL(ExperimentController.MoveRunsLocationAction.class, container);
        }

        public ActionURL getProtocolDetailsURL(ExpProtocol protocol)
        {
            return new ActionURL(ProtocolDetailsAction.class, protocol.getContainer()).addParameter("rowId", protocol.getRowId());
        }

        public ActionURL getProtocolApplicationDetailsURL(ExpProtocolApplication app)
        {
            return new ActionURL(ShowApplicationAction.class, app.getContainer()).addParameter("rowId", app.getRowId());
        }

        public ActionURL getProtocolGridURL(Container c)
        {
            return new ActionURL(ShowProtocolGridAction.class, c);
        }

        public ActionURL getRunGraphDetailURL(ExpRun run)
        {
            return new ActionURL(ShowRunGraphDetailAction.class, run.getContainer()).addParameter("rowId", run.getRowId());
        }

        public ActionURL getRunGraphURL(Container container, int runId)
        {
            return new ActionURL(ShowRunGraphAction.class, container).addParameter("rowId", runId);
        }

        public ActionURL getRunGraphURL(ExpRun run)
        {
            return getRunGraphURL(run.getContainer(), run.getRowId());
        }

        public ActionURL getRunTextURL(Container c, int runId)
        {
            return new ActionURL(ShowRunTextAction.class, c).addParameter("rowId", runId);
        }

        public ActionURL getRunTextURL(ExpRun run)
        {
            return getRunTextURL(run.getContainer(), run.getRowId());
        }

        public ActionURL getDeleteExperimentsURL(Container container, URLHelper returnURL)
        {
            return new ActionURL(DeleteSelectedExperimentsAction.class, container).addParameter("returnURL", returnURL.getLocalURIString());
        }

        public ActionURL getAddRunsToExperimentURL(Container c, ExpExperiment exp)
        {
            return new ActionURL(AddRunsToExperimentAction.class, c).addParameter("expRowId", exp.getRowId());
        }

        public ActionURL getShowRunsURL(Container c, ExperimentRunType type)
        {
            ActionURL result = new ActionURL(ShowRunsAction.class, c);
            result.addParameter("experimentRunFilter", type.getDescription());
            return result;
        }

        public ActionURL getShowAddXarFileURL(Container c)
        {
            return new ActionURL(ShowAddXarFileAction.class, c);
        }

        public ActionURL getShowExperimentsURL(Container c)
        {
            return new ActionURL(ShowRunGroupsAction.class, c);
        }

        public ActionURL getShowSampleSetListURL(Container c)
        {
            return getShowSampleSetListURL(c, null);
        }

        public ActionURL getShowSampleSetURL(ExpSampleSet sampleSet)
        {
            return new ActionURL(ShowMaterialSourceAction.class, sampleSet.getContainer()).addParameter("rowId", sampleSet.getRowId());
        }

        public ActionURL getExperimentListURL(Container container)
        {
            return new ActionURL(ShowRunGroupsAction.class, container);
        }


        public ActionURL getShowSampleSetListURL(Container c, String errorMessage)
        {
            ActionURL url = new ActionURL(ListMaterialSourcesAction.class, c);
            if (errorMessage != null)
            {
                url.addParameter("sampleSetError", errorMessage);
            }
            return url;
        }

        public ActionURL getDeleteDatasURL(Container c, URLHelper returnURL)
        {
            return new ActionURL(DeleteSelectedDataAction.class, c).addParameter("returnURL", returnURL.getLocalURIString());
        }

        public ActionURL getDeleteSelectedExperimentsURL(Container c, URLHelper returnURL)
        {
            return new ActionURL(DeleteSelectedExperimentsAction.class, c).addParameter("returnURL", returnURL.getLocalURIString());
        }

        public ActionURL getDeleteSelectedExpRunsURL(Container container, URLHelper returnURL)
        {
            return new ActionURL(DeleteSelectedExpRunsAction.class, container).addParameter("returnURL", returnURL.getLocalURIString());
        }

        public ActionURL getShowUpdateURL(ExpExperiment experiment)
        {
            return new ActionURL(ShowUpdateAction.class, experiment.getContainer()).addParameter("rowId", experiment.getRowId());
        }

        public ActionURL getRemoveSelectedExpRunsURL(Container container, URLHelper returnURL, ExpExperiment exp)
        {
            return new ActionURL(RemoveSelectedExpRunsAction.class, container).addParameter("returnURL", returnURL.getLocalURIString()).addParameter("expRowId", exp.getRowId());
        }

        public ActionURL getCreateRunGroupURL(Container container, URLHelper returnURL, boolean addSelectedRuns)
        {
            ActionURL result = new ActionURL(CreateRunGroupAction.class, container);
            if (returnURL != null)
            {
                result.addParameter("returnURL", returnURL.getLocalURIString());
            }
            if (addSelectedRuns)
            {
                result.addParameter("addSelectedRuns", "true");
            }
            return result;
        }


        public static ExperimentUrlsImpl get()
        {
            return (ExperimentUrlsImpl)PageFlowUtil.urlProvider(ExperimentUrls.class);
        }

        public ActionURL getDownloadGraphURL(ExpRun run, boolean detail, String focus)
        {
            ActionURL result = new ActionURL(DownloadGraphAction.class, run.getContainer());
            result.addParameter("rowId", run.getRowId()).addParameter("detail", detail);
            if (focus != null)
            {
                result.addParameter("focus", focus);
            }
            return result;
        }

        public ActionURL getBeginURL(Container container)
        {
            return new ActionURL(BeginAction.class, container);
        }

        public ActionURL getDomainEditorURL(Container container, int domainId)
        {
            ActionURL url = new ActionURL(PropertyController.EditDomainAction.class, container);
            url.addParameter("domainId", domainId);
            return url;
        }

        public ActionURL getShowFileURL(Container c, ExpData data, boolean inline)
        {
            ActionURL result = getShowFileURL(c).addParameter("rowId", data.getRowId());
            if (inline)
            {
                result.addParameter("inline", inline);
            }
            return result;
        }

        public ActionURL getMaterialDetailsURL(ExpMaterial material)
        {
            return new ActionURL(ShowMaterialAction.class, material.getContainer()).addParameter("rowId", material.getRowId());
        }

        public ActionURL getDataDetailsURL(ExpData data)
        {
            return new ActionURL(ShowDataAction.class, data.getContainer()).addParameter("rowId", data.getRowId());
        }

        public ActionURL getShowFileURL(Container c)
        {
            return new ActionURL(ShowFileAction.class, c);
        }

        public ActionURL getSetFlagURL(Container container)
        {
            return new ActionURL(SetFlagAction.class, container);
        }

        public ActionURL getShowRunGraphURL(ExpRun run)
        {
            return new ActionURL(ShowRunGraphAction.class, run.getContainer()).addParameter("rowId", run.getRowId());
        }

        public ActionURL getUploadXARURL(Container container)
        {
            return new ActionURL(ShowAddXarFileAction.class, container);
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class SampleSetServiceAction extends GWTServiceAction
    {
        protected BaseRemoteService createService()
        {
            return new SampleSetServiceImpl(getViewContext());
        }
    }
}
