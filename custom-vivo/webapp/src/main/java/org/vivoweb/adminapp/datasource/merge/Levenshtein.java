package org.vivoweb.adminapp.datasource.merge;

import org.apache.commons.text.similarity.LevenshteinDistance;

public class Levenshtein {
    
    private final LevenshteinDistance[] levenshtein = new LevenshteinDistance[1000];
    
    
    public boolean match(String value1, String value2, int percent) {
        int threshold = (int) (value1.length() * (100 - percent) / 100);
        
        if (threshold < Math.abs(value1.length() - value2.length())) {
            return false;
        }
        
        if (null == levenshtein[threshold]) {
            levenshtein[threshold] = new LevenshteinDistance(threshold);
        }
        int distance = levenshtein[threshold].apply(value1, value2);
        
        return -1 != distance;
    }
    
}
