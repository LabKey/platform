/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
    private boolean _isUpdateMode = false;      // false == Insert mode; true == Update mode

    public SurveyMetadataBuilder(Container container, User user, boolean isUpdateMode)
    {
        _container = container;
        _user = user;
        _isUpdateMode = isUpdateMode;
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

    public void setWidths(int mainPanelWidth, int sidebarWidth)
    {
        _surveyDesign.getJSONObject("survey").put("mainPanelWidth", mainPanelWidth);
        _surveyDesign.getJSONObject("survey").put("sidebarWidth", sidebarWidth);
    }

    // Must be called before adding sections;
    // First section will always have the "live" field upon which the others are dependent
    public void addFieldsToShareBetweenDatasets(String... args)
    {
        for (String field : args)
            _fieldsSharedBetweenDatasets.put(field.toLowerCase(), null);
    }

    // Add section with a question for each user editable column in the table
    public void addSectionFromTable(TableInfo tableInfo, @Nullable String sectionTitle, @Nullable String sectionSubTitle,
                                    Set<String> disableFieldsForEdit, Set<String> propagateColumnNames, int width, int labelWidth)
    {
        String sectionName = null != sectionTitle ? sectionTitle : tableInfo.getTitle();
        addSectionHelper(tableInfo, sectionName, sectionSubTitle, tableInfo.getName().toLowerCase(), disableFieldsForEdit, propagateColumnNames, width, labelWidth, false);
    }

    private void addSectionHelper(TableInfo tableInfo, String sectionName, String sectionSubTitle, String queryName,
                                  Set<String> disableFieldsForEdit, Set<String> propagateColumnNames, int width, int labelWidth, boolean initDisabled)
    {
        JSONObject section = new JSONObject();
        section.put("title", sectionName);
        section.put("subTitle", sectionSubTitle);
        section.put("queryName", queryName);

        // Compile list of columns to show
        List<ColumnInfo> columns = new ArrayList<>();
        for (ColumnInfo columnInfo : tableInfo.getUserEditableColumns())
            if ((_isUpdateMode && columnInfo.isShownInUpdateView()) ||
                (!_isUpdateMode && columnInfo.isShownInInsertView()))
            {
                columns.add(columnInfo);
            }

        String uniquePrefix = queryName;
        JSONArray questions = new JSONArray();
        addTableHelper(tableInfo, section, columns, null, null, questions, uniquePrefix, disableFieldsForEdit,
                propagateColumnNames, null, width, labelWidth, false);
        section.put("questions", questions);
        section.put("initDisabled", initDisabled);
        _surveyDesign.getJSONObject("survey").getJSONArray("sections").put(section);
    }

    /**
     * Adds a subsection whose visibility is controlled by a checkbox and that contains a "current samples" section
     * plus optionally an "Add samples" section
     * @param sectionPath path of sections/subsection to this subsection
     * @param checkboxLabel label for the checkbox
     * @param tableInfo table from which to show results
     * @param columns tableInfo columns to show
     * @param filterColumnName tableInfo column to filter on
     * @param filterValue value for filter
     * @param collapseColumnName if includeAddSample and if specified, replace this column in the Add section with a
     *                           column to take a value, which is how many sample rows to generate
     * @param collapseColumnLabel label to use for the collapseColumn replacement in the Add section
     * @param sizeColumnChoices in the Add section, add a column with these size choices
     * @param initHidden true means section (except checkbox) should be initially hidden
     * @param includeAddSample true means include an Add sample section
     */
    public void addCheckboxedSubSection(String sectionPath, String checkboxLabel, TableInfo tableInfo, List<ColumnInfo> columns,
                                        String filterColumnName, Object filterValue, @Nullable String collapseColumnName,
                                        @Nullable String collapseColumnLabel, @Nullable List<String> sizeColumnChoices,
                                        boolean initHidden, boolean includeAddSample, boolean allowDelete)
    {
        // Build metadata like this
        //  subSection
        //      layoutHorizontal: false
        //      questions
        //          subSection (if add new sample requested)
        //              layoutHorizontal: true
        //              queryInfo fields
        //              saveAction: insert
        //              questions:
        //                  add more samples button
        //                  headings 1 thru n
        //                  fields 1 thru n
        //          subSection
        //              layoutHorizontal: true
        //              queryInfo fields
        //              saveAction: update
        //              hasCollapseColumn: if requested by non-null collapseColumnName
        //              questions:
        //                  headings 1 thru n
        //                  fields 1 thru n

        String uniquifier = String.valueOf(_uniquifier++);
        String uniquePrefix = tableInfo.getName().toLowerCase() + uniquifier;
        String checkboxName = uniquePrefix + "-checkbox";

        // Checkbox
        JSONObject extConfig = new JSONObject();
        extConfig.put("name", checkboxName);
        extConfig.put("boxLabel", checkboxLabel);
        extConfig.put("xtype", "checkbox");
        extConfig.put("submitValue", false);        // Checkbox only to control visibility of subsection
        extConfig.put("margin", "2 2 2 4");
        JSONObject question = new JSONObject();
        question.put("extConfig", extConfig);
        question.put("name", checkboxName);
        _questions.put(question);

        JSONArray outerSubSectionQuestions = new JSONArray();
        JSONObject outerSubSection = new JSONObject();
        outerSubSection.put("subSection", true);
        outerSubSection.put("collapsible", false);
        outerSubSection.put("padding", 1);
        outerSubSection.put("hidden", initHidden);
        outerSubSection.put("name", uniquePrefix + "-main");
        String currentSubSectionName = uniquePrefix + "-subsection";

        // Add Sample section
        if (includeAddSample)
        {
            int numColumnsForAddSection = columns.size() + (null != sizeColumnChoices ? 1 : 0);
            JSONArray addSubSectionQuestions = new JSONArray();
            JSONObject addSubSection = new JSONObject();
            String uniqueAddPrefix = uniquePrefix + "-add";
            String uniqueAddSectionName = uniqueAddPrefix + "-subsection";

            addTableHelper(tableInfo, addSubSection, columns, collapseColumnName, collapseColumnLabel, addSubSectionQuestions, uniqueAddPrefix,
                    new HashSet<String>(), new HashSet<String>(), sizeColumnChoices, 0, 0, true);
            addSubSection.put("subSection", true);
            addSubSection.put("questions", addSubSectionQuestions);
            addSubSection.put("queryName", tableInfo.getName().toLowerCase());
            addSubSection.put("queryNameUniquifier", uniquifier);
            addSubSection.put("name", uniqueAddSectionName);
            addSubSection.put("siblingName", currentSubSectionName);
            addSubSection.put("layoutHorizontal", true);
            addSubSection.put("numColumns", numColumnsForAddSection);
            addSubSection.put("saveAction", "insert");
            addSubSection.put("filters", makeFilters(filterColumnName, filterValue));
            addSubSection.put("dontPopulate", true);
            addSubSection.put("title", "Add New Samples");
            addSubSection.put("header", true);
            addSubSection.put("collapsible", true);
            addSubSection.put("collapsed", true);
            if (null != collapseColumnName)
                addSubSection.put("hasCollapseColumn", true);

            addSubSection.put("toolbarButton", "+");
            addSubSection.put("toolbarButtonHandlerKey", uniqueAddSectionName);
            addSubSection.put("toolbarButtonName", "add");
            addSubSection.put("toolbarButtonTooltip", "Add another row for new samples");
            outerSubSectionQuestions.put(addSubSection);
        }

        // Update expanded section
        JSONArray subSectionQuestions = new JSONArray();
        JSONObject subSection = new JSONObject();
        addTableHelper(tableInfo, subSection, columns, null, null, subSectionQuestions, uniquePrefix, new HashSet<String>(),
                new HashSet<String>(), null, 0, 0, true);
        subSection.put("subSection", true);
        subSection.put("collapsible", true);
        subSection.put("header", true);
        subSection.put("questions", subSectionQuestions);
        subSection.put("queryName", tableInfo.getName().toLowerCase());
        subSection.put("queryNameUniquifier", uniquifier);
        subSection.put("name", currentSubSectionName);
        subSection.put("layoutHorizontal", true);
        subSection.put("numColumns", columns.size());
        subSection.put("saveAction", "update");
        subSection.put("filters", makeFilters(filterColumnName, filterValue));
        if (allowDelete)
        {
            subSection.put("allowDelete", allowDelete);
            subSection.put("toolbarButton", "-");
            subSection.put("toolbarButtonHandlerKey", currentSubSectionName);
            subSection.put("toolbarButtonName", "delete");
            subSection.put("toolbarButtonTooltip", "Delete samples selected by checkboxes");
        }
        subSection.put("title", "Current Samples");
        outerSubSectionQuestions.put(subSection);

        String subSectionPath = sectionPath + "/" + uniquePrefix + "-main";
        String function = "function(me, cmp, newValue, oldValue, values) {me.setVisible(newValue);" +
                " if (newValue) this.populateSubSection('" + subSectionPath + "');}";
        outerSubSection.put("listeners", makeListener(checkboxName, function));
        outerSubSection.put("dontClearDirtyFieldWhenHiding", true);
        outerSubSection.put("questions", outerSubSectionQuestions);
        _questions.put(outerSubSection);
    }

    private void addTableHelper(TableInfo tableInfo, JSONObject section, List<ColumnInfo> columns,
                                @Nullable String collapseColumnName, @Nullable String collapseColumnLabel,
                                JSONArray questions, String uniquePrefix, Set<String> disableFieldsForEdit, Set<String> propagateColumnNames,
                                @Nullable List<String> sizeColumnChoices, int width, int labelWidth, boolean separateLabels)
    {
        // Primary keys
        JSONArray pkArray = new JSONArray();
        for (ColumnInfo pkColumn : tableInfo.getPkColumns())
            pkArray.put(pkColumn.getName());
        section.put("primaryKeys", pkArray);

        if (separateLabels)
        {
            section.put("hasHeadingRow", true);
            for (ColumnInfo column : columns)
            {
                if (!column.isHidden())
                    questions.put(makeHeading(column.getName(), column.getLabel(), collapseColumnName, collapseColumnLabel, uniquePrefix, width));
            }

            if (null != sizeColumnChoices)
                questions.put(makeHeading("Size", "Size", null, null, uniquePrefix, width));
        }

        for (String columnName : propagateColumnNames)
        {
            ColumnInfo columnInfo = tableInfo.getColumn(columnName);
            SharedFieldBuilder fieldBuilder = _fieldsSharedBetweenDatasets.get(columnName.toLowerCase());
            if ((null == columnInfo || !columns.contains(columnInfo)) && null != fieldBuilder)
            {
                // Not already a column here and independent has already been seen
                String uniqueName = uniquePrefix + "-" + columnName.toLowerCase();
                JSONObject extConfig = new JSONObject();
                if (0 != width)
                    extConfig.put("width", width);

                extConfig.put("name", uniqueName);
                extConfig.put("submitValue", false);
                if (!separateLabels)
                {
                    if (0 != labelWidth)
                        extConfig.put("labelWidth", labelWidth);
                    extConfig.put("fieldLabel", fieldBuilder.label);
                }

                JSONObject question = new JSONObject();
                setDependentDisplayField(question, extConfig, fieldBuilder, uniqueName);
                question.put("extConfig", extConfig);
                question.put("name", columnName);           // Needs to be not lowercased
                questions.put(question);
            }
        }

        for (ColumnInfo column : columns)
        {
            if (!column.isHidden())
            {
                String columnName = column.getName();
                String uniqueName = uniquePrefix + "-" + columnName.toLowerCase();
                JSONObject extConfig = new JSONObject();
                if (0 != width)
                    extConfig.put("width", width);
                else if (columnName.equalsIgnoreCase("SequenceNum") || columnName.equalsIgnoreCase("VolumeUnits"))
                    extConfig.put("width", 80);
                else if (null != collapseColumnName && columnName.equalsIgnoreCase("ProtocolNumber"))
                    extConfig.put("width", 80);

                extConfig.put("name", uniqueName);
                if (!separateLabels)
                {
                    if (0 != labelWidth)
                        extConfig.put("labelWidth", labelWidth);
                    extConfig.put("fieldLabel", column.getLabel());
                }

                JSONObject question = new JSONObject();
                if (null != collapseColumnName && columnName.equalsIgnoreCase(collapseColumnName))
                {
                    question.put("collapseColumn", true);
                    extConfig.put("xtype", JdbcType.INTEGER.xtype);
                    setIntegerTypeAttrs(extConfig);
                }
                else
                {
                    setType(extConfig, column);
                }

                boolean columnIsDependent = false;
                if (_fieldsSharedBetweenDatasets.containsKey(columnName.toLowerCase()))
                {
                    if (null == _fieldsSharedBetweenDatasets.get(columnName.toLowerCase()))
                    {
                        // First time name is seen
                        SharedFieldBuilder fieldBuilder = new SharedFieldBuilder();
                        fieldBuilder.independentQuestionName = uniqueName;
                        fieldBuilder.independentType = (String)extConfig.get("xtype");
                        fieldBuilder.label = column.getLabel();
                        _fieldsSharedBetweenDatasets.put(columnName.toLowerCase(), fieldBuilder);
                    }
                    else
                    {
                        SharedFieldBuilder fieldBuilder = _fieldsSharedBetweenDatasets.get(columnName.toLowerCase());
                        setDependentDisplayField(question, extConfig, fieldBuilder, uniqueName);
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

        if (null != sizeColumnChoices)
        {
            // Add a Size column comboBox with these choices
            String uniqueName = uniquePrefix + "-" + "size";
            JSONObject extConfig = new JSONObject();
            if (0 != width)
                extConfig.put("width", width);
            extConfig.put("name", uniqueName);
            if (!separateLabels)
            {
                if (0 != labelWidth)
                    extConfig.put("labelWidth", labelWidth);
                extConfig.put("fieldLabel", "Size");
            }
            extConfig.put("xtype", "combo");
            extConfig.put("displayField", "display");
            extConfig.put("valueField", "value");
            extConfig.put("value", sizeColumnChoices.get(0));
            extConfig.put("editable", false);

            JSONArray data = new JSONArray();
            int index = 0;
            for (String choice : sizeColumnChoices)
            {
                JSONObject value = new JSONObject();
                value.put("value", index++);
                value.put("display", choice);
                data.put(value);
            }
            JSONArray fields = new JSONArray();
            fields.put("display");
            fields.put("value");
            JSONObject store = new JSONObject();
            store.put("data", data);
            store.put("fields", fields);
            extConfig.put("store", store);

            JSONObject question = new JSONObject();
            question.put("extConfig", extConfig);
            question.put("name", "Size");           // Needs to be not lowercased
            questions.put(question);
        }
    }

    private JSONObject makeHeading(String name, String label, @Nullable String collapseColumnName,
                                   @Nullable String collapseColumnLabel, String uniquePrefix, int width)
    {
        JSONObject extConfig = new JSONObject();
        if (0 != width)
            extConfig.put("width", width);
        extConfig.put("xtype", "displayfield");

        if (null != collapseColumnName && name.equalsIgnoreCase(collapseColumnName))
        {
            if (null != collapseColumnLabel)
                label = collapseColumnLabel;
            else
                label = "# " + label + "s";
        }
        extConfig.put("value", label);

        String uniqueName = uniquePrefix + "-heading-" + name.toLowerCase();
        extConfig.put("name", uniqueName);
        JSONObject question = new JSONObject();
        question.put("extConfig", extConfig);
        return question;
    }

    public JSONObject makeCustomSection(String sectionTitle, @Nullable String sectionSubTitle, int width, int labelWidth, int padding)
    {
        JSONObject section = new JSONObject();
        section.put("title", sectionTitle);
        if (null != sectionSubTitle)
            section.put("subTitle", sectionSubTitle);
        section.put("defaultLabelWidth", labelWidth);
        section.put("defaultWidth", width);
        if (padding >= 0)
            section.put("padding", padding);
        _questions = new JSONArray();
        return section;
    }

    public JSONObject makeCollapsibleSubSection(String title)
    {
        JSONObject subSection = new JSONObject();
        subSection.put("subSection", true);
        subSection.put("collapsible", true);
        subSection.put("collapsed", true);
        subSection.put("header", true);
        subSection.put("border", 1);
        subSection.put("title", title);
        _questions = new JSONArray();
        return subSection;
    }

    public void completeSubSection(JSONObject section)
    {
        if (null != _questions)
            section.put("questions", _questions);
    }

    public void addQuestion(JSONObject question)
    {
        _questions.put(question);
    }

    public void addDependentDisplayField(ColumnInfo independentColumn, String uniquePrefix, int width, int labelWidth)
    {
        String columnName = independentColumn.getName();
        SharedFieldBuilder fieldBuilder = _fieldsSharedBetweenDatasets.get(columnName.toLowerCase());
        if (null != fieldBuilder)
        {
            String uniqueName = uniquePrefix + "-" + columnName.toLowerCase();
            JSONObject extConfig = new JSONObject();
            extConfig.put("width", width);
            extConfig.put("labelWidth", labelWidth);
            extConfig.put("name", uniqueName);
            extConfig.put("fieldLabel", independentColumn.getLabel());
            setType(extConfig, independentColumn);
            JSONObject question = new JSONObject();
            setDependentDisplayField(question, extConfig, fieldBuilder, uniqueName);
            question.put("extConfig", extConfig);
            question.put("name", columnName);           // Needs to be not lowercased
            question.put("required", false);
            _questions.put(question);
        }

    }

    private void setDependentDisplayField(JSONObject question, JSONObject extConfig, SharedFieldBuilder fieldBuilder, String uniqueName)
    {
        question.put("listeners", new JSONObject());
        fieldBuilder.dependentListeners.add(question.getJSONObject("listeners"));
        fieldBuilder.dependentNames.add(uniqueName);
        if ("datefield" == fieldBuilder.independentType)
        {
            extConfig.put("xtype", "datefield");
            extConfig.put("disabled", true);
            extConfig.put("disabledCls", "");
        }
        else
        {
            extConfig.put("xtype", "displayfield");
        }
        extConfig.put("dependentField", true);
    }

    public void addSection(JSONObject section)
    {
        if (null != _questions)
            section.put("questions", _questions);
        _surveyDesign.getJSONObject("survey").getJSONArray("sections").put(section);
    }

    public void addCustomInfo(Map<String, Object> customInfo)
    {
        // Any custom information the survey director wants to add to the metadata for use by the client
        _surveyDesign.getJSONObject("survey").put("customInfo", customInfo);
    }

    private void setType(JSONObject extConfig, ColumnInfo column)
    {
        boolean isTypeSet = false;
        if (column.isLookup())
        {
            TableInfo lookupTableInfo = column.getFk().getLookupTableInfo();
            if (null != lookupTableInfo && null != lookupTableInfo.getUserSchema())
            {
                extConfig.put("xtype", "lk-genericcombo");
                extConfig.put("schemaName", lookupTableInfo.getUserSchema().getName());
                extConfig.put("queryName", lookupTableInfo.getName());
                List<String> pkColumnNames = lookupTableInfo.getPkColumnNames();
                extConfig.put("keyField", !pkColumnNames.isEmpty() ? pkColumnNames.get(0) : "RowId");   // We only support 1 PK
                extConfig.put("displayField", lookupTableInfo.getTitleColumn());
                extConfig.put("emptyText", "Select...");
                isTypeSet = true;
            }
            else
            {
                extConfig.put("disabled", "true");      // Lookup does not have UserSchema
            }
        }

        if (!isTypeSet)
        {
            if (column.getJdbcType().equals(JdbcType.BOOLEAN))
            {
                extConfig.put("xtype", "checkbox");
            }
            else if (column.getJdbcType().equals(JdbcType.INTEGER) || column.getJdbcType().equals(JdbcType.BIGINT))
            {
                extConfig.put("xtype", column.getJdbcType().xtype);
                setIntegerTypeAttrs(extConfig);
            }
            else if (column.getJdbcType().equals(JdbcType.REAL) || column.getJdbcType().equals(JdbcType.DOUBLE) || column.getJdbcType().equals(JdbcType.DECIMAL))
            {
                extConfig.put("xtype", column.getJdbcType().xtype);
                extConfig.put("allowDecimals", true);
                extConfig.put("hideTrigger", true);
            }
            else
            {
                extConfig.put("xtype", column.getJdbcType().xtype);
            }
        }
    }

    private void setIntegerTypeAttrs(JSONObject extConfig)
    {
        extConfig.put("allowDecimals", false);
        extConfig.put("hideTrigger", true);
        extConfig.put("minValue", 0);
    }

    private JSONArray makeFilters(String filterColumnName, Object filterValue)
    {
        JSONObject filter = new JSONObject();
        filter.put("fieldName", filterColumnName);
        filter.put("fieldValue", filterValue);
        JSONArray filters = new JSONArray();
        filters.put(filter);
        return filters;
    }

    private JSONObject makeListener(String questionName, String function)
    {
        JSONObject changeObject = new JSONObject();
        JSONArray questionNames = new JSONArray();
        questionNames.put(questionName);
        changeObject.put("question", questionNames);
        changeObject.put("fn", function);
        JSONObject listener = new JSONObject();
        listener.put("change", changeObject);
        return listener;
    }

    private class SharedFieldBuilder
    {
        public String independentQuestionName;
        public String independentType;
        public String label;
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
                if (null != fieldBuilder)
                {
                    for (JSONObject dependentListener : fieldBuilder.dependentListeners)
                    {
                        JSONObject changeObject = new JSONObject();
                        JSONArray questionNames = new JSONArray();
                        questionNames.put(fieldBuilder.independentQuestionName);
                        changeObject.put("question", questionNames);
                        String function = "function(me, cmp, newValue, oldValue, values) {me.setValue(newValue);}";
                        changeObject.put("fn", function);
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
            }
            _surveyDesign.getJSONObject("survey").put("fieldDependency", dependency);
        }
    }
}
