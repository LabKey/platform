/*
 * Copyright (c) 2012-2015 LabKey Corporation
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
package org.labkey.api.data;

import java.util.List;

/**
 * User: kevink
 * Date: 6/19/12
 */
public interface CrosstabTableInfo extends TableInfo
{
    /**
     * Returns the {@link org.labkey.api.data.CrosstabSettings} object passed to the constructor.
     *
     * @return The current settings
     */
    CrosstabSettings getSettings();

    /**
     * Returns the list of column dimension members (aka. pivot values)
     * of the selected table or query.
     *
     * @return The list of column dimension members (aka. pivot values)
     */
    List<CrosstabMember> getColMembers();

    /**
     * Returns the corresponding CrosstabMeasure for a fieldkey that refers to the measure without
     * a particular column dimension member (i.e., the measure column in customize view).
     *
     * @param fieldKey The field key
     * @return The corresponding CrosstabMeasure
     */
    CrosstabMeasure getMeasureFromKey(String fieldKey);

    /**
     * Sets the aggregate filters. Aggregate filters are applied post-aggregation, but
     * pre-pivoting, so they can filter out rows with aggregates that do not match the filters.
     * This is typically called only by the {@link org.labkey.api.query.CrosstabView}
     * class to set the aggregate filters established by the user in the customize view dialog.
     *
     * You may pass null to clear the set of aggregate filters.
     *
     * @param filter The set of aggregate filters.
     */
    void setAggregateFilter(Filter filter);

    /**
     * Returns the desired default sort for the table.
     *
     * @return The default sort
     */
    Sort getDefaultSort();
}
