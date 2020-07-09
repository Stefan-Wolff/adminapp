package org.vivoweb.adminapp.datasource;

public class DataTaskStatus {

    private boolean ok = true;
    private String message;
    private boolean isRunning;
    private int completionPercentage;
    private long totalRecords;


    public boolean isStatusOk() {
        return this.ok;
    }

    public String getMessage() {
        return this.message;
    }

    public synchronized boolean isRunning() {
        return this.isRunning;
    }

    public int getCompletionPercentage() {
        return this.completionPercentage;
    }

    public long getTotalRecords() {
        return this.totalRecords;
    }


    public void setStatusOk(boolean ok) {
        this.ok = ok;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public synchronized void setRunning(boolean running) {
        this.isRunning = running;
    }

    public void setCompletionPercentage(int completionPercentage) {
        this.completionPercentage = completionPercentage;
    }

    public void setTotalRecords(long totalRecords) {
        this.totalRecords = totalRecords;
    }
    

}