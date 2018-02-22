/*
 * Copyright (c) 2007-2017 LabKey Corporation
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

import au.com.bytecode.opencsv.CSVWriter;
import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.collections4.iterators.ArrayIterator;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Workbook;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.labkey.api.action.*;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.BaseDownloadAction;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.*;
import org.labkey.api.exp.AbstractParameter;
import org.labkey.api.exp.DuplicateMaterialException;
import org.labkey.api.exp.ExperimentDataHandler;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.ExperimentRunListView;
import org.labkey.api.exp.ExperimentRunType;
import org.labkey.api.exp.Identifiable;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.LsidManager;
import org.labkey.api.exp.LsidType;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.ProtocolApplicationParameter;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpLineage;
import org.labkey.api.exp.api.ExpLineageOptions;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpProtocolApplication;
import org.labkey.api.exp.api.ExpProtocolOutput;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ExperimentJSONConverter;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.exp.form.DeleteForm;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.DomainTemplate;
import org.labkey.api.exp.property.DomainTemplateGroup;
import org.labkey.api.exp.property.DomainUtil;
import org.labkey.api.exp.query.ExpInputTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.exp.xar.LsidUtils;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineRootContainerTree;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineUrls;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryUpdateForm;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.reader.DataLoaderFactory;
import org.labkey.api.reader.ExcelFactory;
import org.labkey.api.reader.MapLoader;
import org.labkey.api.security.ActionNames;
import org.labkey.api.security.CSRF;
import org.labkey.api.security.RequiresLogin;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.ConceptURIProperties;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.StudyUrls;
import org.labkey.api.study.actions.UploadWizardAction;
import org.labkey.api.study.assay.AssayFileWriter;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.util.CSRFUtil;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.ImageUtil;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.TidyUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.BadRequestException;
import org.labkey.api.view.DataView;
import org.labkey.api.view.DetailsView;
import org.labkey.api.view.HBox;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.InsertView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.UpdateView;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ViewServlet;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.template.PageConfig;
import org.labkey.experiment.*;
import org.labkey.experiment.api.DataClass;
import org.labkey.experiment.api.ExpDataClassAttachmentParent;
import org.labkey.experiment.api.ExpDataClassImpl;
import org.labkey.experiment.api.ExpDataImpl;
import org.labkey.experiment.api.ExpExperimentImpl;
import org.labkey.experiment.api.ExpMaterialImpl;
import org.labkey.experiment.api.ExpProtocolApplicationImpl;
import org.labkey.experiment.api.ExpRunImpl;
import org.labkey.experiment.api.ExpSampleSetImpl;
import org.labkey.experiment.api.Experiment;
import org.labkey.experiment.api.ExperimentServiceImpl;
import org.labkey.experiment.api.MaterialSource;
import org.labkey.experiment.api.ProtocolActionStepDetail;
import org.labkey.experiment.api.SampleSetDomainKind;
import org.labkey.experiment.api.SampleSetUpdateService;
import org.labkey.experiment.controllers.property.PropertyController;
import org.labkey.experiment.pipeline.ExperimentPipelineJob;
import org.labkey.experiment.samples.UploadMaterialSetForm;
import org.labkey.experiment.samples.UploadSamplesHelper;
import org.labkey.experiment.types.TypesController;
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
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

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
            throw new RedirectException(url);
        }
    }

    private NavTree appendRootNavTrail(NavTree root)
    {
        // Intentionally don't add an "Experiment" node to the list because it's too overloaded. All content on the
        // default action can be added to a portal page if desired.
        return root;
    }

    @Override
    public PageConfig defaultPageConfig()
    {
        // set default help topic for controller
        PageConfig config = super.defaultPageConfig();
        config.setHelpTopic(new HelpTopic("experiment"));
        return config;
    }

    @ActionNames("begin,gridView")
    @RequiresPermission(ReadPermission.class)
    public class BeginAction extends ShowRunsAction
    {
        public VBox getView(Object o, BindException errors) throws Exception
        {
            VBox result = new VBox(super.getView(o, errors));
            RunGroupWebPart runGroups = new RunGroupWebPart(getViewContext(), false);
            runGroups.showHeader();
            result.addView(runGroups);

            result.addView(new ProtocolWebPart(false, getViewContext()));
            result.addView(new SampleSetWebPart(false, getViewContext()));
            result.addView(new DataClassWebPart(false, getViewContext(), null));

            return result;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendRootNavTrail(root).addChild("Experiment");
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class ShowRunsAction extends SimpleViewAction
    {
        public VBox getView(Object o, BindException errors) throws Exception
        {
            Set<ExperimentRunType> types = ExperimentService.get().getExperimentRunTypes(getContainer());
            ChooseExperimentTypeBean bean = new ChooseExperimentTypeBean(types, ExperimentRunType.getSelectedFilter(types, getViewContext().getRequest().getParameter("experimentRunFilter")), getViewContext().getActionURL().clone(), Collections.emptyList());
            JspView chooserView = new JspView<>("/org/labkey/experiment/experimentRunQueryHeader.jsp", bean);

            ExperimentRunListView view = ExperimentService.get().createExperimentRunWebPart(getViewContext(), bean.getSelectedFilter());
            VBox result = new VBox(chooserView, view);
            result.setTitle(view.getTitle());
            result.setFrame(WebPartView.FrameType.PORTAL);
            view.setFrame(WebPartView.FrameType.NONE);
            return result;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendRootNavTrail(root).addChild("Experiment Runs");
        }
    }

    @RequiresPermission(ReadPermission.class) @ActionNames("showRunGroups, showExperiments")
    public class ShowRunGroupsAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            RunGroupWebPart webPart = new RunGroupWebPart(getViewContext(), false);
            webPart.setFrame(WebPartView.FrameType.NONE);
            return webPart;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic("runGroups");
            return appendRootNavTrail(root).addChild("Run Groups");
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class CreateHiddenRunGroupAction extends ApiAction<SimpleApiJsonForm>
    {
        public ApiResponse execute(SimpleApiJsonForm form, BindException errors) throws Exception
        {
            List<ExpRun> runs = new ArrayList<>();
            // Accept either an explicit list of run IDs
            if (form.getJsonObject().has("runIds"))
            {
                JSONArray runIds = form.getJsonObject().getJSONArray("runIds");
                for (int i = 0; i < runIds.length(); i++)
                {
                    ExpRunImpl run = ExperimentServiceImpl.get().getExpRun(runIds.getInt(i));
                    if (run != null)
                    {
                        runs.add(run);
                    }
                }
            }
            // Or a reference to a DataRegion selection key
            else if (form.getJsonObject().has("selectionKey"))
            {
                Set<String> ids = DataRegionSelection.getSelected(getViewContext(), form.getJsonObject().getString("selectionKey"), true, true);
                for (String id : ids)
                {
                    try
                    {
                        ExpRunImpl run = ExperimentServiceImpl.get().getExpRun(Integer.parseInt(id));
                        if (run != null)
                        {
                            runs.add(run);
                        }
                    }
                    catch (NumberFormatException ignored) {}
                }
            }
            if (runs.isEmpty())
            {
                throw new NotFoundException();
            }
            ExpExperiment group = ExperimentService.get().createHiddenRunGroup(getContainer(), getUser(), runs.toArray(new ExpRun[runs.size()]));
            ApiSimpleResponse response = new ApiSimpleResponse();
            response.putBean(group, "rowId", "LSID", "name", "hidden");
            return response;
        }
    }


    @RequiresPermission(ReadPermission.class)
    public class DetailsAction extends QueryViewAction<ExpObjectForm, ExperimentRunListView>
    {
        private ExpExperimentImpl _experiment;

        public DetailsAction()
        {
            super(ExpObjectForm.class);
        }

        private Pair<ExperimentRunListView, JspView> createViews(ExpObjectForm form, BindException errors)
        {
            _experiment = ExperimentServiceImpl.get().getExpExperiment(form.getRowId());
            if (_experiment == null)
            {
                throw new NotFoundException("Could not find an experiment with RowId " + form.getRowId());
            }

            if (!_experiment.getContainer().equals(getContainer()))
            {
                throw new RedirectException(getViewContext().cloneActionURL().setContainer(_experiment.getContainer()));
            }

            List<? extends ExpProtocol> protocols = _experiment.getAllProtocols();

            Set<ExperimentRunType> types = new TreeSet<>(ExperimentService.get().getExperimentRunTypes(getContainer()));
            ExperimentRunType selectedType = ExperimentRunType.getSelectedFilter(types, getViewContext().getRequest().getParameter("experimentRunFilter"));

            ChooseExperimentTypeBean bean = new ChooseExperimentTypeBean(types, selectedType, getViewContext().getActionURL().clone(), protocols);
            JspView chooserView = new JspView<>("/org/labkey/experiment/experimentRunQueryHeader.jsp", bean, errors);

            ExperimentRunListView runListView = ExperimentRunListView.createView(getViewContext(), bean.getSelectedFilter(), true);
            runListView.getRunTable().setExperiment(_experiment);
            runListView.setShowRemoveFromExperimentButton(true);
            runListView.setShowDeleteButton(true);
            runListView.setShowAddToRunGroupButton(true);
            runListView.setShowExportButtons(true);
            runListView.setShowMoveRunsButton(true);
            return new Pair<>(runListView, chooserView);
        }

        @Override
        protected ModelAndView getHtmlView(ExpObjectForm form, BindException errors) throws Exception
        {
            Pair<ExperimentRunListView, JspView> views = createViews(form, errors);

            CustomPropertiesView customPropertiesView = new CustomPropertiesView(_experiment.getLSID(), getContainer());

            TableInfo runGroupsTable = new ExpSchema(getUser(), getContainer()).getTable(ExpSchema.TableType.RunGroups);

            DetailsView detailsView = new DetailsView(new DataRegion(), _experiment.getRowId());
            detailsView.getDataRegion().setTable(runGroupsTable);
            detailsView.getDataRegion().addColumns(runGroupsTable, "RowId,Name,Created,Modified,Contact,ExperimentDescriptionURL,Hypothesis,Comments");
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
                detailsView.getDataRegion().addColumns(runGroupsTable, "BatchProtocolId");
            }
            else
            {
                detailsView.setTitle("Run Group Details");
            }

            VBox runsVBox = new VBox(views.second, createInitializedQueryView(form, errors, false, null));
            runsVBox.setTitle("Experiment Runs");
            runsVBox.setFrame(WebPartView.FrameType.PORTAL);

            return new VBox(new StandardAndCustomPropertiesView(detailsView, customPropertiesView), runsVBox);
        }

        @Override
        protected ExperimentRunListView createQueryView(ExpObjectForm form, BindException errors, boolean forExport, String dataRegion) throws Exception
        {
            return createViews(form, errors).first;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic("runGroups");
            return appendRootNavTrail(root).addChild("Run Groups", ExperimentUrlsImpl.get().getShowExperimentsURL(getContainer())).addChild(_experiment.getName());
        }
    }

    @RequiresPermission(ReadPermission.class)
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
            setHelpTopic("sampleSets");
            return appendRootNavTrail(root).addChild("Sample Sets");
        }
    }

    @RequiresPermission(ReadPermission.class)
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
                    throw new RedirectException(new ActionURL(ShowAllMaterialsAction.class, getContainer()));
                }
                // Check if the URL specifies the LSID, and stick the bean back into the form
                _source = ExperimentServiceImpl.get().getSampleSet(form.getLsid());
            }

            if (_source == null)
            {
                throw new NotFoundException("No matching sample set found");
            }

            List<ExpSampleSetImpl> allScopedSampleSets = ExperimentServiceImpl.get().getSampleSets(getContainer(), getUser(), true);
            if (!allScopedSampleSets.contains(_source))
            {
                ensureCorrectContainer(getContainer(), _source, getViewContext());
            }

            SamplesSchema schema = new SamplesSchema(getUser(), getContainer());
            QuerySettings settings = schema.getSettings(getViewContext(), "Material", _source.getName());
            QueryView queryView = new QueryView(schema, settings, errors)
            {
                @Override
                protected boolean canInsert()
                {
                    return _source.canImportMoreSamples() && super.canInsert();
                }

                @Override
                protected boolean canUpdate()
                {
                    return _source.canImportMoreSamples() && super.canUpdate();
                }

                @Override
                public ActionButton createDeleteButton()
                {
                    // Use default delete button, but without showing the confirmation text
                    ActionButton button = super.createDeleteButton();
                    if (button != null)
                    {
                        button.setRequiresSelection(true);
                    }
                    return button;
                }

                @Override
                @NotNull
                public PanelButton createExportButton(@Nullable List<String> recordSelectorColumns)
                {
                    PanelButton result = super.createExportButton(recordSelectorColumns);
                    ActionURL url = new ActionURL(ExportSampleSetAction.class, getContainer());
                    url.addParameter("sampleSetId", _source.getRowId());
                    result.addSubPanel("XAR", new JspView<>("/org/labkey/experiment/controllers/exp/exportSampleSetAsXar.jsp", url));
                    return result;
                }

                @Override
                protected void populateButtonBar(DataView view, ButtonBar bar)
                {
                    super.populateButtonBar(view, bar);

                    bar.add(getDeriveSamplesButton());
                }

            };
            queryView.setTitle("Sample Set Contents");

            DetailsView detailsView = new DetailsView(getMaterialSourceRegion(getViewContext(), true), _source.getRowId());
            detailsView.getDataRegion().getDisplayColumn("Name").setURL(null);
            detailsView.getDataRegion().getDisplayColumn("LSID").setVisible(false);
            detailsView.getDataRegion().getDisplayColumn("MaterialLSIDPrefix").setVisible(false);
            detailsView.setTitle("Sample Set Properties");
            detailsView.getDataRegion().getButtonBar(DataRegion.MODE_DETAILS).setStyle(ButtonBar.Style.separateButtons);

            if (_source.hasIdColumns())
            {
                SimpleDisplayColumn idCols = new SimpleDisplayColumn();
                idCols.setCaption("Id Column(s)");
                String names = _source.getIdCols().stream()
                        .filter(Objects::nonNull)
                        .map(DomainProperty::getName)
                        .collect(Collectors.joining(", "));
                if (!names.isEmpty())
                {
                    idCols.setDisplayHtml(PageFlowUtil.filter(names));
                    detailsView.getDataRegion().addDisplayColumn(idCols);
                }
            }

            if (_source.getParentCol() != null)
            {
                SimpleDisplayColumn parentCol = new SimpleDisplayColumn(PageFlowUtil.filter(_source.getParentCol().getName()));
                parentCol.setCaption("Parent Column");
                detailsView.getDataRegion().addDisplayColumn(parentCol);
            }

            if (!getContainer().equals(_source.getContainer()))
            {
                ActionURL definitionURL = PageFlowUtil.urlProvider(ExperimentUrls.class).getShowSampleSetURL(_source);
                SimpleDisplayColumn definedInCol = new SimpleDisplayColumn("<a href=\"" +
                        PageFlowUtil.filter(definitionURL) +
                        "\">" +
                        PageFlowUtil.filter(_source.getContainer().getPath()) +
                        "</a>");
                definedInCol.setCaption("Defined In");
                detailsView.getDataRegion().addDisplayColumn(definedInCol);
            }

            // Not all sample sets can be edited
            DomainKind domainKind = _source.getType().getDomainKind();
            if (!ExperimentService.get().ensureDefaultSampleSet().equals(_source) && domainKind != null && domainKind.canEditDefinition(getUser(), _source.getType()))
            {
                ActionURL editURL = domainKind.urlEditDefinition(_source.getType(), new ViewBackgroundInfo(_source.getContainer(), getUser(), getViewContext().getActionURL()));
                if (editURL != null)
                {
                    editURL.addParameter(ActionURL.Param.returnUrl, getViewContext().getActionURL().toString());
                    ActionButton editTypeButton = new ActionButton(editURL, "Edit Fields", DataRegion.MODE_DETAILS);
                    editTypeButton.setDisplayPermission(UpdatePermission.class);
                    detailsView.getDataRegion().getButtonBar(DataRegion.MODE_DETAILS).add(editTypeButton);
                }

                if (domainKind instanceof SampleSetDomainKind)
                {
                    ActionURL updateURL = new ActionURL(ShowUpdateMaterialSourceAction.class, _source.getContainer());
                    updateURL.addParameter("RowId", _source.getRowId());
                    updateURL.addParameter(ActionURL.Param.returnUrl, getViewContext().getActionURL().toString());
                    ActionButton updateButton = new ActionButton(updateURL, "Edit Set", DataRegion.MODE_DETAILS, ActionButton.Action.LINK);
                    updateButton.setDisplayPermission(UpdatePermission.class);
                    detailsView.getDataRegion().getButtonBar(DataRegion.MODE_DETAILS).add(updateButton);

                    ActionButton deleteButton = new ActionButton(ExperimentController.DeleteMaterialSourceAction.class, "Delete Set", DataRegion.MODE_DETAILS, ActionButton.Action.POST);
                    deleteButton.setDisplayPermission(DeletePermission.class);
                    ActionURL deleteURL = new ActionURL(ExperimentController.DeleteMaterialSourceAction.class, _source.getContainer());
                    deleteURL.addParameter("singleObjectRowId", _source.getRowId());
                    deleteURL.addParameter(ActionURL.Param.returnUrl, ExperimentUrlsImpl.get().getShowSampleSetListURL(getContainer()).toString());

                    deleteButton.setURL(deleteURL);
                    deleteButton.setActionType(ActionButton.Action.LINK);
                    detailsView.getDataRegion().getButtonBar(DataRegion.MODE_DETAILS).add(deleteButton);
                }
            }

            if (_source.canImportMoreSamples())
            {
                TableInfo table = queryView.getTable();
                if (table != null)
                {
                    ActionURL importURL = table.getImportDataURL(getContainer());
                    if (importURL != null)
                    {
                        importURL = importURL.clone();
                        importURL.replaceParameter(ActionURL.Param.returnUrl, getViewContext().getActionURL().toString());
                        ActionButton uploadButton = new ActionButton(importURL, "Import More Samples", DataRegion.MODE_ALL, ActionButton.Action.LINK);
                        uploadButton.setDisplayPermission(UpdatePermission.class);
                        detailsView.getDataRegion().getButtonBar(DataRegion.MODE_DETAILS).add(uploadButton);

                    }
                }
            }

            return new VBox(detailsView, queryView);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic("sampleSets");
            ActionURL url = new ActionURL(ListMaterialSourcesAction.class, getContainer());
            return appendRootNavTrail(root).addChild("Sample Sets", url).addChild("Sample Set " + _source.getName());
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class ShowAllMaterialsAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            ExpSchema schema = new ExpSchema(getUser(), getContainer());
            QuerySettings settings = schema.getSettings(getViewContext(), "Materials", ExpSchema.TableType.Materials.toString());
            QueryView view = new QueryView(schema, settings, errors)
            {
                protected void populateButtonBar(DataView view, ButtonBar bar)
                {
                    super.populateButtonBar(view, bar);
                    bar.add(getDeriveSamplesButton());
                }
            };
            view.setShowDetailsColumn(false);
            return view;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic("sampleSets");
            return appendRootNavTrail(root).addChild("All Materials");
        }
    }

    @RequiresPermission(InsertPermission.class)
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
            uploadForm.setCreateNewColumnsOnExistingSampleSet(false);
            uploadForm.setLoader(new MapLoader(form.getMaterials()));

            UploadSamplesHelper helper = new UploadSamplesHelper(uploadForm);
            helper.uploadMaterials();

            return new ApiSimpleResponse();
        }
    }

    public static final class SaveMaterialsForm extends SimpleApiJsonForm
    {
        public String getName()
        {
            return json.getString("name");
        }

        public List<Map<String, Object>> getMaterials()
        {
            JSONArray materials = json.getJSONArray("materials");
            List<Map<String, Object>> result = new ArrayList<>();
            for (int i=0; i<materials.length(); i++)
            {
                Map<String, Object> props = materials.getJSONObject(i).getJSONObject("properties");
                result.add(props);
            }
            return result;
        }
    }


    /** Only shows standard and custom properties, not parent and child samples. Used for indexing */
    @RequiresPermission(ReadPermission.class)
    public class ShowMaterialSimpleAction extends SimpleViewAction<ExpObjectForm>
    {
        protected ExpMaterialImpl _material;

        public VBox getView(ExpObjectForm form, BindException errors) throws Exception
        {
            Container c = getContainer();
            _material = ExperimentServiceImpl.get().getExpMaterial(form.getRowId());
            if (_material == null && form.getLsid() != null)
            {
                _material = ExperimentServiceImpl.get().getExpMaterial(form.getLsid());
            }
            if (_material == null)
            {
                throw new NotFoundException("Could not find a material with RowId " + form.getRowId());
            }

            ensureCorrectContainer(getContainer(), _material, getViewContext());

            ExpRunImpl run = _material.getRun();
            ExpProtocol sourceProtocol = _material.getSourceProtocol();
            ExpProtocolApplication sourceProtocolApplication = _material.getSourceApplication();

            DataRegion dr = new DataRegion();
            dr.addColumns(ExperimentServiceImpl.get().getTinfoMaterial().getUserEditableColumns());
            dr.removeColumns("RowId", "RunId", "LastIndexed", "LSID", "SourceApplicationId", "CpasType");

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
            detailsView.setFrame(WebPartView.FrameType.PORTAL);

            CustomPropertiesView cpv = new CustomPropertiesView(_material.getLSID(), c);

            return new VBox(new StandardAndCustomPropertiesView(detailsView, cpv));
        }

        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic("sampleSets");
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

    @RequiresPermission(ReadPermission.class)
    public class ShowMaterialAction extends ShowMaterialSimpleAction
    {
        public VBox getView(ExpObjectForm form, BindException errors) throws Exception
        {
            VBox vbox = super.getView(form, errors);

            List<ExpMaterial> materialsToInvestigate = new ArrayList<>();
            final List<ExpRun> successorRuns = new ArrayList<>();
            materialsToInvestigate.add(_material);
            Set<ExpMaterial> investigatedMaterials = new HashSet<>();
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
                StringExpression expr = queryDef.urlExpr(QueryAction.updateQueryRow, null);
                if (expr != null)
                {
                    // Since we're building a detailsURL outside the context of a "row" need to set the correct
                    // container context on the generated expr.
                    ((DetailsURL) expr).setContainerContext(ss.getContainer());
                    String url = expr.eval(Collections.singletonMap(new FieldKey(null, "RowId"), _material.getRowId()));
                    updateLinks.append(PageFlowUtil.textLink("edit", url) + " ");
                }
            }

            if (getContainer().hasPermission(getUser(), InsertPermission.class))
            {
                ActionURL deriveURL = new ActionURL(DeriveSamplesChooseTargetAction.class, getContainer());
                deriveURL.addParameter("rowIds", _material.getRowId());

                updateLinks.append(PageFlowUtil.textLink("derive samples from this sample", deriveURL) + " ");
            }

            vbox.addView(new HtmlView(updateLinks.toString()));

            ExperimentRunListView runListView = ExperimentRunListView.createView(getViewContext(), ExperimentRunType.ALL_RUNS_TYPE, true);
            runListView.setShowRecordSelectors(false);
            runListView.getRunTable().setRuns(successorRuns);
            runListView.getRunTable().setContainerFilter(new ContainerFilter.AllFolders(getUser()));
            runListView.setAllowableContainerFilterTypes(ContainerFilter.Type.Current, ContainerFilter.Type.CurrentAndSubfolders, ContainerFilter.Type.AllFolders);
            runListView.setTitle("Runs using this material or a derived material");

            ParentChildView pv = new ParentChildView(_material, getViewContext());
            vbox.addView(pv);
            vbox.addView(runListView);

            return vbox;
        }

    }


    //
    // DataClass
    //

    @RequiresPermission(ReadPermission.class)
    public class ListDataClassAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            DataClassWebPart view = new DataClassWebPart(false, getViewContext(), null);
            view.setFrame(WebPartView.FrameType.NONE);

            return view;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic("dataClass");
            return appendRootNavTrail(root).addChild("Data Class");
        }
    }

    public static class DataClassForm extends ExpObjectForm
    {
        private String _name;

        public String getName()
        {
            return _name;
        }

        public void setName(String name)
        {
            _name = name;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class ShowDataClassAction extends SimpleViewAction<DataClassForm>
    {
        private ExpDataClassImpl _dataClass;

        @Override
        public ModelAndView getView(DataClassForm form, BindException errors) throws Exception
        {
            if (form.getName() != null)
            {
                _dataClass = ExperimentServiceImpl.get().getDataClass(getContainer(), getUser(), form.getName());
                if (_dataClass == null)
                    throw new NotFoundException("No data class found for name '" + form.getName() + "'");
            }

            if (_dataClass == null && form.getRowId() > 0)
                _dataClass = ExperimentServiceImpl.get().getDataClass(form.getRowId());

            if (_dataClass == null)
                throw new NotFoundException("No data class found");

            ensureCorrectContainer(getContainer(), _dataClass, getViewContext());

            ExpSchema expSchema = new ExpSchema(getUser(), getContainer());
            UserSchema dataClassSchema = (UserSchema)expSchema.getSchema(ExpSchema.NestedSchemas.data.toString());
            if (dataClassSchema == null)
                throw new NotFoundException("exp.dataclass schema not found");
            QueryView queryView = dataClassSchema.createView(getViewContext(), QueryView.DATAREGIONNAME_DEFAULT, _dataClass.getName(), errors);

            TableInfo table = ExpSchema.TableType.DataClasses.createTable(expSchema, null);
            QueryUpdateForm tvf = new QueryUpdateForm(table, getViewContext(), null);
            tvf.setPkVal(_dataClass.getRowId());
            DetailsView detailsView = new DetailsView(tvf);
            detailsView.setTitle("Data Class Properties");

            ButtonBar bb = new ButtonBar();
            bb.setStyle(ButtonBar.Style.separateButtons);

            if (table.hasPermission(getUser(), UpdatePermission.class))
            {
                ActionURL updateUrl = _dataClass.urlUpdate(getUser(), getContainer(), getViewContext().getActionURL());
                ActionButton editButton = new ActionButton("Edit", updateUrl);
                bb.add(editButton);

                ActionURL editFields = _dataClass.urlEditDefinition(getViewContext());
                ActionButton editFieldsButton = new ActionButton("Edit Fields", editFields);
                bb.add(editFieldsButton);
            }
            detailsView.getDataRegion().setButtonBar(bb);

            VBox vbox = new VBox(detailsView, queryView);
            return vbox;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic("dataClass");
            ActionURL url = new ActionURL(ListDataClassAction.class, getContainer());
            return appendRootNavTrail(root).addChild("Data Class", url).addChild(_dataClass.getName());
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class DeleteDataClassAction extends AbstractDeleteAction
    {
        public DeleteDataClassAction()
        {
            super();
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic("dataClass");
            return super.appendNavTrail(root);
        }

        protected void deleteObjects(DeleteForm deleteForm) throws SQLException, ExperimentException, ServletException
        {
            List<ExpDataClass> dataClasses = getDataClasses(deleteForm);
            if (!ensureCorrectContainer(dataClasses))
            {
                throw new UnauthorizedException();
            }
            for (ExpRun run : getRuns(dataClasses))
            {
                if (!run.getContainer().hasPermission(getUser(), DeletePermission.class))
                {
                    throw new UnauthorizedException();
                }
            }
            for (ExpDataClass dataClass : dataClasses)
            {
                dataClass.delete(getUser());
            }
            String selectionKey = deleteForm.getDataRegionSelectionKey();
            DataRegionSelection.clearAll(getViewContext(), selectionKey);
        }

        public ModelAndView getView(DeleteForm deleteForm, boolean reshow, BindException errors) throws Exception
        {
            List<ExpDataClass> dataClasses = getDataClasses(deleteForm);

            if (!ensureCorrectContainer(dataClasses))
            {
                throw new RedirectException(ExperimentUrlsImpl.get().getShowSampleSetListURL(getContainer(), "To delete a data class, you must be in its folder or project."));
            }

            return new ConfirmDeleteView("Data Class", ShowDataClassAction.class, dataClasses, deleteForm, getRuns(dataClasses));
        }

        private List<ExpDataClass> getDataClasses(DeleteForm deleteForm)
        {
            List<ExpDataClass> dataClasses = new ArrayList<>();
            for (int rowId : deleteForm.getIds(false))
            {
                ExpDataClass dataClass = ExperimentServiceImpl.get().getDataClass(rowId);
                if (dataClass != null)
                {
                    dataClasses.add(dataClass);
                }
            }
            return dataClasses;
        }

        private boolean ensureCorrectContainer(List<ExpDataClass> dataClasses)
        {
            for (ExpDataClass dataClass : dataClasses)
            {
                Container sourceContainer = dataClass.getContainer();
                if (!sourceContainer.equals(getContainer()))
                {
                    return false;
                }
            }
            return true;
        }

        private List<? extends ExpRun> getRuns(List<ExpDataClass> dataClasses)
        {
            if (dataClasses.size() > 0)
            {
                List<? extends ExpRun> runArray = ExperimentService.get().getRunsUsingDataClasses(dataClasses);
                return ExperimentService.get().runsDeletedWithInput(runArray);
            }
            else
            {
                return Collections.emptyList();
            }
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class InsertDataClassAction extends FormViewAction<InsertDataClassForm>
    {
        private ActionURL _successUrl;
        private Map<String, DomainTemplate> _domainTemplates;

        @Override
        public void validateCommand(InsertDataClassForm form, Errors errors)
        {
            String name = form.getName();

            if (form.isUseTemplate())
            {
                Set<String> messages = new HashSet<>();
                _domainTemplates = DomainTemplateGroup.getAllTemplates(getContainer());

                if (!_domainTemplates.containsKey(form.getDomainTemplate()))
                    errors.reject(ERROR_MSG, "Unknown template selected: " + form.getDomainTemplate());
                else
                {
                    DomainTemplate template = _domainTemplates.get(form.getDomainTemplate());
                    name = template.getTemplateName();
                }
            }

            if (StringUtils.isBlank(name))
                errors.reject(ERROR_MSG, "DataClass name or template selection is required.");
            else if (ExperimentService.get().getDataClass(getContainer(), getUser(), name) != null)
                errors.reject(ERROR_MSG, "DataClass '" + name + "' already exists.");

        }

        @Override
        public ModelAndView getView(InsertDataClassForm form, boolean reshow, BindException errors) throws Exception
        {
            Set<String> messages = new HashSet<>();
            Set<String> templates = new TreeSet<>();
            Map<String, DomainTemplateGroup> groups = DomainTemplateGroup.getAllGroups(getContainer());

            for (DomainTemplateGroup g : groups.values())
            {
                messages.addAll(g.getErrors());
                templates.addAll(g.getTemplates().keySet());
            }

            form.setAvailableDomainTemplateNames(templates);
            form.setXmlParseErrors(messages);

            return new JspView<>("/org/labkey/experiment/insertDataClass.jsp", form, errors);
        }

        @Override
        public boolean handlePost(InsertDataClassForm form, BindException errors) throws Exception
        {
            if (form.isUseTemplate())
            {
                DomainTemplate template = _domainTemplates.get(form.getDomainTemplate());
                Domain domain = DomainUtil.createDomain(template, getContainer(), getUser(), form.getName());

                _successUrl = domain.getDomainKind().urlShowData(domain, getViewContext());
            }
            else
            {
                ExpDataClass dataClass = ExperimentService.get().createDataClass(
                    getContainer(), getUser(), form.getName(), form.getDescription(),
                    Collections.emptyList(), Collections.emptyList(), form.getMaterialSourceId(), form.getNameExpression(),
                    null
                );

                Domain domain = dataClass.getDomain();
                DomainKind kind = domain.getDomainKind();
                _successUrl = kind.urlEditDefinition(domain, getViewContext());
            }

            return true;
        }

        @Override
        public URLHelper getSuccessURL(InsertDataClassForm form)
        {
            return _successUrl;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Create Data Class");
        }
    }

    public static class InsertDataClassForm extends DataClass
    {
        private boolean _useTemplate;
        private String _domainTemplate;
        private Set<String> _availableDomainTemplateNames;
        private Set<String> _xmlParseErrors;

        public boolean isUseTemplate()
        {
            return _useTemplate;
        }

        public void setUseTemplate(boolean useTemplate)
        {
            _useTemplate = useTemplate;
        }

        public String getDomainTemplate()
        {
            return _domainTemplate;
        }

        public void setDomainTemplate(String domainTemplate)
        {
            _domainTemplate = domainTemplate;
        }

        public Set<String> getAvailableDomainTemplateNames()
        {
            return _availableDomainTemplateNames;
        }

        public void setAvailableDomainTemplateNames(Set<String> availableDomainTemplateNames)
        {
            _availableDomainTemplateNames = availableDomainTemplateNames;
        }

        public Set<String> getXmlParseErrors()
        {
            return _xmlParseErrors;
        }

        public void setXmlParseErrors(Set<String> xmlParseErrors)
        {
            _xmlParseErrors = xmlParseErrors;
        }
    }

    public static class ConceptURIForm
    {
        private String _conceptURI;

        public String getConceptURI()
        {
            return _conceptURI;
        }

        public void setConceptURI(String conceptURI)
        {
            _conceptURI = conceptURI;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class RemoveConceptMappingAction extends ApiAction<ConceptURIForm>
    {
        @Override
        public void validateForm(ConceptURIForm form, Errors errors)
        {
            if (form.getConceptURI() == null || ConceptURIProperties.getLookup(getContainer(), form.getConceptURI()) == null)
                errors.reject(ERROR_MSG, "Concept URI not found: " + form.getConceptURI());
        }

        @Override
        public Object execute(ConceptURIForm form, BindException errors) throws Exception
        {
            ConceptURIProperties.removeLookup(getContainer(), form.getConceptURI());
            return new ApiSimpleResponse("success", true);
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class DataClassAttachmentDownloadAction extends BaseDownloadAction<DataClassAttachmentForm>
    {
        @Nullable
        @Override
        public Pair<AttachmentParent, String> getAttachment(DataClassAttachmentForm form)
        {
            if (form.getLsid() == null || form.getName() == null)
                throw new NotFoundException("Error: missing required param 'lsid' or 'name'.");

            Lsid lsid = new Lsid(form.getLsid());
            AttachmentParent parent = new ExpDataClassAttachmentParent(getContainer(), lsid);

            return new Pair<>(parent, form.getName());
        }
    }

    public static class DataClassAttachmentForm extends LsidForm
    {
        private String _name;

        public String getName()
        {
            return _name;
        }

        public void setName(String name)
        {
            _name = name;
        }
    }

    //
    // END DataClass actions
    //

    public static ActionURL getRunGraphURL(Container c, int runId)
    {
        return new ActionURL(ShowRunGraphAction.class, c).addParameter("rowId", runId);
    }


    @RequiresPermission(ReadPermission.class)
    public class ShowRunGraphAction extends AbstractShowRunAction
    {
        protected VBox createLowerView(ExpRunImpl experimentRun, BindException errors)
        {
            return new VBox(
                    new ToggleRunView(experimentRun, false, true, true),
                    new ExperimentRunGraphView(experimentRun, false));
        }
    }


    @RequiresPermission(ReadPermission.class)
    public class DownloadGraphAction extends SimpleViewAction<ExperimentRunForm>
    {
        public ModelAndView getView(ExperimentRunForm form, BindException errors) throws Exception
        {
            boolean detail = form.isDetail();
            String focus = form.getFocus();
            String focusType = form.getFocusType();

            ExpRunImpl experimentRun = form.lookupRun();
            ensureCorrectContainer(getContainer(), experimentRun, getViewContext());

            ExperimentRunGraph.RunGraphFiles files;
            try
            {
                files = ExperimentRunGraph.generateRunGraph(getViewContext(), experimentRun, detail, focus, focusType);
            }
            catch (ExperimentException e)
            {
                PageFlowUtil.streamTextAsImage(getViewContext().getResponse(), "ERROR: " + e.getMessage(), 600, 150, java.awt.Color.RED);
                return null;
            }

            try
            {
                PageFlowUtil.streamFile(getViewContext().getResponse(), new File(files.getImageFile().getAbsolutePath()), false);
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

            CustomPropertiesView cpv = new CustomPropertiesView(_experimentRun.getLSID(), getContainer());

            vbox.addView(new StandardAndCustomPropertiesView(detailsView, cpv));
            VBox lowerView = createLowerView(_experimentRun, errors);
            lowerView.setFrame(WebPartView.FrameType.PORTAL);
            lowerView.setTitle("Run Details");
            NavTree tree = new NavTree("");
            File runRoot = _experimentRun.getFilePathRoot();
            if (runRoot != null && NetworkDrive.exists(runRoot))
            {
                if (!runRoot.isDirectory())
                {
                    runRoot = runRoot.getParentFile();
                }
                PipeRoot pipelineRoot = PipelineService.get().findPipelineRoot(_experimentRun.getContainer());
                if (pipelineRoot != null)
                {
                    if (pipelineRoot.isUnderRoot(runRoot))
                    {
                        String path = pipelineRoot.relativePath(runRoot);
                        tree.addChild("View Files", PageFlowUtil.urlProvider(PipelineUrls.class).urlBrowse(_experimentRun.getContainer(), null, path));
                    }
                }
            }
            NavTree downloadFiles = new NavTree("Download all files");

            downloadFiles.setScript("exportFiles();");

            tree.addChild(downloadFiles);

            lowerView.setNavMenu(tree);
            lowerView.setIsWebPart(false);

            vbox.addView(lowerView);
            vbox.addView(new ExperimentRunGroupsView(getUser(), getContainer(), _experimentRun, getViewContext().getActionURL(), errors));

            StringBuilder html = new StringBuilder();
            html.append("<form id=\"exportFilesForm\" method=\"post\" action=\"");
            html.append(new ActionURL(ExportRunFilesAction.class, _experimentRun.getContainer()));
            html.append("\"><input type=\"hidden\" value=\"ExportSingleRun\" name=\"");
            html.append(DataRegionSelection.DATA_REGION_SELECTION_KEY);
            html.append("\" /><input type=\"hidden\" name=\"");
            html.append(DataRegion.SELECT_CHECKBOX_NAME);
            html.append("\" value=\"");
            html.append(_experimentRun.getRowId());
            html.append("\" /><input type=\"hidden\" name=\"zipFileName\" value=\"");
            html.append(PageFlowUtil.filter(_experimentRun.getName()));
            html.append(".zip\" /></form>");
            html.append("<script>function exportFiles() { document.getElementById('exportFilesForm').submit(); }</script>");

            HtmlView hiddenFormView = new HtmlView(html.toString());
            vbox.addView(hiddenFormView);

            return vbox;
        }

        protected abstract VBox createLowerView(ExpRunImpl experimentRun, BindException errors);

        public NavTree appendNavTrail(NavTree root)
        {
            return appendRootNavTrail(root).addChild(_experimentRun.getName());
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

    @RequiresPermission(UpdatePermission.class)
    public class ToggleRunExperimentMembershipAction extends SimpleViewAction<ToggleRunExperimentMembershipForm>
    {
        public NavTree appendNavTrail(NavTree root)
        {
            throw new UnsupportedOperationException();
        }

        public ModelAndView getView(ToggleRunExperimentMembershipForm form, BindException errors) throws Exception
        {
            ExpRun run = ExperimentService.get().getExpRun(form.getRunId());
            // Check if the user has permission to update this run
            if (run == null || !run.getContainer().hasPermission(getUser(), UpdatePermission.class))
            {
                throw new NotFoundException();
            }

            ExpExperiment exp = ExperimentService.get().getExpExperiment(form.getExperimentId());
            if (exp == null)
            {
                throw new NotFoundException();
            }
            // Check if this
            if (!ExperimentService.get().getExperiments(run.getContainer(), getUser(), true, false).contains(exp))
            {
                throw new NotFoundException();
            }
            // Users must have permission to view, but not necessarily update, the container the holds the run group
            if (!exp.getContainer().hasPermission(getUser(), ReadPermission.class))
            {
                throw new UnauthorizedException();
            }

            if (form.isIncluded())
            {
                exp.addRuns(getUser(), run);
            }
            else
            {
                exp.removeRun(getUser(), run);
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

            sb.append("<table class=\"labkey-tab-strip\"><tr>");
            addSpace(sb);
            addTab("Graph Summary View", ExperimentUrlsImpl.get().getRunGraphURL(expRun), !showGraphSummary, sb);
            addSpace(sb);
            addTab("Graph Detail View", ExperimentUrlsImpl.get().getRunGraphDetailURL(expRun), !showGraphDetail, sb);
            addSpace(sb);
            addTab("Text View", ExperimentUrlsImpl.get().getRunTextURL(expRun), !showText, sb);
            sb.append("<td class=\"labkey-tab-space\" width=\"100%\"></td>");
            addSpace(sb);
            sb.append("</tr></table>");

            setHtml(sb.toString());
        }

        private void addTab(String text, ActionURL url, boolean selected, StringBuilder sb)
        {
            sb.append("<td class=\"labkey-tab" + (selected ? "-selected" : "" ) + "\" style=\"margin-bottom: 0px;\"><a href=\"" + url + "\">" + PageFlowUtil.filter(text) + "</a></td>");
        }

        private void addSpace(StringBuilder sb)
        {
            sb.append("<td class=\"labkey-tab-space\"><img width=\"5\" src=\"");
            sb.append(AppProps.getInstance().getContextPath());
            sb.append("/_.gif\"></td>");
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class ShowRunTextAction extends AbstractShowRunAction
    {
        protected VBox createLowerView(ExpRunImpl expRun, BindException errors)
        {
            JspView<ExpRun> applicationsView = new JspView<ExpRun>("/org/labkey/experiment/ProtocolApplications.jsp", expRun);
            applicationsView.setFrame(WebPartView.FrameType.TITLE);
            applicationsView.setTitle("Protocol Applications");

            HtmlView toggleView = new ToggleRunView(expRun, true, true, false);

            QuerySettings runDataInputsSettings = new QuerySettings(getViewContext(), "RunDataInputs", ExpSchema.TableType.DataInputs.name());
            UsageQueryView runDataInputsView = new UsageQueryView("Data Inputs", getViewContext(), expRun, ExpProtocol.ApplicationType.ExperimentRun, runDataInputsSettings, errors);
            runDataInputsView.setButtonBarPosition(DataRegion.ButtonBarPosition.TOP);

            QuerySettings runDataOutputsSettings = new QuerySettings(getViewContext(), "RunDataOutputs", ExpSchema.TableType.DataInputs.name());
            UsageQueryView runDataOutputsView = new UsageQueryView("Data Outputs", getViewContext(), expRun, ExpProtocol.ApplicationType.ExperimentRunOutput, runDataOutputsSettings, errors);
            runDataOutputsView.setButtonBarPosition(DataRegion.ButtonBarPosition.NONE);

            QuerySettings runMaterialInputsSetting = new QuerySettings(getViewContext(), "RunMaterialInputs", ExpSchema.TableType.MaterialInputs.name());
            UsageQueryView runMaterialInputsView = new UsageQueryView("Material Inputs", getViewContext(), expRun, ExpProtocol.ApplicationType.ExperimentRun, runMaterialInputsSetting, errors);
            runMaterialInputsView.setButtonBarPosition(DataRegion.ButtonBarPosition.TOP);

            QuerySettings runMaterialOutputsSettings = new QuerySettings(getViewContext(), "RunMaterialOutputs", ExpSchema.TableType.MaterialInputs.name());
            UsageQueryView runMaterialOutputsView = new UsageQueryView("Material Outputs", getViewContext(), expRun, ExpProtocol.ApplicationType.ExperimentRunOutput, runMaterialOutputsSettings, errors);
            runMaterialOutputsView.setButtonBarPosition(DataRegion.ButtonBarPosition.NONE);

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
            setFrame(FrameType.TITLE);
            _run = run;
            _type = type;
            setShowBorders(true);
            setShadeAlternatingRows(true);
            setShowExportButtons(false);
            setShowPagination(false);
            disableContainerFilterSelection();
        }

        @Override
        protected TableInfo createTable()
        {
            ExpInputTable tableInfo = (ExpInputTable)super.createTable();
            tableInfo.setContainerFilter(new ContainerFilter.AllFolders(getUser()));
            tableInfo.setRun(_run, _type);
            return tableInfo;
        }

    }


    public static ActionURL getShowRunGraphDetailURL(Container c, int rowId)
    {
        ActionURL url = new ActionURL(ShowRunGraphDetailAction.class, c);
        url.addParameter("rowId", rowId);
        return url;
    }


    @RequiresPermission(ReadPermission.class)
    public class ShowRunGraphDetailAction extends AbstractShowRunAction
    {
        protected VBox createLowerView(ExpRunImpl run, BindException errors)
        {
            ExperimentRunGraphView gw = new ExperimentRunGraphView(run, true);
            if (null != getViewContext().getActionURL().getParameter("focus"))
                gw.setFocus(getViewContext().getActionURL().getParameter("focus"));
            if (null != getViewContext().getActionURL().getParameter("focusType"))
                gw.setFocusType(getViewContext().getActionURL().getParameter("focusType"));
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
                throw new NotFoundException("Could not find a data with RowId " + form.getRowId());
            }

            ensureCorrectContainer(getContainer(), _data, getViewContext());
            return getDataView(form, errors);
        }

        protected abstract ModelAndView getDataView(DataForm form, BindException errors) throws Exception;

        public NavTree appendNavTrail(NavTree root)
        {
            return appendRootNavTrail(root).addChild("Data " + _data.getName());
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class ShowDataAction extends AbstractDataAction
    {
        public ModelAndView getDataView(DataForm form, BindException errors) throws Exception
        {
            ExpRun run = _data.getRun();
            ExpProtocol sourceProtocol = _data.getSourceProtocol();
            ExpProtocolApplication sourceProtocolApplication = _data.getSourceApplication();
            ExpDataClass dataClass = _data.getDataClass();

            ExpSchema schema = new ExpSchema(getUser(), getContainer());
            TableInfo table;
            int pk;
            if (dataClass == null)
            {
                table = schema.getDatasTable();
                pk = _data.getRowId();
            }
            else
            {
                table = schema.getSchema(ExpSchema.NestedSchemas.data).getTable(dataClass.getName());
                pk = new TableSelector(table, Collections.singleton("rowId"), new SimpleFilter("lsid", _data.getLSID()), null).getObject(Integer.class);
            }

            DataRegion dr = new DataRegion();
            dr.setTable(table);
            List<ColumnInfo> cols = table.getColumns().stream().filter(ColumnInfo::isShownInDetailsView).collect(Collectors.toList());
            dr.addColumns(cols);
            dr.removeColumns("RowId", "Created", "CreatedBy", "Modified", "ModifiedBy", "DataFileUrl", "Run", "LSID", "CpasType", "SourceApplicationId", "Folder", "Generated");
            dr.addDisplayColumn(new ExperimentRunDisplayColumn(run, "Source Experiment Run"));
            dr.addDisplayColumn(new ProtocolDisplayColumn(sourceProtocol, "Source Protocol"));
            dr.addDisplayColumn(new ProtocolApplicationDisplayColumn(sourceProtocolApplication, "Source Protocol Application"));
            dr.addDisplayColumn(new LineageGraphDisplayColumn(_data, run));
            DetailsView detailsView = new DetailsView(dr, pk);
            detailsView.setTitle("Standard Properties");
            detailsView.setFrame(WebPartView.FrameType.PORTAL);
            ButtonBar bb = new ButtonBar();
            bb.setStyle(ButtonBar.Style.separateButtons);

            ExperimentDataHandler handler = _data.findDataHandler();
            ActionURL viewDataURL = handler == null ? null : handler.getContentURL(_data);
            if (viewDataURL != null)
            {
                bb.add(new ActionButton("View data", viewDataURL));
            }

            if (_data.isPathAccessible())
            {
                bb.add(new ActionButton("View file", ExperimentUrlsImpl.get().getShowFileURL(_data, true)));
                bb.add(new ActionButton("Download file", ExperimentUrlsImpl.get().getShowFileURL(_data, false)));

                if (getContainer().hasPermission(getUser(), InsertPermission.class))
                {
                    String relativePath = null;
                    PipeRoot root = PipelineService.get().findPipelineRoot(getContainer());
                    if (root != null)
                    {
                        Path rootFile = root.getRootNioPath();
                        Path dataFile = _data.getFilePath();
                        if (dataFile != null)
                        {
                            Path pathRelative = null;
                            try
                            {
                                pathRelative = rootFile.relativize(dataFile);
                                if (null != pathRelative)
                                    relativePath = pathRelative.toString();
                            }
                            catch (IllegalArgumentException e)
                            {
                                // dataFile not relative to root
                            }
                        }
                    }
                    ActionURL browseURL = PageFlowUtil.urlProvider(PipelineUrls.class).urlBrowse(getContainer(), getViewContext().getActionURL(), relativePath);
                    bb.add(new ActionButton("Browse in pipeline", browseURL));
                }
            }
            dr.setButtonBarPosition(DataRegion.ButtonBarPosition.TOP);
            dr.setButtonBar(bb);

            CustomPropertiesView cpv = new CustomPropertiesView(_data.getLSID(), getContainer());
            HBox hbox = new StandardAndCustomPropertiesView(detailsView, cpv);

            VBox vbox = new VBox(hbox);

            ParentChildView pv = new ParentChildView(_data, getViewContext());
            vbox.addView(pv);

            ExperimentRunListView runListView = ExperimentRunListView.createView(getViewContext(), ExperimentRunType.ALL_RUNS_TYPE, true);
            runListView.getRunTable().setInputData(_data);
            runListView.getRunTable().setContainerFilter(new ContainerFilter.AllFolders(getUser()));
            runListView.setTitle("Runs using this data as an input");
            vbox.addView(runListView);

            if (_data.isInlineImage() && _data.isFileOnDisk())
            {
                ActionURL showFileURL = new ActionURL(ShowFileAction.class, getContainer()).addParameter("rowId", _data.getRowId());
                HtmlView imageView = new HtmlView("<img src=\"" + showFileURL + "\"/>");
                return new VBox(vbox, imageView);
            }
            return vbox;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class CheckDataFileAction extends ApiAction<DataFileForm>
    {
        private ExpDataImpl _data;

        @Override
        public void validateForm(DataFileForm form, Errors errors)
        {
            _data = form.lookupData();
            if (_data == null)
            {
                errors.reject("No ExpData found for id: " + form.getRowId());
            }
        }

        @Override
        public ApiResponse execute(DataFileForm form, BindException errors) throws Exception
        {
            File dataFile = _data.getFile();
            Container dataContainer = _data.getContainer();
            boolean fileExists = _data.isFileOnDisk();
            boolean fileExistsAtCurrent = false;
            File newDataFile = null;

            ApiSimpleResponse response = new ApiSimpleResponse();
            response.put("dataFileUrl", _data.getDataFileUrl());
            response.put("fileExists", fileExists);
            response.put("containerPath", dataContainer.getPath());

            if (!fileExists)
            {
                PipeRoot pipelineRoot = PipelineService.get().findPipelineRoot(dataContainer);
                if (pipelineRoot != null && pipelineRoot.isValid() && dataFile != null)
                {
                    newDataFile = pipelineRoot.resolvePath("/" + AssayFileWriter.DIR_NAME + "/" + dataFile.getName());
                    fileExistsAtCurrent = NetworkDrive.exists(newDataFile);
                    response.put("fileExistsAtCurrent", fileExistsAtCurrent);
                }
            }

            // if the current dataFileUrl does not exist on disk and we have the file at the current
            // pipeline root /assaydata dir, fix the dataFileUrl value
            if (form.isAttemptFilePathFix())
            {
                if (fileExistsAtCurrent)
                {
                    ExpDataFileListener fileListener = new ExpDataFileListener();
                    fileListener.fileMoved(dataFile, newDataFile, getUser(), dataContainer);
                    response.put("filePathFixed", true);

                    // update the ExpData object so that we can get the new dataFileUrl
                    _data = form.lookupData();
                    response.put("newDataFileUrl", _data.getDataFileUrl());
                }
                else
                {
                    response.put("filePathFixed", false);
                }
            }

            response.put("success", true);
            return response;
        }
    }

    public static class DataFileForm extends DataForm
    {
        private boolean _attemptFilePathFix;

        public boolean isAttemptFilePathFix()
        {
            return _attemptFilePathFix;
        }

        public void setAttemptFilePathFix(boolean attemptFilePathFix)
        {
            _attemptFilePathFix = attemptFilePathFix;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class ShowFileAction extends AbstractDataAction
    {
        protected ModelAndView getDataView(DataForm form, BindException errors) throws IOException
        {
            if (!_data.isPathAccessible())
            {
                throw new NotFoundException("Data file " + _data.getDataFileUrl() + " does not exist on disk");
            }

            //Issues 25667 and 31152
            if (form.isInline())
            {
                ExperimentDataHandler h = _data.findDataHandler();
                if (h != null)
                {
                    URLHelper url = h.getShowFileURL(_data);
                    if (url != null)
                    {
                        throw new RedirectException(url);
                    }
                }
            }

            try
            {
                Path realContent = _data.getFilePath();
                if (null == realContent)
                    throw new IllegalStateException("Path not found.");

                boolean inline = _data.isInlineImage() || form.isInline() || "inlineImage".equalsIgnoreCase(form.getFormat());
                if (_data.isInlineImage() && form.getMaxDimension() != null)
                {
                    try (InputStream inputStream = Files.newInputStream(realContent))
                    {
                        BufferedImage image = ImageIO.read(inputStream);
                        // If image, create a thumbnail, otherwise fall through as a regular download attempt
                        if (image != null)
                        {
                            int imageMax = Math.max(image.getHeight(), image.getWidth());
                            if (imageMax > form.getMaxDimension().intValue())
                            {
                                double scale = (double) form.getMaxDimension().intValue() / (double) imageMax;
                                ByteArrayOutputStream bOut = new ByteArrayOutputStream();
                                ImageUtil.resizeImage(image, bOut, scale, 1);
                                PageFlowUtil.streamFileBytes(getViewContext().getResponse(), FileUtil.getFileName(realContent) + ".png", bOut.toByteArray(), !inline);
                                return null;
                            }
                        }
                    }
                }

                boolean extended = "jsonTSVExtended".equalsIgnoreCase(form.getFormat());
                boolean ignoreTypes = "jsonTSVIgnoreTypes".equalsIgnoreCase(form.getFormat());
                if ("jsonTSV".equalsIgnoreCase(form.getFormat()) || extended || ignoreTypes)
                {
                    if (!FileUtil.hasCloudScheme(realContent))                      // TODO: handle streaming from S3 to JSON
                        streamToJSON(realContent.toFile(), form.getFormat(), -1, null);
                    return null;
                }

                try (InputStream inputStream = Files.newInputStream(realContent))
                {
                    PageFlowUtil.streamFile(getViewContext().getResponse(), Collections.emptyMap(), FileUtil.getFileName(realContent), inputStream, !inline);
                }
            }
            catch (IOException e)
            {
                try
                {
                    // Try to write the exception back to the caller if we haven't already flushed the buffer
                    ApiJsonWriter writer = new ApiJsonWriter(getViewContext().getResponse());
                    writer.writeAndClose(e);
                }
                catch (IllegalStateException ise)
                {
                    // Most likely that a disconnected client caused the IOException writing back the response
                }
            }

            return null;
        }
    }


    public static class ParseForm
    {
        String format = "jsonTSV";
        int maxRows = -1;

        public String getFormat()
        {
            return format;
        }

        public void setFormat(String format)
        {
            this.format = format;
        }

        public int getMaxRows()
        {
            return maxRows;
        }

        public void setMaxRows(int maxRow)
        {
            this.maxRows = maxRow;
        }
    }


    @CSRF @RequiresNoPermission
    public class ParseFileAction extends ApiAction<ParseForm>
    {
        @Override
        public Object execute(ParseForm form, BindException errors) throws Exception
        {
            if (!(getViewContext().getRequest() instanceof MultipartHttpServletRequest))
                throw new BadRequestException(HttpServletResponse.SC_BAD_REQUEST, "Expected MultipartHttpServletRequest when posting files.", null);

            MultipartFile formFile = getFileMap().get("file");
            if (formFile == null)
            {
                return true;
            }

            File tempFile = null;
            try
            {
                tempFile = File.createTempFile("parse", formFile.getOriginalFilename());
                FileUtil.copyData(formFile.getInputStream(),tempFile);
                streamToJSON(tempFile, form.getFormat(), form.getMaxRows(), formFile.getOriginalFilename());
            }
            finally
            {
                if (null != tempFile)
                    tempFile.delete();
            }
            return null;
        }
    }


    // SampleSetTest
    private void streamToJSON(File realContent, String format, int maxRow, String originalFileName) throws IOException
    {
        String lowerCaseFileName = realContent.getName().toLowerCase();
        boolean extended = "jsonTSVExtended".equalsIgnoreCase(format);
        boolean ignoreTypes = "jsonTSVIgnoreTypes".equalsIgnoreCase(format);

        JSONArray sheetsArray;
        if (lowerCaseFileName.endsWith(".xls") || lowerCaseFileName.endsWith(".xlsx"))
        {
            try
            {
                sheetsArray = ExcelFactory.convertExcelToJSON(realContent, extended, maxRow);
            }
            catch (InvalidFormatException e)
            {
                throw new NotFoundException("Could not open " + realContent.getName(), e);
            }
        }
        else
        {
            DataLoaderFactory dlf = DataLoader.get().findFactory(realContent, null);
            if (null == dlf)
            {
                throw new ApiUsageException("Unable to parse file " + realContent + ", it is likely of an unsupported file type");
            }
            DataLoader tabLoader = dlf.createLoader(realContent, true);
            tabLoader.setScanAheadLineCount(5000);
            ColumnDescriptor[] cols = tabLoader.getColumns();

            if (ignoreTypes)
                for (ColumnDescriptor col : cols)
                    col.clazz = String.class;

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
                // headers count as a row to be consistent
                if (maxRow > -1 && maxRow <= rowsArray.length()+1)
                    break;

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
            sheetsArray = new JSONArray();
            sheetsArray.put(sheetJSON);
        }
        ApiJsonWriter writer = new ApiJsonWriter(getViewContext().getResponse());
        JSONObject workbookJSON = new JSONObject();
        workbookJSON.put("fileName", realContent.getName());
        workbookJSON.put("sheets", sheetsArray);
        if (originalFileName != null)
            workbookJSON.put("originalFileName", originalFileName);
        writer.writeResponse(new ApiSimpleResponse(workbookJSON));
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

    @RequiresPermission(ReadPermission.class)
    public class ConvertArraysToExcelAction extends ExportAction<ConvertArraysToExcelForm>
    {
        @Override
        public void validate(ConvertArraysToExcelForm form, BindException errors)
        {
            if (form.getJson() == null)
            {
                errors.reject(ERROR_MSG, "Unable to convert to Excel - no spreadsheet data given");
            }
        }

        public void export(ConvertArraysToExcelForm form, HttpServletResponse response, BindException errors) throws Exception
        {
            try
            {
                JSONObject rootObject;
                JSONArray sheetsArray;
                if (form.getJson() == null || form.getJson().trim().length() == 0)
                {
                    // Create JSON so that we return an empty file
                    rootObject = new JSONObject();
                    sheetsArray = new JSONArray();
                    JSONObject sheetObject = new JSONObject();
                    sheetsArray.put(sheetObject);
                }
                else
                {
                    rootObject = new JSONObject(form.getJson());
                    sheetsArray = rootObject.getJSONArray("sheets");
                }
                String filename = rootObject.has("fileName") ? rootObject.getString("fileName") : "ExcelExport.xls";
                ExcelWriter.ExcelDocumentType docType = filename.toLowerCase().endsWith(".xlsx") ? ExcelWriter.ExcelDocumentType.xlsx : ExcelWriter.ExcelDocumentType.xls;

                Workbook workbook =  ExcelFactory.createFromArray(sheetsArray, docType);

                response.setContentType(docType.getMimeType());
                response.setHeader("Content-disposition", "attachment; filename=\"" + filename +"\"");
                response.setHeader("Pragma", "private");
                response.setHeader("Cache-Control", "private");
                workbook.write(response.getOutputStream());
            }
            catch (JSONException | ClassCastException e)
            {
                // We can get a ClassCastException if we expect an array and get a simple String, for example
                HttpView errorView = ExceptionUtil.getErrorView(HttpServletResponse.SC_BAD_REQUEST, "Failed to convert to Excel - invalid input", e, getViewContext().getRequest(), false);
                errorView.render(getViewContext().getRequest(), getViewContext().getResponse());
            }
        }
    }


    @RequiresPermission(ReadPermission.class)
    public class ConvertArraysToTableAction extends ExportAction<ConvertArraysToExcelForm>
    {
        @Override
        public void validate(ConvertArraysToExcelForm form, BindException errors)
        {
            if (form.getJson() == null)
            {
                errors.reject(ERROR_MSG, "Unable to convert to table - no data given");
            }
        }

        public void export(ConvertArraysToExcelForm form, HttpServletResponse response, BindException errors) throws Exception
        {
            try
            {
                JSONObject rootObject;
                JSONArray rowsArray;
                if (form.getJson() == null || form.getJson().trim().length() == 0)
                {
                    // Create JSON so that we return an empty file
                    rootObject = new JSONObject();
                    rowsArray = new JSONArray();
                }
                else
                {
                    rootObject = new JSONObject(form.getJson());
                    rowsArray = rootObject.getJSONArray("rows");
                }

                TSVWriter.DELIM delimType = (rootObject.getString("delim") != null ? TSVWriter.DELIM.valueOf(rootObject.getString("delim")) : TSVWriter.DELIM.TAB);
                TSVWriter.QUOTE quoteType = (rootObject.getString("quoteChar") != null ? TSVWriter.QUOTE.valueOf(rootObject.getString("quoteChar")) : TSVWriter.QUOTE.NONE);
                String filenamePrefix = (rootObject.getString("fileNamePrefix") != null ? rootObject.getString("fileNamePrefix") : "Export" );
                String filename = filenamePrefix + "." + delimType.extension;
                String newlineChar = rootObject.getString("newlineChar") != null ? rootObject.getString("newlineChar") : "\n";

                PageFlowUtil.prepareResponseForFile(response, Collections.emptyMap(), filename, true);
                response.setContentType(delimType.contentType);

                //NOTE: we could also have used TSVWriter; however, this is in use elsewhere and we dont need a custom subclass
                CSVWriter writer = new CSVWriter(response.getWriter(), delimType.delim, quoteType.quoteChar, newlineChar);
                for (int i=0; i < rowsArray.length(); i++)
                {
                    Object[] oa = ((JSONArray)rowsArray.get(i)).toArray();
                    ArrayIterator it = new ArrayIterator(oa);
                    List<String> list = new ArrayList<>();

                    while (it.hasNext())
                    {
                        Object o = it.next();
                        if(o != null)
                            list.add(o.toString());
                        else
                            list.add("");
                    }

                    writer.writeNext(list.toArray(new String[list.size()]));
                }
                writer.close();
            }
            catch (JSONException e)
            {
                HttpView errorView = ExceptionUtil.getErrorView(HttpServletResponse.SC_BAD_REQUEST, "Failed to convert to table - invalid input", e, getViewContext().getRequest(), false);
                errorView.render(getViewContext().getRequest(), getViewContext().getResponse());
            }
        }
    }


    public static class ConvertHtmlToExcelForm
    {
        private String _baseUrl;
        private String _htmlFragment;
        private String _name = "workbook.xls";


        public String getName()
        {
            return _name;
        }

        public void setName(String name)
        {
            _name = name;
        }

        public String getBaseUrl()
        {
            return _baseUrl;
        }

        public void setBaseUrl(String baseUrl)
        {
            _baseUrl = baseUrl;
        }

        public String getHtmlFragment()
        {
            return _htmlFragment;
        }

        public void setHtmlFragment(String htmlFragment)
        {
            _htmlFragment = htmlFragment;
        }
    }


    @RequiresPermission(ReadPermission.class) @CSRF
    public static class ConvertHtmlToExcelAction extends FormViewAction<ConvertHtmlToExcelForm>
    {
        String _responseHtml = null;

        @Override
        public void validateCommand(ConvertHtmlToExcelForm target, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(ConvertHtmlToExcelForm form, boolean reshow, BindException errors) throws Exception
        {
            String html =
                    "<form method=POST><textarea name=\"htmlFragment\" cols=100 rows=40>" +
                            PageFlowUtil.filter(form.getHtmlFragment()) +
                    "</textarea><br>" +
                    "<input type=\"submit\">" +
                    "<input type=hidden name='X-LABKEY-CSRF' value=\"" + CSRFUtil.getExpectedToken(getViewContext()) + "\">" +
                    "</form>";
            return new HtmlView(html);
        }

        @Override
        public boolean handlePost(ConvertHtmlToExcelForm form, BindException errors) throws Exception
        {
            ActionURL url = getViewContext().getActionURL();
            String base = url.getBaseServerURI();
            if (!base.endsWith("/")) base += "/";

            String baseTag = "<base href=\"" + PageFlowUtil.filter(base) + "\"/>";
            String css = PageFlowUtil.getStylesheetIncludes(getContainer());
            String htmlFragment = StringUtils.trimToEmpty(form.getHtmlFragment());
            String html = "<html><head>" + baseTag + css + "</head><body>" + htmlFragment + "</body></html>";

            // UNDONE: strip script
            List<String> tidyErrors = new ArrayList<>();
            String tidy = TidyUtil.tidyHTML(html, false, tidyErrors);

            if (!tidyErrors.isEmpty())
            {
                for (String err : tidyErrors)
                {
                    errors.reject(ERROR_MSG, err);
                }
                return false;
            }

            _responseHtml = tidy;
            return true;
        }

        @Override
        public ModelAndView getSuccessView(ConvertHtmlToExcelForm form)
        {
            // CONSIDER <base href="form.getBaseURL()" />
            getViewContext().getResponse().setHeader("Content-Disposition", "attachment; filename=\"" + form.getName() + "\"");
            getPageConfig().setTemplate(PageConfig.Template.None);
            HtmlView v = new HtmlView(_responseHtml);
            v.setContentType("application/vnd.ms-excel");
            v.setFrame(WebPartView.FrameType.NONE);
            return v;
        }

        @Override
        public URLHelper getSuccessURL(ConvertHtmlToExcelForm convertHtmlToExcelForm)
        {
            return null;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }


    public static ActionURL getShowApplicationURL(Container c, int rowId)
    {
        ActionURL url = new ActionURL(ShowApplicationAction.class, c);
        url.addParameter("rowId", rowId);

        return url;
    }


    @RequiresPermission(ReadPermission.class)
    public class ShowApplicationAction extends SimpleViewAction<ExpObjectForm>
    {
        private ExpProtocolApplicationImpl _app;
        private ExpRun _run;

        public ModelAndView getView(ExpObjectForm form, BindException errors) throws Exception
        {
            _app = ExperimentServiceImpl.get().getExpProtocolApplication(form.getRowId());
            if (_app == null)
            {
                throw new NotFoundException("Could not find Protocol Application");
            }
            _run = _app.getRun();
            if (_run == null)
            {
                throw new NotFoundException("No experiment run associated with Protocol Application");
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
            Map<String, AbstractParameter> map = new HashMap<>();
            for (ProtocolApplicationParameter param : ExperimentService.get().getProtocolApplicationParameters(_app.getRowId()))
            {
                map.put(param.getOntologyEntryURI(), param);
            }

            JspView<Map<String, ? extends AbstractParameter>> paramsView = new JspView<Map<String, ? extends AbstractParameter>>("/org/labkey/experiment/Parameters.jsp", map);
            paramsView.setTitle("Protocol Application Parameters");
            CustomPropertiesView cpv = new CustomPropertiesView(_app.getLSID(), c);
            return new VBox(new StandardAndCustomPropertiesView(detailsView, cpv), paramsView, outMGrid, outDGrid);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendRootNavTrail(root).addChild("Experiment Run", ExperimentUrlsImpl.get().getRunGraphDetailURL(_run)).addChild("Protocol Application " + _app.getName());
        }
    }

    @RequiresPermission(ReadPermission.class)
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

    @RequiresPermission(ReadPermission.class)
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
                throw new NotFoundException("Unable to find a matching protocol");
            }
            ensureCorrectContainer(getContainer(), _protocol, getViewContext());

            JspView<ExpProtocol> detailsView = new JspView<>("/org/labkey/experiment/ProtocolDetails.jsp", _protocol);
            detailsView.setTitle("Standard Properties");

            CustomPropertiesView cpv = new CustomPropertiesView(_protocol.getLSID(), getContainer());
            ProtocolParametersView parametersView = new ProtocolParametersView(_protocol);
            ProtocolListView listView = new ProtocolListView(_protocol, getContainer());

            ExpSchema schema = new ExpSchema(getUser(), getContainer());
            ExperimentRunListView runView = new ExperimentRunListView(schema, ExperimentRunListView.getRunListQuerySettings(schema, getViewContext(), ExpSchema.TableType.Runs.name(), true), ExperimentRunType.ALL_RUNS_TYPE)
            {
                public DataView createDataView()
                {
                    DataView result = super.createDataView();
                    result.getRenderContext().setBaseFilter(new SimpleFilter(FieldKey.fromParts("Protocol", "LSID"), _protocol.getLSID()));
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

    @RequiresPermission(ReadPermission.class)
    public class ProtocolPredecessorsAction extends SimpleViewAction
    {
        private ExpProtocol _parentProtocol;
        private ProtocolActionStepDetail _actionStep;

        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            ActionURL url = getViewContext().getActionURL();

            String parentProtocolLSID = url.getParameter("ParentLSID");
            int actionSequence;
            try
            {
                actionSequence = Integer.parseInt(url.getParameter("Sequence"));
            }
            catch (NumberFormatException e)
            {
                throw new NotFoundException("Could not find SequenceId " + url.getParameter("Sequence"));
            }

            _parentProtocol = ExperimentService.get().getExpProtocol(parentProtocolLSID);
            if (_parentProtocol == null)
            {
                throw new NotFoundException("Unable to find a matching protocol");
            }

            ensureCorrectContainer(getContainer(), _parentProtocol, getViewContext());

            _actionStep = ExperimentServiceImpl.get().getProtocolActionStepDetail(parentProtocolLSID, actionSequence);

            if (_actionStep == null)
            {
                throw new NotFoundException("Unable to find a matching protocol action step");
            }

            ExpProtocol childProtocol = ExperimentService.get().getExpProtocol(_actionStep.getChildProtocolLSID());

            JspView<ExpProtocol> detailsView = new JspView<>("/org/labkey/experiment/ProtocolDetails.jsp", childProtocol);
            detailsView.setTitle("Standard Properties");

            CustomPropertiesView cpv = new CustomPropertiesView(childProtocol.getLSID(), getContainer());

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

    public static class ExpObjectForm extends QueryViewAction.QueryExportForm
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

    @RequiresPermission(DeletePermission.class)
    public class DeleteSelectedExpRunsAction extends AbstractDeleteAction
    {
        public DeleteSelectedExpRunsAction()
        {
            super();
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            // UNDONE: Need help topic on Runs
            setHelpTopic("experiment");
            return super.appendNavTrail(root);
        }

        public ModelAndView getView(DeleteForm deleteForm, boolean reshow, BindException errors) throws Exception
        {
            List<ExpRun> runs = new ArrayList<>();
            for (int runId : deleteForm.getIds(false))
            {
                ExpRun run = ExperimentService.get().getExpRun(runId);
                if (run != null)
                {
                    runs.add(run);
                }
            }

            List<Pair<SecurableResource, ActionURL>> permissionDatasetRows = new ArrayList<>();
            List<Pair<SecurableResource, ActionURL>> noPermissionDatasetRows = new ArrayList<>();
            for (Dataset dataset : StudyService.get().getDatasetsForAssayRuns(runs, getUser()))
            {
                ActionURL url = PageFlowUtil.urlProvider(StudyUrls.class).getDatasetURL(dataset.getContainer(), dataset.getDatasetId());
                if (dataset.canWrite(getUser()))
                {
                    permissionDatasetRows.add(new Pair<SecurableResource, ActionURL>(dataset, url));
                }
                else
                {
                    noPermissionDatasetRows.add(new Pair<SecurableResource, ActionURL>(dataset, url));
                }
            }

            return new ConfirmDeleteView("run", ShowRunGraphAction.class, runs, deleteForm, Collections.emptyList(), "dataset(s) have one or more rows which", permissionDatasetRows, noPermissionDatasetRows);
        }

        protected void deleteObjects(DeleteForm deleteForm) throws ExperimentException
        {
            ExperimentServiceImpl.get().deleteExperimentRunsByRowIds(getContainer(), getUser(), deleteForm.getIds(true));
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
    @RequiresPermission(DeletePermission.class)
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
                try (DbScope.Transaction tx = ExperimentService.get().ensureTransaction())
                {
                    deleteObjects(deleteForm);
                    tx.commit();
                }
                catch (BatchValidationException v)
                {
                    v.addToErrors(errors);
                }

                return !errors.hasErrors();
            }
        }

        public ActionURL getSuccessURL(DeleteForm deleteForm)
        {
            ActionURL url = deleteForm.getReturnActionURL();
            if (null != url)
                return url;
            return ExperimentUrlsImpl.get().getOverviewURL(getContainer());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendRootNavTrail(root).addChild("Confirm Deletion");
        }

        protected abstract void deleteObjects(DeleteForm deleteForm) throws Exception;
    }

    @RequiresPermission(DeletePermission.class)
    public class DeleteProtocolByRowIdsAction extends AbstractDeleteAction
    {
        public DeleteProtocolByRowIdsAction()
        {
            super();
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            // UNDONE: Need help topic on protocols
            setHelpTopic("experiment");
            return super.appendNavTrail(root);
        }

        public ModelAndView getView(DeleteForm deleteForm, boolean reshow, BindException errors) throws Exception
        {
            List<? extends ExpRun> runs = ExperimentService.get().getExpRunsForProtocolIds(false, deleteForm.getIds(false));
            List<ExpProtocol> protocols = getProtocols(deleteForm);
            String noun = "Assay Design";
            List<Pair<SecurableResource, ActionURL>> deleteableDatasets = new ArrayList<>();
            List<Pair<SecurableResource, ActionURL>> noPermissionDatasets = new ArrayList<>();
            for (ExpProtocol protocol : protocols)
            {
                if (AssayService.get().getProvider(protocol) == null)
                {
                    noun = "Protocol";
                }
                for (Dataset dataset : StudyService.get().getDatasetsForAssayProtocol(protocol))
                {
                    Pair<SecurableResource, ActionURL> entry = new Pair<SecurableResource, ActionURL>(dataset, PageFlowUtil.urlProvider(StudyUrls.class).getDatasetURL(dataset.getContainer(), dataset.getDatasetId()));
                    if (dataset.canDeleteDefinition(getUser()))
                    {
                        deleteableDatasets.add(entry);
                    }
                    else
                    {
                        noPermissionDatasets.add(entry);
                    }
                }
            }

            return new ConfirmDeleteView(noun, ProtocolDetailsAction.class, protocols, deleteForm, runs, "Dataset", deleteableDatasets, noPermissionDatasets);
        }

        private List<ExpProtocol> getProtocols(DeleteForm deleteForm)
        {
            List<ExpProtocol> protocols = new ArrayList<>();
            for (int protocolId : deleteForm.getIds(false))
            {
                ExpProtocol protocol = ExperimentService.get().getExpProtocol(protocolId);
                if (protocol != null)
                {
                    protocols.add(protocol);
                }
            }
            return protocols;
        }

        protected void deleteObjects(DeleteForm deleteForm) throws ExperimentException
        {
            for (ExpProtocol protocol : getProtocols(deleteForm))
            {
                protocol.delete(getUser());
            }
        }
    }

    @RequiresPermission(DeletePermission.class)
    public class DeleteMaterialByRowIdAction extends AbstractDeleteAction
    {
        public DeleteMaterialByRowIdAction()
        {
            super();
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic("sampleSets");
            return super.appendNavTrail(root);
        }

        protected void deleteObjects(DeleteForm deleteForm) throws SQLException, ExperimentException, ServletException
        {
            List<ExpMaterial> materials = getMaterials(deleteForm, true);

            for (ExpRun run : getRuns(materials))
            {
                if (!run.getContainer().hasPermission(getUser(), DeletePermission.class))
                    throw new UnauthorizedException();
            }

            for (ExpMaterial expMaterial : materials)
            {
                expMaterial.delete(getUser());
            }
        }

        public ModelAndView getView(DeleteForm deleteForm, boolean reshow, BindException errors) throws Exception
        {
            List<ExpMaterial> materials = getMaterials(deleteForm, false);
            List<ExpRun> runs = getRuns(materials);
            return new ConfirmDeleteView("Sample", ShowMaterialAction.class, materials, deleteForm, runs);
        }

        private List<ExpRun> getRuns(List<ExpMaterial> materials)
                throws SQLException
        {
            // We don't actually delete runs that use the materials - we just disconnect the material from the run
            // In some cases (such as flow) this is required. In others, it's not as sensible
            List<ExpRun> runsToDelete = new ArrayList<>();
            List<? extends ExpRun> runArray = ExperimentService.get().getRunsUsingMaterials(materials);
            for (ExpRun run : ExperimentService.get().runsDeletedWithInput(runArray))
                runsToDelete.add(run);

            return runsToDelete;
        }

        private List<ExpMaterial> getMaterials(DeleteForm deleteForm, boolean clear)
        {
            List<ExpMaterial> materials = new ArrayList<>();
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

    @RequiresPermission(DeletePermission.class)
    public class DeleteSelectedDataAction extends AbstractDeleteAction
    {
        public DeleteSelectedDataAction()
        {
            super();
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            // UNDONE: Need help topic on Datas
            setHelpTopic("experiment");
            return super.appendNavTrail(root);
        }

        protected void deleteObjects(DeleteForm deleteForm) throws Exception
        {
            List<ExpData> datas = getDatas(deleteForm, true);

            for (ExpRun run : getRuns(datas))
            {
                if (!run.getContainer().hasPermission(getUser(), DeletePermission.class))
                    throw new UnauthorizedException();
            }

            // Issue 32076: Delete the exp.Data objects using QueryUpdateService so trigger scripts will be executed
            Map<Optional<ExpDataClass>, List<ExpData>> byDataClass = datas.stream().collect(Collectors.groupingBy(d -> Optional.ofNullable(d.getDataClass())));
            for (Optional<ExpDataClass> opt : byDataClass.keySet())
            {
                SchemaKey schemaKey;
                String queryName;
                ExpDataClass dc = opt.orElse(null);
                List<ExpData> ds = byDataClass.get(opt);
                if (dc == null)
                {
                    // Reference to exp.Data table
                    schemaKey = SchemaKey.fromParts(ExpSchema.SCHEMA_NAME);
                    queryName = ExpSchema.TableType.Data.name();
                }
                else
                {
                    // Reference to exp.data.<DataClass> table
                    schemaKey = SchemaKey.fromParts(ExpSchema.SCHEMA_NAME, ExpSchema.NestedSchemas.data.name());
                    queryName = dc.getName();
                }

                UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), schemaKey);
                if (schema == null)
                    throw new IllegalStateException("Failed to get schema '" + schemaKey + "'");

                TableInfo table = schema.getTable(queryName);
                if (table == null)
                    throw new IllegalStateException("Failed to get table '" + queryName + "' in schema '" + schemaKey + "'");

                QueryUpdateService qus = table.getUpdateService();
                if (qus == null)
                    throw new IllegalStateException();

                qus.deleteRows(getUser(), getContainer(), toKeys(ds), null, null);
            }
        }

        protected List<Map<String, Object>> toKeys(List<ExpData> datas)
        {
            return datas.stream().map(d -> CaseInsensitiveHashMap.<Object>of("rowId", d.getRowId())).collect(Collectors.toList());
        }

        public ModelAndView getView(DeleteForm deleteForm, boolean reshow, BindException errors) throws Exception
        {
            if (errors.hasErrors())
                return new SimpleErrorView(errors, false);

            List<ExpData> datas = getDatas(deleteForm, false);
            List<ExpRun> runs = getRuns(datas);

            return new ConfirmDeleteView("Data", ShowDataAction.class, datas, deleteForm, runs);
        }

        private List<ExpRun> getRuns(List<ExpData> datas)
                throws SQLException
        {
            List<ExpRun> runsToDelete = new ArrayList<>();
            List<? extends ExpRun> runArray = ExperimentService.get().getRunsUsingDatas(datas);
            for (ExpRun run : ExperimentService.get().runsDeletedWithInput(runArray))
                runsToDelete.add(run);

            return runsToDelete;
        }

        private List<ExpData> getDatas(DeleteForm deleteForm, boolean clear)
        {
            List<ExpData> datas = new ArrayList<>();
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

    @RequiresPermission(DeletePermission.class)
    public class DeleteSelectedExperimentsAction extends AbstractDeleteAction
    {
        public DeleteSelectedExperimentsAction()
        {
            super();
        }

        protected void deleteObjects(DeleteForm deleteForm) throws ExperimentException, ServletException
        {
            for (ExpExperiment exp : lookupExperiments(deleteForm))
            {
                exp.delete(getUser());
            }
        }

        public ModelAndView getView(DeleteForm deleteForm, boolean reshow, BindException errors) throws Exception
        {
            List<ExpExperiment> experiments = lookupExperiments(deleteForm);

            List<ExpRun> runs = new ArrayList<>();
            boolean allBatches = true;
            for (ExpExperiment experiment : experiments)
            {
                // Deleting a batch also deletes all of its runs
                if (experiment.getBatchProtocol() != null)
                {
                    runs.addAll(experiment.getRuns());
                }
                else
                {
                    allBatches = false;
                }
            }

            return new ConfirmDeleteView(allBatches ? "batch" : "run group", DetailsAction.class, experiments, deleteForm, runs);
        }

        private List<ExpExperiment> lookupExperiments(DeleteForm deleteForm)
        {
            List<ExpExperiment> experiments = new ArrayList<>();
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

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic("runGroups");
            return super.appendNavTrail(root);
        }
    }

    @RequiresPermission(DeletePermission.class)
    public class DeleteMaterialSourceAction extends AbstractDeleteAction
    {
        public DeleteMaterialSourceAction()
        {
            super();
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic("sampleSets");
            return super.appendNavTrail(root);
        }

        protected void deleteObjects(DeleteForm deleteForm) throws SQLException, ExperimentException, ServletException
        {
            List<ExpSampleSet> sampleSets = getSampleSets(deleteForm);
            if (!ensureCorrectContainer(sampleSets))
            {
                throw new UnauthorizedException();
            }
            for (ExpRun run : getRuns(sampleSets))
            {
                if (!run.getContainer().hasPermission(getUser(), DeletePermission.class))
                {
                    throw new UnauthorizedException();
                }
            }
            for (ExpSampleSet source : sampleSets)
            {
                source.delete(getUser());
            }
            String selectionKey = deleteForm.getDataRegionSelectionKey();
            if (selectionKey != null)
            {
                DataRegionSelection.clearAll(getViewContext(), selectionKey);
            }
        }

        public ModelAndView getView(DeleteForm deleteForm, boolean reshow, BindException errors) throws Exception
        {
            List<ExpSampleSet> sampleSets = getSampleSets(deleteForm);
            ExpSampleSet defaultSampleSet = ExperimentService.get().ensureDefaultSampleSet();
            if (sampleSets.contains(defaultSampleSet))
            {
                throw new RedirectException(ExperimentUrlsImpl.get().getShowSampleSetListURL(getContainer(), "You cannot delete the default sample set."));
            }


            if (!ensureCorrectContainer(sampleSets))
            {
                throw new RedirectException(ExperimentUrlsImpl.get().getShowSampleSetListURL(getContainer(), "To delete a sample set, you must be in its folder or project."));
            }

            return new ConfirmDeleteView("Sample Set", ShowMaterialSourceAction.class, sampleSets, deleteForm, getRuns(sampleSets));
        }

        private List<ExpSampleSet> getSampleSets(DeleteForm deleteForm)
        {
            List<ExpSampleSet> sources = new ArrayList<>();
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

        private List<? extends ExpRun> getRuns(List<ExpSampleSet> sampleSets)
        {
            if (sampleSets.size() > 0)
            {
                List<? extends ExpRun> runArray = ExperimentService.get().getRunsUsingSampleSets(sampleSets.toArray(new ExpSampleSet[sampleSets.size()]));
                return ExperimentService.get().runsDeletedWithInput(runArray);
            }
            else
            {
                return Collections.emptyList();
            }
        }
    }

    @RequiresPermission(UpdatePermission.class)
    public class ShowUpdateMaterialSourceAction extends SimpleViewAction<MaterialSourceForm>
    {
        private ExpSampleSet _sampleSet;

        public ModelAndView getView(MaterialSourceForm form, BindException errors) throws Exception
        {
            try
            {
                _sampleSet = ExperimentService.get().getSampleSet(form.getBean().getRowId());
            }
            catch (ConversionException e)
            {
                throw new NotFoundException("No matching sample set");
            }
            if (_sampleSet == null)
            {
                throw new NotFoundException("No matching sample set with RowId " + form.getBean().getRowId());
            }

            if (_sampleSet.equals(ExperimentService.get().ensureDefaultSampleSet()))
            {
                throw new UnauthorizedException("Cannot edit default sample set");
            }

            if (!_sampleSet.getContainer().equals(getContainer()))
            {
                ActionURL url = getViewContext().getActionURL().clone();
                url.setContainer(_sampleSet.getContainer());
                throw new RedirectException(url);
            }

            UpdateView updateView = new UpdateView(getMaterialSourceRegion(getViewContext(), false), form, errors);
            if (form.getReturnUrl() != null)
            {
                updateView.getDataRegion().addHiddenFormField(ActionURL.Param.returnUrl, form.getReturnUrl().toString());
            }
            return updateView;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic("sampleSets");
            return appendRootNavTrail(root).addChild("Sample Sets", ExperimentUrlsImpl.get().getShowSampleSetListURL(getContainer())).addChild("Sample Set " + _sampleSet.getName());
        }
    }

    private DataRegion getMaterialSourceRegion(ViewContext model, boolean detailsView) throws Exception
    {
        TableInfo tableInfo = ExperimentServiceImpl.get().getTinfoMaterialSource();

        QuerySettings settings = new QuerySettings(model, "MaterialsSource");
        settings.setSelectionKey(DataRegionSelection.getSelectionKey(tableInfo.getSchema().getName(), tableInfo.getName(), "SampleSets", settings.getDataRegionName()));

        DataRegion dr = new DataRegion();
        dr.setSettings(settings);
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
        bb.setStyle(ButtonBar.Style.separateButtons);

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


    @RequiresPermission(InsertPermission.class)
    public class ShowInsertMaterialSourceAction extends SimpleViewAction<MaterialSourceForm>
    {
        public ModelAndView getView(MaterialSourceForm form, BindException errors) throws Exception
        {
            return new InsertView(getMaterialSourceRegion(getViewContext(), false), form, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic("sampleSets");
            return appendRootNavTrail(root).addChild("Sample Sets", ExperimentUrlsImpl.get().getShowSampleSetListURL(getContainer())).addChild("Insert Sample Set");
        }
    }

    @RequiresPermission(UpdatePermission.class)
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
                throw new NotFoundException("MaterialSource with LSID " + _source.getLSID());
            }
            Table.update(getUser(), ExperimentService.get().getTinfoMaterialSource(), form.getTypedValues(), _source.getRowId());
            ExperimentServiceImpl.get().clearCaches();
            return true;
        }

        public ActionURL getSuccessURL(MaterialSourceForm form)
        {
            setHelpTopic("sampleSets");
            return form.getReturnActionURL(ExperimentUrlsImpl.get().getShowSampleSetURL(ExperimentService.get().getSampleSet(_source.getRowId())));
        }
    }

    public static class MaterialSourceForm extends BeanViewForm<MaterialSource>
    {
        public MaterialSourceForm()
        {
            super(MaterialSource.class, ExperimentService.get().getTinfoMaterialSource());
        }
    }

    @RequiresPermission(UpdatePermission.class)
    public class ShowUploadMaterialsAction extends FormViewAction<UploadMaterialSetForm>
    {
        ExpSampleSetImpl _ss;
        ExpSampleSetImpl _newss;

        @Override
        public void validateCommand(UploadMaterialSetForm form, Errors errors)
        {
            if (StringUtils.isEmpty(form.getName()) || form.getName() == null)
                errors.reject(ERROR_MSG, "You must supply a name for the sample set");

            if (form.isImportMoreSamples() && form.getInsertUpdateChoice() == null)
                errors.reject(ERROR_MSG, "Please select how to deal with duplicates.");

            // 11138: IAE in org.labkey.api.reader.AbstractTabLoader.<init>()
            if (StringUtils.isEmpty(form.getData()) && StringUtils.isEmpty(form.getTsvData()))
                errors.reject(ERROR_MSG, "Please paste data into the text field or upload a tsv.");
        }

        @Override
        public ModelAndView getView(UploadMaterialSetForm form, boolean reshow, BindException errors) throws Exception
        {
            return new JspView<>("/org/labkey/experiment/uploadMaterials.jsp", form, errors);
        }

        @Override
        public boolean handlePost(UploadMaterialSetForm form, BindException errors) throws Exception
        {
            try
            {
                _ss = form.getSampleSet();

                // TODO: how to get this FormattedError into the validate command?
                if (!form.isImportMoreSamples() && null != _ss)
                {
                    errors.addError(new FormattedError("A sample set with that name already exists.  If you would like to import samples that set, go here:  " +
                            "<a href=" + getViewContext().getActionURL() + "name=" + form.getName() + "&importMoreSamples=true>Import More Samples</a>"));
                }

                if (!errors.hasErrors())
                {
                    try
                    {
                        UploadSamplesHelper helper = new UploadSamplesHelper(form, _ss == null ? null : _ss.getDataObject());
                        Pair<MaterialSource, List<ExpMaterial>> pair = helper.uploadMaterials();
                        MaterialSource newSource = pair.first;

                        ExpSampleSetImpl activeSampleSet = ExperimentServiceImpl.get().lookupActiveSampleSet(getContainer());
                        ExpSampleSetImpl newSampleSet = ExperimentServiceImpl.get().getSampleSet(newSource.getRowId());

                        if (activeSampleSet == null)
                        {
                            ExperimentService.get().setActiveSampleSet(getContainer(), newSampleSet);
                        }
                        _newss = newSampleSet;
                    }
                    catch (ExperimentException | IOException | ValidationException e)
                    {
                        errors.reject(ERROR_MSG, e.getMessage());
                    }
                }
            }
            finally
            {
                // Success or failure, clear the sample set cache as the object may have been mutated - issue 27407
                ExperimentService.get().clearCaches();
            }

            return !errors.hasErrors();
        }

        @Override
        public URLHelper getSuccessURL(UploadMaterialSetForm form)
        {
            return form.getReturnActionURL(_newss != null ? ExperimentUrlsImpl.get().getShowSampleSetURL(_newss) : getContainer().getStartURL(getUser()));
        }

        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic("sampleSets");
            NavTree nav = appendRootNavTrail(root).addChild("Sample Sets", ExperimentUrlsImpl.get().getShowSampleSetListURL(getContainer()));
            if (_ss != null)
                nav.addChild(_ss.getName(), ExperimentUrlsImpl.get().getShowSampleSetURL(_ss));
            nav.addChild("Import Sample Set");
            return nav;
        }
    }

    @RequiresPermission(UpdatePermission.class)
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
            else if (getViewContext().getRequest().getParameter("RowId") != null)
            {
                rowId = getViewContext().getRequest().getParameter("RowId");
            }
            else
            {
                Set<String> selectedIds = DataRegionSelection.getSelected(getViewContext(), true);
                if (selectedIds.size() == 1)
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

    @RequiresPermission(InsertPermission.class)
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
            if (!PipelineService.get().hasValidPipelineRoot(getContainer()))
            {
                return new NoPipelineRootSetView(getContainer(), "upload a XAR");
            }

            return new JspView<>("/org/labkey/experiment/addXarFile.jsp", null, errors);
        }

        public boolean handlePost(Object o, BindException errors) throws Exception
        {
            if (!(getViewContext().getRequest() instanceof MultipartHttpServletRequest))
                throw new BadRequestException(HttpServletResponse.SC_BAD_REQUEST, "Expected MultipartHttpServletRequest when posting files.", null);

            if (!PipelineService.get().hasValidPipelineRoot(getContainer()))
            {
                return false;
            }

            MultipartFile formFile = getFileMap().get("uploadFile");
            if (formFile == null)
            {
                errors.addError(new LabKeyError("No file was posted by the browser."));
                return false;
            }

            byte[] bytes = formFile.getBytes();
            if (bytes.length == 0)
            {
                errors.addError(new LabKeyError("No file was posted by the browser."));
                return false;
            }

            PipeRoot pipeRoot = PipelineService.get().findPipelineRoot(getContainer());
            File systemDir = pipeRoot.ensureSystemDirectory();
            File uploadDir = new File(systemDir, "UploadedXARs");
            uploadDir.mkdirs();
            if (!uploadDir.isDirectory())
            {
                errors.addError(new LabKeyError("Unable to create a 'system/UploadedXARs' directory under the pipeline root"));
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
                errors.addError(new LabKeyError("Unable to create an 'UploadedXARs/" + userDirName + "' directory under the pipeline root"));
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
                errors.addError(new LabKeyError("Unable to write uploaded XAR file to " + xarFile.getPath()));
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
                    "Uploaded file", true, pipeRoot);
            PipelineService.get().queueJob(job);

            return true;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendRootNavTrail(root).addChild("Upload a .xar or .xar.xml file from your browser");
        }
    }

    @RequiresPermission(UpdatePermission.class)
    public class ShowUpdateAction extends SimpleViewAction<ExperimentForm>
    {
        public ModelAndView getView(ExperimentForm form, BindException errors) throws Exception
        {
            form.refreshFromDb();
            Experiment exp = form.getBean();
            if (exp == null)
            {
                throw new NotFoundException();
            }
            ensureCorrectContainer(getContainer(), ExperimentService.get().getExpExperiment(exp.getRowId()), getViewContext());

            return new ExperimentUpdateView(new DataRegion(), form, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic("runGroups");
            return appendRootNavTrail(root).addChild("Update Run Group");
        }
    }

    @RequiresPermission(UpdatePermission.class)
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
        private Integer _sampleSetId;
        private int[] _dataIds;
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

        public Integer getSampleSetId()
        {
            return _sampleSetId;
        }

        public void setSampleSetId(Integer sampleSetId)
        {
            _sampleSetId = sampleSetId;
        }

        public int[] getDataIds()
        {
            return _dataIds;
        }

        public void setDataIds(int[] dataIds)
        {
            _dataIds = dataIds;
        }

        public List<ExpProtocol> lookupProtocols(ViewContext context, boolean clearSelection)
        {
            List<ExpProtocol> protocols = new ArrayList<>();

            if (_protocolId != null)
            {
                ExpProtocol protocol = ExperimentService.get().getExpProtocol(_protocolId.intValue());
                if (protocol == null || !protocol.getContainer().equals(context.getContainer()))
                {
                    throw new NotFoundException();
                }
                protocols.add(protocol);
                return protocols;
            }

            for (Integer protocolId : DataRegionSelection.getSelectedIntegers(context, clearSelection))
            {
                try
                {
                    ExpProtocol protocol = ExperimentService.get().getExpProtocol(protocolId);
                    if (protocol == null || !protocol.getContainer().equals(context.getContainer()))
                    {
                        throw new NotFoundException();
                    }
                    protocols.add(protocol);
                }
                catch (NumberFormatException e)
                {
                    throw new NotFoundException("Invalid protocol id: " + protocolId);
                }
            }
            if (protocols.isEmpty())
            {
                throw new NotFoundException("No protocols selected");
            }
            return protocols;
        }
    }

    private ActionURL exportXAR(XarExportSelection selection, String lsidRelativizerName, String exportTypeName, String fileName)
            throws SQLException, ExperimentException, ServletException, IOException, PipelineValidationException
    {
        final LSIDRelativizer lsidRelativizer;
        final XarExportType exportType;
        if (lsidRelativizerName == null)
        {
            lsidRelativizer = LSIDRelativizer.FOLDER_RELATIVE;
        }
        else
        {
            try
            {
                lsidRelativizer = LSIDRelativizer.valueOf(lsidRelativizerName);
            }
            catch (IllegalArgumentException e)
            {
                throw new NotFoundException("No such LSID relativizer available: " + lsidRelativizerName);
            }
        }
        if (exportTypeName == null)
        {
            exportType = XarExportType.BROWSER_DOWNLOAD;
        }
        else
        {
            try
            {
                exportType = XarExportType.valueOf(exportTypeName);
            }
            catch (IllegalArgumentException e)
            {
                throw new NotFoundException("No such export type available: " + exportTypeName);
            }
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
                XarExporter exporter = new XarExporter(lsidRelativizer, selection, getUser(), xarXmlFileName, null);

                getViewContext().getResponse().setContentType("application/zip");
                getViewContext().getResponse().setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
                getViewContext().getResponse().setHeader("Pragma", "private");
                getViewContext().getResponse().setHeader("Cache-Control", "private");

                exporter.write(getViewContext().getResponse().getOutputStream());
                return null;
            case PIPELINE_FILE:
                if (!PipelineService.get().hasValidPipelineRoot(getContainer()))
                {
                    throw new IllegalStateException("You must set a valid pipeline root before you can export a XAR to it.");
                }
                PipeRoot pipeRoot = PipelineService.get().findPipelineRoot(getContainer());
                XarExportPipelineJob job = new XarExportPipelineJob(getViewBackgroundInfo(), pipeRoot, fileName, lsidRelativizer, selection, xarXmlFileName);
                PipelineService.get().queueJob(job);
                return getContainer().getStartURL(getUser());
            default:
                throw new IllegalArgumentException("Unknown export type: " + exportType);
        }
    }

    @RequiresPermission(ReadPermission.class)
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

        public ModelAndView getView(ExportOptionsForm form, boolean reshow, BindException errors) throws Exception
        {
            handlePost(form, errors);
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class ExportRunsAction extends AbstractExportAction
    {
        public boolean handlePost(ExportOptionsForm form, BindException errors) throws Exception
        {
            Set<Integer> runIds = DataRegionSelection.getSelectedIntegers(getViewContext(), true);
            if (runIds.isEmpty())
            {
                throw new NotFoundException();
            }

            try
            {
                for (int id : runIds)
                {
                    ExpRun run = ExperimentService.get().getExpRun(id);
                    if (run == null || !run.getContainer().hasPermission(getUser(), ReadPermission.class))
                    {
                        throw new NotFoundException("Could not find run " + id);
                    }
                }

                XarExportSelection selection = new XarExportSelection();
                if (form.getExpRowId() != null)
                {
                    ExpExperiment experiment = ExperimentService.get().getExpExperiment(form.getExpRowId());
                    if (experiment != null && !experiment.getContainer().hasPermission(getUser(), ReadPermission.class))
                    {
                        throw new NotFoundException("Run group " + form.getExpRowId());
                    }
                    selection.addExperimentIds(experiment.getRowId());
                }
                selection.addRunIds(runIds);

                _resultURL = exportXAR(selection, form.getLsidOutputType(), form.getExportType(), form.getXarFileName());
                return true;
            }
            catch (NumberFormatException e)
            {
                throw new NotFoundException(runIds.toString());
            }
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class ExportSampleSetAction extends AbstractExportAction
    {
        public boolean handlePost(ExportOptionsForm form, BindException errors) throws Exception
        {
            Integer rowId = form.getSampleSetId();
            if (rowId == null)
            {
                throw new NotFoundException("No sampleSetId specified");
            }
            ExpSampleSet sampleSet = ExperimentService.get().getSampleSet(rowId.intValue());
            if (sampleSet == null)
            {
                throw new NotFoundException("No such sample set with RowId " + rowId);
            }
            if (!sampleSet.getContainer().hasPermission(getUser(), ReadPermission.class))
            {
                throw new UnauthorizedException();
            }

            XarExportSelection selection = new XarExportSelection();
            selection.addSampleSet(sampleSet);

            _resultURL = exportXAR(selection, form.getLsidOutputType(), form.getExportType(), FileUtil.makeLegalName(sampleSet.getName() + ".xar"));
            return true;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class ExportRunFilesAction extends AbstractExportAction
    {
        public boolean handlePost(ExportOptionsForm form, BindException errors) throws Exception
        {
            Set<Integer> runIds = DataRegionSelection.getSelectedIntegers(getViewContext(), true);
            if (runIds.isEmpty())
            {
                throw new NotFoundException();
            }

            try
            {
                for (int id : runIds)
                {
                    ExpRun run = ExperimentService.get().getExpRun(id);
                    if (run == null || !run.getContainer().hasPermission(getUser(), ReadPermission.class))
                    {
                        throw new NotFoundException("Could not find run " + id);
                    }
                }

                XarExportSelection selection = new XarExportSelection();
                selection.setIncludeXarXml(false);
                if ("role".equalsIgnoreCase(form.getFileExportType()))
                {
                    selection.addRoles(form.getRoles());
                }
                selection.addRunIds(runIds);

                _resultURL = exportXAR(selection, null, null, form.getZipFileName());
                return true;
            }
            catch (NumberFormatException e)
            {
                throw new NotFoundException(runIds.toString());
            }
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class ExportFilesAction extends AbstractExportAction
    {
        public boolean handlePost(ExportOptionsForm form, BindException errors) throws Exception
        {
            int[] dataIds = form.getDataIds();
            if (dataIds == null || dataIds.length == 0)
            {
                throw new NotFoundException();
            }

            try
            {
                List<ExpData> files = new ArrayList<>();
                for (int id : dataIds)
                {
                    ExpData data = ExperimentService.get().getExpData(id);
                    if (data == null || !data.getContainer().hasPermission(getUser(), ReadPermission.class))
                    {
                        throw new NotFoundException("Could not find file " + id);
                    }
                    files.add(data);
                }

                XarExportSelection selection = new XarExportSelection();
                selection.setIncludeXarXml(false);
                selection.addDataIds(dataIds);

                _resultURL = exportXAR(selection, null, null, form.getZipFileName());
                return true;
            }
            catch (NumberFormatException e)
            {
                throw new NotFoundException(Arrays.toString(dataIds));
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

    private void addSelectedRunsToExperiment(ExpExperiment exp) throws SQLException
    {
        Collection<Integer> runIds = DataRegionSelection.getSelectedIntegers(getViewContext(), true);
        List<ExpRun> runs = new ArrayList<>();
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


    @RequiresPermission(InsertPermission.class)
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

    @RequiresPermission(DeletePermission.class)
    public class RemoveSelectedExpRunsAction extends FormHandlerAction<ExperimentRunListForm>
    {
        public void validateCommand(ExperimentRunListForm target, Errors errors)
        {
        }

        public boolean handlePost(ExperimentRunListForm form, BindException errors) throws Exception
        {
            ExpExperiment exp = form.lookupExperiment();
            if (exp == null || !exp.getContainer().hasPermission(getUser(), DeletePermission.class))
            {
                throw new NotFoundException("Could not find run group with RowId " + form.getExpRowId());
            }

            for (int runId : DataRegionSelection.getSelectedIntegers(getViewContext(), true))
            {
                ExpRun run = ExperimentService.get().getExpRun(runId);
                if (run == null || !run.getContainer().hasPermission(getUser(), DeletePermission.class))
                {
                    throw new NotFoundException("Could not find run with RowId " + runId);
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


    public static ActionURL getResolveLsidURL(Container c, @NotNull String type, @NotNull String lsid)
    {
        ActionURL url = new ActionURL(ResolveLSIDAction.class, c);
        url.addParameter("type", type);
        url.addParameter("lsid", lsid);

        return url;
    }


    @RequiresPermission(ReadPermission.class)
    public class ResolveLSIDAction extends SimpleViewAction<LsidForm>
    {
        public ModelAndView getView(LsidForm form, BindException errors) throws Exception
        {
            String message = "";
            if (!PageFlowUtil.empty(form.getLsid()))
            {
                String lsid = Lsid.canonical(form.getLsid());
                ActionURL url = LsidManager.get().getDisplayURL(lsid);
                if (url == null && form.getType() != null)
                {
                    switch (form.getType().toLowerCase())
                    {
                        case "data":
                            url = LsidType.Data.getDisplayURL(new Lsid(lsid));
                            break;
                        case "material":
                            url = LsidType.Material.getDisplayURL(new Lsid(lsid));
                            break;
                    }
                }
                if (null != url)
                {
                    throw new RedirectException(url);
                }

                message = "Could not map lsid to URL";
            }

            String html = message + "<form action=\"" + getViewContext().cloneActionURL().setAction(ResolveLSIDAction.class) + "\">" +
                    " Lsid <input type=text name=lsid size=\"80\" value=\"" +
                    (form.getLsid() == null ? "" : PageFlowUtil.filter(form.getLsid())) + "\">" +
                    PageFlowUtil.button("Go").submit(true) + "</form>";

            return new HtmlView("Enter LSID", html);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Resolve LSID");
        }
    }

    public static class LsidForm
    {
        private String _lsid;

        public String getType()
        {
            return _type;
        }

        public void setType(String type)
        {
            _type = type;
        }

        private String _type;

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
                throw new NotFoundException();
            ExpObject obj = ExperimentService.get().findObjectFromLSID(lsid);
            if (obj == null)
                throw new NotFoundException();
            Container container = obj.getContainer();
            if (!container.hasPermission(getUser(), UpdatePermission.class))
            {
                throw new UnauthorizedException();
            }

            if (!container.hasPermission(getUser(), UpdatePermission.class))
            {
                throw new UnauthorizedException();
            }
            obj.setComment(getUser(), form.getComment());

            if (form.isRedirect())
            {
                String url = obj.urlFlag(!StringUtils.isEmpty(form.getComment()));
                throw new RedirectException(url);
            }
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            throw new UnsupportedOperationException();
        }
    }

    @RequiresPermission(InsertPermission.class)
    public class DeriveSamplesChooseTargetAction extends SimpleViewAction<DeriveMaterialForm>
    {
        private List<ExpMaterial> _materials;

        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic("sampleSets");
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
                throw new NotFoundException("Could not find any matching materials");
            }
        }

        public ModelAndView getView(DeriveMaterialForm form, BindException errors) throws Exception
        {
            if (!_materials.get(0).getContainer().equals(getContainer()))
            {
                ActionURL redirectURL = getViewContext().cloneActionURL().setContainer(_materials.get(0).getContainer());
                throw new RedirectException(redirectURL);
            }

            Container c = getContainer();
            HttpView view;

            PipeRoot root = PipelineService.get().findPipelineRoot(c);
            if (root == null || !root.isValid())
            {
                ActionURL pipelineURL = PageFlowUtil.urlProvider(PipelineUrls.class).urlSetup(c);
                view = new HtmlView("You must <a href=\"" + pipelineURL + "\">configure a valid pipeline root for this folder</a> before deriving samples.");
            }
            else
            {
                Set<String> materialInputRoles = new TreeSet<>();
                materialInputRoles.addAll(ExperimentService.get().getMaterialInputRoles(getContainer()));
                Map<ExpMaterial, String> materialsWithRoles = new LinkedHashMap<>();
                for (ExpMaterial material : _materials)
                {
                    materialsWithRoles.put(material, null);
                }

                List<ExpSampleSet> sampleSets = getUploadableSampleSets();

                DeriveSamplesChooseTargetBean bean = new DeriveSamplesChooseTargetBean(sampleSets, materialsWithRoles, form.getOutputCount(), materialInputRoles, null);
                view = new JspView<>("/org/labkey/experiment/deriveSamplesChooseTarget.jsp", bean);
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

    private ActionButton getDeriveSamplesButton()
    {
        ActionURL urlDeriveSamples = new ActionURL(DeriveSamplesChooseTargetAction.class, getContainer());
        ActionButton deriveButton = new ActionButton(urlDeriveSamples, "Derive Samples");
        deriveButton.setActionType(ActionButton.Action.POST);
        deriveButton.setDisplayPermission(InsertPermission.class);
        deriveButton.setRequiresSelection(true);
        return deriveButton;
    }

    private List<ExpSampleSet> getUploadableSampleSets()
    {
        // Make a copy so we can modify it
        List<ExpSampleSet> sampleSets = new ArrayList<>(ExperimentService.get().getSampleSets(getContainer(), getUser(), true));
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

    @RequiresPermission(InsertPermission.class)
    public class DescribeDerivedSamplesAction extends SimpleViewAction<DeriveMaterialForm>
    {
        List<ExpMaterial> _materials;

        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic("sampleSets");
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
                throw new NotFoundException("Could not find any matching materials");
            }
        }

        public ModelAndView getView(DeriveMaterialForm form, BindException errors) throws Exception
        {
            Container c = getContainer();

            if (form.getOutputCount() <= 0)
            {
                form.setOutputCount(1);
            }

            ExpSampleSet sampleSet = ExperimentService.get().getSampleSet(form.getTargetSampleSetId());
            if (form.getTargetSampleSetId() != 0 && sampleSet == null)
            {
                throw new NotFoundException("Could not find sample set with rowId " + form.getTargetSampleSetId());
            }

            InsertView insertView = new InsertView(new DataRegion(), errors);

            DerivedSamplePropertyHelper helper = new DerivedSamplePropertyHelper(sampleSet, form.getOutputCount(), c, getUser());
            helper.addSampleColumns(insertView, getUser());

            int[] rowIds = form.getRowIds();
            for (int i = 0; i < rowIds.length; i++)
            {
                insertView.getDataRegion().addHiddenFormField("rowIds", Integer.toString(rowIds[i]));
                insertView.getDataRegion().addHiddenFormField("inputRole" + i, form.getInputRole(i) == null ? "" : form.getInputRole(i));
                insertView.getDataRegion().addHiddenFormField("customRole" + i, form.getCustomRole(i) == null ? "" : form.getCustomRole(i));
            }

            insertView.getDataRegion().addHiddenFormField("targetSampleSetId", Integer.toString(form.getTargetSampleSetId()));
            insertView.getDataRegion().addHiddenFormField("outputCount", Integer.toString(form.getOutputCount()));
            insertView.setInitialValues(ViewServlet.adaptParameterMap(getViewContext().getRequest().getParameterMap()));
            ButtonBar bar = new ButtonBar();
            bar.setStyle(ButtonBar.Style.separateButtons);
            ActionButton submitButton = new ActionButton(DeriveSamplesAction.class, "Submit");
            submitButton.setActionType(ActionButton.Action.POST);
            bar.add(submitButton);
            insertView.getDataRegion().setButtonBar(bar);
            insertView.setTitle("Output Samples");

            Map<ExpMaterial, String> materialsWithRoles = new LinkedHashMap<>();
            List<ExpMaterial> materials = form.lookupMaterials();
            for (int i = 0; i < materials.size(); i++)
            {
                materialsWithRoles.put(materials.get(i), form.determineLabel(i));
            }

            DeriveSamplesChooseTargetBean bean = new DeriveSamplesChooseTargetBean(getUploadableSampleSets(), materialsWithRoles, form.getOutputCount(), Collections.emptyList(), helper);
            JspView<DeriveSamplesChooseTargetBean> view = new JspView<>("/org/labkey/experiment/summarizeMaterialInputs.jsp", bean);
            view.setTitle("Input Samples");

            return new VBox(view, insertView);
        }
    }

    @RequiresPermission(InsertPermission.class)
    public class DeriveSamplesAction extends SimpleViewAction<DeriveMaterialForm>
    {
        private DescribeDerivedSamplesAction _action;

        public ModelAndView getView(DeriveMaterialForm form, BindException errors) throws Exception
        {
            List<ExpMaterial> materials = form.lookupMaterials();

            Map<ExpMaterial, String> inputMaterials = new LinkedHashMap<>();
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

            Map<ExpMaterial, String> outputMaterials = new HashMap<>();

            DerivedSamplePropertyHelper helper = new DerivedSamplePropertyHelper(sampleSet, form.getOutputCount(), getContainer(), getUser());

            Map<String, Map<DomainProperty, String>> allProperties;
            try
            {
                boolean valid = true;
                for (Map.Entry<String, Map<DomainProperty, String>> entry : helper.getPostedPropertyValues(getViewContext().getRequest()).entrySet())
                    valid = UploadWizardAction.validatePostedProperties(getViewContext(), entry.getValue(), errors) && valid;
                if (!valid)
                    return redirectError(form, errors);

                allProperties = helper.getSampleProperties(getViewContext().getRequest());
            }
            catch (DuplicateMaterialException e)
            {
                errors.addError(new ObjectError(ColumnInfo.propNameFromName(e.getColName()), null, null, ERROR_MSG + " " + e.getMessage()));
                return redirectError(form, errors);
            }
            catch (ExperimentException e)
            {
                errors.reject(SpringActionController.ERROR_MSG, e.getMessage());
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

            throw new RedirectException(ExperimentUrlsImpl.get().getShowSampleURL(getContainer(), outputMaterials.keySet().iterator().next()));
        }

        private ModelAndView redirectError(DeriveMaterialForm form, BindException errors)
                throws Exception
        {
            _action = new DescribeDerivedSamplesAction();
            _action.setViewContext(getViewContext());
            _action.setPageConfig(defaultPageConfig());
            return _action.getView(form, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic("sampleSets");
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
            List<ExpMaterial> result = new ArrayList<>();
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
                        throw new UnauthorizedException();
                    }
                }
                else
                {
                    throw new NotFoundException("No material with RowId " + rowId);
                }
            }
            result.sort(Comparator.comparing(Identifiable::getName));
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


    public static class ExpInput
    {
        public String role;
        public int rowId;
        public Lsid lsid;
    }

    public static class DerivationSpec
    {
        public String role;
        public Map<String, Object> values;
    }

    public static class DerivationForm
    {
        public List<ExpInput> dataInputs;
        public List<ExpInput> materialInputs;

        public int dataOutputCount;
        public Lsid targetDataClass;
        public Map<String, Object> dataDefault;
        public List<DerivationSpec> dataOutputs;

        public int materialOutputCount;
        public Lsid targetSampleSet;
        public Map<String, Object> materialDefault;
        public List<DerivationSpec> materialOutputs;
    }

    @Marshal(Marshaller.Jackson)
    @RequiresPermission(InsertPermission.class)
    public class DeriveAction extends MutatingApiAction<DerivationForm>
    {
        @Override
        public void validateForm(DerivationForm form, Errors errors)
        {
            if (errors.hasErrors())
                return;

            if (form.materialOutputCount > 0 && form.materialOutputs != null && !form.materialOutputs.isEmpty())
                errors.reject(ERROR_MSG, "Either 'materialOutputCount' or 'materialOutputs' property can be specified, but not both.");

            if (form.dataOutputCount > 0 && form.dataOutputs != null && !form.dataOutputs.isEmpty())
                errors.reject(ERROR_MSG, "Either 'dataOutputCount' or 'dataOutputs' property can be specified, but not both.");

            boolean hasMaterialOutputs = form.materialOutputCount > 0 || form.materialOutputs != null && !form.materialOutputs.isEmpty();
            boolean hasDataOutputs = form.dataOutputCount > 0 || form.dataOutputs != null && !form.dataOutputs.isEmpty();

            if (!hasMaterialOutputs && !hasDataOutputs)
                errors.reject(ERROR_MSG, "At least one data output or material output is required");

            if (hasMaterialOutputs && form.targetSampleSet == null)
                errors.reject(ERROR_MSG, "targetSampleSet lsid required for material outputs");

            if (hasDataOutputs && form.targetDataClass == null)
                errors.reject(ERROR_MSG, "targetDataClass lsid required for data outputs");
        }

        @Override
        public Object execute(DerivationForm form, BindException errors) throws Exception
        {
            // Find material inputs
            int unknownMaterialCount = 0;
            Map<ExpMaterial, String> materialInputs = new LinkedHashMap<>();
            if (form.materialInputs != null)
            {
                for (ExpInput in : form.materialInputs)
                {
                    ExpMaterial m = null;
                    if (in.lsid != null)
                    {
                        m = ExperimentService.get().getExpMaterial(in.lsid.toString());
                        if (m == null)
                            errors.reject(ERROR_MSG, "Can't resolve sample '" + in.lsid + "'");
                    }
                    else if (in.rowId > 0)
                    {
                        m = ExperimentService.get().getExpMaterial(in.rowId);
                        if (m == null)
                            errors.reject(ERROR_MSG, "Can't resolve sample '" + in.rowId + "'");
                    }

                    if (m == null)
                    {
                        errors.reject(ERROR_MSG, "Material input lsid or rowId required");
                        continue;
                    }

                    if (m.getSampleSet() == null)
                    {
                        errors.reject(ERROR_MSG, "Material input is not a member of a SampleSet");
                        continue;
                    }

                    // TODO: check within scope

                    String role = in.role;
                    if (role == null || "".equals(role))
                    {
                        role = "Material" + (unknownMaterialCount == 0 ? "" : Integer.toString(unknownMaterialCount + 1));
                        unknownMaterialCount++;
                    }
                    materialInputs.put(m, role);
                }
            }

            // Find input data
            int unknownDataCount = 0;
            Map<ExpData, String> dataInputs = new LinkedHashMap<>();
            if (form.dataInputs != null)
            {
                for (ExpInput in : form.dataInputs)
                {
                    ExpData d = null;
                    if (in.lsid != null)
                    {
                        d = ExperimentService.get().getExpData(in.lsid.toString());
                        if (d == null)
                            errors.reject(ERROR_MSG, "Can't resolve data '" + in.lsid + "'");
                    }
                    else if (in.rowId > 0)
                    {
                        d = ExperimentService.get().getExpData(in.rowId);
                        if (d == null)
                            errors.reject(ERROR_MSG, "Can't resolve data '" + in.rowId + "'");
                    }

                    if (d == null)
                    {
                        errors.reject(ERROR_MSG, "Data input lsid or rowId required");
                        continue;
                    }

                    if (d.getDataClass() == null)
                    {
                        errors.reject(ERROR_MSG, "Data input is not a member of a DataClass");
                        continue;
                    }

                    // TODO: check within scope
                    String role = in.role;
                    if (role == null || "".equals(role))
                    {
                        role = "Data" + (unknownDataCount == 0 ? "" : Integer.toString(unknownDataCount + 1));
                        unknownDataCount++;
                    }
                    dataInputs.put(d, role);
                }
            }

            ExpSampleSet outSampleSet;
            if (form.targetSampleSet != null)
            {
                // TODO: check in scope and has permission
                outSampleSet = ExperimentService.get().getSampleSet(form.targetSampleSet.toString());
                if (outSampleSet == null)
                    errors.reject(ERROR_MSG, "SampleSet not found: " + form.targetSampleSet.toString());
            }
            else
            {
                outSampleSet = null;
            }

            ExpDataClass outDataClass;
            if (form.targetDataClass != null)
            {
                // TODO: check in scope and has permission
                outDataClass = ExperimentService.get().getDataClass(form.targetDataClass.toString());
                if (outDataClass == null)
                    errors.reject(ERROR_MSG, "DataClass not found: " + form.targetDataClass.toString());
            }
            else
            {
                outDataClass = null;
            }

            if (errors.hasErrors())
                return null;

            // TODO: support list of resolved ExpData or ExpMaterial instead of string concatenated names
            // Create "MaterialInputs/<SampleSet>" columns with a value containing a comma-separated list of Material names
            final Map<String, String> parentInputNames = new HashMap<>();
            for (ExpMaterial material : materialInputs.keySet())
            {
                ExpSampleSet ss = material.getSampleSet();
                String keyName = UploadSamplesHelper.MATERIAL_INPUT_PARENT + "/" + ss.getName();
                parentInputNames.merge(keyName, material.getName(), (s1, s2) -> s1.concat(",").concat(s2));
            }

            // TODO: support list of resolved ExpData or ExpMaterial instead of string concatenated names
            // Create "DataInputs/<DataClass>" columns with a value containing a comma-separated list of ExpData names
            for (ExpData d : dataInputs.keySet())
            {
                ExpDataClass dc = d.getDataClass();
                String keyName = UploadSamplesHelper.DATA_INPUT_PARENT + "/" + dc.getName();
                parentInputNames.merge(keyName, d.getName(), (s1, s2) -> s1.concat(",").concat(s2));
            }


            try (DbScope.Transaction tx = ExperimentService.get().ensureTransaction())
            {

                // output materials
                Map<ExpMaterial, String> outputMaterials = new HashMap<>();
                int materialOutputCount = Math.max(form.materialOutputCount, form.materialOutputs != null ? form.materialOutputs.size() : 0);
                if (materialOutputCount > 0 && outSampleSet != null)
                {
                    DerivedOutputs<ExpMaterial> derived = new DerivedOutputs<ExpMaterial>(parentInputNames, form.materialDefault, form.materialOutputs, materialOutputCount, "Material")
                    {
                        @Override
                        protected TableInfo createTable()
                        {
                            SamplesSchema schema = new SamplesSchema(getUser(), getContainer());
                            return schema.getTable(outSampleSet.getName());
                        }

                        @Override
                        protected List<ExpMaterial> getExpObject(List<Map<String, Object>> insertedRows)
                        {
                            List<Integer> rowIds = insertedRows.stream().map(r -> (Integer)r.get("rowid")).collect(Collectors.toList());
                            List<? extends ExpMaterial> output = ExperimentService.get().getExpMaterials(rowIds);
                            return (List<ExpMaterial>)output;
                        }
                    };

                    outputMaterials = derived.createOutputs();
                }


                // create output data
                Map<ExpData, String> outputData = new HashMap<>();
                int dataOutputCount = Math.max(form.dataOutputCount, form.dataOutputs != null ? form.dataOutputs.size() : 0);
                if (dataOutputCount > 0 && outDataClass != null)
                {
                    DerivedOutputs<ExpData> derived = new DerivedOutputs<ExpData>(parentInputNames, form.dataDefault, form.dataOutputs, dataOutputCount, "Data")
                    {
                        @Override
                        protected TableInfo createTable()
                        {
                            ExpSchema expSchema = new ExpSchema(getUser(), getContainer());
                            UserSchema dataSchema = expSchema.getUserSchema(ExpSchema.NestedSchemas.data.name());
                            return dataSchema.getTable(outDataClass.getName());
                        }

                        @Override
                        protected List<ExpData> getExpObject(List<Map<String, Object>> insertedRows)
                        {
                            List<String> lsids = insertedRows.stream().map(r -> (String)r.get("lsid")).collect(Collectors.toList());
                            List<? extends ExpData> output = ExperimentService.get().getExpDatasByLSID(lsids);
                            return (List<ExpData>)output;
                        }
                    };

                    outputData = derived.createOutputs();
                }

                if (outputMaterials.isEmpty() && outputData.isEmpty())
                    throw new IllegalStateException("Expected to create " + materialOutputCount + " materials and " + dataOutputCount + " datas");

                // finally, create the derived run if there are any parents
                ExpRun run = null;
                if (!materialInputs.isEmpty() || !dataInputs.isEmpty())
                    run = ExperimentService.get().derive(materialInputs, dataInputs, outputMaterials, outputData, new ViewBackgroundInfo(getContainer(), getUser(), null), _log);
                tx.commit();

                StringBuilder successMessage = new StringBuilder("Created ");
                if (outputMaterials.size() > 0)
                    successMessage.append(outputMaterials.size()).append(" materials");
                if (outputData.size() > 0)
                    successMessage.append(outputData.size()).append(" data");

                JSONObject ret;
                if (run != null)
                    ret = ExperimentJSONConverter.serializeRun(run, null);
                else
                    ret = ExperimentJSONConverter.serializeRunOutputs(outputData.keySet(), outputMaterials.keySet());

                return success(successMessage.toString(), ret);
            }
        }

        // Helper class that prepares and executes the QueryUpdateService.insertRows() on the data or material table.
        private abstract class DerivedOutputs<T extends ExpProtocolOutput>
        {
            private final @NotNull Map<String, String> _parentInputNames;
            private final @Nullable Map<String, Object> _defaultValues;
            private final @Nullable List<DerivationSpec> _values;
            private final int _outputCount;
            private final String _rolePrefix;


            public DerivedOutputs(@NotNull Map<String, String> parentInputNames, @Nullable Map<String, Object> defaultValues, @Nullable List<DerivationSpec> values, int outputCount, String rolePrefix)
            {
                _parentInputNames = parentInputNames;
                _defaultValues = defaultValues;
                _values = values;
                _outputCount = outputCount;
                _rolePrefix = rolePrefix;
            }

            public Pair<List<Map<String, Object>>, List<String>> prepareRows()
                    throws ExperimentException
            {
                List<Map<String, Object>> rows = new ArrayList<>();
                List<String> roles = new ArrayList<>();
                int unknownOutputDataCount = 0;

                for (int i = 0; i < _outputCount; i++)
                {
                    Map<String, Object> row = new CaseInsensitiveHashMap<>();
                    if (_defaultValues != null)
                        row.putAll(_defaultValues);
                    DerivationSpec spec = _values != null && i < _values.size() ? _values.get(i) : null;
                    String role = null;
                    if (spec != null)
                    {
                        row.putAll(spec.values);
                        role = spec.role;
                    }

                    // NOTE: Input parents are added to each row, but are only used for name generation and not for derivation.
                    // NOTE: We will derive the inserted samples in a single derivation run after the sample/date have been inserted.
                    row.putAll(_parentInputNames);

                    rows.add(row);

                    if (role == null || "".equals(role))
                    {
                        role = _rolePrefix + (unknownOutputDataCount == 0 ? "" : Integer.toString(unknownOutputDataCount + 1));
                        unknownOutputDataCount++;
                    }
                    roles.add(role);
                }
                return Pair.of(rows, roles);
            }

            protected abstract TableInfo createTable();

            protected abstract List<T> getExpObject(List<Map<String, Object>> insertedRows);

            public Map<T, String> createOutputs() throws ExperimentException, BatchValidationException, DuplicateKeyException, SQLException, QueryUpdateServiceException
            {
                Pair<List<Map<String, Object>>, List<String>> pair = prepareRows();
                List<Map<String, Object>> rows = pair.first;
                List<String> roles = pair.second;

                TableInfo table = createTable();
                QueryUpdateService qus = table.getUpdateService();
                if (qus == null)
                    throw new IllegalStateException();

                Map<Enum, Object> configParams = new HashMap<>();
                configParams.put(SampleSetUpdateService.Options.AddUniqueSuffixForDuplicateNames, true);
                // Skip derivation during insert -- DeriveAction will call ExperimentService.get().derive() after samples are inserted
                configParams.put(SampleSetUpdateService.Options.SkipDerivation, true);

                BatchValidationException qusErrors = new BatchValidationException();
                List<Map<String, Object>> insertedRows = qus.insertRows(getUser(), getContainer(), rows, qusErrors, configParams, null);
                if (qusErrors.hasErrors())
                    throw qusErrors;

                if (insertedRows.size() != roles.size())
                    throw new IllegalStateException("Expected to create " + roles.size() + " new exp objects for derivation");

                List<T> outputs = getExpObject(insertedRows);
                if (outputs.size() != roles.size())
                    throw new IllegalStateException("Expected to create " + roles.size() + " new exp objects for derivation");

                Map<T, String> outputMap = new HashMap<>();
                for (int i = 0; i < outputs.size(); i++)
                {
                    String role = roles.get(i);
                    T data = outputs.get(i);
                    outputMap.put(data, role);
                }

                return outputMap;
            }
        }
    }

    public static class CreateExperimentForm extends ExperimentForm implements DataRegionSelection.DataSelectionKeyForm
    {
        private boolean _addSelectedRuns;
        private String _dataRegionSelectionKey;

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

    @RequiresPermission(InsertPermission.class) @ActionNames("createRunGroup, createExperiment")
    public class CreateRunGroupAction extends SimpleViewAction<CreateExperimentForm>
    {
        public ModelAndView getView(CreateExperimentForm form, BindException errors) throws Exception
        {
            // HACK - convert ExperimentForm to not be a BeanViewForm
            form.setAddSelectedRuns("true".equals(getViewContext().getRequest().getParameter("addSelectedRuns")));
            form.setDataRegionSelectionKey(getViewContext().getRequest().getParameter(DataRegionSelection.DATA_REGION_SELECTION_KEY));
            if ("POST".equalsIgnoreCase(getViewContext().getRequest().getMethod()) && !"true".equals(getViewContext().getRequest().getParameter("noPost")))
            {
                Experiment exp = form.getBean();
                if (exp.getName() == null || exp.getName().trim().length() == 0)
                {
                    errors.reject(ERROR_MSG, "You must specify a name for the experiment");
                }
                else
                {
                    int maxNameLength = ExperimentService.get().getTinfoExperimentRun().getColumn("Name").getScale();
                    if(exp.getName().length() > maxNameLength)
                    {
                        errors.reject(ERROR_MSG, "Name of the experiment must be " + maxNameLength + " characters or less.");
                    }
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

                    if (form.getReturnUrl() != null)
                    {
                        throw new RedirectException(form.getReturnUrl());
                    }
                    throw new RedirectException(ExperimentUrlsImpl.get().getShowExperimentsURL(getContainer()));
                }
            }

            DataRegion drg = new DataRegion();

            drg.addHiddenFormField(ActionURL.Param.returnUrl, getViewContext().getRequest().getParameter(ActionURL.Param.returnUrl.name()));
            drg.addHiddenFormField("addSelectedRuns", java.lang.Boolean.toString("true".equals(getViewContext().getRequest().getParameter("addSelectedRuns"))));
            form.setDataRegionSelectionKey(getViewContext().getRequest().getParameter(DataRegionSelection.DATA_REGION_SELECTION_KEY));
            // Fix issue 27562 - include session-stored selection
            if (form.getDataRegionSelectionKey() != null)
            {
                for (String rowId : DataRegionSelection.getSelected(getViewContext(), form.getDataRegionSelectionKey(), true, false))
                {
                    drg.addHiddenFormField(DataRegion.SELECT_CHECKBOX_NAME, rowId);
                }
            }
            drg.addHiddenFormField(DataRegionSelection.DATA_REGION_SELECTION_KEY, getViewContext().getRequest().getParameter(DataRegionSelection.DATA_REGION_SELECTION_KEY));

            drg.addColumns(ExperimentServiceImpl.get().getTinfoExperiment(), "RowId,Name,LSID,ContactId,ExperimentDescriptionURL,Hypothesis,Comments,Created");

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
            setHelpTopic("runGroups");
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

    @RequiresPermission(DeletePermission.class)
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
            JspView<MoveRunsBean> result = new JspView<>("/org/labkey/experiment/moveRunsLocation.jsp", bean);
            result.setTitle("Choose Destination Folder");
            result.setFrame(WebPartView.FrameType.PORTAL);
            return result;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Move Runs");
        }
    }



    @RequiresPermission(DeletePermission.class)
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
                throw new UnauthorizedException();
            }

            Set<String> runIds = DataRegionSelection.getSelected(getViewContext(), true);
            List<ExpRun> runs = new ArrayList<>();
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
                catch (NumberFormatException ignored) {}
            }

            ViewBackgroundInfo info = getViewBackgroundInfo();
            info.setContainer(_targetContainer);

            try
            {
                ExperimentService.get().moveRuns(info, getContainer(), runs);
            }
            catch (IOException e)
            {
                throw new NotFoundException("Failed to initialize move. Check that the pipeline root is configured correctly. " + e);
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

    @RequiresPermission(ReadPermission.class)
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

            PageFlowUtil.streamFile(getViewContext().getResponse(), new File(f.getAbsolutePath()), false);
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    // TODO: DotGraph has been adding a "runId" parameter, but ShowGraphMoreListAction
    public static ActionURL getShowGraphMoreListURL(Container c, @Nullable Integer runId, @NotNull String objtype)
    {
        ActionURL url = new ActionURL(ShowGraphMoreListAction.class, c);

        if (null != runId)
            url.addParameter("runId", runId);

        url.addParameter("objtype", objtype);

        return url;
    }


    @RequiresPermission(ReadPermission.class)
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
            root.addChild(new NavTree("Experiments", ExperimentUrlsImpl.get().getShowExperimentsURL(getContainer())));
            ExpRun run = ExperimentService.get().getExpRun(_form.getRowId());
            if (run != null)
            {
                root.addChild(new NavTree("Experiment Run", ExperimentUrlsImpl.get().getRunGraphURL(_form.lookupRun())));
            }
            root.addChild(new NavTree("Selected Protocol Applications"));
            return root;
        }
    }

    @RequiresPermission(InsertPermission.class)
    public class ImportXarFileAction extends SimpleViewAction<ImportXarForm>
    {
        public ModelAndView getView(ImportXarForm form, BindException errors) throws Exception
        {
            for (File f : form.getValidatedFiles(getContainer()))
            {
                if (f.isFile())
                {
                    ExperimentPipelineJob job = new ExperimentPipelineJob(getViewBackgroundInfo(), f, "Experiment Import", false, form.getPipeRoot(getContainer()));

                    // TODO: Configure module resources with the appropriate log location per container
                    if (form.getModule() != null)
                    {
                        File logFile = new File(form.getPipeRoot(getContainer()).getRootPath(), "module-resource-xar.log");
                        job.setLogFile(logFile);
                    }

                    PipelineService.get().queueJob(job);
                }
                else
                {
                    throw new NotFoundException("Expected a file but found a directory: " + f.getName());
                }
            }

            return HttpView.redirect(getContainer().getStartURL(getUser()));
        }

        public NavTree appendNavTrail(NavTree root)
        {
            throw new UnsupportedOperationException();
        }
    }


    @RequiresPermission(InsertPermission.class)
    public class ImportXarAction extends ApiAction<ImportXarForm>
    {
        @Override
        public Object execute(ImportXarForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();

            List<Map<String, String>> archives = new ArrayList<>();
            for (File f : form.getValidatedFiles(getContainer()))
            {
                Map<String, String> archive = new HashMap<>();
                ExperimentPipelineJob job = new ExperimentPipelineJob(getViewBackgroundInfo(), f, "Experiment Import", false, form.getPipeRoot(getContainer()));

                // TODO: Configure module resources with the appropriate log location per container
                if (form.getModule() != null)
                {
                    File logFile = new File(form.getPipeRoot(getContainer()).getLogDirectory(), "module-resource-xar.log");
                    job.setLogFile(logFile);
                }

                PipelineService.get().queueJob(job);

                archive.put("file", f.getName());
                archive.put("job", job.getJobGUID());
                archive.put("path", form.getPath()); // echo back the public path

                archives.add(archive);
            }

            response.put("success", true);
            response.put("archives", archives);

            return response;
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
            return getShowApplicationURL(app.getContainer(), app.getRowId());
        }

        public ActionURL getProtocolGridURL(Container c)
        {
            return new ActionURL(ShowProtocolGridAction.class, c);
        }

        public ActionURL getRunGraphDetailURL(ExpRun run)
        {
            return getShowRunGraphDetailURL(run.getContainer(), run.getRowId());
        }

        public ActionURL getRunGraphDetailURL(ExpRun run, @Nullable ExpData focus)
        {
            return getRunGraphDetailURL(run, focus, DotGraph.TYPECODE_DATA);
        }

        public ActionURL getRunGraphDetailURL(ExpRun run, @Nullable ExpMaterial focus)
        {
            return getRunGraphDetailURL(run, focus, DotGraph.TYPECODE_MATERIAL);
        }

        public ActionURL getRunGraphDetailURL(ExpRun run, @Nullable ExpProtocolApplication focus)
        {
            return getRunGraphDetailURL(run, focus, DotGraph.TYPECODE_PROT_APP);
        }

        private ActionURL getRunGraphDetailURL(ExpRun run, @Nullable ExpObject focus, String typeCode)
        {
            ActionURL result = getShowRunGraphDetailURL(run.getContainer(), run.getRowId());
            result.addParameter("detail", "true");
            if (focus != null)
            {
                result.addParameter("focus", typeCode + focus.getRowId());
            }
            return result;
        }

        public ActionURL getRunGraphURL(Container container, int runId)
        {
            return ExperimentController.getRunGraphURL(container, runId);
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
            return new ActionURL(DeleteSelectedExperimentsAction.class, container).addParameter(ActionURL.Param.returnUrl, returnURL.getLocalURIString());
        }

        public ActionURL getDeleteProtocolURL(@NotNull ExpProtocol protocol, URLHelper returnURL)
        {
            ActionURL result = new ActionURL(DeleteProtocolByRowIdsAction.class, protocol.getContainer());
            result.addParameter("singleObjectRowId", protocol.getRowId());
            if (returnURL != null)
            {
                result.addParameter(ActionURL.Param.returnUrl, returnURL.getLocalURIString());
            }
            return result;
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

        public ActionURL getDataClassListURL(Container c)
        {
            return new ActionURL(ListDataClassAction.class, c);
        }

        public ActionURL getDeleteDatasURL(Container c, URLHelper returnURL)
        {
            ActionURL url = new ActionURL(DeleteSelectedDataAction.class, c);
            if (returnURL != null)
                url.addReturnURL(returnURL);
            return url;
        }

        public ActionURL getDeleteMaterialsURL(Container c, URLHelper returnURL)
        {
            ActionURL url = new ActionURL(DeleteMaterialByRowIdAction.class, c);
            if (returnURL != null)
                url.addReturnURL(returnURL);
            return url;
        }

        public ActionURL getDeleteSelectedExperimentsURL(Container c, URLHelper returnURL)
        {
            ActionURL result = new ActionURL(DeleteSelectedExperimentsAction.class, c);
            if (returnURL != null)
                result.addReturnURL(returnURL);
            return result;
        }

        public ActionURL getDeleteSelectedExpRunsURL(Container container, URLHelper returnURL)
        {
            return new ActionURL(DeleteSelectedExpRunsAction.class, container).addReturnURL(returnURL);
        }

        public ActionURL getShowUpdateURL(ExpExperiment experiment)
        {
            return new ActionURL(ShowUpdateAction.class, experiment.getContainer()).addParameter("rowId", experiment.getRowId());
        }

        public ActionURL getRemoveSelectedExpRunsURL(Container container, URLHelper returnURL, ExpExperiment exp)
        {
            return new ActionURL(RemoveSelectedExpRunsAction.class, container).addReturnURL(returnURL).addParameter("expRowId", exp.getRowId());
        }

        public ActionURL getCreateRunGroupURL(Container container, URLHelper returnURL, boolean addSelectedRuns)
        {
            ActionURL result = new ActionURL(CreateRunGroupAction.class, container);
            if (returnURL != null)
            {
                result.addReturnURL(returnURL);
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

        public ActionURL getDownloadGraphURL(ExpRun run, boolean detail, String focus, String focusType)
        {
            ActionURL result = new ActionURL(DownloadGraphAction.class, run.getContainer());
            result.addParameter("rowId", run.getRowId()).addParameter("detail", detail);
            if (focus != null)
            {
                result.addParameter("focus", focus);
            }
            if (focusType != null)
            {
                result.addParameter("focusType", focusType);
            }
            return result;
        }

        public ActionURL getBeginURL(Container container)
        {
            return new ActionURL(BeginAction.class, container);
        }

        public ActionURL getDomainEditorURL(Container container, String domainURI, boolean allowAttachmentProperties, boolean allowFileLinkProperties, boolean showDefaultValueSettings)
        {
            ActionURL url = new ActionURL(PropertyController.EditDomainAction.class, container);
            url.addParameter("domainURI", domainURI);
            if (allowAttachmentProperties)
                url.addParameter("allowAttachmentProperties", "1");
            if (allowFileLinkProperties)
                url.addParameter("allowFileLinkProperties", "1");
            if (showDefaultValueSettings)
                url.addParameter("showDefaultValueSettings", "1");
            return url;

        }

        @Override
        public ActionURL getShowDataClassURL(Container container, int rowId)
        {
            ActionURL url = new ActionURL(ShowDataClassAction.class, container);
            url.addParameter("rowId", rowId);
            return url;
        }

        public ActionURL getShowFileURL(ExpData data, boolean inline)
        {
            ActionURL result = getShowFileURL(data.getContainer()).addParameter("rowId", data.getRowId());
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

        public ActionURL getMaterialDetailsURL(Container c, int materialRowId)
        {
            return new ActionURL(ShowMaterialAction.class, c).addParameter("rowId", materialRowId);
        }

        @Override
        public ActionURL getShowUploadMaterialsURL(Container container)
        {
            return new ActionURL(ShowUploadMaterialsAction.class, container);
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
            return ExperimentController.getRunGraphURL(run.getContainer(), run.getRowId());
        }

        public ActionURL getUploadXARURL(Container container)
        {
            return new ActionURL(ShowAddXarFileAction.class, container);
        }

        @Override
        public ActionURL getRepairTypeURL(Container container)
        {
            return new ActionURL(TypesController.RepairAction.class, container);
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class SampleSetServiceAction extends GWTServiceAction
    {
        protected BaseRemoteService createService()
        {
            return new SampleSetServiceImpl(getViewContext());
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class LineageAction extends ApiAction<ExpLineageOptions>
    {
        private ExpProtocolOutput _output;

        @Override
        public void validateForm(ExpLineageOptions options, Errors errors)
        {
            // TODO: Type and RowId -- OR -- LSID are the only valid way of resolving an _output
            ExperimentService service = ExperimentService.get();

            if (options.getRowId() > 0)
            {
                _output = service.getExpMaterial(options.getRowId());
                if (null == _output)
                    _output = service.getExpData(options.getRowId());

                if (null == _output)
                    throw new NotFoundException("Unable to resolve Experiment Protocol output: " + options.getRowId());
            }
            else if (null != options.getLSID())
            {
                _output = service.getExpMaterial(options.getLSID());
                if (null == _output)
                    _output = service.getExpData(options.getLSID());

                if (null == _output)
                    throw new NotFoundException("Unable to resolve Experiment Protocol output: " + options.getLsid());
            }
            else
            {
                throw new ApiUsageException("One of rowId or lsid required");
            }
        }

        @Override
        public Object execute(ExpLineageOptions options, BindException errors) throws Exception
        {
            ExpLineage lineage = ExperimentService.get().getLineage(_output, options);
            return new ApiSimpleResponse(lineage.toJSON());
        }
    }

    @RequiresPermission(AdminPermission.class)
    @CSRF
    public class RebuildEdgesAction extends MutatingApiAction
    {
        @Override
        public Object execute(Object o, BindException errors)
        {
            ExperimentServiceImpl.get().rebuildAllEdges();
            return success();
        }
    }

}
