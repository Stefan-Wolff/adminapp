package org.vivoweb.adminapp.datasource.merge;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

public class MergeRule {

    private final String URI;
    private String mergeClassURI;
    private int priority;
    private Collection<MergeRuleAtom> atoms = new TreeSet<MergeRuleAtom>(new MergeAtomComparator());
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

    public Collection<MergeRuleAtom> getAtoms() {
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
    
    
    private class MergeAtomComparator implements Comparator<MergeRuleAtom> {

        @Override
        public int compare(MergeRuleAtom o1, MergeRuleAtom o2) {
            int prio1 = o1 instanceof ObjectPropertyMergeAtom ? 1 : 0;
            int prio2 = o2 instanceof ObjectPropertyMergeAtom ? 1 : 0;

            return prio1 - prio2;
        }
    }
    
}
