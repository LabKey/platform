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
package org.labkey.specimen.actions;

import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.specimen.report.SpecimenVisitReportAction;
import org.labkey.specimen.report.participant.ParticipantSiteReportFactory;
import org.labkey.specimen.report.participant.ParticipantSummaryReportFactory;
import org.labkey.specimen.report.participant.ParticipantTypeReportFactory;
import org.labkey.specimen.report.request.RequestEnrollmentSiteReportFactory;
import org.labkey.specimen.report.request.RequestLocationReportFactory;
import org.labkey.specimen.report.request.RequestParticipantReportFactory;
import org.labkey.specimen.report.request.RequestReportFactory;
import org.labkey.specimen.report.specimentype.TypeCohortReportFactory;
import org.labkey.specimen.report.specimentype.TypeParticipantReportFactory;
import org.labkey.specimen.report.specimentype.TypeSummaryReportFactory;

public class SpecimenReportActions
{
    @RequiresPermission(ReadPermission.class)
    public static class ParticipantSummaryReportAction extends SpecimenVisitReportAction<ParticipantSummaryReportFactory>
    {
        public ParticipantSummaryReportAction()
        {
            super(ParticipantSummaryReportFactory.class);
        }
        // no implementation needed; this action exists only to provide an entry point
        // with request->bean translation for this report type.
    }

    @RequiresPermission(ReadPermission.class)
    public static class ParticipantTypeReportAction extends SpecimenVisitReportAction<ParticipantTypeReportFactory>
    {
        public ParticipantTypeReportAction()
        {
            super(ParticipantTypeReportFactory.class);
        }
        // no implementation needed; this action exists only to provide an entry point
        // with request->bean translation for this report type.
    }

    @RequiresPermission(ReadPermission.class)
    public static class ParticipantSiteReportAction extends SpecimenVisitReportAction<ParticipantSiteReportFactory>
    {
        public ParticipantSiteReportAction()
        {
            super(ParticipantSiteReportFactory.class);
        }
        // no implementation needed; this action exists only to provide an entry point
        // with request->bean translation for this report type.
    }

    @RequiresPermission(ReadPermission.class)
    public static class RequestReportAction extends SpecimenVisitReportAction<RequestReportFactory>
    {
        public RequestReportAction()
        {
            super(RequestReportFactory.class);
        }
        // no implementation needed; this action exists only to provide an entry point
        // with request->bean translation for this report type.
    }

    @RequiresPermission(ReadPermission.class)
    public static class RequestEnrollmentSiteReportAction extends SpecimenVisitReportAction<RequestEnrollmentSiteReportFactory>
    {
        public RequestEnrollmentSiteReportAction()
        {
            super(RequestEnrollmentSiteReportFactory.class);
        }
        // no implementation needed; this action exists only to provide an entry point
        // with request->bean translation for this report type.
    }

    @RequiresPermission(ReadPermission.class)
    public static class RequestSiteReportAction extends SpecimenVisitReportAction<RequestLocationReportFactory>
    {
        public RequestSiteReportAction()
        {
            super(RequestLocationReportFactory.class);
        }
        // no implementation needed; this action exists only to provide an entry point
        // with request->bean translation for this report type.
    }

    @RequiresPermission(ReadPermission.class)
    public static class RequestParticipantReportAction extends SpecimenVisitReportAction<RequestParticipantReportFactory>
    {
        public RequestParticipantReportAction()
        {
            super(RequestParticipantReportFactory.class);
        }
        // no implementation needed; this action exists only to provide an entry point
        // with request->bean translation for this report type.
    }

    @RequiresPermission(ReadPermission.class)
    public static class TypeParticipantReportAction extends SpecimenVisitReportAction<TypeParticipantReportFactory>
    {
        public TypeParticipantReportAction()
        {
            super(TypeParticipantReportFactory.class);
        }
        // no implementation needed; this action exists only to provide an entry point
        // with request->bean translation for this report type.
    }

    @RequiresPermission(ReadPermission.class)
    public static class TypeSummaryReportAction extends SpecimenVisitReportAction<TypeSummaryReportFactory>
    {
        public TypeSummaryReportAction()
        {
            super(TypeSummaryReportFactory.class);
        }
        // no implementation needed; this action exists only to provide an entry point
        // with request->bean translation for this report type.
    }

    @RequiresPermission(ReadPermission.class)
    public static class TypeCohortReportAction extends SpecimenVisitReportAction<TypeCohortReportFactory>
    {
        public TypeCohortReportAction()
        {
            super(TypeCohortReportFactory.class);
        }
        // no implementation needed; this action exists only to provide an entry point
        // with request->bean translation for this report type.
    }
}
