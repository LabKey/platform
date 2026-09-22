/*
 * Copyright (c) 2026 LabKey Corporation
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
package org.labkey.api.assay.plate;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.GUID;

import java.util.Collection;
import java.util.Map;

/**
 * Plate facts for modules that must not depend on assay, such as inventory's plate storage. Lives in API for the
 * same reason AssayPlateMetadataService does: the alternative is moving PlateService and its transitive types here.
 */
public interface PlateStorageService
{
    static void setInstance(PlateStorageService impl)
    {
        ServiceRegistry.get().registerService(PlateStorageService.class, impl);
    }

    /** Never null: with the assay module absent, returns an implementation that reports itself unavailable. */
    static @NotNull PlateStorageService get()
    {
        PlateStorageService svc = ServiceRegistry.get().getService(PlateStorageService.class);
        return svc != null ? svc : Unavailable.INSTANCE;
    }

    /**
     * Whether plate storage can be used in this container -- the assay module both deployed and active here.
     * Every other method on this interface requires it, so callers gate on it rather than on a null service.
     */
    boolean isAvailable(@NotNull Container container);

    /**
     * Storage-relevant projection of a plate. Deliberately not Plate itself, whose Well, WellGroup, Position and
     * PlateCustomField return types all live in assay's api-src and so cannot cross this boundary.
     */
    record StoragePlate(
        long rowId,
        String plateId,
        String name,
        String barcode,
        GUID containerId,
        boolean template,
        boolean archived,
        boolean assayPlateSet,
        Long plateSetId,
        String plateSetName,
        String plateTypeName
    )
    {
    }

    /**
     * Resolves plates by rowId, omitting any that don't exist or that the user cannot read. A missing entry is
     * therefore the read check itself, which is what lets a caller skip re-checking downstream — see the inventory
     * trigger's SKIP_PLATE_VALIDATION.
     */
    @NotNull
    Map<Long, StoragePlate> getStoragePlates(@NotNull Collection<Long> plateRowIds, @NotNull Container container, @NotNull User user);

    /**
     * Stands in when the assay module is absent. Throws rather than returning an empty result so that a caller which
     * skipped isAvailable() fails loudly: an empty map would be indistinguishable from "no plate is readable", which
     * is the meaning getStoragePlates callers rely on.
     */
    class Unavailable implements PlateStorageService
    {
        private static final Unavailable INSTANCE = new Unavailable();

        @Override
        public boolean isAvailable(@NotNull Container container)
        {
            return false;
        }

        @Override
        public @NotNull Map<Long, StoragePlate> getStoragePlates(@NotNull Collection<Long> plateRowIds, @NotNull Container container, @NotNull User user)
        {
            throw new IllegalStateException("Plate storage requires the Assay module.");
        }
    }
}
