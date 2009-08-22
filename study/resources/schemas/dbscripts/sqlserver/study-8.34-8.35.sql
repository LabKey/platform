/*
 * Copyright (c) 2009 LabKey Corporation
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
-- Remove ScharpId from SpecimenPrimaryType
ALTER TABLE study.SpecimenPrimaryType DROP CONSTRAINT UQ_PrimaryTypes;
DROP INDEX study.SpecimenPrimaryType.IX_SpecimenPrimaryType_ScharpId;
ALTER TABLE study.SpecimenPrimaryType ADD ExternalId INT NOT NULL DEFAULT 0;
GO
UPDATE study.SpecimenPrimaryType SET ExternalId = ScharpId;
ALTER TABLE study.SpecimenPrimaryType DROP COLUMN ScharpId;
CREATE INDEX IX_SpecimenPrimaryType_ExternalId ON study.SpecimenPrimaryType(ExternalId);
ALTER TABLE study.SpecimenPrimaryType ADD CONSTRAINT UQ_PrimaryType UNIQUE (ExternalId, Container);
GO

-- Remove ScharpId from SpecimenDerivativeType
ALTER TABLE study.SpecimenDerivative DROP CONSTRAINT UQ_Derivatives;
DROP INDEX study.SpecimenDerivative.IX_SpecimenDerivative_ScharpId;
ALTER TABLE study.SpecimenDerivative ADD ExternalId INT NOT NULL DEFAULT 0;
GO
UPDATE study.SpecimenDerivative SET ExternalId = ScharpId;
ALTER TABLE study.SpecimenDerivative DROP COLUMN ScharpId;
CREATE INDEX IX_SpecimenDerivative_ExternalId ON study.SpecimenDerivative(ExternalId);
ALTER TABLE study.SpecimenDerivative ADD CONSTRAINT UQ_Derivative UNIQUE (ExternalId, Container);
GO

-- Remove ScharpId from SpecimenAdditive
ALTER TABLE study.SpecimenAdditive DROP CONSTRAINT UQ_Additives;
DROP INDEX study.SpecimenAdditive.IX_SpecimenAdditive_ScharpId;
ALTER TABLE study.SpecimenAdditive ADD ExternalId INT NOT NULL DEFAULT 0;
GO
UPDATE study.SpecimenAdditive SET ExternalId = ScharpId;
ALTER TABLE study.SpecimenAdditive DROP COLUMN ScharpId;
CREATE INDEX IX_SpecimenAdditive_ExternalId ON study.SpecimenAdditive(ExternalId);
ALTER TABLE study.SpecimenAdditive ADD CONSTRAINT UQ_Additive UNIQUE (ExternalId, Container);
GO

-- Remove ScharpId from Site
ALTER TABLE study.Site ADD ExternalId INT;
GO
UPDATE study.Site SET ExternalId = ScharpId;
ALTER TABLE study.Site DROP COLUMN ScharpId;
GO

-- Remove ScharpId from SpecimenEvent
ALTER TABLE study.SpecimenEvent ADD ExternalId INT;
GO
UPDATE study.SpecimenEvent SET ExternalId = ScharpId;
ALTER TABLE study.SpecimenEvent DROP COLUMN ScharpId;
GO

UPDATE study.Specimen SET
    PrimaryTypeId = (SELECT RowId FROM study.SpecimenPrimaryType WHERE
        ExternalId = study.Specimen.PrimaryTypeId AND study.SpecimenPrimaryType.Container = study.Specimen.Container),
    DerivativeTypeId = (SELECT RowId FROM study.SpecimenDerivative WHERE
        ExternalId = study.Specimen.DerivativeTypeId AND study.SpecimenDerivative.Container = study.Specimen.Container),
    AdditiveTypeId = (SELECT RowId FROM study.SpecimenAdditive WHERE
        ExternalId = study.Specimen.AdditiveTypeId AND study.SpecimenAdditive.Container = study.Specimen.Container);
GO

ALTER TABLE study.Site ADD
    Repository BIT,
    Clinic BIT,
    SAL BIT,
    Endpoint BIT;
GO

UPDATE study.Site SET Repository = IsRepository, Clinic = IsClinic, SAL = IsSAL, Endpoint = IsEndpoint;

ALTER TABLE study.Site DROP COLUMN
    IsRepository,
    IsClinic,
    IsSAL,
    IsEndpoint;
GO