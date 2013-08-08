/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.api.survey.model;

import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.TableInfo;
import org.labkey.api.security.User;
import org.labkey.api.study.DataSet;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: davebradlee
 * Date: 7/11/13
 * Time: 2:51 PM
 */
public class SurveyMetadataBuilder
{
    private JSONObject _surveyDesign;
    private Map<String, SharedFieldBuilder> _fieldsSharedBetweenDatasets = new HashMap<>();
    private boolean _completedSharedFields = false;

    public SurveyMetadataBuilder()
    {
        JSONObject survey = new JSONObject();
        survey.put("layout", "auto");
        survey.put("sections", new JSONArray());

        Map<String, Object> designObjects = new HashMap<>();
        designObjects.put("survey", survey);
        _surveyDesign = new JSONObject(designObjects);
    }

    public JSONObject getSurveyDesign()
    {
        if (!_completedSharedFields)
        {
            completeSharedFields();
            _completedSharedFields = true;
        }
        return _surveyDesign;
    }

    public void setLayout(String layout)
    {
        _surveyDesign.getJSONObject("survey").put("layout", layout);
    }

    // Must be called before adding sections;
    // First section will always have the "live" field upon which the others are dependent
    public void addFieldsToShareBetweenDatasets(String... args)
    {
        for (String field : args)
            _fieldsSharedBetweenDatasets.put(field.toLowerCase(), null);
    }

    public void addSectionFromDataset(@Nullable String sectionTitle, Container container, User user, String datasetName,
                                      Set<String> disableFieldsForEdit, int width, int labelWidth)
    {
        Study study = StudyService.get().getStudy(container);
        if (null == study)
            throw new IllegalStateException("No study found.");
        DataSet dataset = study.getDataSetByName(datasetName);
        if (null == dataset)
            throw new IllegalStateException("Dataset " + datasetName + " not found.");

        JSONObject section = new JSONObject();
        String sectionName = null != sectionTitle ? sectionTitle : dataset.getLabel();
        section.put("title", sectionName);
        section.put("queryName", datasetName.toLowerCase());

        TableInfo tableInfo = dataset.getTableInfo(user);

        // Primary keys
        JSONArray pkArray = new JSONArray();
        for (ColumnInfo pkColumn : tableInfo.getPkColumns())
            pkArray.put(pkColumn.getName());
        section.put("primaryKeys", pkArray);

        JSONArray questions = new JSONArray();
        for (ColumnInfo column : tableInfo.getColumns())
        {
            if (column.isUserEditable())
            {
                String columnName = column.getName();
                String uniqueName = datasetName.toLowerCase() + "-" + columnName.toLowerCase();
                JSONObject extConfig = new JSONObject();
                extConfig.put("width", width);
                extConfig.put("labelWidth", labelWidth);
                extConfig.put("name", uniqueName);
                extConfig.put("fieldLabel", column.getLabel());
                setType(extConfig, column);
                JSONObject question = new JSONObject();

                boolean columnIsDependent = false;
                if (_fieldsSharedBetweenDatasets.containsKey(columnName.toLowerCase()))
                {
                    if (null == _fieldsSharedBetweenDatasets.get(columnName.toLowerCase()))
                    {
                        // First time name is seen
                        SharedFieldBuilder fieldBuilder = new SharedFieldBuilder();
                        fieldBuilder.independent = uniqueName;
                        _fieldsSharedBetweenDatasets.put(columnName.toLowerCase(), fieldBuilder);
                    }
                    else
                    {
                        extConfig.put("disabled", true);
                        question.put("listeners", new JSONObject());
                        SharedFieldBuilder fieldBuilder = _fieldsSharedBetweenDatasets.get(columnName.toLowerCase());
                        fieldBuilder.dependentListeners.add(question.getJSONObject("listeners"));
                        fieldBuilder.dependentNames.add(uniqueName);
                        columnIsDependent = true;
                    }
                }
                question.put("extConfig", extConfig);
                question.put("name", columnName);           // Needs to be not lowercased
                question.put("required", !column.isNullable() && !columnIsDependent);
                if (null != disableFieldsForEdit && disableFieldsForEdit.contains(columnName))
                    question.put("disableForEdit", true);
                questions.put(question);
            }
        }
        section.put("questions", questions);
        _surveyDesign.getJSONObject("survey").getJSONArray("sections").put(section);
    }

    private void setType(JSONObject extConfig, ColumnInfo column)
    {
        if (column.isLookup())
        {
            extConfig.put("xtype", "lk-userscombo");
            extConfig.put("emptyText", "Select...");
        }
        else
        {
            if (column.getJdbcType().equals(JdbcType.BOOLEAN))
            {
                extConfig.put("xtype", "checkbox");
            }
            else if (column.getJdbcType().equals(JdbcType.INTEGER) || column.getJdbcType().equals(JdbcType.BIGINT))
            {
                extConfig.put("xtype", column.getJdbcType().xtype);
                extConfig.put("allowDecimals", false);
            }
            else if (column.getJdbcType().equals(JdbcType.REAL) || column.getJdbcType().equals(JdbcType.DOUBLE) || column.getJdbcType().equals(JdbcType.DECIMAL))
            {
                extConfig.put("xtype", column.getJdbcType().xtype);
                extConfig.put("allowDecimals", true);
                extConfig.put("spinDownEnabled", false);
                extConfig.put("spinUpEnabled", false);
            }
            else
            {
                extConfig.put("xtype", column.getJdbcType().xtype);
            }
        }
    }

    private class SharedFieldBuilder
    {
        public String independent;
        public List<JSONObject> dependentListeners = new ArrayList<>();
        public List<String> dependentNames = new ArrayList<>();
    }

    private void completeSharedFields()
    {
        if (!_fieldsSharedBetweenDatasets.isEmpty())
        {
            JSONObject dependency = new JSONObject();
            for (SharedFieldBuilder fieldBuilder : _fieldsSharedBetweenDatasets.values())
            {
                // Construct listeners
                for (JSONObject dependentListener : fieldBuilder.dependentListeners)
                {
                    JSONObject changeObject = new JSONObject();
                    JSONArray questionNames = new JSONArray();
                    questionNames.put(fieldBuilder.independent);
                    changeObject.put("question", questionNames);
                    String function = "function(me, cmp, newValue, oldValue, values) {me.setValue(newValue);}";
                    changeObject.put("fn", function.toString());
                    dependentListener.put("change", changeObject);
                }

                // Construct dependency map
                JSONArray dependents = new JSONArray();
                for (String dependentName : fieldBuilder.dependentNames)
                {
                    dependents.put(dependentName);
                }
                dependency.put(fieldBuilder.independent, dependents);
            }
            _surveyDesign.getJSONObject("survey").put("fieldDependency", dependency);
        }
    }
}
