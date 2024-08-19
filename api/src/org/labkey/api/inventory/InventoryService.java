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
import org.labkey.api.data.ColumnInfo;
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

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface InventoryService
{
    String PRODUCT_ID = "FreezerManager";

    enum InventoryStatusColumn
    {
        CheckedOut,
        CheckedOutBy,
        Col,
        FreezeThawCount("Freeze/Thaw Count"),
        Row,
        SampleTypeUnits,
        StorageCol,
        StorageComment,
        StorageLocation,
        StorageRow,
        StorageStatus,
        StorageUnit,
        StorageUnitLabel,
        Stored("Entered Storage");

        private final String label;

        InventoryStatusColumn()
        {
            this.label = ColumnInfo.labelFromName(name());
        }

        InventoryStatusColumn(String label)
        {
            this.label = label;
        }

        public String label()
        {
            return this.label;
        }

        public FieldKey fieldKey()
        {
            return FieldKey.fromParts(name());
        }

        public static List<String> names()
        {
            return Arrays.stream(InventoryStatusColumn.values()).map(InventoryStatusColumn::name).toList();
        }

        public static List<String> labels()
        {
            return Arrays.stream(InventoryStatusColumn.values()).map(InventoryStatusColumn::label).toList();
        }
    }

    static void setInstance(InventoryService impl)
    {
        ServiceRegistry.get().registerService(InventoryService.class, impl);
    }

    static InventoryService get()
    {
        return ServiceRegistry.get().getService(InventoryService.class);
    }

    void addAuditEvent(User user, Container c, TableInfo table, AuditBehaviorType auditBehaviorType, @Nullable String userComment, QueryService.AuditAction action, @Nullable List<Map<String, Object>> rows, @Nullable List<Map<String, Object>> existingRows, boolean useTransactionAuditCache);

    Map<String, Integer> moveSamples(Collection<Integer> sampleIds, Container targetContainer, User user);

    @NotNull
    List<Map<String, Object>> getSampleStorageLocationData(User user, Container container, int sampleId);

    List<FieldKey> addInventoryStatusColumns(@Nullable String sampleTypeMetricUnit, ExpMaterialTable table, Container container, User user);

    DataIteratorBuilder getPersistStorageItemDataIteratorBuilder(DataIteratorBuilder data, Container container, User user, ExpSampleType sampleType);

    @NotNull
    String getWellLabel(int boxId, int row, Integer col);

    static boolean isFreezerManagementEnabled(Container c)
    {
        Set<Module> moduleSet = c.getActiveModules();
        return moduleSet.contains(ModuleLoader.getInstance().getModule("Inventory"));
    }
}
