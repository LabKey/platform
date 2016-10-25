/*
 * Copyright (c) 2010-2016 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.*;
import org.labkey.api.query.*;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.specimen.SpecimenController;
import org.labkey.study.model.Vial;

import java.sql.SQLException;
import java.util.*;

/**
 * User: brittp
 * Date: Mar 19, 2010 12:48:46 PM
 */
public class RequestabilityManager
{
    public static class RuleBean
    {
        private Integer _rowId; // SERIAL NOT NULL
        private Container _container; // EntityId NOT NULL
        private int _sortOrder; // INTEGER NOT NULL
        private RuleType _ruleType; // VARCHAR(50)
        private String _ruleData; // VARCHAR(250)
        private MarkType _markType; // VARCHAR(30)

        public RuleBean()
        {
            // no-arg constructor for creation via Table-layer reflection
        }

        public RuleBean(RequestableRule rule)
        {
            _container = rule.getContainer();
            _ruleType = rule.getType();
            _ruleData = rule.getRuleData();
            if (_ruleData != null && _ruleData.length() > 250)
                throw new IllegalArgumentException("Rule data is currently limited to 250 characters.");
            _markType = rule.getMarkType();
        }


        public Integer getRowId()
        {
            return _rowId;
        }

        public void setRowId(Integer rowId)
        {
            _rowId = rowId;
        }

        public Container getContainer()
        {
            return _container;
        }

        public void setContainer(Container container)
        {
            _container = container;
        }

        public int getSortOrder()
        {
            return _sortOrder;
        }

        public void setSortOrder(int sortOrder)
        {
            _sortOrder = sortOrder;
        }

        public String getRuleType()
        {
            return _ruleType.name();
        }

        public void setRuleType(String ruleType)
        {
            _ruleType = RuleType.valueOf(ruleType);
        }

        public String getMarkType()
        {
            return _markType.name();
        }

        public void setMarkType(String markType)
        {
            _markType = MarkType.valueOf(markType);
        }

        public String getRuleData()
        {
            return _ruleData;
        }

        public void setRuleData(String ruleData)
        {
            _ruleData = ruleData;
        }

        public RequestableRule createRule()
        {
            return _ruleType.createRule(_container, _ruleData);
        }

    }

    public static enum MarkType
    {
        AVAILABLE
                {
                    @Override
                    public String getLabel()
                    {
                        return "Available";
                    }},

        UNAVAILABLE
                {
                    @Override
                    public String getLabel()
                    {
                        return "Unavailable";
                    }},

        AVAILABLE_OR_UNAVAILABLE
                {
                    @Override
                    public String getLabel()
                    {
                        return "Available or unavailable";
                    }};


        public abstract String getLabel();
    }


