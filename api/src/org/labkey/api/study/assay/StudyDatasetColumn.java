/*
 * Copyright (c) 2009-2015 LabKey Corporation
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
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.security.User;
import org.labkey.api.study.Dataset;

import java.sql.Types;
import java.util.Map;

/**
 * User: kevink
 * Date: May 31, 2009 8:42:09 PM
 */
public class StudyDatasetColumn extends ExprColumn
{
    private AssayProvider _provider;
    private final Dataset _assayDataset;
    private final User _user;

    public StudyDatasetColumn(TableInfo parent, String name, AssayProvider provider, Dataset assayDataset, User user)
    {
        super(parent, name, new SQLFragment("(CASE WHEN " + getDatasetIdAlias(assayDataset.getContainer()) +
                "._key IS NOT NULL THEN " + assayDataset.getDatasetId() + " ELSE NULL END)"), JdbcType.INTEGER);
        _provider = provider;
        _assayDataset = assayDataset;
        _user = user;
    }

    public Container getStudyContainer()
    {
        return _assayDataset.getContainer();
    }

    public static String getDatasetIdAlias(Container studyContainer)
    {
        return "StudyDataJoin$" + studyContainer.getRowId();
    }

    public String getDatasetIdAlias()
    {
        return getDatasetIdAlias(getStudyContainer());
    }

    @Override
    public void declareJoins(String parentAlias, Map<String, SQLFragment> map)
    {
        super.declareJoins(parentAlias, map);
        SQLFragment joinSql = new SQLFragment();
        String datasetAlias = getDatasetIdAlias();
        Container studyContainer = getStudyContainer();
        TableInfo datasetTable = _assayDataset.getTableInfo(_user);
        ExpProtocol protocol = _assayDataset.getAssayProtocol();

        joinSql.appendComment("<StudyDatasetColumn.join " + studyContainer.getPath() + ">", getSqlDialect());
        joinSql.append(" LEFT OUTER JOIN ").append(datasetTable.getFromSQL(datasetAlias)).append(" ON ");
        joinSql.append(datasetAlias).append("._key = CAST(").append(parentAlias).append(".").append(_provider.getTableMetadata(protocol).getResultRowIdFieldKey().getName()).append(" AS ");
        joinSql.append(getSqlDialect().sqlTypeNameFromSqlType(Types.VARCHAR)).append("(200))");
        joinSql.appendComment("</StudyDatasetColumn.join>", getSqlDialect());
        map.put(datasetAlias, joinSql);
    }
}
