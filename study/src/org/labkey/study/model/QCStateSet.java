/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
package org.labkey.study.model;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.qc.DataState;
import org.labkey.api.qc.QCStateManager;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
/*
 * User: brittp
 * Date: Jul 16, 2008
 * Time: 9:09:25 AM
 */

/**
 * QCStateSet is a helper class designed to make it easier to manage common groupings of QC states.
 * The most common combinations of states are:
 * 1. All public data
 * 2. All private data
 * 3. All data (public and private)
 * 4. Each individual state
 */
public class QCStateSet
{
    // use sets instead of arrays, just in case duplicates are somehow passed in:
    private Set<DataState> _states;
    private String _label;

    public static final String PUBLIC_STATES_LABEL = "Public/approved data";
    public static final String PRIVATE_STATES_LABEL = "Private/non-approved data";
    public static final String ALL_STATES_LABEL = "All data";
    private boolean _includeUnmarked;

    private QCStateSet(Container container, List<DataState> stateSet, boolean includeUnmarked, String label)
    {
        _states = new HashSet<>(stateSet);
        _includeUnmarked = includeUnmarked;
        _label = label;
        if (_label == null)
        {
            QCStateSet publicStates = getPublicStates(container);
            QCStateSet privateStates = getPrivateStates(container);
            QCStateSet allStates = getAllStates(container);
            if (publicStates.getStates().size() > 1 && this.equals(publicStates))
                _label = PUBLIC_STATES_LABEL;
            else if (privateStates.getStates().size() > 1 && this.equals(privateStates))
                _label = PRIVATE_STATES_LABEL;
            else if (allStates.getStates().size() > 1 && this.equals(allStates))
                _label = ALL_STATES_LABEL;
            else
            {
                StringBuilder setLabel = new StringBuilder();
                for (DataState state : stateSet)
                {
                    if (setLabel.length() > 0)
                        setLabel.append(", ");
                    setLabel.append(state.getLabel());
                }
                _label = setLabel.toString();
            }
        }
    }

    private QCStateSet(Container container, List<DataState> stateSet, boolean includeUnmarked)
    {
        this(container, stateSet, includeUnmarked, null);
    }

    private QCStateSet(Container container, int[] stateRowIds, boolean includeUnmarked)
    {
        this(container, getStatesForIds(container, stateRowIds), includeUnmarked);
    }

    private static List<DataState> getStatesForIds(Container container, int[] stateRowIds)
    {
        List<DataState> stateSet = new ArrayList<>();
        for (int stateRowId : stateRowIds)
        {
            DataState state = QCStateManager.getInstance().getStateForRowId(container, stateRowId);
            if (state != null)
                stateSet.add(state);
        }
        return stateSet;
    }

    public Set<DataState> getStates()
    {
        return _states;
    }

    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        QCStateSet that = (QCStateSet) o;