    public enum RuleType
    {
        ADMIN_OVERRIDE
                {
                    @Override
                    public RequestableRule createRule(Container container, String ruleData)
                    {
                        return new AdminOverrideRule(container);
                    }

                    @Override
                    public String getName()
                    {
                        return "Administrator Override";
                    }

                    @Override
                    public String getDescription()
                    {
                        return "Marks vials available or unavailable based on the 'requestable' column in the specimen data feed.";
                    }

                    @Override
                    public MarkType getDefaultMarkType()
                    {
                        return MarkType.AVAILABLE_OR_UNAVAILABLE;
                    }

                    @Override
                    public ActionURL getDefaultTestURL(Container container)
                    {
                        ActionURL testURL = SpecimenController.getSamplesURL(container);
                        testURL.addParameter(SpecimenController.SampleViewTypeForm.PARAMS.showVials, true);
                        testURL.addFilter("SpecimenDetail", FieldKey.fromParts("Requestable"), CompareType.NONBLANK, null);
                        return testURL;
                    }},
        AT_REPOSITORY
                {
                    @Override
                    public RequestableRule createRule(Container container, String ruleData)
                    {
                        return new RepositoryRule(container);
                    }

                    @Override
                    public String getName()
                    {
                        return "At Repository Check";
                    }

                    @Override
                    public String getDescription()
                    {
                        return "Marks vials unavailable if they are not currently held by a repository.";
                    }

                    @Override
                    public MarkType getDefaultMarkType()
                    {
                        return MarkType.UNAVAILABLE;
                    }

                    @Override
                    public ActionURL getDefaultTestURL(Container container)
                    {
                        ActionURL testURL = SpecimenController.getSamplesURL(container);
                        testURL.addParameter(SpecimenController.SampleViewTypeForm.PARAMS.showVials, true);
                        testURL.addFilter("SpecimenDetail", FieldKey.fromParts("AtRepository"), CompareType.EQUAL, Boolean.FALSE);
                        return testURL;

                    }},
        LOCKED_IN_REQUEST
                {
                    @Override
                    public RequestableRule createRule(Container container, String ruleData)
                    {
                        return new LockedInRequestRule(container);
                    }

                    @Override
                    public String getName()
                    {
                        return "Locked In Request Check";
                    }

                    @Override
                    public String getDescription()
                    {
                        return "Marks vials unavailable if they are part of an active specimen request.";
                    }

                    @Override
                    public MarkType getDefaultMarkType()
                    {
                        return MarkType.UNAVAILABLE;
                    }

                    @Override
                    public ActionURL getDefaultTestURL(Container container)
                    {
                        ActionURL testURL = SpecimenController.getSamplesURL(container);
                        testURL.addParameter(SpecimenController.SampleViewTypeForm.PARAMS.showVials, true);
                        testURL.addFilter("SpecimenDetail", FieldKey.fromParts("LockedInRequest"), CompareType.EQUAL, Boolean.TRUE);
                        return testURL;

                    }},
        LOCKED_WHILE_PROCESSING
                {
                    @Override
                    public RequestableRule createRule(Container container, String ruleData)
                    {
                        return new LockedWhileProcessingRule(container);
                    }

                    @Override
                    public String getName()
                    {
                        return "Locked While Processing Check";
                    }

                    @Override
                    public String getDescription()
                    {
                        return "Marks vials unavailable if they are being processed.";
                    }

                    @Override
                    public MarkType getDefaultMarkType()
                    {
                        return MarkType.UNAVAILABLE;
                    }

                    @Override
                    public ActionURL getDefaultTestURL(Container container)
                    {
                        return null;

                    }},
        CUSTOM_QUERY
                {
                    @Override
                    public RequestableRule createRule(Container container, String ruleData)
                    {
                        return new CustomQueryRule(container, ruleData);
                    }

                    @Override
                    public String getName()
                    {
                        return "Custom Query";
                    }

                    @Override
                    public String getDescription()
                    {
                        return "Marks vials available or unavailable based on the results of an administrator-defined query.";
                    }

                    @Override
                    public MarkType getDefaultMarkType()
                    {
                        return null;
                    }

                    @Override
                    public ActionURL getDefaultTestURL(Container container)
                    {
                        return null;
                    }};

        public abstract RequestableRule createRule(Container container, String ruleData);

        public abstract String getName();

        public abstract String getDescription();

        public abstract MarkType getDefaultMarkType();

        @Nullable
        public abstract ActionURL getDefaultTestURL(Container container);
    }

    private static final RequestabilityManager _instance = new RequestabilityManager();

    private RequestabilityManager()
    {
        // private constructor to ensure use of singleton.
    }

    public static class InvalidRuleException extends Exception
    {
        public InvalidRuleException(String msg)
        {
            super(msg);
        }

        public InvalidRuleException(String msg, Exception cause)
        {
            super(msg, cause);
        }
    }

    public static abstract class RequestableRule
    {
        protected Container _container;

        public RequestableRule(Container container)
        {
            _container = container;
        }

        public abstract SQLFragment getAvailabilityReason();

