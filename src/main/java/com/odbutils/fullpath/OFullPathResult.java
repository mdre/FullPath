package com.odbutils.fullpath;

import java.util.Map;
import java.util.Optional;

import com.orientechnologies.orient.core.command.traverse.OTraverse;
import com.orientechnologies.orient.core.sql.executor.OExecutionPlan;
import com.orientechnologies.orient.core.sql.executor.OResult;
import com.orientechnologies.orient.core.sql.executor.OResultSet;

public class OFullPathResult implements OResultSet {

    @Override
    public boolean hasNext() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public OResult next() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void close() {
        // TODO Auto-generated method stub
        
    }

    @Override
    public Optional<OExecutionPlan> getExecutionPlan() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Map<String, Long> getQueryStats() {
        // TODO Auto-generated method stub
        return null;
    }

    
}
