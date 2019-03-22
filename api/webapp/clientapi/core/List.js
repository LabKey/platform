/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @license Copyright (c) 2015-2016 LabKey Corporation
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p/>
 */

/**
 * @namespace The List namespace allows you to create new Lists.
 * @ignore hide from JsDoc for now
 */
LABKEY.List = new function () {
    "use strict";

    /** @scope LABKEY.List */
    return {

        /**
         * Create a new list.
         * A primary key column must be specified with the properties 'keyName' and 'keyType'.  If the key
         * is not provided in the domain design's array of fields, it will be automatically added to the domain.
         *
         * @ignore hide from JsDoc for now
         *
         * @param config A config object with properties from {@link LABKEY.Domain.DomainDesign} and the following additional properties:
         * @param config.name The list name.
         * @param config.keyName The name of the key column.
         * @param config.keyType The type of the key column.  Either "int" or "string".
         * @example
         * <pre>
         * LABKEY.List.create({
         *   name: "mylist",
         *   keyType: "int",
         *   keyName: "one",
         *   description: "my first list",
         *   fields: [{
         *     name: "one", rangeURI: "int"
         *   },{
         *     name: "two", rangeURI: "multiLine", required: true
         *   },{
         *     name: "three", rangeURI: "Attachment"
         *   }]
         * });
         * </pre>
         */
        create : function (config) {
            var createConfig = {
                domainDesign: config,
                options: {}
            };

            if (!createConfig.domainDesign.name)
                throw new Error("List name required");

            if (!config.kind)
            {
                if (config.keyType == "int")
                    config.kind = "IntList";
                else if (config.keyType == "string")
                    config.kind = "VarList";
            }

            if (config.kind != "IntList" && config.kind != "VarList")
                throw new Error("Domain kind or keyType required");
            createConfig.kind = config.kind;

            if (!config.keyName)
                throw new Error("List keyName required");
            createConfig.options.keyName = config.keyName;

            // TODO: other list design options

            LABKEY.Domain.create(createConfig);
        }

    }
};
