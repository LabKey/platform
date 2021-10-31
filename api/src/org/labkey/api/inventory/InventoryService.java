/*
 * Copyright (c) 2017 LabKey Corporation
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
package org.labkey.api.inventory;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.gwt.client.AuditBehaviorType;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.ExperimentalFeatureService;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: kevink
 * Date: 3/26/17
 */
public interface InventoryService
{
    String PRODUCT_ID = "FreezerManager";

    Set<String> INVENTORY_STATUS_COLUMN_NAMES = new CaseInsensitiveHashSet(
            "FreezeThawCount",
            "CheckedOutBy",
            "CheckedOut",
            "StorageRow",
            "StorageCol",
            "StorageLocation",
            "EnteredStorage",
            "StorageStatus",
            "StoredAmount",
            "Units",
            "StorageComment"
    );

    String EXPERIMENTAL_FM_BIOLOGICS = "experimental-freezermanager-biologics";

    static void setInstance(InventoryService impl)
    {
        ServiceRegistry.get().registerService(InventoryService.class, impl);
    }

    static InventoryService get() { return ServiceRegistry.get().getService(InventoryService.class); }

    void addAuditEvent(User user, Container c, TableInfo table, AuditBehaviorType auditBehaviorType, @Nullable String userComment, QueryService.AuditAction action, @Nullable List<Map<String, Object>> rows, @Nullable List<Map<String, Object>> existingRows);

    @NotNull
    List<Map<String, Object>> getSampleStorageLocationData(User user, Container container, int sampleId);

    List<FieldKey> addInventoryStatusColumns(@Nullable String sampleTypeMetricUnit, ExpMaterialTable table, Container container, User user);

    DataIteratorBuilder getPersistStorageItemDataIteratorBuilder(DataIteratorBuilder data, Container container, User user, String metricUnit);

    @NotNull
    String getWellLabel(int boxId, int row, Integer col);

    int recomputeSampleTypeRollup(ExpSampleType sampleType, Container container, boolean forceAll) throws SQLException;

    void recomputeSamplesRollup(Set<Integer> parentIds, String sampleTypeMetricUnit, Container container) throws SQLException;

    static boolean isFreezerManagementEnabled(Container c)
    {
        Set<Module> moduleSet = c.getActiveModules();
        return (moduleSet.contains(ModuleLoader.getInstance().getModule("Inventory"))
                && (!moduleSet.contains(ModuleLoader.getInstance().getModule("Biologics"))
                || ExperimentalFeatureService.get().isFeatureEnabled(InventoryService.EXPERIMENTAL_FM_BIOLOGICS)));
    }

}
