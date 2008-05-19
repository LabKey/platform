/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
-- Theme updates to go with the navigation facelift in 2.1:
UPDATE prop.properties SET VALUE = 'e1ecfc;89a1b4;ffdf8c;336699;ebf4ff;89a1b4'
WHERE name = 'themeColors-Blue' AND value = 'e1ecfc;ffd275;ffdf8c;336699;ebf4ff;b9d1f4';

UPDATE prop.properties SET VALUE = 'cccc99;929146;e1e1c4;666633;e1e1c4;929146'
WHERE name = 'themeColors-Brown' AND value = 'cccc99;a00000;e1e1c4;666633;e1e1c4;b2b166';