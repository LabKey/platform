/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

ALTER TABLE comm.Pages
	ADD PageVersionId int4 NULL;
ALTER TABLE comm.Pages
	ADD CONSTRAINT FK_Pages_PageVersions FOREIGN KEY (PageVersionId) REFERENCES comm.PageVersions (RowId);

UPDATE comm.Pages
SET PageVersionId =
	(SELECT RowId
		FROM comm.PageVersions PV1
		WHERE comm.Pages.EntityId = PV1.PageEntityId
		AND PV1.Version =
			(SELECT MAX(Version) 
			FROM comm.PageVersions PV2
			WHERE PV2.PageEntityId = comm.Pages.EntityId
			)
    )