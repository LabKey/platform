/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

package org.labkey.experiment.samples;

import org.apache.log4j.Logger;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.view.ViewForm;
import org.labkey.experiment.api.ExpSampleSetImpl;
import org.labkey.experiment.api.ExperimentServiceImpl;

import java.io.IOException;
import java.util.Map;

public class UploadMaterialSetForm extends ViewForm
{
    @SuppressWarnings({"UnusedDeclaration"})
    static final private Logger _log = Logger.getLogger(UploadMaterialSetForm.class);
    private String name;
    private boolean importMoreSamples;
    private boolean nameReadOnly;
    private boolean createNewSampleSet = true;
    private boolean createNewColumnsOnExistingSampleSet = false;
    private String data;
    private String tsvData;

    private NameFormatChoice nameFormat;
    private String nameExpression;
    private int idColumn1 = -1;
    private int idColumn2 = -1;
    private int idColumn3 = -1;
    private int parentColumn = -1;
    private InsertUpdateChoice insertUpdateChoice;
    private Integer rowId;
    private boolean addUniqueSuffixForDuplicateNames = false;
    private boolean skipDerivation = false;
    private boolean mergeDerivations = true;

    private DataLoader _loader;

    public enum NameFormatChoice
    {
        NameExpression,
        IdColumns
    }

    public enum InsertUpdateChoice
    {
        /** Insert new rows only.  If the row already exists, throw an error. */
        insertOnly,

        /** Insert new rows only.  Ignore existing rows. */
        insertIgnore,

        /** Insert a new row or update an existing row. Upsert! */
        insertOrUpdate,

        /** Update an existing row.  If the row doesn't exist, throw an error. */
        updateOnly,
    }

    public String getTsvData()
    {
        return tsvData;
    }

    public void setTsvData(String tsvData)
    {
        this.tsvData = tsvData;
    }

    public Integer getRowId()
    {
        return rowId;
    }

    public void setRowId(Integer rowId)
    {
        this.rowId = rowId;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getName()
    {
        return name;
    }

    public boolean isImportMoreSamples()
    {
        return importMoreSamples;
    }

    public void setImportMoreSamples(boolean importMoreSamples)
    {
        this.importMoreSamples = importMoreSamples;
    }

    public void setNameReadOnly(boolean b)
    {
        nameReadOnly = b;
    }

    public boolean getNameReadOnly()
    {
        return nameReadOnly;
    }

    public boolean isCreateNewSampleSet()
    {
        return createNewSampleSet;
    }

    public void setCreateNewSampleSet(boolean createNewSampleSet)
    {
        this.createNewSampleSet = createNewSampleSet;
    }

    public boolean isCreateNewColumnsOnExistingSampleSet()
    {
        return createNewColumnsOnExistingSampleSet;
    }

    public void setCreateNewColumnsOnExistingSampleSet(boolean createNewColumnsOnExistingSampleSet)
    {
        this.createNewColumnsOnExistingSampleSet = createNewColumnsOnExistingSampleSet;
    }

    public boolean isAddUniqueSuffixForDuplicateNames()
    {
        return addUniqueSuffixForDuplicateNames;
    }

    public void setAddUniqueSuffixForDuplicateNames(boolean addUniqueSuffixForDuplicateNames)
    {
        this.addUniqueSuffixForDuplicateNames = addUniqueSuffixForDuplicateNames;
    }

    public boolean isSkipDerivation()
    {
        return skipDerivation;
    }

    public void setSkipDerivation(boolean skipDerivation)
    {
        this.skipDerivation = skipDerivation;
    }

    /**
     * When true, sample derivations are combined together by input parents to reduce the number of
     * derivations runs that will be created.
     * When false, a sample derivation run will be created for each Sample row that has inputs or outputs.
     */
    public boolean isMergeDerivations()
    {
        return mergeDerivations;
    }

    public void setMergeDerivations(boolean mergeDerivations)
    {
        this.mergeDerivations = mergeDerivations;
    }

    public void setData(String data)
    {
        this.data = data;
    }

    public String getData()
    {
        return data;
    }

    public DataLoader getLoader()
    {
        if (_loader == null)
        {
            TabLoader tabLoader = null;
            try
            {
                // NOTE: consider raising runtime exception if both are null (and removing from try block?)
                if (data != null)
                    tabLoader = new TabLoader(data, true);
                else if (tsvData != null)
                    tabLoader = new TabLoader(tsvData, true);
                tabLoader.setThrowOnErrors(true);
            }
            catch (IOException ioe)
            {
                _log.error(ioe);
            }

            _loader = tabLoader;

        }
        return _loader;
    }

    public void setLoader(DataLoader loader)
    {
        assert _loader == null;
        _loader = loader;
    }

    public String getNameFormat()
    {
        return nameFormat == null ? null : nameFormat.toString();
    }

    public NameFormatChoice getNameFormatEnum()
    {
        return nameFormat;
    }

    public void setNameFormat(String nameFormat)
    {
        this.nameFormat = NameFormatChoice.valueOf(nameFormat);
    }

    public String getNameExpression()
    {
        return nameExpression;
    }

    public void setNameExpression(String nameExpression)
    {
        this.nameExpression = nameExpression;
    }

    public void setIdColumn1(int idColumn)
    {
        this.idColumn1 = idColumn;
    }


    public int getIdColumn1()
    {
        return idColumn1;
    }

    public void setIdColumn2(int idColumn)
    {
        this.idColumn2 = idColumn;
    }

    public int getIdColumn2()
    {
        return idColumn2;
    }

    public void setIdColumn3(int idColumn)
    {
        this.idColumn3 = idColumn;
    }

    public int getIdColumn3()
    {
        return this.idColumn3;
    }

    public int getParentColumn()
    {
        return parentColumn;
    }

    public void setParentColumn(int parentColumn)
    {
        this.parentColumn = parentColumn;
    }

    public String getInsertUpdateChoice()
    {
        return insertUpdateChoice == null ? null : insertUpdateChoice.toString();
    }

    public InsertUpdateChoice getInsertUpdateChoiceEnum()
    {
        return insertUpdateChoice;
    }

    public void setInsertUpdateChoice(String choice)
    {
        insertUpdateChoice = InsertUpdateChoice.valueOf(choice);
    }

    public Map<Integer, String> getKeyOptions(boolean allowBlank)
    {
        return new UploadSamplesHelper(this).getIdFieldOptions(allowBlank);
    }

    public ExpSampleSetImpl getSampleSet()
    {
        if (name == null)
            return null;
        return ExperimentServiceImpl.get().getSampleSet(getContainer(), name, true);
    }
}
