package org.vivoweb.adminapp.datasource.merge;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

public class NameVariantBuilder {

    private final Map<String, String> nameCache = new HashMap<String, String>();
    
    public String build(String name) {
        String result = nameCache.get(name);
        if (null != result) {
            return result;
        }
        
        result = name;
        
        if (result.contains(",")) {                                                                         // doe, john -> john doe
            String[] commaParts = result.split(",");
            ArrayUtils.reverse(commaParts);
            result = StringUtils.join(commaParts, StringUtils.SPACE);
        }
        
        result = result.replaceAll("[.,-/]", StringUtils.SPACE)
                       .replaceAll("[ ]+", StringUtils.SPACE).trim();
        
        result = result.replace("ä", "ae")
                       .replace("ö", "oe")
                       .replace("ü", "ue")
                       .replace("ß", "ss");
        
        String[] spaceParts = result.split(StringUtils.SPACE);
        
        if (1 < spaceParts.length) {
            result = spaceParts[0].charAt(0) + StringUtils.SPACE + spaceParts[spaceParts.length-1];         // first name abbreviated && no middle name
        }
        
        nameCache.put(name, result);
        
        return result;  
    }
}
