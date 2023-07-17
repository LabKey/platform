/*
 * Copyright (c) 2023 LabKey Corporation
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

-- Issue 48270 - dedupe redundant rows in SoftwareRelease that have NULLs for VcsTag
UPDATE mothership.serversession s SET softwarereleaseid =
                  (SELECT MaxId
                   FROM mothership.softwarerelease sr1
                            INNER JOIN (SELECT MAX(SoftwareReleaseId) AS MaxId,
                                               VcsUrl,
                                               VcsRevision,
                                               VcsTag,
                                               VcsBranch,
                                               BuildTime
                                        FROM mothership.softwarerelease
                                        GROUP BY VcsUrl, VcsRevision, VcsTag, VcsBranch, BuildTime) sr2
                                       ON
                                               (sr1.VcsUrl = sr2.VcsUrl OR (sr1.VcsUrl IS NULL AND sr2.VcsUrl IS NULL)) AND
                                               (sr1.VcsRevision = sr2.VcsRevision OR
                                                (sr1.VcsRevision IS NULL AND sr2.VcsRevision IS NULL)) AND
                                               (sr1.VcsTag = sr2.VcsTag OR (sr1.VcsTag IS NULL AND sr2.VcsTag IS NULL)) AND
                                               (sr1.VcsBranch = sr2.VcsBranch OR
                                                (sr1.VcsBranch IS NULL AND sr2.VcsBranch IS NULL)) AND
                                               (sr1.BuildTime = sr2.BuildTime OR
                                                (sr1.BuildTime IS NULL AND sr2.BuildTime IS NULL))
                   WHERE s.SoftwareReleaseId = sr1.SoftwareReleaseId);

DELETE FROM mothership.softwarerelease WHERE SoftwareReleaseId NOT IN
                                             (SELECT DISTINCT SoftwareReleaseId FROM mothership.serversession);

