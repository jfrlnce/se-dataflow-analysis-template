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

    public KittenChecker(KittenErrorReporter reporter) {
        this.reporter = reporter;
    }

    @Override
    protected void internalTransform(Body body, String phase, Map<String, String> options) {
        UnitGraph graph = new ExceptionalUnitGraph(body);
        KittenAnalysis analysis = new KittenAnalysis(graph, reporter);
        
    }

    private class KittenAnalysis extends ForwardFlowAnalysis<Unit, Map<String, String>> {
        Map<Unit, Map<String, String>> unitToAfterFlowMap = new HashMap<>();

        public KittenAnalysis(UnitGraph graph, KittenErrorReporter reporter) {
            super(graph);
            doAnalysis();
        }

        @Override
        protected void flowThrough(Map<String, String> in, Unit unit, Map<String, String> out) {
            out.putAll(in); 

            if (unit instanceof Stmt) {
                Stmt stmt = (Stmt) unit;
                if (stmt.containsInvokeExpr()) {
                    InvokeExpr invokeExpr = stmt.getInvokeExpr();
                    if (invokeExpr.getMethod().getDeclaringClass().getName().equals("Kitten")) {
                        String methodName = invokeExpr.getMethod().getName();
                        String variableName = ((Local) ((InstanceInvokeExpr) invokeExpr).getBase()).toString();
                        String currentState = in.getOrDefault(variableName, "sleeping");
                        String newState = mapMethodNameToState(methodName);

                        if (!isValidTransition(currentState, methodName)) {
                            int line = unit.getJavaSourceStartLineNumber();
                            reporter.reportError(variableName, line, newState, currentState);
                        } else {
                            out.put(variableName, newState);
                        }
                    }
                }
            }
            unitToAfterFlowMap.put(unit, new HashMap<>(out)); 
        }

        @Override
        protected Map<String, String> newInitialFlow() {
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

