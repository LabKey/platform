/*
 * Copyright (c) 2007-2010 LabKey Corporation
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
update mothership.serverinstallation set serverhostname=serverip where serverhostname is null;

delete from mothership.serverinstallation where serverinstallationid not in (select serverinstallationid from mothership.serversession);

CREATE INDEX IX_ServerSession_ServerInstallationId ON mothership.serversession(serverinstallationid);
CREATE INDEX IX_ExceptionReport_ExceptionStackTraceId ON mothership.exceptionreport(exceptionstacktraceid);
CREATE INDEX IX_ExceptionReport_ServerSessionId ON mothership.exceptionreport(serversessionid);

CREATE INDEX IX_ServerInstallation_Container ON mothership.ServerInstallation(container);
CREATE INDEX IX_ExceptionStackTrace_Container ON mothership.ExceptionStackTrace(container);
