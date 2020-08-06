package org.vivoweb.adminapp.datasource.merge;

public class AuthorGroupMergeAtom extends TextMergeAtom {

    private final int numPublications;
    private final int numPersons;
    
    public AuthorGroupMergeAtom(String uri, String mergeDataPropertyURI, int matchDegree, boolean nameVariants, int numPublications, int numPersons) {
        super(uri, mergeDataPropertyURI, matchDegree, nameVariants);
        
        if (2 > numPublications) {
            throw new IllegalArgumentException("Invalid number of publications configured: " + numPublications + " - merge rule pattern: " + uri);
        }
        if (2 > numPersons) {
            throw new IllegalArgumentException("Invalid number of persons configured: " + numPublications + " - merge rule pattern: " + uri);
        }
        
        this.numPublications = numPublications;
        this.numPersons = numPersons;
    }

    public int getNumPublications() {
        return numPublications;
    }

    public int getNumPersons() {
        return numPersons;
    }

}
