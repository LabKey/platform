/*
 * Copyright (c) 2011-2013 LabKey Corporation
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

CREATE PROCEDURE exp.deleteObjectById(@container ENTITYID, @objectid INTEGER) AS
BEGIN
    SET NOCOUNT ON
        SELECT @objectid = ObjectId FROM exp.Object WHERE Container=@container AND ObjectId=@objectid
        IF (@objectid IS NULL)
            RETURN
    BEGIN TRANSACTION
        DELETE exp.ObjectProperty WHERE ObjectId IN
            (SELECT ObjectId FROM exp.Object WHERE OwnerObjectId = @objectid)
        DELETE exp.ObjectProperty WHERE ObjectId = @objectid
        DELETE exp.Object WHERE OwnerObjectId = @objectid
        DELETE exp.Object WHERE ObjectId = @objectid
    COMMIT
END
GO
