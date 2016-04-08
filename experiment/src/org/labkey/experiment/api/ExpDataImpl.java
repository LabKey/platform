/*
 * Copyright (c) 2006-2015 LabKey Corporation
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

package org.labkey.experiment.api;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.ExperimentDataHandler;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Handler;
import org.labkey.api.exp.XarFormatException;
import org.labkey.api.exp.XarSource;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.search.SearchResultTemplate;
import org.labkey.api.search.SearchScope;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.MimeMap;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;
import org.labkey.api.webdav.SimpleDocumentResource;
import org.labkey.experiment.controllers.exp.ExperimentController;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ExpDataImpl extends AbstractProtocolOutputImpl<Data> implements ExpData
{
    public static final SearchService.SearchCategory expDataCategory = new SearchService.SearchCategory("data", "ExpData");


    /**
     * Temporary mapping until experiment.xml contains the mime type
     */
    private static MimeMap MIME_MAP = new MimeMap();

    static public List<ExpDataImpl> fromDatas(List<Data> datas)
    {
        List<ExpDataImpl> ret = new ArrayList<>(datas.size());
        for (Data data : datas)
        {
            ret.add(new ExpDataImpl(data));
        }
        return ret;
    }

    public ExpDataImpl(Data data)
    {
        super(data);
    }

    @Nullable
    @Override
    public String getDescription()
    {
        return _object.getDescription();
    }

    @Nullable
    public URLHelper detailsURL()
    {
        DataType dataType = getDataType();
        if (dataType == null)
            return null;

        return dataType.getDetailsURL(this);
    }

    public List<ExpProtocolApplicationImpl> getTargetApplications()
    {
        return getTargetApplications(new SimpleFilter(FieldKey.fromParts("DataId"), getRowId()), ExperimentServiceImpl.get().getTinfoDataInput());
    }

    public List<ExpRunImpl> getTargetRuns()
    {
        return getTargetRuns(ExperimentServiceImpl.get().getTinfoDataInput(), "DataId");
    }

    public DataType getDataType()
    {
        return ExperimentService.get().getDataType(getLSIDNamespacePrefix());
    }

    public void setDataFileURI(URI uri)
    {
        ensureUnlocked();
        if (uri != null && !uri.isAbsolute())
        {
            throw new IllegalArgumentException("URI must be absolute.");
        }
        String s = uri == null ? null : uri.toString();
        // Strip off any trailing "/"
        if (s != null && s.endsWith("/"))
        {
            s = s.substring(0, s.length() - 1);
        }
        _object.setDataFileUrl(s);
    }

    public void save(User user)
    {
        // Replace the default "Data" cpastype if the Data belongs to a DataClass
        ExpDataClassImpl dataClass = getDataClass();
        if (dataClass != null && DEFAULT_CPAS_TYPE.equals(getCpasType()))
           setCpasType(dataClass.getLSID());

        boolean isNew = getRowId() == 0;
        save(user, ExperimentServiceImpl.get().getTinfoData());

        if (isNew)
        {
            if (dataClass != null)
            {
                Map<String, Object> map = new HashMap<>();
                map.put("lsid", getLSID());
                Table.insert(user, dataClass.getTinfo(), map);
            }
        }
        index(null);
    }

    public URI getDataFileURI()
    {
        String url = _object.getDataFileUrl();
        if (url == null)
            return null;
        try
        {
            return new URI(_object.getDataFileUrl());
        }
        catch (URISyntaxException use)
        {
            return null;
        }
    }

    public ExperimentDataHandler findDataHandler()
    {
        return Handler.Priority.findBestHandler(ExperimentServiceImpl.get().getExperimentDataHandlers(), (ExpData)this);
    }

    public String getDataFileUrl()
    {
        return _object.getDataFileUrl();
    }

    @Nullable
    public File getFile()
    {
        return _object.getFile();
    }

    public boolean isInlineImage()
    {
        return null != getFile() && MIME_MAP.isInlineImageFor(getFile());
    }

    public String urlFlag(boolean flagged)
    {
        String ret = null;
        if (getLSID() != null)
        {
            DataType type = getDataType();
            if (type != null)
            {
                ret = type.urlFlag(flagged);
            }
            if (ret != null)
                return ret;
        }
        if (flagged)
        {
            return AppProps.getInstance().getContextPath() + "/Experiment/flagData.png";
        }
        return AppProps.getInstance().getContextPath() + "/Experiment/images/unflagData.png";
    }

    public void delete(User user)
    {
        ExperimentServiceImpl.get().deleteDataByRowIds(getContainer(), Collections.singleton(getRowId()));
    }
    
    public String getMimeType()
    {
        if (null != getDataFileUrl())
            return MIME_MAP.getContentTypeFor(getDataFileUrl());
        else
            return null;
    }

    public boolean isFileOnDisk()
    {
        File f = getFile();
        return f != null && NetworkDrive.exists(f) && f.isFile();
    }

    public String getCpasType()
    {
        String result = _object.getCpasType();
        if (result != null)
            return result;

        ExpDataClass dataClass = getDataClass();
        if (dataClass != null)
            return dataClass.getLSID();

        return ExpData.DEFAULT_CPAS_TYPE;
    }

    public void setGenerated(boolean generated)
    {
        ensureUnlocked();
        _object.setGenerated(generated);
    }

    @Override
    public boolean isGenerated()
    {
        return _object.isGenerated();
    }

    @Override
    @Nullable
    public ExpDataClassImpl getDataClass()
    {
        if (_object.getClassId() != null)
            return ExperimentServiceImpl.get().getDataClass(_object.getClassId());

        return null;
    }

    public void importDataFile(PipelineJob job, XarSource xarSource) throws ExperimentException
    {
        String dataFileURL = getDataFileUrl();
        if (dataFileURL == null)
            return;

        if (xarSource.shouldIgnoreDataFiles())
        {
            job.debug("Skipping load of data file " + dataFileURL + " based on the XAR source");
            return;
        }

        try
        {
            job.debug("Trying to load data file " + dataFileURL + " into the system");

            File file = new File(new URI(dataFileURL));

            if (!file.exists())
            {
                job.debug("Unable to find the data file " + file.getPath() + " on disk.");
                return;
            }

            // Check that the file is under the pipeline root to prevent users from referencing a file that they
            // don't have permission to import
            PipeRoot pr = PipelineService.get().findPipelineRoot(job.getContainer());
            if (!xarSource.allowImport(pr, job.getContainer(), file))
            {
                if (pr == null)
                {
                    job.warn("No pipeline root was set, skipping load of file " + file.getPath());
                    return;
                }
                job.debug("The data file " + file.getAbsolutePath() + " is not under the folder's pipeline root: " + pr + ". It will not be loaded directly, but may be loaded if referenced from other files that are under the pipeline root.");
                return;
            }

            ExperimentDataHandler handler = findDataHandler();
            try
            {
                handler.importFile(this, file, job.getInfo(), job.getLogger(), xarSource.getXarContext());
            }
            catch (ExperimentException e)
            {
                throw new XarFormatException(e);
            }

            job.debug("Finished trying to load data file " + dataFileURL + " into the system");
        }
        catch (URISyntaxException e)
        {
            throw new XarFormatException(e);
        }
    }

    // Get all text strings from the data class for indexing
    @NotNull
    public List<String> getIndexValues()
    {
        ExpDataClassImpl dc = this.getDataClass();
        List<String> values = Collections.emptyList();
        if (null != dc)
        {
            values = new ArrayList<>();
            TableInfo t = dc.getTinfo();
            SQLFragment sql = new SQLFragment("SELECT ");
            boolean first = true;
            for (ColumnInfo ci : t.getColumns())
            {
                if (ci.getJdbcType().toString().equals("VARCHAR"))
                {
                    // Don't index lsid's or sequences
                    if (ci.getName().equals("lsid") || ci.getName().equals("sequence"))
                        continue;

                    if (!first)
                        sql.append(", ");
                    else
                        first = false;

                    sql.append(ci.getName());
                }
            }
            if (!first)
            {
                sql.append(" FROM expdataclass.").append(t.getName()).append(" WHERE LSID = ?");
                sql.add(getLSID());
                Map<String, Object> indexes = new SqlSelector(t.getSchema(), sql).getMap();
                if (indexes != null)
                {
                    for (Object o : indexes.values())
                    {
                        if (null != o && o instanceof String)
                            values.add((String) o);
                    }
                }
            }
        }
        return values;
    }

    @NotNull
    public Collection<String> getAliases()
    {
        TableInfo mapTi = ExperimentService.get().getTinfoDataAliasMap();
        TableInfo ti = ExperimentService.get().getTinfoAlias();
        SQLFragment sql = new SQLFragment()
                .append("SELECT a.name FROM ").append(mapTi, "m")
                .append(" JOIN ").append(ti, "a")
                .append(" ON m.alias = a.RowId WHERE m.lsid = ? ");
        sql.add(getLSID());
        ArrayList<String> aliases = new SqlSelector(mapTi.getSchema(), sql).getArrayList(String.class);
        return Collections.unmodifiableList(aliases);
    }

    public String getDocumentId()
    {
        // CONSIDER: use lsid so we can crack it later?  Include dataClassId?
        return "data:" + new Path(getContainer().getId(), Integer.toString(getRowId()));
    }

    public void index(SearchService.IndexTask task)
    {
        if (task == null)
        {
            SearchService ss = ServiceRegistry.get().getService(SearchService.class);
            if (null == ss)
                return;
            task = ss.defaultTask();
        }

        Map<String, Object> props = new HashMap<>();
        StringBuilder keywords = new StringBuilder();
        StringBuilder identifiers = new StringBuilder();

        // Add name to title
        if (null != getDescription())
            keywords.append(getDescription());

        StringBuilder title = new StringBuilder(getName());

        // Add aliases in parenthesis in the title
        Collection<String> aliases = this.getAliases();
        if (!aliases.isEmpty())
        {
            title.append(" (").append(StringUtils.join(aliases, ", ")).append(")");
        }

        boolean first = true;
        List<String> dataClassText = getIndexValues();
        for (String text : dataClassText)
        {
            if (text.contains("lsid"))
                continue;

            if (first)
                first = false;
            else
                identifiers.append(", ");

            identifiers.append(text);
        }

        ExpDataClass dc = this.getDataClass();
        if (null != dc)
        {
            ActionURL show = new ActionURL(ExperimentController.ShowDataClassAction.class,getContainer()).addParameter("rowId", dc.getRowId());
            NavTree t = new NavTree(dc.getName(), show);
            String nav = NavTree.toJS(Collections.singleton(t), null, false).toString();
            props.put(SearchService.PROPERTY.navtrail.toString(), nav);

            props.put(DataSearchResultTemplate.PROPERTY, dc.getName());
        }

        props.put(SearchService.PROPERTY.categories.toString(), expDataCategory.toString());
        props.put(SearchService.PROPERTY.title.toString(), title.toString());
        // NOTE: All string values are added as keywords so they will be stemmed.
        // The search query will always be stemmed so we may not find literal identifiers.
        props.put(SearchService.PROPERTY.keywordsMed.toString(), keywords.toString() + " " + identifiers.toString());

        ActionURL view = ExperimentController.ExperimentUrlsImpl.get().getDataDetailsURL(this);
        view.setExtraPath(getContainer().getId());
        String docId = getDocumentId();
        final int id = getRowId();

        StringBuilder body = new StringBuilder(title).append(" ").append(keywords);

        // All identifiers (indexable text values) in the body
        if (identifiers.length() > 0)
            body.append("\n").append(identifiers);

        SimpleDocumentResource r = new SimpleDocumentResource(
                new Path(docId),
                docId,
                getContainer().getId(),
                "text/plain",
                body.toString().getBytes(),
                view, props) {
            @Override
            public void setLastIndexed(long ms, long modified)
            {
                ExperimentServiceImpl.get().setDataLastIndexed(id, ms);
            }
        };

        task.addResource(r, SearchService.PRIORITY.item);
    }

    public static class DataSearchResultTemplate implements SearchResultTemplate
    {
        public static final String NAME = "data";
        public static final String PROPERTY = "dataclass";

        @Nullable
        @Override
        public String getName()
        {
            return NAME;
        }

        @Nullable
        @Override
        public String getCategories()
        {
            return expDataCategory.getName();
        }

        @Nullable
        @Override
        public SearchScope getSearchScope()
        {
            return SearchScope.FolderAndSubfolders;
        }

        @NotNull
        @Override
        public String getResultNameSingular()
        {
            if (HttpView.hasCurrentView())
            {
                ViewContext ctx = HttpView.currentContext();
                String dataclass = ctx.getActionURL().getParameter(PROPERTY);
                if (dataclass != null)
                {
                    ExpDataClass dc = ExperimentService.get().getDataClass(ctx.getContainer(), ctx.getUser(), dataclass);
                    if (dc != null)
                        return dc.getName();
                }
            }

            return "data";
        }

        @NotNull
        @Override
        public String getResultNamePlural()
        {
            return getResultNameSingular();
        }

        @Override
        public boolean includeNavigationLinks()
        {
            return true;
        }

        @Override
        public boolean includeAdvanceUI()
        {
            return false;
        }

        @Nullable
        @Override
        public String getExtraHtml(ViewContext ctx)
        {
            String q = ctx.getActionURL().getParameter("q");

            if (StringUtils.isNotBlank(q))
            {
                String dataclass = ctx.getActionURL().getParameter(PROPERTY);
                ActionURL url = ctx.cloneActionURL().deleteParameter(PROPERTY);
                url.replaceParameter("_dc", String.valueOf((int)Math.round(1000 * Math.random())));

                StringBuilder html = new StringBuilder();
                html.append("<div class=\"labkey-search-filter\">");

                appendParam(html, null, dataclass, "All", false, url);
                for (ExpDataClass dc : ExperimentService.get().getDataClasses(ctx.getContainer(), ctx.getUser(), true))
                {
                    appendParam(html, dc.getName(), dataclass, dc.getName(), true, url);
                }

                html.append("</div>");
                return html.toString();
            }
            else
            {
                return null;
            }
        }

        private void appendParam(StringBuilder sb, @Nullable String dataclass, @Nullable String current, @NotNull String label, boolean addParam, ActionURL url)
        {
            sb.append("<span>");

            if (!Objects.equals(dataclass, current))
            {
                sb.append("<a href=\"");

                if (addParam)
                    url = url.clone().addParameter(PROPERTY, dataclass);

                sb.append(PageFlowUtil.filter(url));
                sb.append("\">");
                sb.append(PageFlowUtil.filter(label));
                sb.append("</a>");
            }
            else
            {
                sb.append(label);
            }

            sb.append("</span> ");
        }

        @Nullable
        @Override
        public String getHiddenInputsHtml(ViewContext ctx)
        {
            String dataclass = ctx.getActionURL().getParameter(PROPERTY);
            if (dataclass != null)
            {
                return "<input type='hidden' id='search-type' name='" + PROPERTY + "' value='" + PageFlowUtil.filter(dataclass) + "'>";
            }

            return null;
        }


        @Override
        public String reviseQuery(ViewContext ctx, String q)
        {
            String dataclass = ctx.getActionURL().getParameter(PROPERTY);

            if (null != dataclass)
                return "+(" + q + ") +" + PROPERTY + ":" + dataclass;
            else
                return q;
        }

        @Override
        public NavTree appendNavTrail(NavTree root, ViewContext ctx, @NotNull SearchScope scope, @Nullable String category)
        {
            NavTree tree = SearchResultTemplate.super.appendNavTrail(root, ctx, scope, category);

            String dataclass = ctx.getActionURL().getParameter(PROPERTY);
            if (dataclass != null)
            {
                String text = tree.getText();
                tree.setText(text + " - " + dataclass);
            }
            return tree;
        }
    }
}
