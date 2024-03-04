/*
 * Copyright (c) 2009-2018 LabKey Corporation
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
package org.labkey.assay.actions;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.action.ConfirmAction;
import org.labkey.api.action.SimpleErrorView;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.assay.AssayDataCollector;
import org.labkey.api.assay.AssayService;
import org.labkey.api.assay.PipelineDataCollector;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.usageMetrics.SimpleMetricsService;
import org.labkey.api.util.DOM;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.Pair;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.template.PageConfig;
import org.labkey.assay.AssayModule;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.labkey.api.util.DOM.A;
import static org.labkey.api.util.DOM.Attribute.href;
import static org.labkey.api.util.DOM.LI;
import static org.labkey.api.util.DOM.P;
import static org.labkey.api.util.DOM.UL;
import static org.labkey.api.util.DOM.at;
import static org.labkey.api.util.DOM.createHtmlFragment;

/**
 * User: jeckels
 * Date: Apr 13, 2009
 */
@RequiresPermission(InsertPermission.class)
public class PipelineDataCollectorRedirectAction extends SimpleViewAction<PipelineDataCollectorRedirectAction.UploadRedirectForm>
{
    private static final Logger LOG = LogManager.getLogger(PipelineDataCollectorRedirectAction.class);

    @Override
    public ModelAndView getView(UploadRedirectForm form, BindException errors)
    {
        Container container = getContainer();
        // Can't trust the form's getPath() because it translates the empty string into null, and we
        // need to know if the parameter was present
        String path = getViewContext().getRequest().getParameter("path");
        List<File> files = new ArrayList<>();
        if (path != null)
        {
            PipeRoot root = PipelineService.get().findPipelineRoot(container);
            if (root == null)
            {
                throw new NotFoundException("No pipeline root is available");
            }
            File f = root.resolvePath(path);
            if (!NetworkDrive.exists(f))
            {
                throw new NotFoundException("Unable to find file: " + path);
            }

            for (String fileName : getViewContext().getRequest().getParameterValues("file"))
            {
                if (fileName.contains("/") || fileName.contains("\\"))
                {
                    throw new NotFoundException(fileName);
                }
                File file = new File(f, fileName);
                if (!NetworkDrive.exists(file))
                {
                    throw new NotFoundException(fileName);
                }
                files.add(file);
            }
        }
        else
        {
            for (int dataId : DataRegionSelection.getSelectedIntegers(getViewContext(), true))
            {
                ExpData data = ExperimentService.get().getExpData(dataId);
                if (data == null || !data.getContainer().equals(container))
                {
                    throw new NotFoundException("Could not find all selected datas");
                }

                File f = data.getFile();
                if (f != null && f.isFile())
                {
                    files.add(f);
                }
            }
        }

        if (files.isEmpty())
        {
            throw new NotFoundException("Could not find any matching files");
        }

        if (!form.isAllowCrossRunFileInputs())
        {
            // check for files with existing runs and prompt for confirmation if there are any
            var pair = filesWithExistingRuns(files);
            Map<ExpData, ExpRun> filesWithRun = pair.second;
            if (!filesWithRun.isEmpty())
            {
                // Create a confirmation view to prompt user about importing the files that have already been created by
                // another run.  Ideally, we'd just extend ConfirmAction directly. Unfortunately, ConfirmAction expects
                // the prompt view to be accessed via GET and then POSTs when the user confirms the action. However, the
                // PipelineDataCollectorRedirectAction is invoked initially via POST from the file browser before we've
                // confirmed. To create the confirmWrapper.jsp view, we need to initialize a FakeConfirmAction to pass
                // the property values for rendering the hidden form values.

                FakeConfirmAction confirmAction = new FakeConfirmAction();
                confirmAction.setViewContext(getViewContext());
                MutablePropertyValues mpv = new MutablePropertyValues(getPropertyValues());
                // add the property that will be POSTed if user confirms
                mpv.addPropertyValue("allowCrossRunFileInputs", true);
                confirmAction.setProperties(mpv);

                ModelAndView confirmView = getConfirmView(pair.first, filesWithRun);
                JspView<FakeConfirmAction> confirmWrapper = new JspView<>("/org/labkey/api/action/confirmWrapper.jsp", confirmAction);
                confirmWrapper.setBody(confirmView);
                getPageConfig().setTemplate(PageConfig.Template.Dialog);
                return confirmWrapper;
            }
        }

        if (errors.getErrorCount() > 0)
        {
            return new SimpleErrorView(errors);
        }

        Collections.sort(files);
        List<Map<String, File>> maps = new ArrayList<>();
        for (File file : files)
        {
            maps.add(Collections.singletonMap(AssayDataCollector.PRIMARY_FILE, file));
        }
        PipelineDataCollector.setFileCollection(getViewContext().getRequest().getSession(true), container, form.getProtocol(), maps);

        ActionURL url = AssayService.get().getProvider(form.getProtocol()).getImportURL(container, form.getProtocol());
        if (form.isAllowCrossRunFileInputs())
            url.addParameter("allowCrossRunFileInputs", "true");

        SimpleMetricsService.get().increment(AssayModule.NAME, "AssayDesigner", "ImportFromFile");
        throw new RedirectException(url);
    }

