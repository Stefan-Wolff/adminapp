package org.vivoweb.adminapp.datasource.merge;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

/**
 * Performance optimized version of building name variant (short name).
 * 
 * @author swolff
 */
public class NameVariantBuilder {

    private StringBuilder firstName = new StringBuilder();
    private StringBuilder lastName = new StringBuilder();
    private StringBuilder shortName = new StringBuilder();
    
    private final Map<String, String> nameCache = new HashMap<String, String>();
    
    
    /**
     * @param name 
     *              must not be <code>null</code> and 
     *              must be lower case
     */
    public String build(String name) {
        String cached = nameCache.get(name);
        if (null != cached) {
            return cached;
        }
        
        firstName.setLength(0);
        lastName.setLength(0);
        
        boolean firstDone = false;
        boolean switchOrder = false;
        
        for (char c : name.toCharArray()) {
            if (',' == c) {                                             // doe, john -> john doe
                switchOrder = true;
                firstDone = true;
                continue;
            } else if (' ' == c || '.' == c || '-' == c || '/' == c) {
                if (0 == firstName.length()) {
                    continue;
                }
                
                firstDone = true;
                
                if (0 != lastName.length()) {
                    if (switchOrder) {
                        break;
                    } else {
                        lastName.setLength(0);
                    }
                }
                
                continue;
            }
            
            if (!firstDone) {
               firstName.append(replaceUmlauts(c));
            } else {
               lastName.append(replaceUmlauts(c));
            }
        }
        
        if (0 == firstName.length() && 0 == lastName.length()) {
            return StringUtils.EMPTY;
        }
        
        if (switchOrder) {
            StringBuilder trade = firstName;
            firstName = lastName;
            lastName = trade;
        }
        
        shortName.setLength(0);
        
        if (0 == firstName.length()) {
            shortName.append(lastName);
            
        } else if (0 == lastName.length()) {
            shortName.append(firstName);
            
        } else {
            shortName.append(firstName.charAt(0))
            .append(' ')
            .append(lastName);
        }

        String result = shortName.toString();        
        nameCache.put(name, result);
        
        return result;
    }
    
    
    private char[] replaceUmlauts(char c) {
        char[][] charMapping = {{'a', 'e'}, {'o', 'e'}, {'u', 'e'}, {'s', 's'}};
        
        switch(c) {
            case 'ä':
                return charMapping[0];      // ae
                
            case 'ö':
                return charMapping[1];      // oe
                
            case 'ü':
                return charMapping[2];      // ue
                
            case 'ß':
                return charMapping[3];      // ss
                
            default:
                char[] r = {c};
                return r;
        }
    }
    
    
}
