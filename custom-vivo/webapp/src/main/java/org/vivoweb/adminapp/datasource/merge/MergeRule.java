package org.vivoweb.adminapp.datasource.merge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MergeRule {

    private final String URI;
    private String mergeClassURI;
    private int priority;
    private List<MergeRuleAtom> atoms = new ArrayList<MergeRuleAtom>();
    private Map<String, MergeRule> linkedRules = new HashMap<String, MergeRule>(); // <linkingObjectPropertyURI, linkedRule>
    
    public MergeRule(String uri) {
        this.URI = uri;
    }
    
    public String getURI() {
        return this.URI;
    }

    public String getMergeClassURI() {
        return this.mergeClassURI;
    }

    public void setMergeClassURI(String mergeClassURI) {
        this.mergeClassURI = mergeClassURI;
    }

    public int getPriority() {
        return this.priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public List<MergeRuleAtom> getAtoms() {
        return this.atoms;
    }

    public void addAtom(MergeRuleAtom atom) {
        this.atoms.add(atom);
    }

    public void addLinkedRule(String objPropURI, MergeRule rule) {
        linkedRules.put(objPropURI, rule);
    }

    public Map<String, MergeRule> getLinkedRules() {
        return linkedRules;
    }
}
