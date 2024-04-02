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
        protected Map<String, Set<String>> newInitialFlow() {
            return new HashMap<>();
        }

        @Override
        protected Map<String, Set<String>> entryInitialFlow() {
            return new HashMap<>();
        }

        @Override
        protected void merge(Map<String, Set<String>> in1, Map<String, Set<String>> in2, Map<String, Set<String>> out) {
            out.clear();
            in1.forEach((key, value) -> out.put(key, new HashSet<>(value)));
            in2.forEach((key, value) -> out.merge(key, value, (v1, v2) -> { v1.addAll(v2); return v1; }));
        }

        @Override
        protected void copy(Map<String, Set<String>> source, Map<String, Set<String>> dest) {
            dest.clear();
            source.forEach((key, value) -> dest.put(key, new HashSet<>(value)));
        }

        @Override
        protected void flowThrough(Map<String, Set<String>> current, Unit unit, Map<String, Set<String>> next) {
            next.putAll(current);
            if (unit instanceof InvokeStmt) {
                InvokeStmt stmt = (InvokeStmt) unit;
                InvokeExpr invokeExpr = stmt.getInvokeExpr();
                if (invokeExpr instanceof InstanceInvokeExpr) {
                    InstanceInvokeExpr instanceInvokeExpr = (InstanceInvokeExpr) invokeExpr;
                    String variableName = instanceInvokeExpr.getBase().toString();
                    String methodName = invokeExpr.getMethod().getName();
                    Set<String> currentStates = current.getOrDefault(variableName, new HashSet<>(Arrays.asList("sleeping")));
                    Set<String> newStates = new HashSet<>();
                    for (String state : currentStates) {
                        if (isValidTransition(state, methodName)) {
                            newStates.add(mapMethodNameToState(methodName));
                        } else {
                            reportError(variableName, stmt, state, mapMethodNameToState(methodName));
                        }
                    }
                    if (!newStates.isEmpty()) {
                        next.put(variableName, newStates);
                    }
                }
            }
            if (isLoopHead(unit)) {
                handleLoop(next);
            }
        }

        private boolean isValidTransition(String currentState, String methodName) {
            
            return true; 
        }

        private String mapMethodNameToState(String methodName) {
           
            return "unknown"; 
        }

        private void reportError(String variableName, InvokeStmt stmt, String currentState, String targetState) {
            int line = stmt.getJavaSourceStartLineNumber();
            reporter.reportError(variableName, line, targetState, currentState);
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
