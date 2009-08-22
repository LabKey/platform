/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
CREATE INDEX IX_AssayRun_Container ON study.AssayRun(Container);

CREATE INDEX IX_Plate_Container ON study.Plate(Container);

CREATE INDEX IX_SampleRequest_Container ON study.SampleRequest(Container);

CREATE INDEX IX_SampleRequest_StatusId ON study.SampleRequest(StatusId);

CREATE INDEX IX_SampleRequest_DestinationSiteId ON study.SampleRequest(DestinationSiteId);

CREATE INDEX IX_SampleRequestActor_Container ON study.SampleRequestActor(Container);

CREATE INDEX IX_SampleRequestEvent_Container ON study.SampleRequestEvent(Container);

CREATE INDEX IX_SampleRequestEvent_RequestId ON study.SampleRequestEvent(RequestId);

CREATE INDEX IX_SampleRequestRequirement_Container ON study.SampleRequestRequirement(Container);

CREATE INDEX IX_SampleRequestRequirement_RequestId ON study.SampleRequestRequirement(RequestId);

CREATE INDEX IX_SampleRequestRequirement_ActorId ON study.SampleRequestRequirement(ActorId);

CREATE INDEX IX_SampleRequestRequirement_SiteId ON study.SampleRequestRequirement(SiteId);

CREATE INDEX IX_SampleRequestSpecimen_Container ON study.SampleRequestSpecimen(Container);

CREATE INDEX IX_SampleRequestSpecimen_SampleRequestId ON study.SampleRequestSpecimen(SampleRequestId);

CREATE INDEX IX_SampleRequestSpecimen_SpecimenGlobalUniqueId ON study.SampleRequestSpecimen(SpecimenGlobalUniqueId);

CREATE INDEX IX_SampleRequestStatus_Container ON study.SampleRequestStatus(Container);

CREATE INDEX IX_Specimen_Container ON study.Specimen(Container);

CREATE INDEX IX_Specimen_GlobalUniqueId ON study.Specimen(GlobalUniqueId);

CREATE INDEX IX_SpecimenAdditive_Container ON study.SpecimenAdditive(Container);

CREATE INDEX IX_SpecimenDerivative_Container ON study.SpecimenDerivative(Container);

CREATE INDEX IX_SpecimenPrimaryType_Container ON study.SpecimenPrimaryType(Container);

CREATE INDEX IX_SpecimenEvent_Container ON study.SpecimenEvent(Container);

CREATE INDEX IX_SpecimenEvent_LabId ON study.SpecimenEvent(LabId);

CREATE INDEX IX_Well_PlateId ON study.Well(PlateId);

CREATE INDEX IX_Well_Container ON study.Well(Container);

CREATE INDEX IX_WellGroup_PlateId ON study.WellGroup(PlateId);

CREATE INDEX IX_WellGroup_Container ON study.WellGroup(Container);