        public int updateRequestability(User user, List<Vial> vials) throws InvalidRuleException
        {
            TableInfo tableInfoVial = StudySchema.getInstance().getTableInfoVial(_container);
            if (null == tableInfoVial)
                throw new IllegalStateException("Expected Vial table to exist.");

            SQLFragment reason = getAvailabilityReason();
            SQLFragment updateSQL = new SQLFragment("UPDATE ");
            updateSQL.append(tableInfoVial.getSelectName()).append(" SET ");
            updateSQL.append(getAvailableAssignmentSQL());
            updateSQL.append(", AvailabilityReason = ");
            updateSQL.append(reason);
            updateSQL.append(" WHERE ");
            updateSQL.append(getFilterSQL(_container, user, vials));
            return new SqlExecutor(StudySchema.getInstance().getSchema()).execute(updateSQL);
        }

        protected SQLFragment getAvailableAssignmentSQL()
        {
            MarkType type = getMarkType();
            if (!(type == MarkType.AVAILABLE || type == MarkType.UNAVAILABLE))
                throw new IllegalStateException("Rules that do not specify a simple mark type must override getUpdateSQL.");

            return new SQLFragment("Available = ?", type == MarkType.AVAILABLE ? Boolean.TRUE : Boolean.FALSE);
        }

        protected SQLFragment getGlobalUniqueIdInSQL(List<Vial> vials)
        {
            SQLFragment sql = new SQLFragment();
            if (vials != null && vials.size() > 0)
            {
                sql.append("GlobalUniqueId ");
                sql.append(getSpecimenGlobalUniqueIdSet(vials));
            }
            return sql;
        }

        protected SQLFragment getSpecimenGlobalUniqueIdSet(List<Vial> vials)
        {
            List<String> specimenIDs = new ArrayList<>(vials.size());
            for (Vial vial : vials)
            {
                specimenIDs.add(vial.getGlobalUniqueId());
            }
            return StudySchema.getInstance().getSqlDialect().appendInClauseSql(new SQLFragment(), specimenIDs);
        }

        protected abstract SQLFragment getFilterSQL(Container container, User user, List<Vial> vials) throws InvalidRuleException;

        public MarkType getMarkType()
        {
            return getType().getDefaultMarkType();
        }

        public String getRuleData()
        {
            return null;
        }

        public abstract RuleType getType();

        @Nullable
        public ActionURL getTestURL(User user)
        {
            return getType().getDefaultTestURL(_container);
        }

        public String getName()
        {
            String name = getType().getName();
            String extra = getExtraName();
            if (extra != null)
                name += ": " + extra;
            return name;
        }

        public Container getContainer()
        {
            return _container;
        }
/*
       If the rule's type isn't enough to identify the rule, getExtraName can be used to provide the user
       with additional information.
        */
        protected String getExtraName()
        {
            return null;
        }
    }

    public static final String CUSTOM_QUERY_DATA_SEPARATOR = "~";
    private static class CustomQueryRule extends RequestableRule
    {
        private String _schemaName;
        private String _queryName;
        private String _viewName;
        private boolean _markRequestable;

        protected CustomQueryRule(Container container, String state)
        {
            super(container);
            String[] values = state.split(CUSTOM_QUERY_DATA_SEPARATOR);
            _schemaName = values[0];
            _queryName = values[1];
            _viewName = values[2];
            if (_viewName != null && _viewName.length() == 0)
                _viewName = null;
            _markRequestable = Boolean.parseBoolean(values[3]);
        }

        @Override
        public ActionURL getTestURL(User user)
        {
            ActionURL url = QueryService.get().urlFor(user, _container, QueryAction.executeQuery, _schemaName, _queryName);
            if (_viewName != null)
                url.addParameter(QueryView.DATAREGIONNAME_DEFAULT + "." + QueryParam.viewName.name(), _viewName);
            return url;
        }

        @Override
        public MarkType getMarkType()
        {
            return _markRequestable ? MarkType.AVAILABLE : MarkType.UNAVAILABLE;
        }

