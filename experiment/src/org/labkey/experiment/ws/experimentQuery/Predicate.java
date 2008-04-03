/**
 * Predicate.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.4 Jul 07, 2006 (02:38:00 PDT) WSDL2Java emitter.
 */

package org.labkey.experiment.ws.experimentQuery;

public class Predicate implements java.io.Serializable {
    private java.lang.String _value_;
    private static java.util.HashMap _table_ = new java.util.HashMap();

    // Constructor
    protected Predicate(java.lang.String value) {
        _value_ = value;
        _table_.put(_value_,this);
    }

    public static final java.lang.String _equal = "equal";
    public static final java.lang.String _like = "like";
    public static final Predicate equal = new Predicate(_equal);
    public static final Predicate like = new Predicate(_like);
    public java.lang.String getValue() { return _value_;}
    public static Predicate fromValue(java.lang.String value)
          throws java.lang.IllegalArgumentException {
        Predicate enumeration = (Predicate)
            _table_.get(value);
        if (enumeration==null) throw new java.lang.IllegalArgumentException();
        return enumeration;
    }
    public static Predicate fromString(java.lang.String value)
          throws java.lang.IllegalArgumentException {
        return fromValue(value);
    }
    public boolean equals(java.lang.Object obj) {return (obj == this);}
    public int hashCode() { return toString().hashCode();}
    public java.lang.String toString() { return _value_;}
    public java.lang.Object readResolve() throws java.io.ObjectStreamException { return fromValue(_value_);}
    public static org.apache.axis.encoding.Serializer getSerializer(
           java.lang.String mechType, 
           java.lang.Class _javaType,  
           javax.xml.namespace.QName _xmlType) {
        return 
          new org.apache.axis.encoding.ser.EnumSerializer(
            _javaType, _xmlType);
    }
    public static org.apache.axis.encoding.Deserializer getDeserializer(
           java.lang.String mechType, 
           java.lang.Class _javaType,  
           javax.xml.namespace.QName _xmlType) {
        return 
          new org.apache.axis.encoding.ser.EnumDeserializer(
            _javaType, _xmlType);
    }
    // Type metadata
    private static org.apache.axis.description.TypeDesc typeDesc =
        new org.apache.axis.description.TypeDesc(Predicate.class);

    static {
        typeDesc.setXmlType(new javax.xml.namespace.QName("http://cpas.fhcrc.org/experimentQuery/xml", "Predicate"));
    }
    /**
     * Return type metadata object
     */
    public static org.apache.axis.description.TypeDesc getTypeDesc() {
        return typeDesc;
    }

}
