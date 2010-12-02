/*
 * Copyright (c) 2010 LabKey Corporation
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


/* study-10.20-10.21.sql */

SELECT core.executeJavaUpgradeCode('materializeDatasets');


ALTER TABLE exp.objectproperty DROP CONSTRAINT pk_objectproperty;
ALTER TABLE exp.objectproperty DROP CONSTRAINT fk_objectproperty_object;
ALTER TABLE exp.objectproperty DROP CONSTRAINT fk_objectproperty_propertydescriptor;
DROP INDEX exp.idx_objectproperty_propertyid;

DELETE FROM exp.ObjectProperty
WHERE propertyid IN (SELECT DP.propertyid FROM exp.propertydomain DP JOIN exp.domaindescriptor D on DP.domainid = D.domainid JOIN study.dataset DS ON D.domainuri = DS.typeuri);

VACUUM FULL exp.ObjectProperty;

ALTER TABLE exp.objectproperty
  ADD CONSTRAINT pk_objectproperty PRIMARY KEY (objectid, propertyid),
  ADD CONSTRAINT fk_objectproperty_object FOREIGN KEY (objectid)
      REFERENCES exp."object" (objectid) MATCH SIMPLE
      ON UPDATE NO ACTION ON DELETE NO ACTION,
  ADD CONSTRAINT fk_objectproperty_propertydescriptor FOREIGN KEY (propertyid)
      REFERENCES exp.propertydescriptor (propertyid) MATCH SIMPLE
      ON UPDATE NO ACTION ON DELETE NO ACTION;

CREATE INDEX idx_objectproperty_propertyid
  ON exp.objectproperty
  USING btree
  (propertyid);

DROP TABLE study.StudyData;

/* study-10.21-10.22.sql */

ALTER TABLE study.Study
    ADD COLUMN BlankQCStatePublic BOOLEAN NOT NULL DEFAULT FALSE;