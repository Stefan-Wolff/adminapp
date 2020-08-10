package org.vivoweb.adminapp.datasource.merge;

/**
 * @author swolff
 */
public class ObjectPropertyMergeAtom extends MergeRuleAtom {

    private final String mergeObjectPropertyURI;
    
    public ObjectPropertyMergeAtom(String uri, String mergeObjectPropertyURI) {
        super(uri);
        
        if (null == mergeObjectPropertyURI) {
            throw new IllegalArgumentException("No object property given!");
        }
        
        this.mergeObjectPropertyURI = mergeObjectPropertyURI;
    }

    public String getMergeObjectPropertyURI() {
        return this.mergeObjectPropertyURI;
    }
    
}
