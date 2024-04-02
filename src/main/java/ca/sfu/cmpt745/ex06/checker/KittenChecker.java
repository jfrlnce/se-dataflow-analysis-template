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
        protected void flowThrough(Map<String, String> in, Unit unit, Map<String, String> out) {
          out.putAll(in);         
          processUnit(unit, in, out);
          if (isLoopHeader(unit) || isConditionalBranch(unit)) {
              saveStateForUnit(unit, in);
          }
          if (isLoopEnd(unit) || isConditionalJoin(unit)) {
              Map<String, String> savedState = getSavedStateForUnit(unit);
              Map<String, String> mergedState = mergeSavedStateWithCurrent(savedState, out);
              out.clear();
              out.putAll(mergedState);
          }
        }


        private void processUnit(Unit unit, Map<String, String> in, Map<String, String> out) {
            if (unit instanceof InvokeStmt) {
                InvokeStmt invokeStmt = (InvokeStmt) unit;
                InvokeExpr invokeExpr = invokeStmt.getInvokeExpr();
                processInvokeExpr(invokeExpr, in, out, unit);
            } else if (unit instanceof DefinitionStmt) {
                DefinitionStmt definitionStmt = (DefinitionStmt) unit;
                Value rightOp = definitionStmt.getRightOp();
                if (rightOp instanceof InvokeExpr) {
                    processInvokeExpr((InvokeExpr) rightOp, in, out, unit);
                }
            }
        }

        private void processInvokeExpr(InvokeExpr invokeExpr, Map<String, String> in, Map<String, String> out, Unit unit) {
            if (!(invokeExpr instanceof InstanceInvokeExpr)) return;

            InstanceInvokeExpr instanceInvokeExpr = (InstanceInvokeExpr) invokeExpr;
            String methodName = invokeExpr.getMethod().getName();
            SootMethod method = invokeExpr.getMethod();
            if (!method.getDeclaringClass().getName().equals("Kitten")) return;

            String variableName = instanceInvokeExpr.getBase().toString();
            String newState = mapMethodNameToState(methodName);
            String currentState = in.getOrDefault(variableName, "sleeping"); 
            if (!isValidTransition(currentState, newState)) {
                
                int line = unit.getJavaSourceStartLineNumber();
                reporter.reportError(variableName, line, newState, currentState);
            } else {
                out.put(variableName, newState);
            }
        }

        private boolean isLoopHeader(Unit unit) {
            Body body = this.body; 
            DirectedGraph<Unit> graph = new ExceptionalUnitGraph(body);
            List<Unit> loopHeaders = new ArrayList<>();
            for (Unit u : graph) {
                for (Unit succ : graph.getSuccsOf(u)) {
                    if (graph.getPredsOf(succ).contains(u) && body.getUnits().indexOf(succ) < body.getUnits().indexOf(u)) {
                        loopHeaders.add(succ);
                    }
                }
            }
            
            return loopHeaders.contains(unit);
        }

        private boolean isLoopEnd(Unit unit) {
            
            DirectedGraph<Unit> graph = this.graph;
            Set<Unit> loopHeaders = new HashSet<>(findLoopHeaders(this.body)); 
            for (Unit header : loopHeaders) {
                if (graph.getPredsOf(header).contains(unit)) {
                    return true;
                }

          
                for (Unit succ : graph.getSuccsOf(unit)) {
                    if (!loopHeaders.contains(succ) && allSuccessorsOutsideLoop(succ, graph, loopHeaders)) {
                        return true; 
                    }
                }
            }
            return false;
        }

        private boolean allSuccessorsOutsideLoop(Unit unit, DirectedGraph<Unit> graph, Set<Unit> loopHeaders) {
            
            for (Unit succ : graph.getSuccsOf(unit)) {  
                if (loopHeaders.contains(succ) || graph.getPredsOf(succ).stream().anyMatch(loopHeaders::contains)) {
                    return false;
                }
            }
            return true; 
        }

        private Set<Unit> findLoopHeaders(Body body) {
            Set<Unit> loopHeaders = new HashSet<>();
            DirectedGraph<Unit> graph = new ExceptionalUnitGraph(body);
            for (Unit u : graph) {
                for (Unit succ : graph.getSuccsOf(u)) {
                    if (graph.getPredsOf(succ).contains(u) && body.getUnits().indexOf(succ) < body.getUnits().indexOf(u)) {
                        loopHeaders.add(succ);
                    }
                }
            }
            return loopHeaders;
        }

        private boolean isConditionalBranch(Unit unit) {
            return unit instanceof IfStmt;
        }

        private boolean isConditionalJoin(Unit unit) {
            DirectedGraph<Unit> graph = this.graph;         
            List<Unit> predecessors = graph.getPredsOf(unit);
            if (predecessors.size() <= 1) {
                return false; 
            }
            
            for (Unit pred : predecessors) {
    
                for (Unit succ : graph.getSuccsOf(pred)) {
                    if (succ.equals(unit) && pred instanceof IfStmt) {
                        return true; 
                    }
                }
            }

           
            return false;
        }


        private Map<String, String> mergeSavedStateWithCurrent(Map<String, String> savedState, Map<String, String> currentState) {
            
            Map<String, String> mergedState = new HashMap<>(currentState);
            
            
            savedState.forEach((kitten, state) -> {
                if (currentState.containsKey(kitten)) {
                    String currentStateForKitten = currentState.get(kitten);
                    
                    if (!state.equals(currentStateForKitten)) {
                      
                        mergedState.put(kitten, "unknown");
                    }
                    
                } else {
                    
                    mergedState.put(kitten, state);
                }
            });
            
            return mergedState;
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





