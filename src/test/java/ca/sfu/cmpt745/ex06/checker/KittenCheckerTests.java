package ca.sfu.cmpt745.ex06.checker;

import java.util.Map;
import java.util.EnumSet;

import java.util.List;
import java.util.ArrayList;

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

        private Map<String, String> lastMethodCalled = new HashMap<>();
        private Map<String, List<String>> methodSequenceContext = new HashMap<>();
        @Override
        protected void flowThrough(Map<String, String> current, Unit unit, Map<String, String> next) {
            next.putAll(current);

            if (unit instanceof InvokeStmt) {
                InvokeStmt stmt = (InvokeStmt) unit;
                InvokeExpr invokeExpr = stmt.getInvokeExpr();
                if (invokeExpr instanceof InstanceInvokeExpr) {
                    InstanceInvokeExpr instanceInvokeExpr = (InstanceInvokeExpr) invokeExpr;
                    String variableName = instanceInvokeExpr.getBase().toString();
                    String methodName = invokeExpr.getMethod().getName();
                    String currentState = current.getOrDefault(variableName, "sleeping");

                    String newState = mapMethodNameToState(methodName);
                    boolean validTransition = isValidTransition(currentState, methodName);
                      System.out.println("Analyzing: " + variableName + ", Method: " + methodName + 
                               ", Current State: " + currentState + ", New State: " + newState + 
                               ", Valid Transition: " + validTransition);

                    methodSequenceContext.computeIfAbsent(variableName, k -> new ArrayList<>()).add(methodName);

                    if (validTransition) {
                        System.out.println("State Transition: " + variableName + " from " + currentState + " to " + newState);
                        next.put(variableName, newState);
                        lastMethodCalled.put(variableName, methodName);
                    } else {
                        reporter.reportError(variableName, unit.getJavaSourceStartLineNumber(), newState, currentState);
                    }
                }
            }
        }

        private boolean isValidTransition(String currentState, String methodName) {
          switch (methodName) {
              case "pet":
                  return !currentState.equals("running") && !currentState.equals("playing");
              case "tease":
                  return !currentState.equals("sleeping") && !currentState.equals("eating");
              case "ignore":
                  return !currentState.equals("sleeping") && !currentState.equals("eating") && !currentState.equals("playing");
              case "feed":
                    return true; 
              case "scare": 
                  return true;
              default:
                  return true;
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

