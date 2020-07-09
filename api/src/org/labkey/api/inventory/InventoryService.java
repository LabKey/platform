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
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.gwt.client.AuditBehaviorType;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;

import java.util.List;
import java.util.Map;

/**
 * User: kevink
 * Date: 3/26/17
 */
public interface InventoryService
{
    static void setInstance(InventoryService impl)
    {
        ServiceRegistry.get().registerService(InventoryService.class, impl);
    }

    static InventoryService get() { return ServiceRegistry.get().getService(InventoryService.class); }

    void addAuditEvent(User user, Container c, TableInfo table, AuditBehaviorType auditBehaviorType, QueryService.AuditAction action, List<Map<String, Object>>... params);

    @NotNull
    List<Map<String, Object>> getSampleStorageLocationData(User user, Container container, int sampleId);

    void addInventoryStatusColumns(ExpMaterialTable table, Container container);
}