        public String getRuleData()
        {
            return _schemaName + CUSTOM_QUERY_DATA_SEPARATOR +
                    _queryName + CUSTOM_QUERY_DATA_SEPARATOR +
                    (_viewName != null ? _viewName : "") + CUSTOM_QUERY_DATA_SEPARATOR +
                    _markRequestable;
        }

        public SQLFragment getFilterSQL(Container container, User user, List<Vial> vials) throws InvalidRuleException
        {
            SimpleFilter viewFilter = new SimpleFilter();
            if (_viewName != null)
            {
                CustomView baseView = QueryService.get().getCustomView(user, container, user, _schemaName, _queryName, _viewName);
                if (baseView != null)
                {
                    // copy our saved view filter into our SimpleFilter via an ActionURL (yuck...)
                    ActionURL url = new ActionURL();
                    baseView.applyFilterAndSortToURL(url, "mockDataRegion");
                    viewFilter.addUrlFilters(url, "mockDataRegion");
                }
                else
                    throw new InvalidRuleException("Could not find view " + _viewName + " on query " + _queryName + " in schema " + _schemaName + ".");
            }

            if (vials != null && vials.size() > 0)
            {
                Set<String> globalUniqueIds = new HashSet<>();
                for (Vial vial : vials)
                    globalUniqueIds.add(vial.getGlobalUniqueId());
                viewFilter.addCondition(new SimpleFilter.InClause(FieldKey.fromParts("GlobalUniqueId"), globalUniqueIds));
           }

            UserSchema schema = QueryService.get().getUserSchema(user, container, _schemaName);
            TableInfo tinfo = schema.getTable(_queryName);
            if (null == tinfo)
                throw new InvalidRuleException("Query " + _queryName + " in schema " + _schemaName + " not found.");
            ColumnInfo globalUniqueIdCol = tinfo.getColumn("GlobalUniqueId");
            if (globalUniqueIdCol == null)
                throw new InvalidRuleException("Query " + _queryName + " in schema " + _schemaName + " doesn't include required column name 'GlobalUniqueId'.");


            SQLFragment vialListSql = Table.getSelectSQL(tinfo, Collections.singleton(globalUniqueIdCol), viewFilter, null);

            SQLFragment updateFilter = new SQLFragment("GlobalUniqueId IN (SELECT GlobalUniqueId FROM (");
            updateFilter.append(vialListSql);
            updateFilter.append(") X )");

            return updateFilter;
        }

        @Override
        public SQLFragment getAvailabilityReason()
        {
            return new SQLFragment("'This vial is " + getMarkType().getLabel().toLowerCase() + " because it was found in the set called \"" + _queryName + "\".'");
        }

        public String getExtraName()
        {
            return _schemaName + "." + _queryName + (_viewName != null ? ", view " + _viewName : "");
        }

        @Override
        public RuleType getType()
        {
            return RuleType.CUSTOM_QUERY;
        }
    }

    private static class RepositoryRule extends RequestableRule
    {
        public RepositoryRule(Container container)
        {
            super(container);
        }

        public SQLFragment getFilterSQL(Container container, User user, List<Vial> vials) throws InvalidRuleException
        {
            SQLFragment sql = new SQLFragment("AtRepository = ?", Boolean.FALSE);
            if (vials != null && vials.size() > 0)
                sql.append(" AND ").append(getGlobalUniqueIdInSQL(vials));
            return sql;
        }

        @Override
        public RuleType getType()
        {
            return RuleType.AT_REPOSITORY;
        }

        @Override
        public SQLFragment getAvailabilityReason()
        {
            return new SQLFragment("'This vial is unavailable because it is not currently held by a repository.'");
        }
    }

    private static class AdminOverrideRule extends RequestableRule
    {
        public AdminOverrideRule(Container container)
        {
            super(container);
        }

        @Override
        public SQLFragment getAvailabilityReason()
        {
            SQLFragment sql = new SQLFragment("CASE WHEN Requestable = ? THEN 'This vial''s availability status was set by an administrator. Please contact an administrator for more information.' ELSE ' ' END");
            sql.add(false);
            return sql;
        }

