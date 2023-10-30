package org.snomed.snowstormlite.domain.valueset;

import org.hl7.fhir.r4.model.ValueSet;

public class FHIRValueSetFilter {

    private String property;

    private String op;

    private String value;

    public FHIRValueSetFilter() {
    }

    public FHIRValueSetFilter(String property, String op, String value) {
        this.property = property;
        this.op = op;
        this.value = value;
    }

    public FHIRValueSetFilter(ValueSet.ConceptSetFilterComponent hapiFilter) {
        property = hapiFilter.getProperty();
        op = hapiFilter.getOp().toCode();
        value = hapiFilter.getValue();
    }

    public ValueSet.ConceptSetFilterComponent toHapi() {
        ValueSet.ConceptSetFilterComponent component = new ValueSet.ConceptSetFilterComponent();
        component.setProperty(property);
        component.setOp(ValueSet.FilterOperator.fromCode(op));
        component.setValue(value);
        return component;
    }

    public String getProperty() {
        return property;
    }

    public void setProperty(String property) {
        this.property = property;
    }

    public String getOp() {
        return op;
    }

    public void setOp(String op) {
        this.op = op;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
