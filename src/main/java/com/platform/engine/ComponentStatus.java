package com.platform.engine;

public enum ComponentStatus {
    DRAFT,      // defined in DB, no class generated yet
    LOADING,    // class detected, being loaded
    ACTIVE,     // loaded, receiving events
    RELOADING,  // being reloaded, events queued
    UNLOADED,   // manually unloaded, not receiving events
    FAILED      // load or execution failure
}
