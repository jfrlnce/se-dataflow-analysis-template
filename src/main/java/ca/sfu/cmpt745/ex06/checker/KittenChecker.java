package ca.sfu.cmpt745.ex06.checker;

import java.util.Map;
import java.util.EnumSet;

import soot.Body;
import soot.BodyTransformer;
import soot.G;
import soot.Local;
import soot.RefType;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.jimple.*;
import soot.jimple.AbstractExprSwitch;
import soot.tagkit.LineNumberTag;
import soot.tagkit.Tag;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.graph.UnitGraph;

import soot.toolkits.scalar.ForwardFlowAnalysis;
import java.util.HashMap;

public class KittenChecker extends BodyTransformer {
    final KittenErrorReporter reporter;

    KittenChecker(KittenErrorReporter reporter) {
        this.reporter = reporter;
    }

    @Override
    protected void internalTransform(Body body, String phase, Map options) {
        UnitGraph graph = new ExceptionalUnitGraph(body);
        KittenAnalysis analysis = new KittenAnalysis(graph, reporter);
    }

    private class KittenAnalysis extends ForwardFlowAnalysis<Unit, Map<String, String>> {
        private final UnitGraph graph;
        private final KittenErrorReporter reporter;

        public KittenAnalysis(UnitGraph graph, KittenErrorReporter reporter) {
            super(graph);
            this.graph = graph;
            this.reporter = reporter;
            doAnalysis();
        }

        @Override
        protected Map<String, String> newInitialFlow() {
            return new HashMap<>();
        }

        @Override
        protected Map<String, String> entryInitialFlow() {
            return new HashMap<>();
        }

        @Override
        protected void merge(Map<String, String> in1, Map<String, String> in2, Map<String, String> out) {
            out.clear();
            out.putAll(in1);
            in2.forEach((key, value) -> out.putIfAbsent(key, value));
        }

        @Override
        protected void copy(Map<String, String> source, Map<String, String> dest) {
            dest.clear();
            dest.putAll(source);
        }

        @Override
        protected void flowThrough(Map<String, String> current, Unit unit, Map<String, String> next) {
            next.putAll(current); 

            if (unit instanceof InvokeStmt) {
                handleInvokeStatement((InvokeStmt) unit, current, next);
            }
            
            // Loop detection and handling
            if (isLoopHead(unit)) {
                Map<String, String> mergedState = mergeStatesAtLoopHead(unit, current);
                Map<String, String> loopBodyState = analyzeLoopBody(unit, mergedState);
                next.putAll(loopBodyState);
            }
        }

        private void handleInvokeStatement(InvokeStmt stmt, Map<String, String> current, Map<String, String> next) {
            InvokeExpr invokeExpr = stmt.getInvokeExpr();
            if (invokeExpr instanceof InstanceInvokeExpr) {
                InstanceInvokeExpr instanceInvokeExpr = (InstanceInvokeExpr) invokeExpr;
                String variableName = instanceInvokeExpr.getBase().toString();
                String methodName = invokeExpr.getMethod().getName();
                String currentState = current.getOrDefault(variableName, "sleeping");
                String newState = mapMethodNameToState(methodName);
                boolean validTransition = isValidTransition(currentState, methodName);
                if (!validTransition) {
                    int line = stmt.getJavaSourceStartLineNumber();
                    reporter.reportError(variableName, line, newState, currentState);
                } else {
                    next.put(variableName, newState);
                }
            }
        }


        private boolean isLoopHead(Unit unit) {
            
            return false; 
        }

        private Map<String, String> mergeStatesAtLoopHead(Unit loopHead, Map<String, String> current) {
            
            return new HashMap<>(current); 
        }

        private Map<String, String> analyzeLoopBody(Unit loopHead, Map<String, String> initialState) {
            
        }

        private boolean isValidTransition(String currentState, String methodName) {
            switch (methodName) {
                case "pet": return !currentState.equals("running") && !currentState.equals("playing");
                case "tease": return !currentState.equals("sleeping") && !currentState.equals("eating");
                case "ignore": return !currentState.equals("sleeping") && !currentState.equals("eating") && !currentState.equals("playing");
                case "scare": return true; 
                default: return true; 
            }
        }

        private String mapMethodNameToState(String methodName) {
            switch (methodName) {
                case "pet": return "sleeping";
                case "feed": return "eating";
                case "tease": return "playing";
                case "ignore": return "plotting";
                case "scare": return "running";
                default: return "unknown"; 
            }
        }
    }
}



