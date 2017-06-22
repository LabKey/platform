/*
 * Copyright (c) 2014-2017 LabKey Corporation
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
package org.labkey.study.importer;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.ImportException;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.TableInfo;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.StudySchema;
import org.labkey.study.model.StudyManager;
import org.labkey.study.query.StudyQuerySchema;
import org.labkey.study.writer.StudyArchiveDataTypes;
import org.labkey.study.writer.StudyPropertiesWriter;
import org.labkey.study.xml.ExportDirType;
import org.springframework.validation.BindException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by klum on 3/17/14.
 */
public class StudyPropertiesImporter extends DefaultStudyDesignImporter
{
    Map<Object, Object> _personnelIdMap = new HashMap<>();
    Map<Object, Object> _objectiveIdMap = new HashMap<>();
    private SharedTableMapBuilder _personnelTableMapBuilder = new SharedTableMapBuilder(_personnelIdMap, "Label");
    private SharedTableMapBuilder _objectiveTableMapBuilder = new SharedTableMapBuilder(_objectiveIdMap, "Label");

    private String getDataType()
    {
        return StudyArchiveDataTypes.TOP_LEVEL_STUDY_PROPERTIES;
    }

    /**
     * Imports additional study related properties into the properties sub folder
     */
    public void process(StudyImportContext ctx, VirtualFile root, BindException errors) throws Exception
    {
        if (!ctx.isDataTypeSelected(getDataType()))
            return;

        ExportDirType dirType = ctx.getXml().getProperties();

        if (dirType != null)
        {
            VirtualFile vf = root.getDir(dirType.getDir());
            if (vf != null)
            {
                DbScope scope = StudySchema.getInstance().getSchema().getScope();
                try (DbScope.Transaction transaction = scope.ensureTransaction())
                {
                    boolean isDataspaceProject = ctx.isDataspaceProject();

                    // import any custom study design table properties
                    importTableinfo(ctx, vf, StudyPropertiesWriter.SCHEMA_FILENAME);

                    // import the objective and personnel tables
                    StudyQuerySchema schema = StudyQuerySchema.createSchema(ctx.getStudy(), ctx.getUser(), true);
                    StudyQuerySchema projectSchema = isDataspaceProject ? new StudyQuerySchema(StudyManager.getInstance().getStudy(ctx.getProject()), ctx.getUser(), true) : schema;

                    if (!isDataspaceProject)
                    {
                        // objective is cross-container and thus not supported for dataspace import
                        StudyQuerySchema.TablePackage objectiveTablePackage = schema.getTablePackage(ctx, projectSchema, StudyQuerySchema.OBJECTIVE_TABLE_NAME);
                        importTableData(ctx, vf, objectiveTablePackage, _objectiveTableMapBuilder,
                                new PreserveExistingProjectData(ctx.getUser(), objectiveTablePackage.getTableInfo(), "Label", "RowId", _objectiveIdMap));
                    }

                    StudyQuerySchema.TablePackage propertiesTablePackage = schema.getTablePackage(ctx, projectSchema, StudyQuerySchema.PROPERTIES_TABLE_NAME);
                    importTableData(ctx, vf, propertiesTablePackage, null, new StudyPropertiesTransform());

                    StudyQuerySchema.TablePackage personnelTablePackage = schema.getTablePackage(ctx, projectSchema, StudyQuerySchema.PERSONNEL_TABLE_NAME);
                    importTableData(ctx, vf, personnelTablePackage, _personnelTableMapBuilder,
                            new PersonnelTableTransform(ctx.getUser(), personnelTablePackage.getTableInfo(), "Label", "RowId", _personnelIdMap));

                    transaction.commit();
                }

                ctx.addTableIdMap("Personnel", _personnelIdMap);
                ctx.addTableIdMap("Objective", _objectiveIdMap);
            }
            else
                throw new ImportException("Unable to open the folder at : " + dirType.getDir());
        }
    }

    private class PersonnelTableTransform extends PreserveExistingProjectData
    {
        public PersonnelTableTransform(User user, TableInfo table, String fieldName, @Nullable String keyName, @Nullable Map<Object, Object> keyMap)
        {
            super(user, table, fieldName, keyName, keyMap);
        }

        @Override
        public List<Map<String, Object>> transform(StudyImportContext ctx, List<Map<String, Object>> origRows) throws ImportException
        {
            initializeData();
            List<Map<String, Object>> newRows = new ArrayList<>();

            for (Map<String, Object> row : origRows)
            {
                Map<String, Object> currentRow = new CaseInsensitiveHashMap<>();
                currentRow.putAll(row);

                String existingValueKey = getExistingValueKey(currentRow, _fieldNames);
                if (StringUtils.isNotEmpty(existingValueKey))
                {
                    if (!_existingValues.containsKey(existingValueKey))
                    {
                        // try to line up the userId field with an existing user with the same exported display name
                        currentRow.put("userId", null);
                        if (currentRow.containsKey("userId") && currentRow.containsKey("userId.displayName"))
                        {
                            Object displayName = currentRow.get("userId.displayName");
                            if (null != displayName)
                            {
                                User user = UserManager.getUserByDisplayName(displayName.toString());
                                currentRow.remove("userId.displayName");
                                if (user != null)
                                    currentRow.put("userId", user.getUserId());
                                else
                                    ctx.getLogger().warn("No user found matching the display name : " + displayName);
                            }
                            else
                                ctx.getLogger().warn("Null display name was found.");
                        }
                        newRows.add(currentRow);
                    }
                    else if (null != _keyMap)
                    {
                        Object key = currentRow.get(_keyName);
                        if (null != key)
                            _keyMap.put(key, _existingValues.get(existingValueKey));
                    }
                }
            }
            return newRows;
        }
    }

    private class StudyPropertiesTransform implements TransformHelper
    {
        Set PROPS_TO_SKIP = new CaseInsensitiveHashSet("Label", "StartDate", "EndDate", "SubjectNounSingular",
            "SubjectNounPlural", "SubjectColumnName", "Grant", "Investigator", "Species", "AssayPlan",
            "ParticipantAliasDatasetId", "ParticipantAliasProperty", "ParticipantAliasSourceProperty",
            "Description", "DescriptionRendererType");

        @Override
        public List<Map<String, Object>> transform(StudyImportContext ctx, List<Map<String, Object>> origRows) throws ImportException
        {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Map<String, Object> origRow : origRows)
            {
                Map<String, Object> row = new HashMap<>();
                for (String key : origRow.keySet())
                {
                    // Issue 30235: skip those properties that are set via TopLevelStudyPropertiesImporter
                    if (!PROPS_TO_SKIP.contains(key))
                        row.put(key, origRow.get(key));
                }
                rows.add(row);
            }
            return rows;
        }
    }
}