    private class FakeConfirmAction extends ConfirmAction<Object>
    {
        @Override
        public ModelAndView getConfirmView(Object o, BindException errors) throws Exception { throw new IllegalStateException(); }

        @Override
        public boolean handlePost(Object o, BindException errors) throws Exception { throw new IllegalStateException(); }

        @Override
        public void validateCommand(Object o, Errors errors) { throw new IllegalStateException(); }

        @Override
        public @NotNull URLHelper getSuccessURL(Object o)
        {
            return getViewContext().getActionURL();
        }
    }

    private ModelAndView getConfirmView(List<File> remaining, Map<ExpData, ExpRun> filesWithRun)
    {
        List<DOM.Renderable> nodes = new ArrayList<>();

        DOM.Renderable warn = P(
                "The files listed below have been created by another run.",
                " Importing these files into an assay will will attach the existing file as an input to the assay",
                " and create another file record as an output of the assay run.",
                " Would you like to continue to import these files?",
                UL(filesWithRun.entrySet().stream().map(e ->
                        LI("File '" + e.getKey().getName() + "' created by run '",
                                A(at(href, e.getValue().detailsURL()), e.getValue().getName()),
                                "' (" + e.getValue().getProtocol().getName() + ")"))));

        nodes.add(warn);

        if (!remaining.isEmpty())
        {
            nodes.add(P("The remaining files have not yet been imported:",
                    UL(remaining.stream().map(f -> LI(f.getName())))));
        }

        return new HtmlView(createHtmlFragment(nodes));
    }

    // split the files into those that have been created by an run and the rest.
    protected Pair<List<File>, Map<ExpData, ExpRun>> filesWithExistingRuns(List<File> files)
    {
        var unimported = new ArrayList<File>();
        var existing = new LinkedHashMap<ExpData, ExpRun>();
        for (File file : files)
        {
            ExpData data = ExperimentService.get().getExpDataByURL(file, getContainer());
            if (data != null)
                LOG.info("Found existing data: rowId=" + data.getRowId() + ", url=" + data.getDataFileUrl());
            if (data != null && data.getRun() != null)
            {
                ExpRun previousRun = data.getRun();
                String msg ="File '" + data.getName() + "' has been previously imported in run '" + previousRun.getName() + "' (" + previousRun.getRowId() + ")";
                LOG.warn(msg);
                existing.put(data, previousRun);
            }
            else
            {
                unimported.add(file);
            }
        }
        return Pair.of(unimported, existing);
    }

    @Override
    public void addNavTrail(NavTree root)
    {
        root.addChild("Assay Upload Attempt");
    }

    public static class UploadRedirectForm
    {
        private int _protocolId;
        private String _path;
        private boolean _allowCrossRunFileInputs;

        public int getProtocolId()
        {
            return _protocolId;
        }

        public ExpProtocol getProtocol()
        {
            ExpProtocol result = ExperimentService.get().getExpProtocol(_protocolId);
            if (result == null)
            {
                throw new NotFoundException("Could not find protocol");
            }
            return result;
        }

        public void setProtocolId(int protocolId)
        {
            _protocolId = protocolId;
        }

        public String getPath()
        {
            return _path;
        }

        public void setPath(String path)
        {
            _path = path;
        }

        public void setAllowCrossRunFileInputs(boolean allowCrossRunFileInputs)
        {
            _allowCrossRunFileInputs = allowCrossRunFileInputs;
        }

        public boolean isAllowCrossRunFileInputs()
        {
            return _allowCrossRunFileInputs;
        }
    }

}
