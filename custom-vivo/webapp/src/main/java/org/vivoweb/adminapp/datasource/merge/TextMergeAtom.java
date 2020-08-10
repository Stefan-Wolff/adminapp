package org.vivoweb.adminapp.datasource.merge;

/**
 * @author swolff
 */
public class TextMergeAtom extends MergeRuleAtom {

    private String mergeDataPropertyURI;
    private final int matchDegree;
    private final boolean nameVariants;
    
    public TextMergeAtom(String uri, String mergeDataPropertyURI, int matchDegree, boolean nameVariants) {
        super(uri);
        
        if (null == mergeDataPropertyURI) {
            throw new IllegalArgumentException("No data property to merge on defined! Merge Rule Pattern: " + uri);
        }
        
        this.mergeDataPropertyURI = mergeDataPropertyURI;
        this.matchDegree = matchDegree;
        this.nameVariants = nameVariants;
    }

    public String getMergeDataPropertyURI() {
        return this.mergeDataPropertyURI;
    }

    public void setMergeDataPropertyURI(String mergePropertyURI) {
        this.mergeDataPropertyURI = mergePropertyURI;
    }
    
    public int getMatchDegree() {
        return this.matchDegree;
    }

    public boolean nameVariants() {
        return nameVariants;
    }
    
}
