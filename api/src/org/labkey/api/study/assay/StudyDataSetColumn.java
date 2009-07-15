/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.api.study.assay;

import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.ExprColumn;

import java.sql.Types;
import java.util.Map;

/**
 * User: kevink
 * Date: May 31, 2009 8:42:09 PM
 */
public class StudyDataSetColumn extends ExprColumn
{
    private Container _studyContainer;
    private int _protocolId;
    private AssayProvider _provider;

    public StudyDataSetColumn(TableInfo parent, String name, Container studyContainer, int protocolId, AssayProvider provider)
    {
        super(parent, name, new SQLFragment(getDatasetIdAlias(studyContainer) + ".datasetid"), Types.INTEGER);
        _studyContainer = studyContainer;
        _protocolId = protocolId;
        _provider = provider;
    }

    public Container getStudyContainer()
    {
        return _studyContainer;
    }

    public static String getDatasetIdAlias(Container studyContainer)
    {
        return "StudyDataJoin$" + studyContainer.getRowId();
    }

    public String getDatasetIdAlias()
    {
        return getDatasetIdAlias(_studyContainer);
    }

    @Override
    public void declareJoins(String parentAlias, Map<String, SQLFragment> map)
    {
        super.declareJoins(parentAlias, map);
        SQLFragment joinSql = new SQLFragment();
        String datasetAlias = getDatasetIdAlias();
        String studyDataAlias = "StudyData" + _studyContainer.getRowId();

        joinSql.append(" LEFT OUTER JOIN study.StudyData ").append(studyDataAlias).append(" ON ");
        joinSql.append(studyDataAlias).append("._key = CAST(" + parentAlias + "." + _provider.getTableMetadata().getResultRowIdFieldKey().getName() + " AS ");
        joinSql.append(getSqlDialect().sqlTypeNameFromSqlType(Types.VARCHAR)).append("(200)) AND ");
        joinSql.append(studyDataAlias).append(".container = ?\n");
        joinSql.add(_studyContainer.getId());

        joinSql.append("LEFT OUTER JOIN (\n");
        joinSql.append("SELECT MAX(study.Dataset.datasetid) AS datasetid, study.Dataset.container \n");
        joinSql.append("FROM study.Dataset \n");
        joinSql.append("WHERE study.Dataset.protocolid = ? \n");
        joinSql.add(_protocolId);
        joinSql.append("GROUP BY study.Dataset.container\n");
        joinSql.append(") ").append(datasetAlias).append(" ON ").append(studyDataAlias).append(".container = ").append(datasetAlias);
        joinSql.append(".container AND ").append(studyDataAlias).append(".datasetid = ").append(datasetAlias).append(".datasetid");
        map.put(datasetAlias, joinSql);
    }
}
