/*
 * Copyright (c) 2015-2017 LabKey Corporation
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
package org.labkey.query.reports;

import org.apache.commons.collections4.MultiMapUtils;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.Container;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.report.ReportDB;
import org.labkey.api.reports.report.ReportDescriptor;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Created by adam on 8/13/2015.
 */
public class DatabaseReportCache
{
    private static final Cache<Container, ReportCollections> REPORT_DB_CACHE = CacheManager.getBlockingCache(CacheManager.UNLIMITED, CacheManager.DAY, "Database Report Cache", (c, argument) -> new ReportCollections(c));

    private static class ReportCollections
    {
        private final Map<Integer, Report> _rowIdMap;
        private final Map<String, Report> _entityIdMap;
        private final MultiValuedMap<String, Report> _reportKeyMap;
        private final Collection<Report> _inheritableReports;

        private ReportCollections(Container c)
        {
            ReportServiceImpl svc = ReportServiceImpl.getInstance();
            Map<Integer, Report> rowIdMap = new HashMap<>();
            Map<String, Report> entityIdMap = new HashMap<>();
            MultiValuedMap<String, Report> reportKeyMap = new ArrayListValuedHashMap<>();
            List<Report> inheritableReports = new LinkedList<>();

            new TableSelector(CoreSchema.getInstance().getTableInfoReport(), SimpleFilter.createContainerFilter(c, "ContainerId"), null).forEach(reportDB -> {
                Report report = svc._getInstance(reportDB);
                rowIdMap.put(reportDB.getRowId(), report);
                entityIdMap.put(reportDB.getEntityId(), report);
                reportKeyMap.put(reportDB.getReportKey(), report);

                if ((reportDB.getFlags() & ReportDescriptor.FLAG_INHERITABLE) != 0)
                    inheritableReports.add(report);
            }, ReportDB.class);

            _rowIdMap = Collections.unmodifiableMap(rowIdMap);
            _entityIdMap = Collections.unmodifiableMap(entityIdMap);
            _reportKeyMap = MultiMapUtils.unmodifiableMultiValuedMap(reportKeyMap);
            _inheritableReports = Collections.unmodifiableList(inheritableReports);
        }

        private @Nullable Report getForRowId(int rowId)
        {
            return _rowIdMap.get(rowId);
        }

        private @Nullable Report getForEntityId(String entityId)
        {
            return _entityIdMap.get(entityId);
        }

        private @NotNull Collection<Report> getForReportKey(String reportKey)
        {
            Collection<Report> reports = _reportKeyMap.get(reportKey);
            return null != reports ? reports : Collections.emptyList();
        }

        private @NotNull Collection<Report> getReports()
        {
            return _rowIdMap.values();
        }

        private @NotNull Collection<Report> getInheritableReports()
        {
            return _inheritableReports;
        }
    }

    static @Nullable Report getReport(Container c, int rowId)
    {
        return REPORT_DB_CACHE.get(c).getForRowId(rowId);
    }

    static @Nullable Report getReportByEntityId(Container c, String entityId)
    {
        return REPORT_DB_CACHE.get(c).getForEntityId(entityId);
    }

    static @NotNull Collection<Report> getReports(Container c)
    {
        return Collections.unmodifiableCollection(REPORT_DB_CACHE.get(c).getReports());
    }

    static @NotNull Collection<Report> getReportsByReportKey(Container c, String reportKey)
    {
        return REPORT_DB_CACHE.get(c).getForReportKey(reportKey);
    }

    static @NotNull Collection<Report> getInheritableReports(Container c)
    {
        return Collections.unmodifiableCollection(REPORT_DB_CACHE.get(c).getInheritableReports());
    }

    static void uncache(Container c)
    {
        REPORT_DB_CACHE.remove(c);
    }
}
