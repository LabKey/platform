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
SET search_path TO core, public;

CREATE OR REPLACE RULE Users_Update AS
	ON UPDATE TO Users DO INSTEAD
		UPDATE UsersData SET
			ModifiedBy = NEW.ModifiedBy,
			Modified = NEW.Modified,
			FirstName = NEW.FirstName,
			LastName = NEW.LastName,
			Phone = NEW.Phone,
			Mobile = NEW.Mobile,
			Pager = NEW.Pager,
			IM = NEW.IM,
			Description = NEW.Description,
			LastLogin = NEW.LastLogin
		WHERE UserId = NEW.UserId;