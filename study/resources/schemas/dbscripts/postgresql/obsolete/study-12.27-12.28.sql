/*
 * Copyright (c) 2012-2013 LabKey Corporation
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

-- Additional fields in Event and Vial tables
ALTER TABLE study.Vial ADD LatestComments VARCHAR(500);
ALTER TABLE study.Vial ADD LatestQualityComments VARCHAR(500);
ALTER TABLE study.Vial ADD LatestDeviationCode1 VARCHAR(50);
ALTER TABLE study.Vial ADD LatestDeviationCode2 VARCHAR(50);
ALTER TABLE study.Vial ADD LatestDeviationCode3 VARCHAR(50);
ALTER TABLE study.Vial ADD LatestConcentration REAL;
ALTER TABLE study.Vial ADD LatestIntegrity REAL;
ALTER TABLE study.Vial ADD LatestRatio REAL;
ALTER TABLE study.Vial ADD LatestYield REAL;

ALTER TABLE study.SpecimenEvent ALTER COLUMN Comments TYPE VARCHAR(500);
ALTER TABLE study.SpecimenEvent ADD QualityComments VARCHAR(500);
ALTER TABLE study.SpecimenEvent ADD DeviationCode1 VARCHAR(50);
ALTER TABLE study.SpecimenEvent ADD DeviationCode2 VARCHAR(50);
ALTER TABLE study.SpecimenEvent ADD DeviationCode3 VARCHAR(50);
ALTER TABLE study.SpecimenEvent ADD Concentration REAL;
ALTER TABLE study.SpecimenEvent ADD Integrity REAL;
ALTER TABLE study.SpecimenEvent ADD Ratio REAL;
ALTER TABLE study.SpecimenEvent ADD Yield REAL;
