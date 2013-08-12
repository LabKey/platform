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
import java.util.HashSet;
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
    private Container _container;
    private User _user;
    private int _uniquifier = 1;
    private JSONArray _questions;

    public SurveyMetadataBuilder(Container container, User user)
    {
        _container = container;
        _user = user;
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

    // Add section with a question for each user editable column in the table
    public void addSectionFromTable(@Nullable String sectionTitle, TableInfo tableInfo,
                                    Set<String> disableFieldsForEdit, int width, int labelWidth)
    {
        String sectionName = null != sectionTitle ? sectionTitle : tableInfo.getTitle();
        addSectionHelper(tableInfo, sectionName, tableInfo.getName().toLowerCase(), disableFieldsForEdit, width, labelWidth, true);
    }

    // Add section with a question for each user editable column in the dataset
    public void addSectionFromDataset(@Nullable String sectionTitle, String datasetName,
                                      Set<String> disableFieldsForEdit, int width, int labelWidth)
    {
        Study study = StudyService.get().getStudy(_container);
        if (null == study)
            throw new IllegalStateException("No study found.");
        DataSet dataset = study.getDataSetByName(datasetName);
        if (null == dataset)
            throw new IllegalStateException("Dataset " + datasetName + " not found.");

        String sectionName = null != sectionTitle ? sectionTitle : dataset.getLabel();
        TableInfo tableInfo = dataset.getTableInfo(_user);
        addSectionHelper(tableInfo, sectionName, datasetName.toLowerCase(), disableFieldsForEdit, width, labelWidth, false);
    }

    private void addSectionHelper(TableInfo tableInfo, String sectionName, String queryName,
                                  Set<String> disableFieldsForEdit, int width, int labelWidth, boolean initDisabled)
    {
        JSONObject section = new JSONObject();
        section.put("title", sectionName);
        section.put("queryName", queryName);
        List<ColumnInfo> columns = tableInfo.getUserEditableColumns();
        String uniquePrefix = queryName;
        JSONArray questions = new JSONArray();
        addTableHelper(tableInfo, section, columns, questions, uniquePrefix, disableFieldsForEdit, width, labelWidth);
        section.put("questions", questions);
        section.put("initDisabled", initDisabled);
        _surveyDesign.getJSONObject("survey").getJSONArray("sections").put(section);
    }

    private void addTableHelper(TableInfo tableInfo, JSONObject section, List<ColumnInfo> columns, JSONArray questions,
                                String uniquePrefix, Set<String> disableFieldsForEdit, int width, int labelWidth)
    {
        // Primary keys
        JSONArray pkArray = new JSONArray();
        for (ColumnInfo pkColumn : tableInfo.getPkColumns())
            pkArray.put(pkColumn.getName());
        section.put("primaryKeys", pkArray);

        for (ColumnInfo column : columns)
        {
            if (!column.isHidden())
            {
                String columnName = column.getName();
                String uniqueName = uniquePrefix + "-" + columnName.toLowerCase();
                JSONObject extConfig = new JSONObject();
                if (0 != width)
                    extConfig.put("width", width);
                if (0 != labelWidth)
                    extConfig.put("labelWidth", labelWidth);
                extConfig.put("name", uniqueName);
                extConfig.put("fieldLabel", column.getLabel());
                setType(extConfig, column, _container);
                JSONObject question = new JSONObject();

                boolean columnIsDependent = false;
                if (_fieldsSharedBetweenDatasets.containsKey(columnName.toLowerCase()))
                {
                    if (null == _fieldsSharedBetweenDatasets.get(columnName.toLowerCase()))
                    {
                        // First time name is seen
                        SharedFieldBuilder fieldBuilder = new SharedFieldBuilder();
                        fieldBuilder.independentQuestionName = uniqueName;
                        fieldBuilder.independentType = (String)extConfig.get("xtype");
                        _fieldsSharedBetweenDatasets.put(columnName.toLowerCase(), fieldBuilder);
                    }
                    else
                    {
                        SharedFieldBuilder fieldBuilder = _fieldsSharedBetweenDatasets.get(columnName.toLowerCase());
                        question.put("listeners", new JSONObject());
                        fieldBuilder.dependentListeners.add(question.getJSONObject("listeners"));
                        fieldBuilder.dependentNames.add(uniqueName);
                        extConfig.put("disabled", true);
                        extConfig.put("xtype", fieldBuilder.independentType);           // type must match independent type
                        extConfig.put("dependentField", true);
                        columnIsDependent = true;
                    }
                }
                else if (column.isReadOnly())
                {
                    extConfig.put("disabled", true);
                }
                question.put("extConfig", extConfig);
                question.put("name", columnName);           // Needs to be not lowercased
                question.put("required", !column.isNullable() && !columnIsDependent);
                if (null != disableFieldsForEdit && disableFieldsForEdit.contains(columnName))
                    question.put("disableForEdit", true);
                questions.put(question);
            }
        }
    }

    public JSONObject makeCustomSection(String sectionTitle, int width, int labelWidth, int padding)
    {
        JSONObject section = new JSONObject();
        section.put("title", sectionTitle);
        section.put("defaultLabelWidth", labelWidth);
        section.put("defaultWidth", width);
        if (padding >= 0)
            section.put("padding", padding);
        _questions = new JSONArray();
        return section;
    }

    public void addCheckboxedSubSection(JSONObject section, String checkboxLabel, TableInfo tableInfo, List<ColumnInfo> columns,
                                        String filterColumnName, Object filterValue, boolean initHidden)
    {
        String uniquifier = String.valueOf(_uniquifier++);
        String uniquePrefix = tableInfo.getName().toLowerCase() + uniquifier;
        int width = 150;      //(int)section.get("defaultWidth");
        int labelWidth = 100; //(int)section.get("defaultLabelWidth");
        String checkboxName = uniquePrefix + "-checkbox";
        JSONObject extConfig = new JSONObject();
        extConfig.put("width", width);
        extConfig.put("labelWidth", labelWidth);
        extConfig.put("name", checkboxName);
        extConfig.put("fieldLabel", checkboxLabel);
        extConfig.put("xtype", "checkbox");
        extConfig.put("submitValue", false);        // Checkbox only to control visibility of subsection
        JSONObject question = new JSONObject();
        question.put("extConfig", extConfig);
        question.put("name", checkboxName);
        _questions.put(question);

        JSONArray subSectionQuestions = new JSONArray();
        JSONObject subSection = new JSONObject();
        addTableHelper(tableInfo, subSection, columns, subSectionQuestions, uniquePrefix, new HashSet<String>(), 0, 0);
        subSection.put("subSection", true);
        subSection.put("hidden", initHidden);
        subSection.put("questions", subSectionQuestions);
        subSection.put("queryName", tableInfo.getName().toLowerCase());
        subSection.put("queryNameUniquifier", uniquifier);

        JSONObject filter = new JSONObject();
        filter.put("fieldName", filterColumnName);
        filter.put("fieldValue", filterValue);
        JSONArray filters = new JSONArray();
        filters.put(filter);
        subSection.put("filters", filters);

        JSONObject changeObject = new JSONObject();
        JSONArray questionNames = new JSONArray();
        questionNames.put(checkboxName);
        changeObject.put("question", questionNames);
        String function = "function(me, cmp, newValue, oldValue, values) {me.setVisible(newValue);}";
        changeObject.put("fn", function.toString());
        JSONObject listener = new JSONObject();
        listener.put("change", changeObject);
        subSection.put("listeners", listener);
        subSection.put("name", uniquePrefix + "-subsection");
        _questions.put(subSection);
    }

    public void addSection(JSONObject section)
    {
        if (null != _questions)
            section.put("questions", _questions);
        _surveyDesign.getJSONObject("survey").getJSONArray("sections").put(section);
    }

    private void setType(JSONObject extConfig, ColumnInfo column, Container container)
    {
        if (column.isLookup())
        {
            String lookupTableName = column.getFk().getLookupTableName();
            String displayField = "Description";
            if (lookupTableName.equalsIgnoreCase("location"))
                displayField = "Label";
            else if (column.getLegalName().toLowerCase().equalsIgnoreCase(StudyService.get().getSubjectColumnName(container)))
                displayField = StudyService.get().getSubjectColumnName(container);

            extConfig.put("xtype", "lk-genericcombo");
            extConfig.put("queryName", lookupTableName);
            extConfig.put("keyField", "RowId");
            extConfig.put("displayField", displayField);
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
        public String independentQuestionName;
        public String independentType;
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
                    questionNames.put(fieldBuilder.independentQuestionName);
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
                dependency.put(fieldBuilder.independentQuestionName, dependents);
            }
            _surveyDesign.getJSONObject("survey").put("fieldDependency", dependency);
        }
    }
}
