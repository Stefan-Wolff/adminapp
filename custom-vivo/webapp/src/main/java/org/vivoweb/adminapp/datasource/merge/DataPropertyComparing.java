package org.vivoweb.adminapp.datasource.merge;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.jena.rdf.model.Resource;

/**
 * Processing of a TextMergeRule, made for parallel processing.
 * 
 * @author swolff
 */
public class DataPropertyComparing implements Runnable {
    private static final Log log = LogFactory.getLog(DataPropertyComparing.class);
    
    private final Levenshtein lv = new Levenshtein();
    private final Set<Entry<Resource, List<String>>> valueSet;
    private final List<List<Resource>> duplicates = new LinkedList<>();
    private final int start;
    private final int end;
    private final int matchDegree;
    private final int threadID;
    private boolean done = false;
    
    
    public DataPropertyComparing(Set<Entry<Resource, List<String>>> valueSet, int start, int end, int matchDegree, int threadID) {
        this.valueSet = valueSet;
        this.start = start;
        this.end = end;
        this.matchDegree = matchDegree;
        this.threadID = threadID;
    }
    
    @Override
    public void run() {
        log.info("data property comparing: start thread " + threadID + "  start: " + start + "  end: " + end);
        
        int i = 0;
        for (Entry<Resource, List<String>> entry : valueSet) {
            if (i >= start) {
                
                for (String value1 : entry.getValue()) {
                    
                    int j = 0;
                    for (Entry<Resource, List<String>> entry2 : valueSet) {
                        if (i < j) {
                            for (String value2 : entry2.getValue()) {
                           
                                boolean match = false;
                                if (100 > matchDegree) {
                                    if (lv.match(value1, value2, matchDegree)) {
                                        match = true;
                                    }
                                
                                } else if (value1.equals(value2)) {
                                    match = true;
                                }
                                
                                if (match) {
                                    List<Resource> duplList = new LinkedList<>();
                                    duplList.add(entry.getKey());
                                    duplList.add(entry2.getKey());
                                    duplicates.add(duplList);
                                    break;
                                }
    
                            }
                        }
                        
                        j++;
                    }
                }
                
            }
            
            if (++i >= end) {
                break;
            }
        }
        
        synchronized (this) {
            done = true;
            notify();
            log.info("data property comparing: thread " + threadID + " done");
        }
        
    }
    
    public synchronized void waitForDone() throws IOException {
        if (done) {
            return;
        }
        
        try {
            wait();
        } catch (InterruptedException e) {
            // should never be thrown
            log.error(e);
            throw new IOException(e);
        }
    }
    
    public List<List<Resource>> getDuplicates() {
        return duplicates;
    }
 
}
