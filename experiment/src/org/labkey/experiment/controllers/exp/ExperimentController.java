/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.action.*;
import org.labkey.api.data.*;
import org.labkey.api.exp.*;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.api.*;
import org.labkey.api.exp.xar.LsidUtils;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineUrls;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.security.ACL;
import org.labkey.api.security.ActionNames;
import org.labkey.api.security.RequiresPermission;
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
import org.json.JSONArray;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.imageio.ImageIO;
import java.io.*;
import java.net.URI;
import java.sql.SQLException;
import java.util.*;
import java.awt.image.BufferedImage;

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

    @RequiresPermission(ACL.PERM_READ)
    public class BeginAction extends GridViewAction
    {
    }

    @RequiresPermission(ACL.PERM_READ)
    public class GridViewAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            RunGroupWebPart v = new RunGroupWebPart(getViewContext(), false);
            v.showHeader();

            Set<ExperimentRunType> types = ExperimentService.get().getExperimentRunTypes(getContainer());
            ChooseExperimentTypeBean bean = new ChooseExperimentTypeBean(types, ExperimentRunType.getSelectedFilter(types, getViewContext().getRequest().getParameter("experimentRunFilter")), getViewContext().getActionURL().clone());
            JspView chooserView = new JspView<ChooseExperimentTypeBean>("/org/labkey/experiment/experimentRunQueryHeader.jsp", bean);
            chooserView.setTitle(ExperimentModule.EXPERIMENT_RUN_WEB_PART_NAME);
            chooserView.setTitleHref(ExperimentUrlsImpl.get().getShowRunsURL(getContainer(), ExperimentRunType.ALL_RUNS_TYPE));

            ExperimentRunListView runView = ExperimentRunListView.createView(getViewContext(), bean.getSelectedFilter(), true);
            runView.setShowDeleteButton(true);
            runView.setShowAddToRunGroupButton(true);
            runView.setShowMoveRunsButton(true);

            ProtocolWebPart protocolView = new ProtocolWebPart(false, getViewContext());
            SampleSetWebPart sampleSet = new SampleSetWebPart(false, getViewContext());

            return new VBox(chooserView, runView, v, protocolView, sampleSet);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendRootNavTrail(root);
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class ShowRunsAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            Set<ExperimentRunType> types = ExperimentService.get().getExperimentRunTypes(getContainer());
            ChooseExperimentTypeBean bean = new ChooseExperimentTypeBean(types, ExperimentRunType.getSelectedFilter(types, getViewContext().getRequest().getParameter("experimentRunFilter")), getViewContext().getActionURL().clone());
            JspView chooserView = new JspView<ChooseExperimentTypeBean>("/org/labkey/experiment/experimentRunQueryHeader.jsp", bean);

            ExperimentRunListView view = ExperimentRunListView.createView(getViewContext(), bean.getSelectedFilter(), true);
            view.setShowDeleteButton(true);
            view.setShowAddToRunGroupButton(true);
            view.setShowMoveRunsButton(true);
            return new VBox(chooserView, view);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendRootNavTrail(root).addChild("Experiment Runs");
        }
    }

    @RequiresPermission(ACL.PERM_READ) @ActionNames("showRunGroups, showExperiments")
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

    @RequiresPermission(ACL.PERM_READ)
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


    @RequiresPermission(ACL.PERM_READ)
    public class DetailsAction extends SimpleViewAction<ExperimentForm>
    {
        private ExpExperimentImpl _experiment;

        public ModelAndView getView(ExperimentForm form, BindException errors) throws Exception
        {
            Container c = getContainer();

            int rowId = form.getBean().getRowId();
            _experiment = ExperimentServiceImpl.get().getExpExperiment(rowId);
            if (_experiment == null)
            {
                throw new NotFoundException("Could not find an experiment with RowId " + rowId);
            }

            if (!_experiment.getContainer().equals(getViewContext().getContainer()))
            {
                HttpView.throwRedirect(getViewContext().cloneActionURL().setContainer(_experiment.getContainer()));
            }

            form.setBean(_experiment.getDataObject());

            CustomPropertiesView customPropertiesView = new CustomPropertiesView(_experiment.getLSID(), getViewContext().cloneActionURL(), c);

            DetailsView detailsView = new DetailsView(new DataRegion(), form);
            detailsView.getDataRegion().setTable(ExperimentServiceImpl.get().getTinfoExperiment());
            detailsView.getDataRegion().addColumns(ExperimentServiceImpl.get().getTinfoExperiment(), "RowId,Name,LSID,ContactId,ExperimentDescriptionURL,Hypothesis,Comments");
            detailsView.getDataRegion().getDisplayColumn(0).setVisible(false);
            detailsView.getDataRegion().getDisplayColumn(2).setWidth("60%");

            ButtonBar bb = new ButtonBar();
            ActionButton b = new ActionButton(ExperimentUrlsImpl.get().getShowUpdateURL(_experiment), "Edit");
            b.setDisplayPermission(ACL.PERM_UPDATE);
            bb.add(b);
            detailsView.getDataRegion().setButtonBar(bb);
            detailsView.setTitle("Run Group Details");

            VBox vbox = new VBox();
            vbox.addView(new StandardAndCustomPropertiesView(detailsView, customPropertiesView));

            ExpProtocol[] protocols = _experiment.getProtocols();

            Set<ExperimentRunType> types = new TreeSet<ExperimentRunType>(ExperimentService.get().getExperimentRunTypes(getContainer()));
            ExperimentRunType selectedType = ExperimentRunType.getSelectedFilter(types, getViewContext().getRequest().getParameter("experimentRunFilter"));
            if (selectedType == null)
            {
                if (protocols.length == 0)
                {
                    selectedType = ExperimentRunType.ALL_RUNS_TYPE;
                }
                else
                {
                    Handler.Priority bestPriority = null;
                    for (ExperimentRunType type : types)
                    {
                        Handler.Priority worstFilterPriority = Handler.Priority.HIGH;
                        for (ExpProtocol protocol : protocols)
                        {
                            Handler.Priority p = type.getPriority(protocol);
                            if (worstFilterPriority != null && (p == null || p.compareTo(worstFilterPriority) < 0))
                            {
                                worstFilterPriority = p;
                            }
                        }

                        if (worstFilterPriority != null && (bestPriority == null || bestPriority.compareTo(worstFilterPriority) < 0))
                        {
                            bestPriority = worstFilterPriority;
                            selectedType = type;
                        }
                    }
                }
            }

            ChooseExperimentTypeBean bean = new ChooseExperimentTypeBean(types, selectedType, getViewContext().getActionURL().clone());
            JspView chooserView = new JspView<ChooseExperimentTypeBean>("/org/labkey/experiment/experimentRunQueryHeader.jsp", bean);

            ExperimentRunListView runListView = ExperimentRunListView.createView(getViewContext(), bean.getSelectedFilter(), true);
            runListView.getRunTable().setExperiment(_experiment);
            runListView.setShowRemoveFromExperimentButton(true);
            runListView.setShowDeleteButton(true);
            runListView.setShowAddToRunGroupButton(true);
            runListView.setShowExportXARButton(true);
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

    @RequiresPermission(ACL.PERM_READ)
    public class ListMaterialSourcesAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            SampleSetWebPart view = new SampleSetWebPart(false, getViewContext());
            view.setTitle(null);
            view.setSampleSetError(getViewContext().getRequest().getParameter("sampleSetError"));

            return view;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendRootNavTrail(root).addChild("Sample Sets");
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class ShowMaterialSourceAction extends SimpleViewAction<MaterialSourceForm>
    {
        private ExpSampleSet _source;

        public ModelAndView getView(MaterialSourceForm form, BindException errors) throws Exception
        {
            MaterialSource source = form.getBean();
            if (source != null)
                _source = ExperimentServiceImpl.get().getSampleSet(source.getRowId());

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
            QueryView queryView = new QueryView(schema, settings)
            {
                protected void populateButtonBar(DataView view, ButtonBar bar)
                {
                    super.populateButtonBar(view, bar);

                        ActionURL deleteMaterialUrl = new ActionURL(DeleteMaterialByRowIdAction.class, getContainer());
                        ActionButton deleteMaterial = new ActionButton("", "Delete Selected");
                        deleteMaterial.setScript("return verifySelected(this.form, \"" + deleteMaterialUrl.getLocalURIString() + "\", \"post\", \"Samples\")");
                        deleteMaterial.setActionType(ActionButton.Action.POST);
                        deleteMaterial.setDisplayPermission(ACL.PERM_DELETE);
                        bar.add(deleteMaterial);

                    if (_source.canImportMoreSamples())
                    {
                        ActionURL urlUploadSamples = new ActionURL(ShowUploadMaterialsAction.class, getContainer());
                        urlUploadSamples.addParameter("name", sourceName);
                        urlUploadSamples.addParameter("importMoreSamples", "true");
                        ActionButton uploadButton = new ActionButton(urlUploadSamples.toString(), "Import More Samples", DataRegion.MODE_ALL, ActionButton.Action.LINK);
                        uploadButton.setDisplayPermission(ACL.PERM_UPDATE);
                        bar.add(uploadButton);
                    }

                    ActionURL urlDeriveSamples = new ActionURL(DeriveSamplesChooseTargetAction.class, getContainer());
                    ActionButton deriveButton = new ActionButton("", "Derive Samples");
                    deriveButton.setScript("return verifySelected(this.form, \"" + urlDeriveSamples.getLocalURIString() + "\", \"post\", \"Samples\")");
                    deriveButton.setActionType(ActionButton.Action.POST);
                    deriveButton.setDisplayPermission(ACL.PERM_INSERT);
                    bar.add(deriveButton);
                }
            };
            queryView.setShowRecordSelectors(getViewContext().hasPermission(ACL.PERM_DELETE) || getViewContext().hasPermission(ACL.PERM_INSERT));

            queryView.setTitle("Sample Set Contents");

            DetailsView detailsView = new DetailsView(SampleSetWebPart.getMaterialSourceRegion(getViewContext()), form);
            detailsView.setTitle("Sample Set Properties");

            return new VBox(detailsView, queryView);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            ActionURL url = new ActionURL(ListMaterialSourcesAction.class, getContainer());
            return appendRootNavTrail(root).addChild("Sample Sets", url).addChild("Sample Set " + _source.getName());
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class ShowAllMaterialsAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            ExpSchema schema = new ExpSchema(getUser(), getContainer());
            QuerySettings settings = new QuerySettings(getViewContext(), "Materials");
            settings.setSchemaName(schema.getSchemaName());
            settings.setAllowChooseQuery(false);
            settings.setQueryName(ExpSchema.TableType.Materials.toString());
            QueryView view = new QueryView(schema, settings)
            {
                protected void populateButtonBar(DataView view, ButtonBar bar)
                {
                    super.populateButtonBar(view, bar);

                    ActionURL urlDeriveSamples = new ActionURL(DeriveSamplesChooseTargetAction.class, getContainer());
                    ActionButton deriveButton = new ActionButton("", "Derive Samples");
                    deriveButton.setScript("return verifySelected(this.form, \"" + urlDeriveSamples.getLocalURIString() + "\", \"post\", \"Samples\")");
                    deriveButton.setActionType(ActionButton.Action.POST);
                    deriveButton.setDisplayPermission(ACL.PERM_INSERT);
                    bar.add(deriveButton);
                }
            };
            view.setShowDetailsColumn(false);
            view.setShowRecordSelectors(true);
            return view;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendRootNavTrail(root).addChild("All Materials");
        }
    }



    @RequiresPermission(ACL.PERM_READ)
    public class ShowMaterialAction extends SimpleViewAction<MaterialForm>
    {
        private ExpMaterialImpl _material;

        public ModelAndView getView(MaterialForm form, BindException errors) throws Exception
        {
            Container c = getContainer();
            Material inputMaterial = form.getBean();
            _material = ExperimentServiceImpl.get().getExpMaterial(inputMaterial.getRowId());
            if (_material == null && inputMaterial.getLSID() != null)
            {
                _material = ExperimentServiceImpl.get().getExpMaterial(inputMaterial.getLSID());
            }
            if (_material == null)
            {
                HttpView.throwNotFound("Could not find a material with RowId " + inputMaterial.getRowId());
            }

            ensureCorrectContainer(getContainer(), _material, getViewContext());

            form.setBean(_material.getDataObject());

            ExpRunImpl run = _material.getRun();
            ExpProtocol sourceProtocol = _material.getSourceProtocol();
            ExpProtocolApplication sourceProtocolApplication = _material.getSourceApplication();

            ExpSampleSet sampleSet = _material.getSampleSet();

            DataRegion dr = new DataRegion();
            dr.addColumns(ExperimentServiceImpl.get().getTinfoMaterial().getUserEditableColumns());
            dr.removeColumns("RowId", "RunId", "SourceProtocolLSID", "SourceApplicationId");
            //dr.addColumns(extraProps);
            dr.addDisplayColumn(new ExperimentRunDisplayColumn(run, "Source Experiment Run"));
            dr.addDisplayColumn(new ProtocolDisplayColumn(sourceProtocol, "Source Protocol"));
            dr.addDisplayColumn(new ProtocolApplicationDisplayColumn(sourceProtocolApplication, "Source Protocol Application"));
            dr.addDisplayColumn(new LineageGraphDisplayColumn(_material, run));
            dr.addDisplayColumn(new SampleSetDisplayColumn(_material));

            ButtonBar bb = new ButtonBar();
            //TODO: Can't yet edit materials uploaded from a material source
            if (null != sampleSet)
            {
                ActionButton ab = new ActionButton("Show Grid");
                ab.setActionType(ActionButton.Action.LINK);
                ab.setURL(ExperimentUrlsImpl.get().getShowSampleSetURL(sampleSet));
                bb.add(ab);
            }
            dr.setButtonBar(bb);
            DetailsView detailsView = new DetailsView(dr, form);
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

            List<ExpRun> runsToInvestigate = new ArrayList<ExpRun>();
            ExpRun parentRun = _material.getRun();
            if (parentRun != null)
            {
                runsToInvestigate.add(parentRun);
            }
            Set<ExpRun> investigatedRuns = new HashSet<ExpRun>();
            final Set<ExpMaterial> predecessorMaterials = new HashSet<ExpMaterial>();
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
                for (ExpMaterial m : predecessorRun.getMaterialInputs().keySet())
                {
                    ExpRun mRun = m.getRun();
                    if (mRun != null)
                    {
                        if (!investigatedRuns.contains(mRun))
                        {
                            runsToInvestigate.add(mRun);
                        }
                    }
                    else
                    {
                        predecessorMaterials.add(m);
                    }
                }
            }

            if (getContainer().hasPermission(getUser(), ACL.PERM_INSERT))
            {
                ActionURL deriveURL = new ActionURL(DeriveSamplesChooseTargetAction.class, getContainer());
                deriveURL.addParameter("rowIds", _material.getRowId());

                HtmlView deriveView = new HtmlView("[<a href=\"" + deriveURL + "\">derive samples from this sample</a>]");
                vbox.addView(deriveView);
            }

            ExperimentRunListView runListView = ExperimentRunListView.createView(getViewContext(), ExperimentRunType.ALL_RUNS_TYPE, true);
            runListView.getRunTable().setRuns(successorRuns);
            runListView.getRunTable().setContainerFilter(ContainerFilter.Filters.ALL_IN_SITE, getUser());
            runListView.setTitle("Runs using this material or a derived material");
            vbox.addView(runListView);

            ExpSchema schema = new ExpSchema(getUser(), getContainer());
            QuerySettings settings = new QuerySettings(getViewContext(), "predecessorMaterials");
            settings.setSchemaName(schema.getSchemaName());
            settings.setQueryName(ExpSchema.TableType.Materials.toString());
            settings.setAllowChooseQuery(false);
            QueryView predecessorMaterialView = new QueryView(schema, settings)
            {
                protected TableInfo createTable()
                {
                    ExpMaterialTable table = ExperimentServiceImpl.get().createMaterialTable(ExpSchema.TableType.Materials.toString(), "materials", getSchema());
                    table.setMaterials(predecessorMaterials);
                    table.populate();
                    return table;
                }
            };
            predecessorMaterialView.setShowBorders(true);
            predecessorMaterialView.setShowExportButtons(false);
            predecessorMaterialView.setShadeAlternatingRows(true);
            predecessorMaterialView.setButtonBarPosition(DataRegion.ButtonBarPosition.BOTTOM);
            predecessorMaterialView.setTitle("Materials from which this material is derived");
            vbox.addView(predecessorMaterialView);
            return vbox;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendRootNavTrail(root).addChild("Sample " + _material.getName());
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class ShowRunGraphAction extends AbstractShowRunAction
    {
        protected HttpView createLowerView(ExpRunImpl experimentRun)
        {
            return new ExperimentRunGraphView(experimentRun, false);
        }
    }


    @RequiresPermission(ACL.PERM_READ)
    public class DownloadGraphAction extends SimpleViewAction<ExperimentRunForm>
    {
        public ModelAndView getView(ExperimentRunForm form, BindException errors) throws Exception
        {
            boolean detail = form.isDetail();
            String focus = form.getFocus();

            ExpRunImpl experimentRun = form.lookupRun();
            ensureCorrectContainer(getContainer(), experimentRun, getViewContext());

            ExperimentRunGraph.RunGraphFiles files = ExperimentRunGraph.generateRunGraph(getViewContext(), experimentRun, detail, focus);

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
            vbox.addView(createLowerView(_experimentRun));
            return vbox;
        }

        protected abstract HttpView createLowerView(ExpRunImpl experimentRun);

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

    @RequiresPermission(ACL.PERM_UPDATE)
    public class ToggleRunExperimentMembershipAction extends SimpleViewAction<ToggleRunExperimentMembershipForm>
    {
        public NavTree appendNavTrail(NavTree root)
        {
            throw new UnsupportedOperationException();
        }

        public ModelAndView getView(ToggleRunExperimentMembershipForm form, BindException errors) throws Exception
        {
            ExpRun run = ExperimentService.get().getExpRun(form.getRunId());
            if (run == null || !run.getContainer().equals(getViewContext().getContainer()))
            {
                return HttpView.throwNotFound();
            }

            ExpExperiment exp = ExperimentService.get().getExpExperiment(form.getExperimentId());
            if (exp == null || !exp.getContainer().equals(getViewContext().getContainer()))
            {
                return HttpView.throwNotFound();
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

    @RequiresPermission(ACL.PERM_READ)
    public class ShowRunTextAction extends AbstractShowRunAction
    {
        protected HttpView createLowerView(ExpRunImpl expRun)
        {
            JspView<RunInputOutputBean> inputView = new JspView<RunInputOutputBean>("/org/labkey/experiment/ExperimentRunInputOutput.jsp", new RunInputOutputBean(expRun.getMaterialInputs(), expRun.getDataInputs()));
            inputView.setFrame(WebPartView.FrameType.TITLE);
            inputView.setTitle("Run Inputs");

            Map<ExpMaterial, String> outputMaterials = new LinkedHashMap<ExpMaterial, String>();
            for (ExpMaterial material : expRun.getMaterialOutputs())
            {
                outputMaterials.put(material, null);
            }
            Map<ExpData, String> outputDatas = new LinkedHashMap<ExpData, String>();
            for (ExpData expData : expRun.getDataOutputs())
            {
                outputDatas.put(expData, null);
            }
            JspView<RunInputOutputBean> outputView = new JspView<RunInputOutputBean>("/org/labkey/experiment/ExperimentRunInputOutput.jsp", new RunInputOutputBean(outputMaterials, outputDatas));
            outputView.setFrame(WebPartView.FrameType.TITLE);
            outputView.setTitle("Run Outputs");

            HBox inputOutputHBox = new HBox(inputView, outputView);

            JspView<ExpRun> applicationsView = new JspView<ExpRun>("/org/labkey/experiment/ProtocolApplications.jsp", expRun);
            applicationsView.setFrame(WebPartView.FrameType.TITLE);
            applicationsView.setTitle("Protocol Applications");

            HtmlView toggleView = new HtmlView("[<a href=\"" + ExperimentUrlsImpl.get().getRunGraphURL(expRun) + "\">graph summary view</a>] [<a href=\"" + ExperimentUrlsImpl.get().getRunGraphDetailURL(expRun) + "\">graph detail view</a>]");

            VBox result = new VBox(toggleView, inputOutputHBox, applicationsView);
            result.setTitle("Text View");
            result.setFrame(WebPartView.FrameType.PORTAL);
            return result;
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class ShowRunGraphDetailAction extends AbstractShowRunAction
    {
        protected HttpView createLowerView(ExpRunImpl run)
        {
            ExperimentRunGraphView gw = new ExperimentRunGraphView(run, true);
            if (null != getViewContext().getActionURL().getParameter("focus"))
                gw.setFocus(getViewContext().getActionURL().getParameter("focus"));
            return gw;
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

    @RequiresPermission(ACL.PERM_READ)
    public class ShowDataAction extends AbstractDataAction
    {
        public ModelAndView getDataView(DataForm form, BindException errors) throws Exception
        {
            Container c = getContainer();
            String relativePath = null;
            if (c.hasPermission(getUser(), ACL.PERM_INSERT))
            {
                PipeRoot root = PipelineService.get().findPipelineRoot(c);
                if (root != null)
                {
                    URI uri = root.getUri(c);
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
            dr.removeColumns("DataFileUrl", "RowId", "RunId", "SourceProtocolLSID", "SourceApplicationId");
            dr.addDisplayColumn(new DataFileURLDisplayColumn(_data));
            dr.addDisplayColumn(new ExperimentRunDisplayColumn(run, "Source Experiment Run"));
            dr.addDisplayColumn(new ProtocolDisplayColumn(sourceProtocol, "Source Protocol"));
            dr.addDisplayColumn(new ProtocolApplicationDisplayColumn(sourceProtocolApplication, "Source Protocol Application"));
            dr.addDisplayColumn(new LineageGraphDisplayColumn(_data, run));
            DetailsView detailsView = new DetailsView(dr, _data.getRowId());
            detailsView.setTitle("Standard Properties");
            ButtonBar bb = new ButtonBar();

            ActionURL viewDataURL = _data.findDataHandler().getContentURL(getContainer(), _data);
            if (viewDataURL != null)
            {
                bb.add(new ActionButton("View data", viewDataURL));
            }

            if (_data.isFileOnDisk())
            {
                bb.add(new ActionButton("View file", ExperimentUrlsImpl.get().getShowFileURL(c, _data, true)));
                bb.add(new ActionButton("Download file", ExperimentUrlsImpl.get().getShowFileURL(c, _data, false)));

                if (getContainer().hasPermission(getUser(), ACL.PERM_INSERT))
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
            runListView.getRunTable().setContainerFilter(ContainerFilter.Filters.ALL_IN_SITE, getUser());
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

    @RequiresPermission(ACL.PERM_READ)
    public class ShowFileAction extends AbstractDataAction
    {
        protected ModelAndView getDataView(DataForm form, BindException errors) throws Exception
        {
            String dataURL = _data.getDataFileUrl();

            File realContent = new File(new URI(dataURL));
            if (!realContent.exists() || !realContent.isFile())
            {
                HttpView.throwNotFound("Data file, " + realContent + ", does not exist on disk");
            }

            try
            {
                boolean inline = _data.isInlineImage() || form.isInline();
                if (_data.isInlineImage() && form.getMaxDimension() != null && _data.isFileOnDisk())
                {
                    ByteArrayOutputStream bOut = new ByteArrayOutputStream();
                    BufferedImage image = ImageIO.read(_data.getDataFile());
                    int imageMax = Math.max(image.getHeight(), image.getWidth());
                    if (imageMax > form.getMaxDimension())
                    {
                        double scale = (double)form.getMaxDimension().intValue() / (double)imageMax;
                        ImageUtil.resizeImage(image, bOut, scale, 1);
                        PageFlowUtil.streamFileBytes(getViewContext().getResponse(), realContent.getName(), bOut.toByteArray(), !inline);
                        return null;
                    }
                }
                PageFlowUtil.streamFile(getViewContext().getResponse(), realContent.getAbsolutePath(), !inline);
            }
            catch (IOException e)
            {
                HttpView.throwNotFound("Unable to get file: " + e.toString());
            }

            return null;
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class ShowApplicationAction extends SimpleViewAction<ProtocolApplicationForm>
    {
        private ExpProtocolApplicationImpl _app;
        private ExpRun _run;

        public ModelAndView getView(ProtocolApplicationForm form, BindException errors) throws Exception
        {
            _app = ExperimentServiceImpl.get().getExpProtocolApplication(form.getBean().getRowId());
            if (_app == null)
            {
                HttpView.throwNotFound("Could not find Protocol Application");
            }
            _run = _app.getRun();
            if (_run == null)
            {
                HttpView.throwNotFound("No experiment run associated with Protocol Application");
            }
            form.setBean(_app.getDataObject());
            ensureCorrectContainer(getContainer(), _app, getViewContext());

            ExpProtocol protocol = _app.getProtocol();

            DataRegion dr = new DataRegion();
            dr.addColumns(ExperimentServiceImpl.get().getTinfoProtocolApplication().getUserEditableColumns());
            DetailsView detailsView = new DetailsView(dr, form);
            dr.removeColumns("RunId", "ProtocolLSID", "RowId");
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

    @RequiresPermission(ACL.PERM_READ)
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

    @RequiresPermission(ACL.PERM_READ)
    public class ProtocolDetailsAction extends SimpleViewAction<ProtocolForm>
    {
        private ExpProtocol _protocol;

        public ModelAndView getView(ProtocolForm form, BindException errors) throws Exception
        {
            Protocol bean = form.getBean();
            _protocol = ExperimentService.get().getExpProtocol(bean.getRowId());
            if (_protocol == null)
            {
                _protocol = ExperimentServiceImpl.get().getExpProtocol(bean.getLSID());
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
            ExperimentRunListView runView = new ExperimentRunListView(schema, ExperimentRunListView.getRunListQuerySettings(schema, getViewContext(), ExpSchema.TableType.Runs.name(), false), ExperimentRunType.ALL_RUNS_TYPE)
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

    @RequiresPermission(ACL.PERM_READ)
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
    }

    public static class ProtocolApplicationForm extends BeanViewForm<ProtocolApplication>
    {
        public ProtocolApplicationForm()
        {
            super(ProtocolApplication.class, ExperimentServiceImpl.get().getTinfoProtocolApplication());
        }
    }

    public static class ProtocolForm extends BeanViewForm<Protocol>
    {
        public ProtocolForm()
        {
            super(Protocol.class, ExperimentServiceImpl.get().getTinfoProtocol());
        }
    }

    public static class MaterialForm extends BeanViewForm<Material>
    {
        public MaterialForm()
        {
            super(Material.class, ExperimentServiceImpl.get().getTinfoMaterial());
        }
    }

    @RequiresPermission(ACL.PERM_DELETE)
    public class DeleteSelectedExpRunsAction extends AbstractDeleteAction
    {
        public DeleteSelectedExpRunsAction()
        {
            super("Experiment Run");
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

            return new ConfirmDeleteView("ExperimentRun", "showRunGraph", runs, deleteForm);
        }

        protected void deleteObjects(DeleteForm deleteForm) throws SQLException, ExperimentException
        {
            ExperimentService.get().deleteExperimentRunsByRowIds(getContainer(), getUser(), deleteForm.getIds(true));
        }
    }

    private abstract class AbstractDeleteAction extends FormViewAction<DeleteForm>
    {
        private final String _objectType;

        public AbstractDeleteAction(String objectType)
        {
            _objectType = objectType;
        }

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
            return appendRootNavTrail(root).addChild("Confirm " + _objectType + " Deletion");
        }

        protected abstract void deleteObjects(DeleteForm deleteForm) throws SQLException, ExperimentException, ServletException;
    }

    @RequiresPermission(ACL.PERM_DELETE)
    public class DeleteProtocolByRowIdsAction extends AbstractDeleteAction
    {
        public DeleteProtocolByRowIdsAction()
        {
            super("Protocol");
        }

        public ModelAndView getView(DeleteForm deleteForm, boolean reshow, BindException errors) throws Exception
        {
            List<ExpRun> runs = ExperimentService.get().getExpRunsForProtocolIds(false, deleteForm.getIds(false));
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

    @RequiresPermission(ACL.PERM_DELETE)
    public class DeleteMaterialByRowIdAction extends AbstractDeleteAction
    {
        public DeleteMaterialByRowIdAction()
        {
            super("Sample");
        }

        protected void deleteObjects(DeleteForm deleteForm) throws SQLException, ExperimentException, ServletException
        {
            for (ExpRun run : getRuns(deleteForm))
            {
                if (!run.getContainer().hasPermission(getUser(), ACL.PERM_DELETE))
                {
                    HttpView.throwUnauthorized();
                }
            }

            ExperimentService.get().deleteMaterialByRowIds(getContainer(), deleteForm.getIds(true));
        }

        public ModelAndView getView(DeleteForm deleteForm, boolean reshow, BindException errors) throws Exception
        {
            List<ExpMaterial> materials = getMaterials(deleteForm);
            List<ExpRun> runs = getRuns(deleteForm);
            return new ConfirmDeleteView("Sample", "showMaterial", materials, deleteForm, runs);
        }

        private List<ExpRun> getRuns(DeleteForm deleteForm)
                throws SQLException
        {
            ExpRun[] runs = ExperimentService.get().getRunsUsingMaterials(deleteForm.getIds(false));
            return ExperimentService.get().runsDeletedWithInput(runs);
        }

        private List<ExpMaterial> getMaterials(DeleteForm deleteForm)
        {
            List<ExpMaterial> materials = new ArrayList<ExpMaterial>();
            for (int materialId : deleteForm.getIds(false))
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

    @RequiresPermission(ACL.PERM_DELETE)
    public class DeleteSelectedDataAction extends AbstractDeleteAction
    {
        public DeleteSelectedDataAction()
        {
            super("Data");
        }

        protected void deleteObjects(DeleteForm deleteForm) throws ExperimentException, ServletException
        {
            ExperimentService.get().deleteDataByRowIds(getContainer(), deleteForm.getIds(true));
        }

        public ModelAndView getView(DeleteForm deleteForm, boolean reshow, BindException errors) throws Exception
        {
            List<ExpData> datas = new ArrayList<ExpData>();
            for (int dataId : deleteForm.getIds(false))
            {
                ExpData data = ExperimentService.get().getExpData(dataId);
                if (data != null)
                {
                    datas.add(data);
                }
            }
            List<ExpRun> runs = ExperimentService.get().getRunsUsingDatas(datas);

            return new ConfirmDeleteView("Data", "showData", datas, deleteForm, runs);
        }
    }

    @RequiresPermission(ACL.PERM_DELETE)
    public class DeleteSelectedExperimentsAction extends AbstractDeleteAction
    {
        public DeleteSelectedExperimentsAction()
        {
            super("Run Group");
        }

        protected void deleteObjects(DeleteForm deleteForm) throws SQLException, ExperimentException, ServletException
        {
            ExperimentService.get().deleteExperimentByRowIds(getContainer(), deleteForm.getIds(true));
        }

        public ModelAndView getView(DeleteForm deleteForm, boolean reshow, BindException errors) throws Exception
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

            return new ConfirmDeleteView("Run Group", "details", experiments, deleteForm);
        }
    }

    @RequiresPermission(ACL.PERM_DELETE)
    public class DeleteMaterialSourceAction extends AbstractDeleteAction
    {
        public DeleteMaterialSourceAction()
        {
            super("Sample Set");
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
                if (!run.getContainer().hasPermission(getUser(), ACL.PERM_DELETE))
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

            if (!ensureCorrectContainer(sampleSets))
            {
                HttpView.throwRedirect(ExperimentUrlsImpl.get().getShowSampleSetListURL(getContainer(), "To delete a material source, you must be in its folder or project."));
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

    public static class DeleteForm extends ViewFormData implements DataRegionSelection.DataSelectionKeyForm
    {
        private String _returnURL;
        private boolean _forceDelete;
        private String _dataRegionSelectionKey;

        public int[] getIds(boolean clear)
        {
            return PageFlowUtil.toInts(DataRegionSelection.getSelected(getViewContext(), clear));
        }

        public String getReturnURL()
        {
            return _returnURL;
        }

        public void setReturnURL(String returnURL)
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

    @RequiresPermission(ACL.PERM_UPDATE)
    public class ShowUpdateMaterialSourceAction extends SimpleViewAction<MaterialSourceForm>
    {
        private MaterialSource _source;

        public ModelAndView getView(MaterialSourceForm form, BindException errors) throws Exception
        {
            _source = form.getBean();
            if (null == _source.getName())
                _source = ExperimentServiceImpl.get().getMaterialSource(_source.getRowId());

            return new UpdateView(SampleSetWebPart.getMaterialSourceRegion(getViewContext()), form, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendRootNavTrail(root).addChild("Sample Sets", ExperimentUrlsImpl.get().getShowSampleSetListURL(getContainer())).addChild("Sample Set " + _source.getName());
        }
    }

    @RequiresPermission(ACL.PERM_INSERT)
    public class ShowInsertMaterialSourceAction extends SimpleViewAction<MaterialSourceForm>
    {
        public ModelAndView getView(MaterialSourceForm form, BindException errors) throws Exception
        {
            return new InsertView(SampleSetWebPart.getMaterialSourceRegion(getViewContext()), form, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendRootNavTrail(root).addChild("Sample Sets", ExperimentUrlsImpl.get().getShowSampleSetListURL(getContainer())).addChild("Insert Sample Set");
        }
    }

    @RequiresPermission(ACL.PERM_UPDATE)
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
            Table.update(getUser(), ExperimentService.get().getTinfoMaterialSource(), form.getTypedValues(), _source.getRowId(), null);
            return true;
        }

        public ActionURL getSuccessURL(MaterialSourceForm materialSourceForm)
        {
            return ExperimentUrlsImpl.get().getShowSampleSetURL(ExperimentService.get().getSampleSet(_source.getRowId()));
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class EditSampleSetTypeAction extends SimpleViewAction<MaterialSourceForm>
    {
        public ModelAndView getView(MaterialSourceForm form, BindException errors) throws Exception
        {
            ExpSampleSet ss = ExperimentService.get().getSampleSet(form.getBean().getRowId());
            if (ss == null)
            {
                return HttpView.throwNotFound("Could not find sample set with rowId " + form.getBean().getRowId());
            }
            HttpView.throwRedirect(ss.getType().urlEditDefinition(false, false));
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

    @RequiresPermission(ACL.PERM_UPDATE)
    public class ShowUploadMaterialsAction extends SimpleViewAction<UploadMaterialSetForm>
    {
        public ModelAndView getView(UploadMaterialSetForm form, BindException errors) throws Exception
        {
            if (isPost())
            {
                if (StringUtils.isEmpty(form.getName()) || form.getName() == null)
                {
                    errors.reject(ERROR_MSG, "You must supply a name for the sample set");
                }
                else
                {
                    String materialSourceLsid = ExperimentService.get().getSampleSetLsid(form.getName(), getContainer()).toString();
                    MaterialSource sourceExisting = ExperimentServiceImpl.get().getMaterialSource(materialSourceLsid);

                    if (!form.isImportMoreSamples() && null != sourceExisting)
                    {
                        errors.reject(ERROR_MSG, "A sample set with that name already exists.  If you would like to import samples that set, go here:  " +
                                "<a href=" + getViewContext().getActionURL() + "name=" + form.getName() + "&importMoreSamples=true>Import More Samples</a>");
                    }
                    if (form.isImportMoreSamples() && form.getOverwriteChoice() == null)
                    {
                        errors.reject(ERROR_MSG, "Please select how to deal with duplicates.");
                    }
                }

                if (errors.getErrorCount() == 0)
                {
                    try
                    {
                        UploadSamplesHelper helper = new UploadSamplesHelper(form);
                        MaterialSource newSource = helper.uploadMaterials();

                        ExpSampleSet activeSampleSet = ExperimentService.get().lookupActiveSampleSet(getContainer());
                        if (activeSampleSet == null)
                        {
                            ExperimentService.get().setActiveSampleSet(getContainer(), ExperimentService.get().getSampleSet(newSource.getRowId()));
                        }
                        HttpView.throwRedirect(ExperimentUrlsImpl.get().getShowSampleSetURL(ExperimentService.get().getSampleSet(newSource.getRowId())));
                    }
                    catch (ExperimentException e)
                    {
                        errors.reject(ERROR_MSG, e.getMessage());
                    }
                }
            }
            return new JspView<UploadMaterialSetForm>("/org/labkey/experiment/uploadMaterials.jsp", form, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendRootNavTrail(root).addChild("Sample Set", ExperimentUrlsImpl.get().getShowSampleSetListURL(getContainer())).addChild("Import Sample Set");
        }
    }

    @RequiresPermission(ACL.PERM_UPDATE)
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

            if (rowId != null)
            {
                ExpSampleSet sampleSet = ExperimentService.get().getSampleSet(Integer.parseInt(rowId));
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

    @RequiresPermission(ACL.PERM_INSERT)
    public class ShowAddXarFileAction extends SimpleViewAction<AddXarFileForm>
    {
        public ModelAndView getView(AddXarFileForm form, BindException errors) throws Exception
        {
            if (!hasValidPipelineURI(getContainer()))
            {
                return new NoPipelineRootSetView(getContainer(), "upload a XAR");
            }

            return new JspView<AddXarFileForm>("/org/labkey/experiment/addXarFile.jsp", form);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendRootNavTrail(root).addChild("Upload a .xar or .xar.xml file from your browser");
        }
    }

    @RequiresPermission(ACL.PERM_UPDATE)
    public class ShowUpdateAction extends SimpleViewAction<ExperimentForm>
    {
        public ModelAndView getView(ExperimentForm form, BindException errors) throws Exception
        {
            form.refreshFromDb(false);
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

    @RequiresPermission(ACL.PERM_UPDATE)
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

        public ExportBean(LSIDRelativizer selectedRelativizer, XarExportType selectedExportType, String fileName, ExportOptionsForm form, ActionURL postURL)
        {
            _selectedRelativizer = selectedRelativizer;
            _selectedExportType = selectedExportType;
            _fileName = fileName;
            _dataRegionSelectionKey = form.getDataRegionSelectionKey();
            _error = form.getError();
            _expRowId = form.getExpRowId();
            _postURL = postURL;
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
        private String _fileName;
        private Integer _protocolId;

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

        public String getFileName()
        {
            return _fileName;
        }

        public void setFileName(String fileName)
        {
            _fileName = fileName;
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

    @RequiresPermission(ACL.PERM_READ)
    public class ExportRunsOptionsAction extends SimpleViewAction<ExportOptionsForm>
    {
        public ModelAndView getView(ExportOptionsForm form, BindException errors) throws Exception
        {
            Set<String> runIds = DataRegionSelection.getSelected(getViewContext(), false);

            ExpRun run = null;
            if (runIds != null && !runIds.isEmpty())
            {
                run = ExperimentService.get().getExpRun(Integer.parseInt(runIds.iterator().next()));
            }

            String fileName = "exported.xar";
            if (run != null)
            {
                if (run.getName().endsWith("..."))
                {
                    fileName = run.getName().substring(0, run.getName().length() - "...".length());
                }
                else
                {
                    fileName = run.getName();
                }
                fileName = fileName + ".xar";
            }
            fileName = fixupExportName(fileName);

            ActionURL postURL = new ActionURL(ExportRunsAction.class, getContainer());
            return new JspView<ExportBean>("/org/labkey/experiment/XARExportOptions.jsp", new ExportBean(LSIDRelativizer.FOLDER_RELATIVE, XarExportType.BROWSER_DOWNLOAD, fileName, form, postURL));
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("XAR Export Options");
        }
    }

    private ActionURL exportXAR(XarExportSelection selection, String lsidRelativizerName, String exportTypeName, String fileName, ActionURL errorURL)
            throws SQLException, ExperimentException, ServletException, IOException
    {
        if (lsidRelativizerName == null || exportTypeName == null)
        {
            errorURL.addParameter("error", "Must specify an LSID relativizer and an export type");
            return errorURL;
        }

        LSIDRelativizer lsidRelativizer = LSIDRelativizer.valueOf(lsidRelativizerName);
        XarExportType exportType = XarExportType.valueOf(exportTypeName);

        fileName = fixupExportName(fileName);
        String xarXmlFileName = null;
        if (fileName.endsWith(".xar") || fileName.endsWith(".XAR") || fileName.endsWith("Xar"))
            xarXmlFileName = fileName + ".xml";

        switch (exportType)
        {
            case BROWSER_DOWNLOAD:
                XarExporter exporter = new XarExporter(lsidRelativizer, DataURLRelativizer.ARCHIVE, selection, xarXmlFileName, null);

                getViewContext().getResponse().setContentType("application/zip");
                getViewContext().getResponse().setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");

                try
                {
                    exporter.write(getViewContext().getResponse().getOutputStream());
                    return null;
                }
                catch (Exception e)
                {
                    errorURL.addParameter("error", e.getMessage());
                    return errorURL;
                }
            case PIPELINE_FILE:
                PipeRoot pipeRoot = PipelineService.get().findPipelineRoot(getContainer());
                File pipeRootDir = pipeRoot == null ? null : pipeRoot.getRootPath();
                if (pipeRootDir == null || !pipeRootDir.exists())
                {
                    errorURL.addParameter("error", "You must set a valid pipeline root before you can export a XAR to it.");
                    HttpView.throwRedirect(errorURL);
                }
                XarExportPipelineJob job = new XarExportPipelineJob(getViewBackgroundInfo(), pipeRootDir, fileName, lsidRelativizer, selection, xarXmlFileName);
                PipelineService.get().queueJob(job);
                return PageFlowUtil.urlProvider(PipelineUrls.class).urlReferer(getContainer());
            default:
                throw new IllegalArgumentException("Unknown export type: " + exportType);
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class ExportProtocolsOptionsAction extends SimpleViewAction<ExportOptionsForm>
    {
        public ModelAndView getView(ExportOptionsForm form, BindException errors) throws Exception
        {
            List<ExpProtocol> protocols = form.lookupProtocols(getViewContext(), false);

            String fileName;
            if (protocols.size() == 1)
            {
                fileName = fixupExportName(protocols.get(0).getName() + ".xar");
            }
            else
            {
                fileName = protocols.size() + "AssayDefinitions.xar";
            }

            ActionURL postURL = new ActionURL(ExportProtocolsAction.class, getContainer());
            ExportBean bean = new ExportBean(LSIDRelativizer.FOLDER_RELATIVE, XarExportType.BROWSER_DOWNLOAD, fileName, form, postURL);
            return new JspView<ExportBean>("/org/labkey/experiment/XARExportOptions.jsp", bean);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("XAR Export Options");
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class ExportProtocolsAction extends AbstractExportAction
    {
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

            ActionURL errorURL = new ActionURL(ExportProtocolsOptionsAction.class, getContainer());
            errorURL.addParameter(DataRegionSelection.DATA_REGION_SELECTION_KEY, form.getDataRegionSelectionKey());
            if (form.getProtocolId() != null)
            {
                errorURL.addParameter("protocolId", form.getProtocolId().intValue());
            }
            _resultURL = exportXAR(selection, form.getLsidOutputType(), form.getExportType(), form.getFileName(), errorURL);
            if (_resultURL != errorURL && form.getDataRegionSelectionKey() != null)
            {
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

    @RequiresPermission(ACL.PERM_READ)
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
                    if (run == null || !run.getContainer().equals(getContainer()))
                    {
                        HttpView.throwNotFound("Could not find run " + id);
                    }
                }

                XarExportSelection selection = new XarExportSelection();
                if (form.getExpRowId() != null)
                {
                    ExpExperiment experiment = ExperimentService.get().getExpExperiment(form.getExpRowId().intValue());
                    if (experiment != null && !experiment.getContainer().equals(getContainer()))
                    {
                        HttpView.throwNotFound("Experiment " + form.getExpRowId());
                    }
                    selection.addExperimentIds(experiment.getRowId());
                }
                selection.addRunIds(ids);

                ActionURL errorURL = new ActionURL(ExportRunsOptionsAction.class, getContainer());
                if (form.getExpRowId() != null)
                {
                    errorURL.addParameter("expRowId", form.getExpRowId().intValue());
                }
                errorURL.addParameter(DataRegionSelection.DATA_REGION_SELECTION_KEY, form.getDataRegionSelectionKey());
                _resultURL = exportXAR(selection, form.getLsidOutputType(), form.getExportType(), form.getFileName(), errorURL);
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


    @RequiresPermission(ACL.PERM_INSERT)
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

    @RequiresPermission(ACL.PERM_DELETE)
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
                    HttpView.throwNotFound("Run " + runId);
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

    @RequiresPermission(ACL.PERM_READ)
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

            String html = message + "<form action=\"" + getViewContext().getActionURL().relativeUrl("resolveLSID", null) + "\">" +
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
        private String _flagSessionId;
        private boolean _redirect = true;

        public String getComment()
        {
            return _comment;
        }

        public void setComment(String comment)
        {
            _comment = comment;
        }

        public String getFlagSessionId()
        {
            return _flagSessionId;
        }

        public void setFlagSessionId(String flagSessionId)
        {
            _flagSessionId = flagSessionId;
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

    @RequiresPermission(ACL.PERM_NONE)
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
            if (!container.hasPermission(getUser(), ACL.PERM_UPDATE))
                HttpView.throwUnauthorized();

            String sessionId = form.getFlagSessionId();
            if (sessionId == null)
                throw new IllegalArgumentException("No session id");
            if (!sessionId.equals(getViewContext().getSession().getId()))
                throw new IllegalArgumentException("Wrong session id");
            if (!container.hasPermission(getUser(), ACL.PERM_UPDATE))
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

    @RequiresPermission(ACL.PERM_INSERT)
    public class DeriveSamplesChooseTargetAction extends SimpleViewAction<DeriveMaterialForm>
    {
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Derive Samples");
        }

        public ModelAndView getView(DeriveMaterialForm form, BindException errors) throws Exception
        {
            List<ExpMaterial> materials = form.lookupMaterials();
            if (materials.isEmpty())
            {
                return HttpView.throwNotFound("Could not find any matching materials");
            }
            if (!materials.get(0).getContainer().equals(getContainer()))
            {
                ActionURL redirectURL = getViewContext().cloneActionURL().setContainer(materials.get(0).getContainer());
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
                materialInputRoles.addAll(ExperimentService.get().getMaterialInputRoles(getContainer(), null));
                Map<ExpMaterial, String> materialsWithRoles = new LinkedHashMap<ExpMaterial, String>();
                for (ExpMaterial material : materials)
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
    
    @RequiresPermission(ACL.PERM_INSERT)
    public class DescribeDerivedSamplesAction extends SimpleViewAction<DeriveMaterialForm>
    {
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Derive Samples");
        }

        public ModelAndView getView(DeriveMaterialForm form, BindException errors) throws Exception
        {
            Container c = getContainer();

            ExpSampleSet sampleSet = ExperimentService.get().getSampleSet(form.getTargetSampleSetId());

            InsertView insertView = new InsertView(new DataRegion(), errors);

            DerivedSamplePropertyHelper helper = new DerivedSamplePropertyHelper(sampleSet, form.getOutputCount(), c, getUser());
            helper.addSampleColumns(insertView.getDataRegion(), getViewContext().getUser());

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

    @RequiresPermission(ACL.PERM_INSERT)
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
                outputMaterial.insert(getUser());

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

        public List<ExpMaterial> lookupMaterials() throws ServletException
        {
            List<ExpMaterial> result = new ArrayList<ExpMaterial>();
            for (int rowId : getRowIds())
            {
                ExpMaterial material = ExperimentService.get().getExpMaterial(rowId);
                if (material != null)
                {
                    if (material.getContainer().hasPermission(_context.getUser(), ACL.PERM_READ))
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

    @RequiresPermission(ACL.PERM_INSERT) @ActionNames("createRunGroup, createExperiment")
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
            drg.addHiddenFormField("addSelectedRuns", Boolean.toString("true".equals(getViewContext().getRequest().getParameter("addSelectedRuns"))));
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

    @RequiresPermission(ACL.PERM_DELETE)
    public class MoveRunsLocationAction extends SimpleViewAction<MoveRunsForm>
    {
        public ModelAndView getView(MoveRunsForm form, BindException errors) throws Exception
        {
            ActionURL moveURL = new ActionURL(MoveRunsAction.class, getContainer());
            ContainerTree ct = new ContainerTree("/", getUser(), ACL.PERM_INSERT, moveURL)
            {
                protected void renderCellContents(StringBuilder html, Container c, ActionURL url)
                {
                    boolean hasRoot = PipelineService.get().findPipelineRoot(c) != null;
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



    @RequiresPermission(ACL.PERM_DELETE)
    public class MoveRunsAction extends FormHandlerAction<MoveRunsForm>
    {
        private Container _targetContainer;
        public void validateCommand(MoveRunsForm target, Errors errors)
        {
        }

        public boolean handlePost(MoveRunsForm form, BindException errors) throws Exception
        {
            getViewContext().requiresPermission(ACL.PERM_DELETE);
            _targetContainer = ContainerManager.getForId(form.getTargetContainerId());
            if (_targetContainer == null || !_targetContainer.hasPermission(getUser(), ACL.PERM_INSERT))
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

    @RequiresPermission(ACL.PERM_READ)
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

    @RequiresPermission(ACL.PERM_READ)
    public class ShowGraphMoreListAction extends SimpleViewAction<ExperimentRunForm>
    {
        private ExperimentRunForm _form;
        public ModelAndView getView(ExperimentRunForm form, BindException errors) throws Exception
        {
            _form = form;
            return new GraphMoreGrid(getContainer(), getViewContext().getActionURL());
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

    public static class AddXarFileForm
    {
        protected String _path;
        private String _error;

        public String getPath()
        {
            return _path;
        }

        public void setPath(String path)
        {
            _path = path;
        }

        public String getError()
        {
            return _error;
        }

        public void setError(String error)
        {
            _error = error;
        }
    }

    @RequiresPermission(ACL.PERM_INSERT)
    public class UploadXarFileAction extends SimpleViewAction<Object>
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            if (!(getViewContext().getRequest() instanceof MultipartHttpServletRequest))
                throw new IllegalStateException("Expected MultipartHttpServletRequest when posting files.");

            if (!hasValidPipelineURI(getContainer()))
            {
                return new NoPipelineRootSetView(getContainer(), "upload a XAR");
            }

            MultipartFile formFile = getFileMap().get("uploadFile");
            if (formFile == null)
            {
                HttpView.throwRedirect(ExperimentUrlsImpl.get().getShowAddXarFileURL(getContainer(), "No file was posted by the browser."));
                return null;
            }

            byte[] bytes = formFile.getBytes();
            if (bytes.length == 0)
            {
                HttpView.throwRedirect(ExperimentUrlsImpl.get().getShowAddXarFileURL(getContainer(), "No file was posted by the browser."));
            }

            File systemDir = PipelineService.get().findPipelineRoot(getContainer()).ensureSystemDirectory();
            File uploadDir = new File(systemDir, "UploadedXARs");
            uploadDir.mkdirs();
            if (!uploadDir.isDirectory())
            {
                HttpView.throwRedirect(ExperimentUrlsImpl.get().getShowAddXarFileURL(getContainer(), "Unable to create a 'system/UploadedXARs' directory under the pipeline root"));
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
                HttpView.throwRedirect(ExperimentUrlsImpl.get().getShowAddXarFileURL(getContainer(), "Unable to create an 'UploadedXARs/" + userDirName + "' directory under the pipeline root"));
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
                HttpView.throwRedirect(ExperimentUrlsImpl.get().getShowAddXarFileURL(getContainer(), "Unable to write uploaded XAR file to " + xarFile.getPath()));
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

            // Forward to the job's container.
            HttpView.throwRedirect(PageFlowUtil.urlProvider(PipelineUrls.class).urlBegin(getContainer()));
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }

    public static File getPipelineRoot(Container c) throws SQLException
    {
        PipeRoot pr = PipelineService.get().findPipelineRoot(c);
        if (pr == null)
            return null;

        URI uri = pr.getUri(c);
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

    @RequiresPermission(ACL.PERM_INSERT)
    public class ImportXarFileAction extends SimpleViewAction<AddXarFileForm>
    {
        public ModelAndView getView(AddXarFileForm form, BindException errors) throws Exception
        {
            Container c = getContainer();
            ActionURL url;
            File f = null;

            File rootFile = getPipelineRoot(c);
            if (rootFile != null)
            {
                URI uriData = URIUtil.resolve(rootFile.toURI(), form.getPath());
                if (uriData != null)
                    f = new File(uriData);
            }

            if (null != f && f.exists() && f.isFile())
            {
                PipelineService service = PipelineService.get();

                ExperimentPipelineJob job = new ExperimentPipelineJob(getViewBackgroundInfo(), f, "Experiment Import", false);
                service.queueJob(job);

                url = getContainer().getStartURL(getViewContext());
            }
            else
            {
                url = new ActionURL(ShowAddXarFileAction.class, getContainer());
                url.addParameter("error", "File not found.");
            }
            return HttpView.redirect(url);
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
            return new ActionURL(GridViewAction.class, c);
        }

        public ActionURL getExperimentDetailsURL(Container c, ExpExperiment expExperiment)
        {
            return new ActionURL(DetailsAction.class, c).addParameter("rowId", expExperiment.getRowId());
        }

        public ActionURL getShowSampleURL(Container c, ExpMaterial material)
        {
            return new ActionURL(ShowMaterialAction.class, c).addParameter("rowId", material.getRowId());
        }

        public ActionURL getExportRunsOptionsURL(Container container, ExpExperiment experiment)
        {
            ActionURL result = new ActionURL(ExportRunsOptionsAction.class, container);
            if (experiment != null)
            {
                result.addParameter("expRowId", experiment.getRowId());
            }
            return result;
        }

        public ActionURL getExportProtocolOptionsURL(Container container, ExpProtocol protocol)
        {
            return new ActionURL(ExperimentController.ExportProtocolsOptionsAction.class, container).addParameter("protocolId", protocol.getRowId());
        }

        public ActionURL getMoveRunsLocationURL(Container container)
        {
            return new ActionURL(ExperimentController.MoveRunsLocationAction.class, container);
        }

        public ActionURL getProtocolDetailsURL(ExpProtocol protocol)
        {
            return new ActionURL(ProtocolDetailsAction.class, protocol.getContainer()).addParameter("rowId", protocol.getRowId());
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

        public ActionURL getShowAddXarFileURL(Container c, String error)
        {
            ActionURL result = new ActionURL(ShowAddXarFileAction.class, c);
            if (error != null)
            {
                result.addParameter("error", error);
            }
            return result;
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

        public ActionURL getDeleteDatasURL(Container c, ActionURL returnURL)
        {
            return new ActionURL(DeleteSelectedDataAction.class, c).addParameter("returnURL", returnURL.toString());
        }

        public ActionURL getDeleteSelectedExperimentsURL(Container c, ActionURL returnURL)
        {
            return new ActionURL(DeleteSelectedExperimentsAction.class, c).addParameter("returnURL", returnURL.toString());
        }

        public ActionURL getDeleteSelectedExpRunsURL(Container container, ActionURL returnURL)
        {
            return new ActionURL(DeleteSelectedExpRunsAction.class, container).addParameter("returnURL", returnURL.toString());
        }

        public ActionURL getShowUpdateURL(ExpExperiment experiment)
        {
            return new ActionURL(ShowUpdateAction.class, experiment.getContainer()).addParameter("rowId", experiment.getRowId());
        }

        public ActionURL getRemoveSelectedExpRunsURL(Container container, ActionURL returnURL, ExpExperiment exp)
        {
            return new ActionURL(RemoveSelectedExpRunsAction.class, container).addParameter("returnURL", returnURL.toString()).addParameter("expRowId", exp.getRowId());
        }

        public ActionURL getCreateRunGroupURL(Container container, ActionURL returnURL, boolean addSelectedRuns)
        {
            ActionURL result = new ActionURL(CreateRunGroupAction.class, container);
            if (returnURL != null)
            {
                result.addParameter("returnURL", returnURL.toString());
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
            ActionURL result = new ActionURL(ShowFileAction.class, c).addParameter("rowId", data.getRowId());
            if (inline)
            {
                result.addParameter("inline", inline);
            }
            return result;
        }

        public ActionURL getSetFlagURL(HttpServletRequest request)
        {
            ActionURL url = new ActionURL(SetFlagAction.class, ContainerManager.getRoot());
            url.addParameter("flagSessionId", request.getSession().getId());
            return url;
        }
    }
}
