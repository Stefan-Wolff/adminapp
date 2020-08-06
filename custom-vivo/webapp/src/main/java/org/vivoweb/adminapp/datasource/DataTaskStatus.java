package org.vivoweb.adminapp.datasource;

public class DataTaskStatus {

    private boolean ok = true;
    private String message;
    private boolean isRunning;
    private long totalRecords;
    private int progress;


    public boolean isStatusOk() {
        return this.ok;
    }

    public String getMessage() {
        return this.message;
    }

    public boolean isRunning() {
        return this.isRunning;
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

    public void setRunning(boolean running) {
        this.isRunning = running;
    }

    public void setTotalRecords(long totalRecords) {
        this.totalRecords = totalRecords;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }
    

    
}