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
          // Copy the incoming state to the outgoing state
          out.putAll(in);

          // Process the current unit (statement)
          processUnit(unit, in, out);

          // Check for loop headers and conditional branches
          if (isLoopHeader(unit) || isConditionalBranch(unit)) {
              // Save the current state for later comparison and merging
              saveStateForUnit(unit, in);
          }

          // After processing the unit, check if it's a loop end or a conditional join point
          if (isLoopEnd(unit) || isConditionalJoin(unit)) {
              // Retrieve the previously saved state
              Map<String, String> savedState = getSavedStateForUnit(unit);
              // Merge the saved state with the current state
              Map<String, String> mergedState = mergeSavedStateWithCurrent(savedState, out);
              // Update the outgoing state with the merged state
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
            String currentState = in.getOrDefault(variableName, "sleeping"); // Default state is "sleeping"

            if (!isValidTransition(currentState, newState)) {
                // Report an error if the transition is invalid
                reportTransitionError(variableName, currentState, newState, unit);
            } else {
                // Update the state for the next analysis iteration
                out.put(variableName, newState);
            }
        }

        private boolean isLoopHeader(Unit unit) {
            Body body = this.body; // Assume this.body is the method body being analyzed
            DirectedGraph<Unit> graph = new ExceptionalUnitGraph(body);
            
            // Find all units that have successors which are earlier in the body (indicative of a back edge)
            List<Unit> loopHeaders = new ArrayList<>();
            for (Unit u : graph) {
                for (Unit succ : graph.getSuccsOf(u)) {
                    if (graph.getPredsOf(succ).contains(u) && body.getUnits().indexOf(succ) < body.getUnits().indexOf(u)) {
                        // This means 'succ' is a predecessor of 'u' and appears earlier in the list of units, indicating a loop
                        loopHeaders.add(succ);
                    }
                }
            }
            
            // Check if the current unit is in the list of identified loop headers
            return loopHeaders.contains(unit);
        }

        private boolean isLoopEnd(Unit unit) {
            // Leverage the graph and loopHeaders identified in isLoopHeader
            DirectedGraph<Unit> graph = this.graph;
            Set<Unit> loopHeaders = new HashSet<>(findLoopHeaders(this.body)); // Assuming findLoopHeaders method returns a List or Set of loop header units

            // Loop ends are typically units with successors that are either:
            // - directly the loop header (for loops with a single exit back to the start), or
            // - outside the loop structure (indicating a break or exit condition).
            for (Unit header : loopHeaders) {
                // Check if 'unit' is a direct predecessor of any loop header (back edge to start the loop again)
                if (graph.getPredsOf(header).contains(unit)) {
                    return true;
                }

                // Check if 'unit' leads outside of the loop by verifying if any of its successors are not in the loop
                for (Unit succ : graph.getSuccsOf(unit)) {
                    if (!loopHeaders.contains(succ) && allSuccessorsOutsideLoop(succ, graph, loopHeaders)) {
                        return true; // 'unit' is a loop end as it leads outside
                    }
                }
            }
            return false;
        }

        private boolean allSuccessorsOutsideLoop(Unit unit, DirectedGraph<Unit> graph, Set<Unit> loopHeaders) {
            // Check if all successors of 'unit' lead outside the identified loop headers, indicating an exit
            for (Unit succ : graph.getSuccsOf(unit)) {
                // If any successor is within the loop, then 'unit' is not leading outside
                if (loopHeaders.contains(succ) || graph.getPredsOf(succ).stream().anyMatch(loopHeaders::contains)) {
                    return false;
                }
            }
            return true; // All successors lead outside, indicating 'unit' is at a loop exit
        }

        private Set<Unit> findLoopHeaders(Body body) {
            // Implementation that identifies loop headers based on back edges in the CFG
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




