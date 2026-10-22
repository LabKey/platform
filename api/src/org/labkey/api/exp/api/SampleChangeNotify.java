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
package org.labkey.api.exp.api;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.notification.NotificationService;
import org.labkey.api.data.AccumulatingCommitTask;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.logging.LogHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public enum SampleChangeNotify
{
    SampleDataChanged;

    private static final Logger LOG = LogHelper.getLogger(SampleChangeNotify.class, "Sample data change WebSocket notifications");
    private static final List<SampleCountsListener> LISTENERS = new CopyOnWriteArrayList<>();

    // Stands for "any sample type"; sample type row ids are positive
    private static final Long ANY_SAMPLE_TYPE = -1L;

    /** Told which sample types' samples changed in a container. Empty sampleTypeIds means any sample type may have. */
    public interface SampleCountsListener
    {
        /** Called inside the changing transaction just before it commits, or right away when there is no transaction */
        void samplesChanging(@NotNull Container container, @NotNull Set<Long> sampleTypeIds);

        /**
         * Called after the change commits
         * @return true if the listener will send {@link #sendSampleDataChangedEvent} itself once its counts are current
         */
        boolean samplesChanged(@NotNull Container container, @NotNull Set<Long> sampleTypeIds);
    }

    public static void addListener(SampleCountsListener listener)
    {
        LISTENERS.add(listener);
    }

    private static Collection<Long> toTaskValues(@Nullable Collection<Long> sampleTypeIds)
    {
        return sampleTypeIds == null || sampleTypeIds.isEmpty() ? Set.of(ANY_SAMPLE_TYPE) : sampleTypeIds;
    }

    private static Set<Long> fromTaskValues(Set<Long> values)
    {
        return values.contains(ANY_SAMPLE_TYPE) ? Set.of() : values;
    }

    /**
     * Tells listeners about a change inside the current transaction, once per container. Use this alone only when the
     * caller also arranges a post-commit {@link #fireSampleDataChanged}; otherwise use {@link SampleDataChangedTask}.
     */
    public static void recordSampleDataChange(@NotNull DbScope scope, @NotNull Container container, @Nullable Collection<Long> sampleTypeIds)
    {
        new SampleDataChangingTask(container).register(scope, toTaskValues(sampleTypeIds), DbScope.CommitTaskOption.PRECOMMIT);
    }

    private static class SampleDataChangingTask extends AccumulatingCommitTask<Container, Long>
    {
        SampleDataChangingTask(@NotNull Container container)
        {
            super(container);
        }

        @Override
        protected void process(@NotNull Container container, @NotNull Set<Long> values)
        {
            for (SampleCountsListener listener : LISTENERS)
            {
                // A listener failure must not roll back the sample change itself
                try
                {
                    listener.samplesChanging(container, fromTaskValues(values));
                }
                catch (Exception e)
                {
                    LOG.warn("Sample counts listener failed for container " + container.getPath(), e);
                }
            }
        }
    }

    /** Commit task that collects the changed sample type ids per container, so a transaction notifies once per container. */
    public static class SampleDataChangedTask extends AccumulatingCommitTask<Container, Long>
    {
        private final Container _container;

        public SampleDataChangedTask(@NotNull Container container)
        {
            super(container);
            _container = container;
        }

        /** Records the change in the current transaction and notifies after it commits; null or empty sampleTypeIds means any sample type in the container. */
        public void register(@NotNull DbScope scope, @Nullable Collection<Long> sampleTypeIds)
        {
            recordSampleDataChange(scope, _container, sampleTypeIds);
            register(scope, toTaskValues(sampleTypeIds), DbScope.CommitTaskOption.POSTCOMMIT);
        }

        @Override
        protected void process(@NotNull Container container, @NotNull Set<Long> sampleTypeIds)
        {
            fireSampleDataChanged(container, fromTaskValues(sampleTypeIds));
        }
    }

    public static void fireSampleDataChanged(@Nullable Container container)
    {
        fireSampleDataChanged(container, null);
    }

    /** After a change commits, tells listeners which sample types changed, then notifies clients unless a listener will once its counts are current. */
    public static void fireSampleDataChanged(@Nullable Container container, @Nullable Collection<Long> sampleTypeIds)
    {
        if (container == null)
            return;

        Set<Long> ids = sampleTypeIds == null ? Set.of() : Set.copyOf(sampleTypeIds);
        boolean deferred = false;
        for (SampleCountsListener listener : LISTENERS)
        {
            try
            {
                deferred |= listener.samplesChanged(container, ids);
            }
            catch (Exception e)
            {
                LOG.warn("Sample counts listener failed for container " + container.getPath(), e);
            }
        }

        if (!deferred)
            sendSampleDataChangedEvent(container);
    }

    /** Sends the SampleDataChanged WebSocket event to every user who can read the container */
    public static void sendSampleDataChangedEvent(@NotNull Container container)
    {
        try
        {
            Set<Class<? extends Permission>> read = Collections.singleton(ReadPermission.class);
            List<User> readers = SecurityManager.getUsersWithPermissions(container, read);
            List<Integer> userIds = new ArrayList<>(readers.size());
            for (User user : readers)
                userIds.add(user.getUserId());

            if (!userIds.isEmpty())
                NotificationService.get().sendServerEvent(userIds, SampleChangeNotify.SampleDataChanged);
        }
        catch (Exception e)
        {
            LOG.warn("Failed to send sample data changed notification for container " + container.getPath(), e);
        }
    }
}
