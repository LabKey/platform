/*
 * Copyright (c) 2012-2014 LabKey Corporation
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
package org.labkey.api.ldk;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableCustomizer;
import org.labkey.api.data.TableInfo;
import org.labkey.api.ldk.notification.NotificationSection;
import org.labkey.api.ldk.table.ButtonConfigFactory;
import org.labkey.api.security.User;

import java.util.List;
import java.util.Map;

/**
 * User: bimber
 * Date: 11/4/12
 * Time: 3:48 PM
 */
abstract public class LDKService
{
    static LDKService instance;

    public static final String ALL_TABLES = "~~ALL_TABLES~~";
    public static final String ALL_SCHEMAS = "~~ALL_SCHEMAS~~";

    public static LDKService get()
    {
        return instance;
    }

    static public void setInstance(LDKService instance)
    {
        LDKService.instance = instance;
    }

    abstract public TableCustomizer getDefaultTableCustomizer();

    abstract public TableCustomizer getBuiltInColumnsCustomizer(boolean disableFacetingForNumericCols);

    abstract public TableCustomizer getColumnsOrderCustomizer();

    abstract public Map<String, Object> getContainerSizeJson(Container c, User u, boolean includeAllRootTypes, boolean includeFileCount);

    abstract public void applyNaturalSort(AbstractTableInfo ti, String colName);

    abstract public void appendCalculatedDateColumns(AbstractTableInfo ti, @Nullable String dateColName, @Nullable String enddateColName);

    abstract public void registerSiteSummaryNotification(NotificationSection ns);

    abstract public boolean isNaturalizeInstalled();

    abstract public void logPerfMetric(Container c, User u, String type, String comment, Double value);

    abstract public void registerContainerScopedTable(String dbSchemaName, String tableName, String pseudoPk);

    abstract public void registerQueryButton(ButtonConfigFactory btn, String schema, String query);

    abstract public List<ButtonConfigFactory> getQueryButtons(TableInfo ti);

    abstract public void customizeButtonBar(AbstractTableInfo ti, List<ButtonConfigFactory> buttons);
}
