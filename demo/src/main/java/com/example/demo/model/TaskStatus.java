package com.example.demo.model;

public enum TaskStatus {
    TODO, IN_PROGRESS, DONE;

    public boolean canTransitionTo(TaskStatus next){
        return next.ordinal() > this.ordinal();
    }
}
