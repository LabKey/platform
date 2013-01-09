/*
 * Copyright (c) 2011 LabKey Corporation
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

/* study-11.20-11.21.sql */

ALTER TABLE study.Study ADD Description text;

/* study-11.21-11.22.sql */

ALTER TABLE study.Study ADD ProtocolDocumentEntityId entityid;

SELECT core.executeJavaUpgradeCode('assignProtocolDocumentEntityId');

/* study-11.22-11.23.sql */

ALTER TABLE study.Study ALTER COLUMN ProtocolDocumentEntityId SET NOT NULL;

/* study-11.23-11.24.sql */

-- populate the view category table with the dataset categories
INSERT INTO core.ViewCategory (Container, Label, CreatedBy, ModifiedBy)
  SELECT Container, Category, 0, 0 FROM study.Dataset WHERE LENGTH(Category) > 0 GROUP BY Container, Category;

ALTER TABLE study.Dataset ADD COLUMN CategoryId Integer;

UPDATE study.Dataset ds
    SET CategoryId = (SELECT rowId FROM core.ViewCategory vc WHERE ds.container = vc.container AND ds.category = vc.label);

-- drop the category column
ALTER TABLE study.Dataset DROP COLUMN Category;

/* study-11.24-11.25.sql */

ALTER TABLE study.Study ADD SourceStudyContainerId entityid;

/* study-11.25-11.26.sql */

ALTER TABLE study.Study ADD DescriptionRendererType VARCHAR(50) NOT NULL DEFAULT 'TEXT_WITH_LINKS';

/* study-11.26-11.27.sql */

ALTER TABLE study.Study ADD Investigator VARCHAR(200);

ALTER TABLE study.Study ADD StudyGrant VARCHAR(200);

/* study-11.27-11.28.sql */

ALTER TABLE study.Study RENAME COLUMN studyGrant TO "Grant";