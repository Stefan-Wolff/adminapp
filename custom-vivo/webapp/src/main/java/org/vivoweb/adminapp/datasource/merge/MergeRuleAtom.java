package org.vivoweb.adminapp.datasource.merge;

public abstract class MergeRuleAtom {
    
    private final String URI;

    public MergeRuleAtom(String uri) {
        this.URI = uri;
    }
    
    public String getURI() {
        return this.URI;
    }
    
}