        @Override
        public RuleType getType()
        {
            return RuleType.ADMIN_OVERRIDE;
        }

        public SQLFragment getFilterSQL(Container container, User user, List<Vial> vials) throws InvalidRuleException
        {
            SQLFragment sql = new SQLFragment("Requestable IS NOT NULL");
            if (vials != null && vials.size() > 0)
                sql.append(" AND ").append(getGlobalUniqueIdInSQL(vials));
            return sql;
        }

        @Override
        protected SQLFragment getAvailableAssignmentSQL()
        {
            return new SQLFragment("Available = Requestable");
        }
    }

    private static class LockedInRequestRule extends RequestableRule
    {
        public LockedInRequestRule(Container container)
        {
            super(container);
        }

        public SQLFragment getFilterSQL(Container container, User user, List<Vial> vials) throws InvalidRuleException
        {
            SQLFragment sql = new SQLFragment("LockedInRequest = ?", Boolean.TRUE);
            if (vials != null && vials.size() > 0)
                sql.append(" AND ").append(getGlobalUniqueIdInSQL(vials));
            return sql;
        }

        @Override
        public RuleType getType()
        {
            return RuleType.LOCKED_IN_REQUEST;
        }

        @Override
        public SQLFragment getAvailabilityReason()
        {
            return new SQLFragment("'This vial is unavailable because it is locked in a specimen request.'");
        }
    }

    private static class LockedWhileProcessingRule extends RequestableRule
    {
        public LockedWhileProcessingRule(Container container)
        {
            super(container);
        }

        @Override
        public SQLFragment getFilterSQL(Container container, User user, List<Vial> vials) throws InvalidRuleException
        {
            TableInfo tableInfoVial = StudySchema.getInstance().getTableInfoVial(_container);
            if (null == tableInfoVial)
                throw new IllegalStateException("Expected Vial table to exist.");

            SQLFragment sql = new SQLFragment();
            if (vials != null && vials.size() > 0)
                sql.append(getGlobalUniqueIdInSQL(vials)).append(" AND ");

            sql.append(" RowId IN (SELECT SV.RowId FROM ").append(tableInfoVial.getSelectName()).append(" SV")
                .append(" LEFT OUTER JOIN (SELECT Container, SampleRequestId, SpecimenGlobalUniqueId FROM ")
                .append(StudySchema.getInstance().getTableInfoSampleRequestSpecimen().getSelectName())
                .append(") SRS ON (GlobalUniqueId = SpecimenGlobalUniqueId)")
                .append(" LEFT OUTER JOIN (SELECT RowId, StatusId FROM ")
                .append(StudySchema.getInstance().getTableInfoSampleRequest().getSelectName())
                .append(") SR ON (SR.RowId = SRS.SampleRequestId)")
                .append(" LEFT OUTER JOIN (SELECT RowId, FinalState FROM ")
                .append(StudySchema.getInstance().getTableInfoSampleRequestStatus().getSelectName())
                .append(") SRST ON (SRST.RowId = SR.StatusId)")
                .append(" WHERE SRS.Container = ? AND SRST.FinalState = ?");
            sql.add(container.getId());
            sql.add(false);
            if (vials != null && vials.size() > 0)
                sql.append(" AND SpecimenGlobalUniqueId ").append(getSpecimenGlobalUniqueIdSet(vials));

            sql.append(")");
            return sql;
        }

        @Override
        public RuleType getType()
        {
            return RuleType.LOCKED_WHILE_PROCESSING;
        }

        @Override
        public SQLFragment getAvailabilityReason()
        {
            return new SQLFragment("'This vial is unavailable because it is being processed.'");
        }
    }

    public static RequestabilityManager getInstance()
    {
        return _instance;
    }

