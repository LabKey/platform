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
 
CREATE OR REPLACE FUNCTION exp.deleteObjectById(ENTITYID, INTEGER) RETURNS void AS '
DECLARE
    _container ALIAS FOR $1;
    _inputObjectId ALIAS FOR $2;
    _objectid INTEGER;
BEGIN
        _objectid := (SELECT ObjectId FROM exp.Object WHERE Container=_container AND ObjectId=_inputObjectid);
        IF (_objectid IS NULL) THEN
            RETURN;
        END IF;
--    START TRANSACTION;
        DELETE FROM exp.ObjectProperty WHERE ObjectId IN
            (SELECT ObjectId FROM exp.Object WHERE Container=_container AND OwnerObjectId = _objectid);
        DELETE FROM exp.ObjectProperty WHERE ObjectId = _objectid;
        DELETE FROM exp.Object WHERE Container=_container AND OwnerObjectId = _objectid;
        DELETE FROM exp.Object WHERE ObjectId = _objectid;
--    COMMIT;
    RETURN;
END;
' LANGUAGE plpgsql;

