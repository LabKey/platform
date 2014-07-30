/*
 * Copyright (c) 2014 LabKey Corporation
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

ALTER TABLE study.StudyDesignAssays ADD Target NVARCHAR(200);
ALTER TABLE study.StudyDesignAssays ADD Methodology NVARCHAR(200);
ALTER TABLE study.StudyDesignAssays ADD Category NVARCHAR(200);
ALTER TABLE study.StudyDesignAssays ADD TargetFunction NVARCHAR(200);
ALTER TABLE study.StudyDesignAssays ADD LeadContributor NVARCHAR(200);
ALTER TABLE study.StudyDesignAssays ADD Contact NVARCHAR(200);
ALTER TABLE study.StudyDesignAssays ADD Summary TEXT;
ALTER TABLE study.StudyDesignAssays ADD Keywords TEXT;

ALTER TABLE study.StudyDesignLabs ADD PI NVARCHAR(200);
ALTER TABLE study.StudyDesignLabs ADD Description TEXT;
ALTER TABLE study.StudyDesignLabs ADD Summary TEXT;
ALTER TABLE study.StudyDesignLabs ADD Institution NVARCHAR(200);

