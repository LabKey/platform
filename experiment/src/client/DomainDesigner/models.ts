import {List, Record, fromJS} from "immutable";
import {number} from "prop-types";

interface IPropDescType{
    name: string,
    display?: string,
    rangeURI?: string,
    conceptURI?: string
}

export class PropDescType extends Record({
    name: '',
    display: '',
    rangeURI: '',
    conceptURI: ''
}) implements IPropDescType {
    name: string;
    display: string;
    rangeURI: string;
    conceptURI: string;

    constructor(values?: {[key:string]: any}) {
        super(values);
    }
}

export const PropDescTypes = List([
    new PropDescType({name: 'string', display: 'Text (String)', rangeURI: 'http://www.w3.org/2001/XMLSchema#string'}),
    new PropDescType({name: 'multiLine', display: 'Multi-Line Text', rangeURI: 'http://www.w3.org/2001/XMLSchema#multiLine'}),
    new PropDescType({name: 'boolean', display: 'Boolean', rangeURI: 'http://www.w3.org/2001/XMLSchema#boolean'}),
    new PropDescType({name: 'int', display: 'Integer', rangeURI: 'http://www.w3.org/2001/XMLSchema#int'}),
    new PropDescType({name: 'double', display: 'Number (Double)', rangeURI: 'http://www.w3.org/2001/XMLSchema#double'}),
    new PropDescType({name: 'dateTime', display: 'Date Time', rangeURI: 'http://www.w3.org/2001/XMLSchema#dateTime'}),
    new PropDescType({name: 'flag', display: 'Flag (String)', rangeURI: 'http://www.w3.org/2001/XMLSchema#string', conceptURI: 'http://www.labkey.org/exp/xml#flag'}),
    new PropDescType({name: 'fileLink', display: 'File', rangeURI: 'http://cpas.fhcrc.org/exp/xml#fileLink'}),
    new PropDescType({name: 'attachment', display: 'Attachment', rangeURI: 'http://www.labkey.org/exp/xml#attachment'}),
    new PropDescType({name: 'users', display: 'User', rangeURI: 'http://www.labkey.org/exp/xml#int'}),
    new PropDescType({name: 'ParticipantId', display: 'Subject/Participant (String)', rangeURI: 'http://www.w3.org/2001/XMLSchema#string', conceptURI: 'http://cpas.labkey.com/Study#ParticipantId'}),
    new PropDescType({name: 'lookup', display: 'Lookup'}),
]);

export const DOMAIN_FORM_ID = 'domain-form'
export const DOMAIN_FIELD_PREFIX = 'dom-row-';
export const DOMAIN_FIELD_NAME = 'name';
export const DOMAIN_FIELD_TYPE = 'type';
export const DOMAIN_FIELD_REQ = 'req';
export const DOMAIN_FIELD_DETAILS = 'details';
export const DOMAIN_FIELD_ADV = 'adv';

interface IDomainForm
{
    domain: DomainDesign
    onChange?: () => any
    onSubmit?: () => any
}

export class DomainForm extends Record({
    domain: undefined,
    onChange: undefined,
    onSubmit: undefined,
}) implements IDomainForm {
    domain: undefined;
    onChange: undefined;
    onSubmit: undefined;

    constructor(values?: {[key:string]: any}) {
        super(values);
    }
}

interface IDomainDesign {
    name: string
    description?: string
    domainURI: string
    domainId: number
    fields?: List<DomainField>
    indices?: List<DomainIndex>
}

export class DomainDesign extends Record({
    name: '',
    description: '',
    domainURI: undefined,
    domainId: null,
    fields: List<DomainField>(),
    indices: List<DomainIndex>()
}) implements IDomainDesign {
    name: string;
    description: string;
    domainURI: string;
    domainId: number;
    fields: List<DomainField>;
    indices: List<DomainIndex>;

    constructor(values?: {[key:string]: any}) {
        super(values);
    }
}

interface IDomainIndex {
    columns: Array<string> | List<string>
    type: 'primary' | 'unique'
}

class DomainIndex extends Record({
    columns: List<string>(),
    type: undefined
}) implements IDomainIndex {
    columns: List<string>;
    type: 'primary' | 'unique';

    static fromJS(rawIndices: Array<IDomainIndex>): List<DomainIndex> {
        let indices = List<DomainIndex>().asMutable();

        for (let i=0; i < rawIndices.length; i++) {
            indices.push(new DomainIndex(fromJS(rawIndices[i])));
        }

        return indices.asImmutable();
    }

    constructor(values?: {[key:string]: any}) {
        super(values);
    }
}

interface IDomainField {
    name: string
    rangeURI: string
    propertyId: number
    description?: string
    label?: string
    conceptURI?: string
    required?: boolean
    lookupContainer?: string
    lookupSchema?: string
    lookupQuery?: string
    scale?: number
    hidden?: boolean
    userEditable?: boolean
    shownInInsertView?: boolean
    shownInUpdateView?: boolean
    updatedField?: boolean
    newField?: boolean
}

export class DomainField extends Record({
    propertyId: undefined,
    name: undefined,
    description: undefined,
    label: undefined,
    rangeURI: undefined,
    conceptURI: undefined,
    required: false,
    lookupContainer: undefined,
    lookupSchema: undefined,
    lookupQuery: undefined,
    scale: undefined,
    updatedField: undefined,
    newField: undefined
}) implements IDomainField {
    propertyId: number;
    name: string;
    description: string;
    label: string;
    rangeURI: string;
    conceptURI: string;
    required: boolean;
    lookupContainer: string;
    lookupSchema: string;
    lookupQuery: string;
    scale: number;
    updatedField: boolean;
    newField: boolean;

    static fromJS(rawFields: Array<IDomainField>): List<DomainField> {
        let fields = List<DomainField>().asMutable();

        for (let i=0; i < rawFields.length; i++) {
            fields.push(new DomainField(rawFields[i]));
        }

        return fields.asImmutable();
    }

    constructor(values?: {[key:string]: any}) {
        super(values);
    }
}