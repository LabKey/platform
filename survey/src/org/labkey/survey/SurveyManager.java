/*
 * Copyright (c) 2012-2017 LabKey Corporation
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

package org.labkey.survey;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.action.NullSafeBindException;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.MultiValuedMapCollectors;
import org.labkey.api.data.AuditConfigurable;
import org.labkey.api.data.BeanObjectFactory;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.JsonWriter;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.gwt.client.AuditBehaviorType;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleResourceCache;
import org.labkey.api.module.ModuleResourceCacheHandler;
import org.labkey.api.module.ModuleResourceCaches;
import org.labkey.api.module.ResourceRootProvider;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.survey.model.Survey;
import org.labkey.api.survey.model.SurveyDesign;
import org.labkey.api.survey.model.SurveyListener;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Stream;

public class SurveyManager
{
    private static final Logger _log = Logger.getLogger(SurveyManager.class);
    private static final SurveyManager _instance = new SurveyManager();
    private static final List<SurveyListener> _surveyListeners = new CopyOnWriteArrayList<>();
    private static final ModuleResourceCache<MultiValuedMap<String, SurveyDesign>> MODULE_SURVEY_DESIGN_CACHE = ModuleResourceCaches.create("Module Survey Design Cache", new SurveyDesignResourceCacheHandler(), ResourceRootProvider.getSubdirectories(Path.parse("surveys")));

    public static final String MODULE_RESOURCE_FILE_EXTENSION = ".metadata.json";
    public static final String MODULE_RESOURCE_PREFIX = "module:";

    private SurveyManager()
    {
        // prevent external construction with a private default constructor
    }

    public static SurveyManager get()
    {
        return _instance;
    }

    public static void addSurveyListener(SurveyListener listener)
    {
        _surveyListeners.add(listener);
    }

    public static void removeDesignListener(SurveyListener listener)
    {
        _surveyListeners.remove(listener);
    }

    @Nullable
    public JSONObject createSurveyTemplate(ViewContext context, String schemaName, String queryName)
    {
        BindException errors = new NullSafeBindException(this, "form");
        UserSchema schema = QueryService.get().getUserSchema(context.getUser(), context.getContainer(), schemaName);
        Map<String, Object> survey = new HashMap<>();

        if (schema != null)
        {
            QuerySettings settings = schema.getSettings(context, QueryView.DATAREGIONNAME_DEFAULT, queryName);
            QueryView view = schema.createView(context, settings, errors);

            if (view != null)
            {
                survey.put("layout", "auto");
                survey.put("showCounts", false); // whether or not to show the count of completed questions next to the section header

                Map<String, Object> panel = new HashMap<>();

                panel.put("title", queryName);
                panel.put("description", null);
                panel.put("header", true);
                panel.put("collapsible", true);
                panel.put("defaultLabelWidth", 350);

                List<Map<String, Object>> columns = new ArrayList<>();
                for (DisplayColumn dc : view.getDisplayColumns())
                {
                    if (dc.isQueryColumn() && dc.isEditable())
                    {
                        Map<String, Object> metaDataMap = JsonWriter.getMetaData(dc, null, false, true, false);
                        Map<String, Object> trimmedMap = getTrimmedMetaData(metaDataMap);

                        // set defaults for the survey questions
                        trimmedMap.put("width", 800);

                        // issue 17131: since surveys will likely be used in subfolders, always add the lookup containerPath
                        if (trimmedMap.containsKey("lookup"))
                        {
                            JSONObject lookup = (JSONObject)trimmedMap.get("lookup");
                            if (!lookup.containsKey("containerPath"))
                            {
                                // use the container Id for the generated property as it avoids dealing with special/tricky characters
                                // the user can still enter the path as a string if they would like
                                String containerPath = context.getContainer().getPath();
                                if (containerPath.matches("\\A\\p{ASCII}*\\z"))
                                    lookup.put("containerPath", containerPath);
                                else
                                    lookup.put("containerPath", context.getContainer().getEntityId());

                                trimmedMap.put("lookup", lookup);
                            }
                        }

                        columns.add(trimmedMap);
                    }
                }
                panel.put("questions", columns);

                survey.put("sections", Collections.singletonList(panel));
            }
        }
        return new JSONObject(survey);
    }

    public List<String> getKeyMetaDataProps()
    {
        return Arrays.asList("name", "caption", "shortCaption", "hidden", "jsonType", "inputType", "lookup", "required");
    }

    public Map<String, Object> getTrimmedMetaData(Map<String, Object> origMap)
    {
        // trim the metadata property map to just those properties needed for rendering the Survey questions
        List<String> props = getKeyMetaDataProps();
        Map<String, Object> trimmedMap = new LinkedHashMap<>();
        for (String property : props)
        {
            if (origMap.get(property) != null)
                trimmedMap.put(property, origMap.get(property));
        }

        // issue 16908: we use "required" in the survey metadata instead of "nullable"
        if (origMap.get("nullable") != null)
            trimmedMap.put("required", !(Boolean.parseBoolean(origMap.get("nullable").toString())));

        return trimmedMap;
    }

    public SurveyDesign saveSurveyDesign(Container container, User user, SurveyDesign survey)
    {
        DbScope scope = SurveySchema.getInstance().getSchema().getScope();

        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            SurveyDesign ret;
            if (survey.isNew())
            {
                survey.beforeInsert(user, container.getId());
                ret = Table.insert(user, SurveySchema.getInstance().getSurveyDesignsTable(), survey);
            }
            else
                ret = Table.update(user, SurveySchema.getInstance().getSurveyDesignsTable(), survey, survey.getRowId());

            transaction.commit();
            return ret;
        }
    }

    public Survey saveSurvey(Container container, User user, Survey survey)
    {
        DbScope scope = SurveySchema.getInstance().getSchema().getScope();
        List<Throwable> errors;

        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            TableInfo table = SurveySchema.getInstance().getSurveysTable();
            if (table.supportsAuditTracking())
            {
                ((AuditConfigurable)table).setAuditBehavior(AuditBehaviorType.DETAILED);
            }
            Survey ret;
            BeanObjectFactory<Survey> objectFactory = new BeanObjectFactory<>(Survey.class);
            if (survey.isNew())
            {
                survey.beforeInsert(user, container.getId());
                ret = Table.insert(user, table, survey);

                errors = fireCreatedSurvey(container, user, survey, objectFactory.toMap(ret, null));
            }
            else
            {
                Survey prev = getSurvey(container, user, survey.getRowId());
                ret = Table.update(user, table, survey, survey.getRowId());
                errors = fireUpdateSurvey(container, user, ret, objectFactory.toMap(prev, null), objectFactory.toMap(ret, null));
            }

            if (!errors.isEmpty())
            {
                Throwable first = errors.get(0);
                if (first instanceof RuntimeException)
                    throw (RuntimeException)first;
                else
                    throw new RuntimeException(first);
            }

            transaction.commit();
            return ret;
        }
    }

    public SurveyDesign getSurveyDesign(Container container, User user, int surveyId)
    {
        return new TableSelector(SurveySchema.getInstance().getSurveyDesignsTable(), new SimpleFilter(FieldKey.fromParts("rowId"), surveyId), null).getObject(SurveyDesign.class);
    }

    /**
     * Experimental, retrieves a module based survey design from the cache. Module based survey IDs
     * look like : module:schema/query for example module:mpower/participant.
     */
    @Nullable
    public SurveyDesign getModuleSurveyDesign(Container container, String surveyId)
    {
        if (surveyId != null && surveyId.startsWith(MODULE_RESOURCE_PREFIX))
        {
            // parse out the path parts
            Path parts = Path.parse(surveyId.substring(MODULE_RESOURCE_PREFIX.length()));
            if (parts.size() == 2)
            {
                Map<String, SurveyDesign> surveyDesignMap = getModuleSurveyDesigns(container, parts.get(0));
                if (surveyDesignMap.containsKey(parts.get(1)))
                {
                    return surveyDesignMap.get(parts.get(1));
                }
            }
        }
        return null;
    }

    public SurveyDesign[] getSurveyDesigns(Container container, ContainerFilter filter)
    {
        DbSchema schema = SurveySchema.getInstance().getSchema();
        SQLFragment sql = new SQLFragment("SELECT * FROM ").append(SurveySchema.getInstance().getSurveyDesignsTable(), "sd");
        sql.append(" WHERE ");
        sql.append(filter.getSQLFragment(schema, new SQLFragment("sd.container"), container));
        return new SqlSelector(schema, sql).getArray(SurveyDesign.class);
    }

    public SurveyDesign[] getSurveyDesigns(SimpleFilter filter)
    {
        TableInfo table = SurveySchema.getInstance().getSurveyDesignsTable();
        DbSchema schema = SurveySchema.getInstance().getSchema();
        SQLFragment sql = new SQLFragment("SELECT * FROM ").append(table, "sd");
        sql.append(" ").append(filter.getSQLFragment(table.getSqlDialect()));

        return new SqlSelector(schema, sql).getArray(SurveyDesign.class);
    }

    public Survey getSurvey(Container container, User user, int rowId)
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("rowId"), rowId);
        return new TableSelector(SurveySchema.getInstance().getSurveysTable(), filter, null).getObject(Survey.class);
    }

    public Survey getSurvey(Container container, User user, String schema, String query, String responsePk)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Container"), container);
        filter.addCondition(FieldKey.fromParts("SurveyDesignId", "SchemaName"), schema);
        filter.addCondition(FieldKey.fromParts("SurveyDesignId", "QueryName"), query);
        filter.addCondition(FieldKey.fromParts("ResponsesPK"), responsePk);
        return new TableSelector(SurveySchema.getInstance().getSurveysTable(), filter, null).getObject(Survey.class);
    }

    public Survey[] getSurveys(Container container)
    {
        return new TableSelector(SurveySchema.getInstance().getSurveysTable(), new SimpleFilter(FieldKey.fromParts("container"), container), null).getArray(Survey.class);
    }

    // delete all survey designs and survey instances in this container
    public void delete(Container c, User user)
    {
        SurveySchema s = SurveySchema.getInstance();
        SqlExecutor executor = new SqlExecutor(s.getSchema());

        SurveyDesign[] surveyDesigns = getSurveyDesigns(c, ContainerFilter.CURRENT);

        for (SurveyDesign design : surveyDesigns)
            deleteSurveyDesign(c, user, design.getRowId(), true);

        Survey[] surveys = getSurveys(c);

        if (surveys.length > 0)
        {
            SQLFragment deleteSurveysSql = new SQLFragment("DELETE FROM ");
            deleteSurveysSql.append(s.getSurveysTable().getSelectName()).append(" WHERE Container = ?").add(c);
            executor.execute(deleteSurveysSql);

            // invoke any survey listeners to clean up any dependent objects
            for (Survey survey : surveys)
                fireDeleteSurvey(c, user, survey);
        }
/*

        SQLFragment deleteSurveyDesignsSql = new SQLFragment("DELETE FROM ");
        deleteSurveyDesignsSql.append(s.getSurveyDesignsTable().getSelectName()).append(" WHERE Container = ?").add(c);
        executor.execute(deleteSurveyDesignsSql);
*/
    }

    /**
     * Deletes a specified survey design
     * @param c
     * @param user
     * @param surveyDesignId
     * @param deleteSurveyInstances - true to delete survey instances of this design
     */
    public void deleteSurveyDesign(Container c, User user, int surveyDesignId, boolean deleteSurveyInstances)
    {
        DbScope scope = SurveySchema.getInstance().getSchema().getScope();

        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            SurveySchema s = SurveySchema.getInstance();
            SqlExecutor executor = new SqlExecutor(s.getSchema());

            if (deleteSurveyInstances)
            {
                for (Survey survey : getSurveys(c, user, surveyDesignId))
                    deleteSurvey(c, user, survey.getRowId());
            }
            SQLFragment deleteSurveyDesignsSql = new SQLFragment("DELETE FROM ");
            deleteSurveyDesignsSql.append(s.getSurveyDesignsTable().getSelectName()).append(" WHERE RowId = ?").add(surveyDesignId);
            executor.execute(deleteSurveyDesignsSql);

            transaction.commit();
        }
    }

    public Survey[] getSurveys(Container c, User user, int surveyDesignId)
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("surveyDesignId"), surveyDesignId);

        return new TableSelector(SurveySchema.getInstance().getSurveysTable(), filter, null).getArray(Survey.class);
    }

    public void deleteSurvey(Container c, User user, int surveyId)
    {
        DbScope scope = SurveySchema.getInstance().getSchema().getScope();
        List<Throwable> errors;

        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            Survey survey = getSurvey(c, user, surveyId);

            if (survey != null)
            {
                TableInfo table = SurveySchema.getInstance().getSurveysTable();


                Table.delete(table, surveyId);
                errors = fireDeleteSurvey(c, user, survey);

                if (!errors.isEmpty())
                {
                    Throwable first = errors.get(0);
                    if (first instanceof RuntimeException)
                        throw (RuntimeException)first;
                    else
                        throw new RuntimeException(first);
                }
            }
            transaction.commit();
        }
    }

    public List<Throwable> fireBeforeDeleteSurvey(Container c, User user, Survey survey)
    {
        List<Throwable> errors = new ArrayList<>();

        for (SurveyListener l : _surveyListeners)
        {
            try
            {
                l.surveyBeforeDelete(c, user, survey);
            }
            catch (Throwable t)
            {
                errors.add(t);
            }
        }
        return errors;
    }

    public static List<Throwable> fireDeleteSurvey(Container c, User user, Survey survey)
    {
        List<Throwable> errors = new ArrayList<>();
        SurveyDesign design = SurveyManager.get().getSurveyDesign(c, user, survey.getSurveyDesignId());

        for (SurveyListener l : _surveyListeners)
        {
            try
            {
                // delete the row in the responses table
                deleteSurveyResponse(c, user, design, survey);
                l.surveyDeleted(c, user, survey);
            }
            catch (Throwable t)
            {
                errors.add(t);
            }
        }
        return errors;
    }

    private static void deleteSurveyResponse(Container c, User user, SurveyDesign design, Survey survey)
    {
        TableInfo table = SurveyManager.get().getSurveyResponsesTableInfo(c, user, design);
        if (table != null)
        {
            QueryUpdateService qus = table.getUpdateService();
            FieldKey pk = getSurveyPk(table);
            if (qus != null && pk != null)
            {
                try {
                    List<Map<String, Object>> keys = new ArrayList<>();

                    if (survey.getResponsesPk() != null)
                    {
                        keys.add(Collections.singletonMap(pk.getName(), survey.getResponsesPk()));
                        qus.deleteRows(user, ContainerManager.getForId(survey.getContainerId()), keys, null, null);
                    }
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    @Nullable
    public static FieldKey getSurveyPk(TableInfo table)
    {
        FieldKey pk = null;
        if (table.supportsAuditTracking())
        {
            pk = ((AuditConfigurable)table).getAuditRowPk();
        }
        else
        {
            List<String> pks = table.getPkColumnNames();
            if (pks.size() == 1)
                return FieldKey.fromParts(pks.get(0));
            else if (table.getColumn(FieldKey.fromParts("EntityId")) != null)
                return FieldKey.fromParts("EntityId");
            else if (table.getColumn(FieldKey.fromParts("RowId")) != null)
                return FieldKey.fromParts("RowId");
        }
        return pk;
    }

    public List<Throwable> fireUpdateSurvey(Container c, User user, Survey survey, Map<String, Object> oldRow, Map<String, Object> row)
    {
        List<Throwable> errors = new ArrayList<>();

        for (SurveyListener l : _surveyListeners)
        {
            try {
                l.surveyUpdated(c, user, survey, oldRow, row);
            }
            catch (Throwable t)
            {
                errors.add(t);
            }
        }
        return errors;
    }

    public List<Throwable> fireCreatedSurvey(Container c, User user, Survey survey, Map<String, Object> rowData)
    {
        List<Throwable> errors = new ArrayList<>();

        for (SurveyListener l : _surveyListeners)
        {
            try {
                l.surveyCreated(c, user, survey, rowData);
            }
            catch (Throwable t)
            {
                errors.add(t);
            }
        }
        return errors;
    }

    public List<Throwable> fireBeforeUpdateSurveyResponses(Container c, User user, Survey survey)
    {
        List<Throwable> errors = new ArrayList<>();

        for (SurveyListener l : _surveyListeners)
        {
            try {
                l.surveyResponsesBeforeUpdate(c, user, survey);
            }
            catch (Throwable t)
            {
                errors.add(t);
            }
        }
        return errors;
    }

    public List<Throwable> fireUpdateSurveyResponses(Container c, User user, Survey survey, Map<String, Object> rowData)
    {
        List<Throwable> errors = new ArrayList<>();

        for (SurveyListener l : _surveyListeners)
        {
            try
            {
                l.surveyResponsesUpdated(c, user, survey, rowData);
            }
            catch (Throwable t)
            {
                errors.add(t);
            }
        }
        return errors;
    }

    public ArrayList<String> getSurveyLockedStates()
    {
        ArrayList<String> states = new ArrayList<>();
        for (SurveyListener l : _surveyListeners)
        {
            states.addAll(l.getSurveyLockedStates());
        }
        return states;
    }

    public TableInfo getSurveyResponsesTableInfo(Container container, User user, SurveyDesign survey)
    {
        if (container != null)
        {
            UserSchema schema = QueryService.get().getUserSchema(user, container, survey.getSchemaName());

            if (schema != null)
            {
                return schema.getTable(survey.getQueryName());
            }
        }
        return null;
    }

    public Map<String, SurveyDesign> getModuleSurveyDesigns(Container container, String schemaName)
    {
        Collection<Module> modules = container.getActiveModules();
        Map<String, SurveyDesign> designMap = new CaseInsensitiveHashMap<>();

        for (Module module : modules)
        {
            Collection<SurveyDesign> surveyDesigns = MODULE_SURVEY_DESIGN_CACHE.getResourceMap(module).get(schemaName);

            if (!surveyDesigns.isEmpty())
            {
                // CacheLoader returns empty collection (not null) for non-existent directories
                //noinspection ConstantConditions
                for (SurveyDesign design : surveyDesigns)
                {
                    if (design.getQueryName() != null)
                    {
                        designMap.put(design.getQueryName(), design);
                    }
                }
            }
        }

        return designMap;
    }

    private static class SurveyDesignResourceCacheHandler implements ModuleResourceCacheHandler<MultiValuedMap<String, SurveyDesign>>
    {
        @Override
        public MultiValuedMap<String, SurveyDesign> load(Stream<? extends Resource> resources, Module module)
        {
            return unmodifiable(resources
                .filter(getFilter(MODULE_RESOURCE_FILE_EXTENSION))
                .map(this::loadSurveyDesign)
                .filter(Objects::nonNull)
                .collect(MultiValuedMapCollectors.of(SurveyDesign::getSchemaName, Function.identity())));
        }

        private @Nullable SurveyDesign loadSurveyDesign(Resource r)
        {
            SurveyDesign design = null;

            try
            {
                String metadata = PageFlowUtil.getStreamContentsAsString(r.getInputStream());
                String errorMessage = validateSurveyMetadata(metadata);
                if (errorMessage == null)
                {
                    design = new SurveyDesign();

                    design.setMetadata(metadata);
                    design.setSchemaName(r.parent().getName());
                    design.setQueryName(r.getName().substring(0, r.getName().length() - MODULE_RESOURCE_FILE_EXTENSION.length()));
                }
                else
                {
                    _log.error(errorMessage);
                }
            }
            catch (IOException e)
            {
                _log.error(e.getMessage());
            }

            return design;
        }
    }

    /**
     * Used to validate the basic shape of the survey design JSON
     * @param metadata
     * @return the error messages
     */
    public static String validateSurveyMetadata(String metadata) throws IOException, JsonProcessingException
    {
        ObjectMapper mapper = new ObjectMapper();
        mapper.readTree(metadata);
        StringBuilder sb = new StringBuilder();

        try
        {
            JSONObject o = new JSONObject(metadata);

            if (o.has("survey"))
            {
                JSONObject jsonSurvey = o.getJSONObject("survey");
                if (jsonSurvey.has("sections"))
                {
                    JSONArray jsonSections = jsonSurvey.getJSONArray("sections");

                    if (jsonSections.length() == 0)
                        sb.append("The sections JSON array cannot be empty");

                    for (int i=0; i < jsonSections.length(); i++)
                    {
                        JSONObject section = jsonSections.getJSONObject(i);

                        if (!section.containsKey("title"))
                            sb.append("Each section must contain a property named : 'title'\n");
                        else if (section.has("questions"))
                        {
                            for (JSONObject question : section.getJSONArray("questions").toJSONObjectArray())
                            {
                                if (question == null)
                                {
                                    sb.append("Each of the elements in the question array must be an object and cannot be null\n");
                                    break;
                                }
                            }
                        }
                        else if (section.has("extAlias"))
                            section.getString("extAlias");
                        else
                            sb.append("Each section must contain a JSON array named 'questions' or contain a JSON string for the 'extAlias'\n");
                    }
                }
                else
                    sb.append("The survey object must contain a JSON array named : 'sections'");
            }
            else
                sb.append("Survey metadata must have a top level property named : 'survey'");
        }
        catch (JSONException e)
        {
            sb.append(e.getMessage());
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testGetMetaDataForColumn()
        {
            SurveyManager sm = SurveyManager.get();
            ColumnInfo ci = new ColumnInfo("test");
            DisplayColumn dc = new DataColumn(ci, false);
            Map<String, Object> metaDataMap = JsonWriter.getMetaData(dc, null, false, true, false);
            Map<String, Object> trimmedMap = sm.getTrimmedMetaData(metaDataMap);

            // we may not have all of the key properties in our trimmed map, but we shouldn't have any extras
            List<String> props = sm.getKeyMetaDataProps();
            for (String key : trimmedMap.keySet())
            {
                assertTrue("Unexpected property in the trimmed metadata map", props.contains(key));
            }

            // check a few of the key properties
            assertTrue("Unexpected property value", trimmedMap.get("name").equals("test"));
            assertTrue("Unexpected property value", trimmedMap.get("caption").equals("Test"));
            assertTrue("Unexpected property value", trimmedMap.get("shortCaption").equals("Test"));
            assertTrue("Unexpected property value", trimmedMap.get("hidden").equals(false));
            assertTrue("Unexpected property value", trimmedMap.get("jsonType").equals("string"));
            assertTrue("Unexpected property value", trimmedMap.get("inputType").equals("text"));
            assertTrue("Unexpected property value", trimmedMap.get("required").equals(true));
        }
    }
}