        if (_includeUnmarked != that._includeUnmarked) return false;
        return _states.equals(that._states);
    }

    public int hashCode()
    {
        int result;
        result = _states.hashCode();
        result = 31 * result + (_includeUnmarked ? 1 : 0);
        return result;
    }

    public String getLabel()
    {
        return _label;
    }

    public String getStateInClause(String rowIdColumnAlias)
    {
        // degenerate case: we've been asked to filter down to a QC state set that contains
        // no states, and doesn't include unmarked items.  This can happen, for example, when
        // all states are defined as 'private' and a user selects the default 'Public' set for viewing.
        // In this case, we'll simply return filter ensures zero rows are returned:
        if (_states.size() == 0 && !_includeUnmarked)
            return "0 = 1";
        
        StringBuilder sql = new StringBuilder();
        sql.append("(");
        if (_states.size() > 0)
        {
            sql.append(rowIdColumnAlias).append(" IN (");
            String comma = "";
            for (DataState state : _states)
            {
                sql.append(comma).append(state.getRowId());
                comma = ", ";
            }
            sql.append(")");
        }
        if (_includeUnmarked)
        {
            // our length will be 1 if our statement consists of just the open paren:
            if (sql.length() > 1)
                sql.append(" OR ");
            sql.append(rowIdColumnAlias).append(" IS NULL");
        }
        sql.append(")");
        return sql.toString();
    }

    private boolean isIncludeUnmarked()
    {
        return _includeUnmarked;
    }

    public String getFormValue()
    {
        StringBuilder formValue = new StringBuilder();
        for (DataState state : _states)
        {
            if (formValue.length() > 0)
                formValue.append(",");
            formValue.append(state.getRowId());
        }
        if (_includeUnmarked)
        {
            if (formValue.length() > 0)
                formValue.append(",");
            formValue.append("-1");
        }
        return formValue.toString();
    }

    public static QCStateSet getAllStates(Container container)
    {
        List<DataState> states = QCStateManager.getInstance().getStates(container);
        return new QCStateSet(container, states, true, ALL_STATES_LABEL);
    }

    public static QCStateSet getPublicStates(Container container)
    {
        StudyImpl study = StudyManager.getInstance().getStudy(container);
        List<DataState> selectedStates = new ArrayList<>();
        for (DataState state : QCStateManager.getInstance().getStates(container))
        {
            if (state.isPublicData())
                selectedStates.add(state);
        }
        if (study == null)
            return new QCStateSet(container, selectedStates, false, PUBLIC_STATES_LABEL);
        return new QCStateSet(container, selectedStates, study.isBlankQCStatePublic(), PUBLIC_STATES_LABEL);
    }

    public static QCStateSet getPrivateStates(Container container)
    {
        StudyImpl study = StudyManager.getInstance().getStudy(container);
        List<DataState> selectedStates = new ArrayList<>();
        for (DataState state : QCStateManager.getInstance().getStates(container))
        {
            if (!state.isPublicData())
                selectedStates.add(state);
        }
        if (study == null)
            return new QCStateSet(container, selectedStates, false, PRIVATE_STATES_LABEL);
        return new QCStateSet(container, selectedStates, !study.isBlankQCStatePublic(), PRIVATE_STATES_LABEL);
    }

    public static QCStateSet getDefaultStates(Container container)
    {
        StudyImpl study = StudyManager.getInstance().getStudy(container);
        return (study != null && study.isShowPrivateDataByDefault()) ? getAllStates(container) : getPublicStates(container);
    }

    public static QCStateSet getSelectedStates(Container container, int[] stateRowIds, boolean includeUnmarked)
    {
        if (stateRowIds == null || stateRowIds.length == 0)
            return getDefaultStates(container);
        return new QCStateSet(container, stateRowIds, includeUnmarked);
    }

    private static QCStateSet getSingletonSet(Container container, DataState state)
    {
        return new QCStateSet(container, Collections.singletonList(state), false, state.getLabel());
    }

    public static QCStateSet getSelectedStates(Container container, String formValue)
    {
        if (formValue == null || formValue.length() == 0)
            return getDefaultStates(container);

        String[] rowIdStrings = formValue.split(",");
        List<Integer> rowIds = new ArrayList<>();
        boolean includeUnmarked = false;
        try
        {
            for (String rowIdString : rowIdStrings)
            {
                int rowId = Integer.parseInt(rowIdString);
                if (rowId == -1)
                    includeUnmarked = true;
                else
                    rowIds.add(rowId);
            }
        }
        catch (NumberFormatException e)
        {
            // invalid form value, assume public data only:
            return getDefaultStates(container);
        }

        int[] rowIdArray = new int[rowIds.size()];
        for (int i = 0; i < rowIds.size(); i++)
            rowIdArray[i] = rowIds.get(i).intValue();
        return QCStateSet.getSelectedStates(container, rowIdArray, includeUnmarked);
    }

    public static List<QCStateSet> getSelectableSets(Container container)
    {
        List<QCStateSet> set = new ArrayList<>();

        QCStateSet publicStates = getPublicStates(container);
        if (publicStates.getStates().size() > 1 || publicStates.isIncludeUnmarked())
            set.add(QCStateSet.getPublicStates(container));

        QCStateSet privateStates = getPrivateStates(container);
        if (privateStates.getStates().size() > 1)
            set.add(QCStateSet.getPrivateStates(container));
    
        set.add(QCStateSet.getAllStates(container));

        for (DataState state : QCStateManager.getInstance().getStates(container))
            set.add(QCStateSet.getSingletonSet(container, state));
        return set;
    }

    public String toString()
    {
        return getLabel();
    }

    /**
     * Finds the first QC state parameter from the passed URL
     */
    @Nullable
    public static String getQCParameter(String dataRegionName, ActionURL url)
    {
        String key = dataRegionName + "." + FieldKey.fromParts("QCState", "Label") + SimpleFilter.SEPARATOR_CHAR;
        // finds the first QC state parameter
        Pair<String, String> param = url.getParameters().stream()
                .filter(p -> p.getKey().startsWith(key))
                .findFirst().orElse(null);

        return param != null ? param.getKey() : null;
    }

    public static String getQCUrlFilterKey(CompareType compareType, String dataRegionName)
    {
        return new CompareType.CompareClause(FieldKey.fromParts("QCState", "Label"), compareType, false).toURLParam( dataRegionName + ".").getKey();
    }

    private static Set<String> getQCLabelSet(String QCLabels)
    {
        return QCLabels != null ? new HashSet<>(Arrays.asList(QCLabels.split(";"))) : new HashSet<>();
    }

    public static String getQCUrlFilterValue(QCStateSet qcStates)
    {
        List<String> qcLabels = qcStates.getStates()
                .stream()
                .map(DataState::getLabel)
                .collect(Collectors.toList());
        String filterValue = new SimpleFilter.InClause(FieldKey.fromParts(""), qcLabels).toURLParam("").getValue();
        return filterValue != null ? filterValue : "";
    }

    public static String selectedQCStateLabelFromUrl(ActionURL actionURL, String dataRegionName, String QCStateLabel, String publicQCUrlFilterValue, String privateQCUrlFilterValue)
    {
        String eq = getQCUrlFilterKey(CompareType.EQUAL, dataRegionName);
        String in = getQCUrlFilterKey(CompareType.IN, dataRegionName);

        boolean urlHasEqFilter = actionURL.getParameter(eq) != null;
        boolean urlHasInFilter = actionURL.getParameter(in) != null;
        boolean urlHasNotInFilter = actionURL.getParameter(getQCUrlFilterKey(CompareType.NOT_IN, dataRegionName)) != null;
        boolean urlHasIsBlankFilter = actionURL.getParameter(getQCUrlFilterKey(CompareType.ISBLANK, dataRegionName)) != null;

        String urlValue = actionURL.getParameter(urlHasInFilter ? in : eq);
        Set<String> urlValueSet = urlValue != null ? new HashSet<>(Arrays.asList(urlValue.split(";"))) : new HashSet<>();
        boolean urlFilterIsPublicQCStates = QCStateLabel.equals(PUBLIC_STATES_LABEL) && getQCLabelSet(publicQCUrlFilterValue).equals(urlValueSet);
        boolean urlFilterIsPrivateQCStates = QCStateLabel.equals(PRIVATE_STATES_LABEL) && getQCLabelSet(privateQCUrlFilterValue).equals(urlValueSet);

        if (urlHasInFilter && urlFilterIsPublicQCStates)
        {
            return PUBLIC_STATES_LABEL;
        }
        else if (urlHasInFilter && urlFilterIsPrivateQCStates)
        {
            return PRIVATE_STATES_LABEL;
        }
        else if (QCStateLabel.equals(ALL_STATES_LABEL) && (urlHasInFilter || urlHasNotInFilter || urlHasIsBlankFilter || !urlHasEqFilter))
        {
            return ALL_STATES_LABEL;
        }
        else if (urlHasEqFilter && urlValueSet.equals(new HashSet<>(Arrays.asList(QCStateLabel))))
        {
            return QCStateLabel;
        }
        return null;
    }

    public static ActionURL getQCStateFilteredURL(ActionURL urlHelper, String QCUrlFilterValue, String dataRegionName, Container container)
    {
        String publicQCUrlFilterValue = getQCUrlFilterValue(QCStateSet.getPublicStates(container));
        String privateQCUrlFilterValue = getQCUrlFilterValue(QCStateSet.getPrivateStates(container));
        String eq = getQCUrlFilterKey(CompareType.EQUAL, dataRegionName);
        String in = getQCUrlFilterKey(CompareType.IN, dataRegionName);

        // Account for the [none] QC State
        StudyImpl study = StudyManager.getInstance().getStudy(container);
        if (study != null && study.isBlankQCStatePublic() && !publicQCUrlFilterValue.equals(""))
        {
            publicQCUrlFilterValue += ";";
        }
        else if (study != null && !study.isBlankQCStatePublic() && !privateQCUrlFilterValue.equals(""))
        {
            privateQCUrlFilterValue += ";";
        }

        urlHelper = urlHelper.deleteParameter(getQCUrlFilterKey(CompareType.ISBLANK, dataRegionName));
        urlHelper = urlHelper.deleteParameter(getQCUrlFilterKey(CompareType.NEQ_OR_NULL, dataRegionName));
        urlHelper = urlHelper.deleteParameter(getQCUrlFilterKey(CompareType.NOT_IN, dataRegionName));

        switch (QCUrlFilterValue)
        {
            case PUBLIC_STATES_LABEL -> {
                urlHelper = urlHelper.replaceParameter(in, publicQCUrlFilterValue);
                urlHelper = urlHelper.deleteParameter(eq);
            }
            case PRIVATE_STATES_LABEL -> {
                urlHelper = urlHelper.replaceParameter(in, privateQCUrlFilterValue);
                urlHelper = urlHelper.deleteParameter(eq);
            }
            case ALL_STATES_LABEL -> {
                urlHelper = urlHelper.deleteParameter(in);
                urlHelper = urlHelper.deleteParameter(eq);
            }
            default -> {
                urlHelper = urlHelper.replaceParameter(eq, QCUrlFilterValue);
                urlHelper = urlHelper.deleteParameter(in);
            }
        }

        return urlHelper;
    }
}
