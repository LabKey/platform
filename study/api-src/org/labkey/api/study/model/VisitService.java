/*
 * Copyright (c) 2021-2026 LabKey Corporation
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
package org.labkey.api.study.model;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.study.Study;
import org.labkey.api.study.Visit;

import java.math.BigDecimal;
import java.util.Collection;

/**
 * Provides specimen module access to a few visit-related methods while keeping VisitImpl, StudyManager, and all
 * their dependencies in study-main
 */
public interface VisitService
{
    static VisitService get()
    {
        return ServiceRegistry.get().getService(VisitService.class);
    }

    static void setInstance(VisitService impl)
    {
        ServiceRegistry.get().registerService(VisitService.class, impl);
    }

    Collection<? extends Visit> getVisits(Study study, Visit.Order order);

    ValidationException updateParticipantVisitsWithCohortUpdate(Study study, User user, boolean failForUndefinedVisits, @Nullable Logger logger);

    /**
     * Updates this study's participant, visit, and participant visit tables. Also updates automatic cohort assignments.
     */
    ValidationException updateParticipantVisits(Study study, User user);

    // Methods created to support Vaccine study designs
    Visit createVisit(Study study, User user, @NotNull BigDecimal seqMin, String label, Visit.Type type);

    void deleteVisit(Study study, User user, Visit visit);
}
