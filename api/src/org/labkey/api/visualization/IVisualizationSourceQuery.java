/*
 * Copyright (c) 2011-2017 LabKey Corporation
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
package org.labkey.api.visualization;

import org.labkey.api.data.Container;
import org.labkey.api.query.UserSchema;
import org.labkey.api.util.Pair;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: brittp
 * Date: Jun 7, 2011 2:13:35 PM
 */
public interface IVisualizationSourceQuery
{
    Set<VisualizationSourceColumn> getSelects(VisualizationSourceColumn.Factory factory, boolean includeRequiredExtraCols);

    VisualizationSourceColumn getPivot();

    String getSQL(VisualizationSourceColumn.Factory factory) throws SQLGenerationException;

    IVisualizationSourceQuery getJoinTarget();

    String getSQLAlias();

    String getAlias();

    List<Pair<VisualizationSourceColumn, VisualizationSourceColumn>> getJoinConditions();

    Set<VisualizationSourceColumn> getSorts();

    Set<VisualizationAggregateColumn> getAggregates();

    boolean contains(VisualizationSourceColumn column);

    String getSelectListName(Set<VisualizationSourceColumn> selectAliases);

    Map<String, Set<VisualizationSourceColumn>> getColumnNameToValueAliasMap(VisualizationSourceColumn.Factory factory, boolean measuresOnly);

    UserSchema getSchema();

    Container getContainer();

    String getQueryName();

    void addSelect(VisualizationSourceColumn select, boolean measure);

    boolean isSkipVisitJoin();

    boolean isVisitTagQuery();

    /**
     * True if any select or aggregate requires a left join explicitly. This is an override for any columns
     * that might require some form of an INNER JOIN.
     * @return
     */
    boolean isRequireLeftJoin();
}
