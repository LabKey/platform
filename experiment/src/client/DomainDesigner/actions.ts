import {Utils, Domain} from "../../../../api/webapp/labkey-api-js/dist/labkey-api-js-core.es";
import {List} from "immutable";
// import {Utils, Domain} from "@labkey/api";
import {
    DOMAIN_FIELD_NAME,
    DOMAIN_FIELD_PREFIX,
    DOMAIN_FIELD_REQ,
    DOMAIN_FIELD_TYPE,
    DomainDesign,
    PropDescTypes
} from "./models";


export function fetchDomain(domainId: number, schemaName: string, queryName: string): Promise<DomainDesign> {
    return new Promise((resolve, reject) => {
        Domain.get({
            domainId,
            schemaName,
            queryName,
            success: (data) => {
                resolve(data);
            },
            failure: (error) => {
                reject(error);
            }
        })
    });
}

export function saveDomain(domain: DomainDesign) : Promise<DomainDesign> {
    return new Promise((resolve, reject) => {
        Domain.save({
            domainDesign: domain,
            domainId: domain.domainId,
            success: (data) => {
                resolve(data);
            },
            failure: (error) => {
                reject(error);
            }
        })
    })
}

export function updateField(domain: DomainDesign, fieldId: string, value: any) {
    const idType = fieldId.split(DOMAIN_FIELD_PREFIX)[1];
    const type = idType.split("-")[0];
    const id = idType.split("-")[1];

    const newFields = domain.fields.map((field) => {

        if (field.propertyId.toString() === id) {
            field.updatedField = true;
            switch (type) {
                case DOMAIN_FIELD_NAME:
                    field.name = value;
                    break;
                case DOMAIN_FIELD_TYPE:
                    PropDescTypes.map((type) => {
                        if (type.name === value) {
                            field.rangeURI = type.rangeURI;
                            field.conceptURI = type.conceptURI;
                        }
                    });
                    break;
                case DOMAIN_FIELD_REQ:
                    field.required = value;
                    break;
            }
        }

        return field;
    });

    return Object.assign({}, domain, {fields: List(newFields)});
}

export function clearFieldDetails(domain: DomainDesign) {

    const newFields = domain.fields.map((field) => {
        field.updatedField = false;
        field.newField = false;

        return field;
    });

    return Object.assign({}, domain, {fields: List(newFields)});
}