    public List<RequestableRule> getRules(Container container)
    {
        TableInfo ruleTableInfo = StudySchema.getInstance().getTableInfoSampleAvailabilityRule();
        List<RuleBean> ruleBeans = new TableSelector(ruleTableInfo,
                SimpleFilter.createContainerFilter(container),
                new Sort("SortOrder")).getArrayList(RuleBean.class);
        List<RequestableRule> rules = new ArrayList<>();
        for (RuleBean bean : ruleBeans)
            rules.add(bean.createRule());

        return rules;
    }

    public void saveRules(Container container, User user, List<RequestableRule> rules) throws SQLException
    {
        TableInfo ruleTableInfo = StudySchema.getInstance().getTableInfoSampleAvailabilityRule();
        DbScope scope = StudySchema.getInstance().getSchema().getScope();
        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            Table.delete(ruleTableInfo, new SimpleFilter(FieldKey.fromString("Container"), container.getId()));

            int sortOrder = 0;
            for (RequestableRule rule : rules)
            {
                RuleBean bean = new RuleBean(rule);
                bean.setSortOrder(sortOrder++);
                Table.insert(user, ruleTableInfo, bean);
            }

            transaction.commit();
        }
    }

    public void setDefaultRules(Container container, User user) throws SQLException
    {
        List<RequestableRule> rules = new ArrayList<>();
        rules.add(new RepositoryRule(container));
        rules.add(new AdminOverrideRule(container));
        rules.add(new LockedInRequestRule(container));
        saveRules(container, user, rules);
    }

    private void updateRequestability(Container container, User user, boolean resetToAvailable, Logger logger, List<Vial> vials) throws InvalidRuleException
    {
        DbSchema schema = StudySchema.getInstance().getSchema();
        assert schema.getScope().isTransactionActive() : "Requestability should always be updated within a transaction wrapping the addition/removal of vials.";
        if (logger != null)
            logger.info("Updating vial availability...");

        if (resetToAvailable)
        {
            TableInfo tableInfoVial = StudySchema.getInstance().getTableInfoVial(container);
            if (null == tableInfoVial)
                throw new IllegalStateException("Expected Vial table to exist.");

            if (logger != null)
                logger.info("\tResetting vials to default available state.");
            SQLFragment updateSQL = new SQLFragment("UPDATE ");
            updateSQL.append(tableInfoVial.getSelectName())
                     .append(" SET Available = ?, AvailabilityReason = ? ");
            updateSQL.add(Boolean.TRUE);
            updateSQL.add(null);
            if (vials != null && vials.size() > 0)
            {
                updateSQL.append("WHERE RowId IN (");
                String sep = "";
                for (Vial vial : vials)
                {
                    updateSQL.append(sep).append("?");
                    sep = ", ";
                    updateSQL.add(vial.getRowId());
                }
                updateSQL.append(")");
            }
            new SqlExecutor(schema).execute(updateSQL);

            if (logger != null)
                logger.info("\tReset complete.");
        }

        for (RequestableRule rule : getRules(container))
        {
            String action = rule.getMarkType().getLabel().toLowerCase();
            if (logger != null)
                logger.info("\tMarking vials " + action + " based on " + rule.getName());
            int updatedCount = rule.updateRequestability(user, vials);
            if (logger != null)
                logger.info("\t" + updatedCount + " vials marked " + action + ".");
        }

        if (logger != null)
            logger.info("Vial availability update complete.");

    }

    public void updateRequestability(Container container, User user, List<Vial> vials) throws InvalidRuleException
    {
        updateRequestability(container, user, true, null, vials);
    }

    public void updateRequestability(Container container, User user, boolean resetToAvailable, boolean editingSpecimens, Logger logger) throws InvalidRuleException
    {
        updateRequestability(container, user, (resetToAvailable || editingSpecimens), logger, null);
    }

    public static String makeSpecimenUnavailableMessage(Vial vial, @Nullable String additionalText)
    {
        String message = String.format("Specimen %s%s%s",
                vial.getGlobalUniqueId(),
                null != vial.getAvailabilityReason() ? vial.getAvailabilityReason().replaceFirst("This vial", "") : " is not available.",
                null != additionalText ? " " + additionalText : "");
        return message;
    }
}
