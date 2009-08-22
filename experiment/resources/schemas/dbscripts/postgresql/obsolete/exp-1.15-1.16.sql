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
ALTER TABLE exp.ProtocolParameter RENAME COLUMN StringValue TO ShortStringValue;
ALTER TABLE exp.ProtocolParameter ADD COLUMN StringValue varchar(4000) NULL;
UPDATE exp.ProtocolParameter SET StringValue = ShortStringValue;
ALTER TABLE exp.ProtocolParameter DROP COLUMN ShortStringValue;

UPDATE exp.ProtocolParameter
    SET StringValue = FileLinkValue WHERE ValueType='FileLink';

ALTER TABLE exp.ProtocolParameter DROP COLUMN FileLinkValue;

ALTER TABLE exp.ProtocolParameter DROP COLUMN XmlTextValue;

ALTER TABLE exp.ProtocolApplicationParameter RENAME COLUMN StringValue TO ShortStringValue;
ALTER TABLE exp.ProtocolApplicationParameter ADD COLUMN StringValue varchar(4000) NULL;
UPDATE exp.ProtocolApplicationParameter SET StringValue = ShortStringValue;
ALTER TABLE exp.ProtocolApplicationParameter DROP COLUMN ShortStringValue;

UPDATE exp.ProtocolApplicationParameter
    SET StringValue = FileLinkValue WHERE ValueType='FileLink';

ALTER TABLE exp.ProtocolApplicationParameter DROP COLUMN FileLinkValue;

ALTER TABLE exp.ProtocolApplicationParameter DROP COLUMN XmlTextValue;
