package il.ac.bgu.cs.fvm.impl;

import il.ac.bgu.cs.fvm.FvmFacade;
import il.ac.bgu.cs.fvm.automata.Automaton;
import il.ac.bgu.cs.fvm.automata.MultiColorAutomaton;
import il.ac.bgu.cs.fvm.channelsystem.ChannelSystem;
import il.ac.bgu.cs.fvm.circuits.Circuit;
import il.ac.bgu.cs.fvm.channelsystem.ParserBasedInterleavingActDef;
import il.ac.bgu.cs.fvm.channelsystem.InterleavingActDef;
import il.ac.bgu.cs.fvm.ltl.LTL;
import il.ac.bgu.cs.fvm.nanopromela.NanoPromelaFileReader;
import il.ac.bgu.cs.fvm.programgraph.*;
import il.ac.bgu.cs.fvm.transitionsystem.AlternatingSequence;
import il.ac.bgu.cs.fvm.transitionsystem.TransitionSystem;
import il.ac.bgu.cs.fvm.util.Pair;
import il.ac.bgu.cs.fvm.verification.VerificationResult;
import il.ac.bgu.cs.fvm.transitionsystem.Transition;
import il.ac.bgu.cs.fvm.nanopromela.NanoPromelaParser;
import org.antlr.v4.runtime.tree.ParseTree;
import il.ac.bgu.cs.fvm.exceptions.ActionNotFoundException;
import il.ac.bgu.cs.fvm.exceptions.StateNotFoundException;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implement the methods in this class. You may add additional classes as you
 * want, as long as they live in the {@code impl} package, or one of its 
 * sub-packages.
 */
public class FvmFacadeImpl implements FvmFacade {

    @Override
    public <S, A, P> TransitionSystem<S, A, P> createTransitionSystem() {
        return new TransitionSystemImplementaition<S, A, P>();
    }

    @Override
    public <S, A, P> boolean isActionDeterministic(TransitionSystem<S, A, P> ts) {
        for (S state : ts.getStates()) {
            for (A action : ts.getActions()) {
                if (post(ts, state, action).size() > 1) {
                    return false;
                }
            }
        }

        return ts.getInitialStates().size() <= 1;
    }


    @Override
    public <S, A, P> boolean isAPDeterministic(TransitionSystem<S, A, P> ts) {
        for (S s1 : ts.getStates()) {
            if (post(ts, s1).stream()
                    .filter(s2 -> ts.getLabel(s1).equals(ts.getLabel(s2)))
                    .count() > 1) {
                return false;
            }
        }

        return ts.getInitialStates().size() <= 1;
    }

    @Override
    public <S, A, P> boolean isExecution(TransitionSystem<S, A, P> ts, AlternatingSequence<S, A> e) {
        return isInitialExecutionFragment(ts, e) && isMaximalExecutionFragment(ts, e);
    }

    @Override
    public <S, A, P> boolean isExecutionFragment(TransitionSystem<S, A, P> ts, AlternatingSequence<S, A> e) {
        while (e.size() > 1) {
            S s1 = e.head();
            A a = e.tail().head();
            S s2 = e.tail().tail().head();

            requireStates(ts, s1, s2);
            requireActions(ts, a);

            if (ts.getTransitions().stream()
                    .noneMatch(trans ->
                            trans.getFrom().equals(s1) &&
                                    trans.getAction().equals(a) &&
                                    trans.getTo().equals(s2))) {
                return false;
            }

            e = e.tail().tail();
        }
        if (e.size() == 1) {
            requireStates(ts, e.head());
        }
        return true;
    }

    @Override
    public <S, A, P> boolean isInitialExecutionFragment(TransitionSystem<S, A, P> ts, AlternatingSequence<S, A> e) {
        return isExecutionFragment(ts, e) && ts.getInitialStates().contains(e.head());
    }

    @Override
    public <S, A, P> boolean isMaximalExecutionFragment(TransitionSystem<S, A, P> ts, AlternatingSequence<S, A> e) {
        return isExecutionFragment(ts, e) && isStateTerminal(ts, e.last());
    }

    @Override
    public <S, A> boolean isStateTerminal(TransitionSystem<S, A, ?> ts, S s) {
        requireStates(ts, s);
        return post(ts, s).isEmpty();
    }


    @Override
    public <S> Set<S> post(TransitionSystem<S, ?, ?> ts, S s) {
        return ts.getTransitions().stream()
                .filter(trans -> trans.getFrom().equals(s))
                .map(Transition::getTo)
                .collect(Collectors.toSet());
    }

    @Override
    public <S> Set<S> post(TransitionSystem<S, ?, ?> ts, Set<S> c) {
        return c.stream()
                .map(s -> post(ts, s))
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());
    }

    @Override
    public <S, A> Set<S> post(TransitionSystem<S, A, ?> ts, S s, A a) {
        return ts.getTransitions().stream()
                .filter(trans -> trans.getFrom().equals(s) && trans.getAction().equals(a))
                .map(Transition::getTo)
                .collect(Collectors.toSet());

    }

    @Override
    public <S, A> Set<S> post(TransitionSystem<S, A, ?> ts, Set<S> c, A a) {
        return c.stream()
                .map(s -> post(ts, s, a))
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());
    }


    @Override
    public <S> Set<S> pre(TransitionSystem<S, ?, ?> ts, S s) {
        return ts.getTransitions().stream()
                .filter(trans -> trans.getTo().equals(s))
                .map(Transition::getFrom)
                .collect(Collectors.toSet());
    }

    @Override
    public <S> Set<S> pre(TransitionSystem<S, ?, ?> ts, Set<S> c) {
        return c.stream()
                .map(s -> pre(ts, s))
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());
    }

    @Override
    public <S, A> Set<S> pre(TransitionSystem<S, A, ?> ts, S s, A a) {
        return ts.getTransitions().stream()
                .filter(trans -> trans.getTo().equals(s) && trans.getAction().equals(a))
                .map(Transition::getFrom)
                .collect(Collectors.toSet());
    }

    @Override
    public <S, A> Set<S> pre(TransitionSystem<S, A, ?> ts, Set<S> c, A a) {
        return c.stream()
                .map(s -> pre(ts, s, a))
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());
    }

    @Override
    public <S, A> Set<S> reach(TransitionSystem<S, A, ?> ts) {
        List<S> states = new ArrayList<>(ts.getInitialStates());
        Set<S> reachable = new HashSet<>();

        while (!states.isEmpty()) {
            S state = states.get(0);
            states.remove(state);
            if (!reachable.contains(state)) {
                reachable.add(state);
                states.addAll(post(ts, reachable).stream()
                        .filter(s -> !states.contains(s))
                        .collect(Collectors.toList()));
            }
        }

        return reachable;
    }

    private <S1, S2> Set<Pair<S1, S2>> interleaveStates(Set<S1> states1, Set<S2> states2) {
        Set<Pair<S1, S2>> result = new HashSet<>();
        states1.forEach(s1 ->
                states2.forEach(s2 ->
                        result.add(Pair.pair(s1, s2))));
        return result;
    }

    private <T> Set<T> union(Collection<T> t1, Collection<T> t2) {
        return Stream.concat(t1.stream(), t2.stream()).collect(Collectors.toSet());
    }

    @Override
    public <S1, S2, A, P> TransitionSystem<Pair<S1, S2>, A, P> interleave(TransitionSystem<S1, A, P> ts1, TransitionSystem<S2, A, P> ts2) {
        return interleave(ts1, ts2, Collections.emptySet());
    }

    @Override
    public <S1, S2, A, P> TransitionSystem<Pair<S1, S2>, A, P> interleave(TransitionSystem<S1, A, P> ts1, TransitionSystem<S2, A, P> ts2, Set<A> handShakingActions) {
        TransitionSystem<Pair<S1, S2>, A, P> interleaved = createTransitionSystem();
        // States
        interleaved.addAllStates(interleaveStates(ts1.getStates(), ts2.getStates()));
        // Initial states
        for(Pair s : interleaveStates(ts1.getInitialStates(), ts2.getInitialStates())){
            interleaved.setInitial(s, true);
        }
        // Actions
        interleaved.addAllActions(union(ts1.getActions(), ts2.getActions()));
        // Atomic Propositions
        interleaved.addAllAtomicPropositions(union(ts1.getAtomicPropositions(), ts2.getAtomicPropositions()));
        // Transitions
        ts1.getTransitions().stream()
                .filter(t -> !handShakingActions.contains(t.getAction()))
                .forEach(t1 ->
                        interleaved.getStates().forEach(p1 ->
                                interleaved.getStates().forEach(p2 -> {
                                    if (t1.getFrom().equals(p1.getFirst()) && t1.getTo().equals(p2.getFirst()) && p1.getSecond().equals(p2.getSecond())) {
                                        interleaved.addTransition(new Transition<>(p1, t1.getAction(), p2));
                                    }
                                })));
        ts2.getTransitions().stream()
                .filter(t -> !handShakingActions.contains(t.getAction()))
                .forEach(t2 ->
                        interleaved.getStates().forEach(p1 ->
                                interleaved.getStates().forEach(p2 -> {
                                    if (t2.getFrom().equals(p1.getSecond()) && t2.getTo().equals(p2.getSecond()) && p1.getFirst().equals(p2.getFirst())) {
                                        interleaved.addTransition(new Transition<>(p1, t2.getAction(), p2));
                                    }
                                })));
        handShakingActions.forEach(a ->
                ts1.getTransitions().forEach(t1 ->
                        ts2.getTransitions().forEach(t2 -> {
                            if (t1.getAction().equals(a) && t2.getAction().equals(a)) {
                                interleaved.addTransition(new Transition<>(Pair.pair(t1.getFrom(), t2.getFrom()), a, Pair.pair(t1.getTo(), t2.getTo())));
                            }
                        })));

        Set<Pair<S1, S2>> reachableStates = reach(interleaved);
        Set<Pair<S1, S2>> unreachableStates = interleaved.getStates().stream()
                .filter(p -> !reachableStates.contains(p))
                .collect(Collectors.toSet());
        Set<Transition<Pair<S1, S2>, A>> unreachableTransitions = interleaved.getTransitions().stream()
                .filter(t ->
                        unreachableStates.stream().anyMatch(p ->
                                t.getFrom().equals(p) || t.getTo().equals(p)))
                .collect(Collectors.toSet());

        unreachableTransitions.forEach(interleaved::removeTransition);
        unreachableStates.forEach(interleaved::removeState);

        // Labels
        interleaved.getStates().forEach(p ->
                Stream.concat(ts1.getLabel(p.getFirst()).stream(), ts2.getLabel(p.getSecond()).stream())
                        .forEach(l -> interleaved.addToLabel(p, l)));

        return interleaved;
    }

    @Override
    public <L, A> ProgramGraph<L, A> createProgramGraph() {
        return new ProgramGraphImplementation<>();
    }

    @Override
    public <L1, L2, A> ProgramGraph<Pair<L1, L2>, A> interleave(ProgramGraph<L1, A> pg1, ProgramGraph<L2, A> pg2) {
        ProgramGraph<Pair<L1, L2>, A> interleaved = createProgramGraph();

        pg1.getInitalizations().forEach(i1 ->
                pg2.getInitalizations().forEach(i2 ->
                        interleaved.addInitalization(Stream.concat(i1.stream(), i2.stream()).collect(Collectors.toList()))));
        interleaveStates(pg1.getLocations(), pg2.getLocations()).forEach(interleaved::addLocation);
        for(Pair l : interleaveStates(pg1.getInitialLocations(), pg2.getInitialLocations())){
            interleaved.setInitial(l, true);
        }

        pg1.getTransitions().forEach(t1 ->
                interleaved.getLocations().forEach(p1 ->
                        interleaved.getLocations().forEach(p2 -> {
                            if (t1.getFrom().equals(p1.getFirst()) && t1.getTo().equals(p2.getFirst()) && p1.getSecond().equals(p2.getSecond())) {
                                interleaved.addTransition(new PGTransition<>(p1, t1.getCondition(), t1.getAction(), p2));
                            }
                        })));
        pg2.getTransitions().forEach(t2 ->
                interleaved.getLocations().forEach(p1 ->
                        interleaved.getLocations().forEach(p2 -> {
                            if (t2.getFrom().equals(p1.getSecond()) && t2.getTo().equals(p2.getSecond()) && p1.getFirst().equals(p2.getFirst())) {
                                interleaved.addTransition(new PGTransition<>(p1, t2.getCondition(), t2.getAction(), p2));
                            }
                        })));

        return interleaved;
    }

    private Boolean[] toBinary(int number, int length) {
        StringBuilder binaryStringBuilder = new StringBuilder(Integer.toBinaryString(number));
        for (int i = 0; i < (length - binaryStringBuilder.length()); i++) {
            binaryStringBuilder.insert(0, "0");
        }

        String binaryString = binaryStringBuilder.toString();
        Boolean[] result = new Boolean[length];

        for (int i = 0; i < length; i++) {
            result[i] = binaryString.charAt(i) == '1';
        }

        return result;
    }

    @Override
    public TransitionSystem<Pair<Map<String, Boolean>, Map<String, Boolean>>, Map<String, Boolean>, Object> transitionSystemFromCircuit(Circuit c) {
        TransitionSystem<Pair<Map<String, Boolean>, Map<String, Boolean>>, Map<String, Boolean>, Object> ts = createTransitionSystem();

        List<String> inputPortNames = new ArrayList<>(c.getInputPortNames());
        List<String> registerNames = new ArrayList<>(c.getRegisterNames());
        List<String> outputPortNames = new ArrayList<>(c.getOutputPortNames());

        int inputLength = c.getInputPortNames().size();
        int registerLength = c.getRegisterNames().size();

        double inputCombinations = Math.pow(2, c.getInputPortNames().size());
        double registerCombinations = Math.pow(2, c.getRegisterNames().size());

        // Handle states, init-states & actions
        for (int i = 0; i < inputCombinations; i++) {
            Boolean[] binaryInput = toBinary(i, inputLength);
            Map<String, Boolean> inputState = new HashMap<>();
            for (int varIndex = 0; varIndex < binaryInput.length; varIndex++) {
                inputState.put(inputPortNames.get(varIndex), binaryInput[varIndex]);
            }

            for (int j = 0; j < registerCombinations; j++) {
                Boolean[] binaryRegisters = toBinary(j, registerLength);
                Map<String, Boolean> registerState = new HashMap<>();
                for (int varIndex = 0; varIndex < binaryInput.length; varIndex++) {
                    registerState.put(registerNames.get(varIndex), binaryRegisters[varIndex]);
                }

                ts.addState(Pair.pair(inputState, registerState));
                if (j == 0) {
                    ts.setInitial(Pair.pair(inputState, registerState), true);
                }
            }

            ts.addAction(inputState);
        }

        union(inputPortNames, union(registerNames, outputPortNames)).forEach(ts::addAtomicProposition);

        ts.getStates().forEach(s1 ->
                ts.getStates().forEach(s2 -> {
                    if (c.updateRegisters(s1.getFirst(), s1.getSecond()).equals(s2.getSecond())) {
                        ts.addTransition(new Transition<>(s1, s2.getFirst(), s2));
                    }
                }));

        Set<Pair<Map<String, Boolean>, Map<String, Boolean>>> reachableStates = reach(ts);
        Set<Pair<Map<String, Boolean>, Map<String, Boolean>>> unreachableStates = ts.getStates().stream()
                .filter(s -> !reachableStates.contains(s))
                .collect(Collectors.toSet());
        Set<Transition<Pair<Map<String, Boolean>, Map<String, Boolean>>, Map<String, Boolean>>> unreachableTransitions = ts.getTransitions().stream()
                .filter(t -> unreachableStates.contains(t.getFrom()) || unreachableStates.contains(t.getTo()))
                .collect(Collectors.toSet());

        unreachableTransitions.forEach(ts::removeTransition);
        unreachableStates.forEach(ts::removeState);

        ts.getStates().forEach(s ->
                union(s.getFirst().entrySet(),
                        union(s.getSecond().entrySet(),
                                c.computeOutputs(s.getFirst(), s.getSecond()).entrySet()))
                        .stream()
                        .filter(Map.Entry::getValue)
                        .map(Map.Entry::getKey)
                        .forEach(name -> ts.addToLabel(s, name)));
        return ts;
    }

    @Override
    public <L, A> TransitionSystem<Pair<L, Map<String, Object>>, A, String> transitionSystemFromProgramGraph(ProgramGraph<L, A> pg, Set<ActionDef> actionDefs, Set<ConditionDef> conditionDefs) {
        TransitionSystem<Pair<L, Map<String, Object>>, A, String> ts = createTransitionSystem();
        // Initializations evaluation
        Set<Map<String, Object>> initialEvals = pg.getInitalizations().stream().map(init -> {
            Map<String, Object> initEval = new HashMap<>();
            for (String s : init) {
                Set<ActionDef> matches = actionDefs.stream().filter(ad -> ad.isMatchingAction(s)).collect(Collectors.toSet());
                for (ActionDef ad : matches) {
                    initEval = ad.effect(initEval, s);
                }
            }
            return initEval;
        }).collect(Collectors.toSet());

        if (initialEvals.isEmpty()) {
            initialEvals.add(new HashMap<>());
        }

        // Initial states
        pg.getInitialLocations().forEach(loc ->
                initialEvals.forEach(eval -> {
                    ts.addState(Pair.pair(loc, eval));
                    ts.setInitial(Pair.pair(loc, eval), true);
                }));

        // States, Actions & Transitions
        List<Pair<L, Map<String, Object>>> states = new ArrayList<>(ts.getInitialStates());
        while (!states.isEmpty()) {
            Pair<L, Map<String, Object>> fromState = states.get(0);
            states.remove(0);

            pg.getTransitions().forEach(t -> {
                if (t.getFrom().equals(fromState.getFirst()) && conditionDefs.stream().anyMatch(c -> c.evaluate(fromState.getSecond(), t.getCondition()))) {
                    Set<ActionDef> matches = actionDefs.stream()
                            .filter(ad -> ad.isMatchingAction(t.getAction()))
                            .collect(Collectors.toSet());
                    Consumer<Map<String, Object>> addToTransitionSystem = evaluation -> {
                        Pair<L, Map<String, Object>> toState = Pair.pair(t.getTo(), evaluation);
                        if (!ts.getStates().contains(toState)) {
                            ts.addState(toState);
                            states.add(toState);
                        }

                        ts.addAction(t.getAction());
                        ts.addTransition(new Transition<>(fromState, t.getAction(), toState));
                    };
                    if (matches.isEmpty()) {
                        addToTransitionSystem.accept(fromState.getSecond());
                    } else {
                        matches.forEach(ad -> addToTransitionSystem.accept(ad.effect(fromState.second, t.getAction())));
                    }
                }
            });
        }

        // Atomic Propositions & labels
        ts.getStates().forEach(s -> {
            ts.addAtomicProposition(s.getFirst().toString());
            ts.addToLabel(s, s.first.toString());
            s.getSecond().forEach((key, value) -> {
                String expr = key + " = " + value;
                ts.addAtomicProposition(expr);
                ts.addToLabel(s, expr);
            });
        });

        return ts;
    }
    private Set<List<String>> addInitializations(Set<List<String>> initializations, Set<List<String>> toAdd) {
        Set<List<String>> result = new HashSet<>();
        if (initializations.isEmpty()) {
            return toAdd;
        }

        if (toAdd.isEmpty()) {
            return initializations;
        }

        initializations.forEach(init1 ->
                toAdd.forEach(init2 ->
                        result.add(Stream.concat(init1.stream(), init2.stream()).collect(Collectors.toList()))
                )
        );
        return result;
    }
    private <L> Set<List<L>> addInitialLocations(Set<List<L>> initialLocations, Set<L> toAdd) {
        Set<List<L>> result = new HashSet<>();

        if (initialLocations.isEmpty()) {
            result.add(new ArrayList<>(toAdd));
            return result;
        }

        initialLocations.forEach(init1 ->
                toAdd.forEach(init2 ->
                        result.add(Stream.concat(init1.stream(), Stream.of(init2)).collect(Collectors.toList()))
                )
        );

        return result;
    }
    @Override
    public <L, A> TransitionSystem<Pair<List<L>, Map<String, Object>>, A, String> transitionSystemFromChannelSystem(ChannelSystem<L, A> cs) {
        TransitionSystem<Pair<List<L>, Map<String, Object>>, A, String> ts = createTransitionSystem();
        List<ProgramGraph<L, A>> pgs = cs.getProgramGraphs();
        Set<List<L>> initialLocations = new HashSet<>();

        for (ProgramGraph<L, A> pg : pgs) {
            initialLocations = addInitialLocations(initialLocations, pg.getInitialLocations());
        }

        Set<List<String>> initializations = new HashSet<>(pgs.get(0).getInitalizations());
        for (int i = 1; i < pgs.size(); i++) {
            initializations = addInitializations(initializations, pgs.get(i).getInitalizations());
        }

        ParserBasedInterleavingActDef ad = new ParserBasedInterleavingActDef();
        for (List<L> lst : initialLocations) {
            Consumer<Map<String, Object>> addInitial = eval -> {
                Pair<List<L>, Map<String, Object>> newState = Pair.pair(lst, eval);
                ts.addState(newState);
                ts.setInitial(newState, true);
            };

            if (initializations.isEmpty()) {
                addInitial.accept(new HashMap<>());
            } else {
                Map<String, Object> eval = new HashMap<>();
                for (List<String> init : initializations) {
                    for (String s : init) {
                        eval = ad.effect(eval, s);
                    }
                    addInitial.accept(eval);
                }
            }
        }

        Set<Pair<List<L>, Map<String, Object>>> checked = new HashSet<>();
        Set<Pair<List<L>, Map<String, Object>>> toCheck = new HashSet<>(ts.getInitialStates());

        ParserBasedCondDef cd = new ParserBasedCondDef();
        ParserBasedInterleavingActDef iad = new ParserBasedInterleavingActDef();
        Map<String, Object> eval;

        while (!toCheck.isEmpty()) {
            Set<Pair<List<L>, Map<String, Object>>> toBeChecked = new HashSet<>();
            for (Pair<List<L>, Map<String, Object>> fromState : toCheck) {
                checked.add(fromState);
                for (int i = 0; i < pgs.size(); i++) {
                    ProgramGraph<L, A> pg = pgs.get(i);
                    for (PGTransition<L, A> trans : pg.getTransitions()) {
                        if (fromState.getSecond() != null) {
                            if (fromState.getFirst().get(i).equals(trans.getFrom()) && cd.evaluate(fromState.getSecond(), trans.getCondition()) && !iad.isOneSidedAction(trans.getAction().toString())) {
                                eval = ad.effect(fromState.getSecond(), trans.getAction());
                                List<L> toState = new ArrayList<>(fromState.getFirst());
                                toState.set(i, trans.getTo());
                                Pair<List<L>, Map<String, Object>> newState = Pair.pair(toState, eval);
                                if (newState.getSecond() != null) {
                                    ts.addState(newState);
                                    if (!checked.contains(newState)) {
                                        toBeChecked.add(newState);
                                    }
                                    ts.addAction(trans.getAction());
                                    ts.addTransition(new Transition<>(fromState, trans.getAction(), newState));
                                }
                            }
                        }
                    }

                }
                Set<Transition<List<L>, A>> handShakes = new HashSet<>();
                List<List<PGTransition<L, A>>> pgTrans = new ArrayList<>();
                for (int i = 0; i < pgs.size(); i++) {
                    List<PGTransition<L, A>> transitionsList = new ArrayList<>();
                    L loc = fromState.getFirst().get(i);
                    pgs.get(i).getTransitions().stream()
                            .filter(trans -> fromState.getSecond() != null && trans.getFrom().equals(loc) && cd.evaluate(fromState.getSecond(), trans.getCondition()))
                            .forEach(transitionsList::add);
                    pgTrans.add(transitionsList);
                }
                for (int i = 0; i < pgs.size(); i++) {
                    for (int j = 0; j < pgs.size(); j++) {
                        if (i != j) {
                            for (PGTransition<L, A> pg1 : pgTrans.get(i)) {
                                for (PGTransition<L, A> pg2 : pgTrans.get(j)) {
                                    String act = pg1.getAction().toString() + "|" + pg2.getAction().toString();
                                    if (iad.isMatchingAction(act)) {
                                        List<L> tmp = new ArrayList<>(fromState.getFirst());
                                        tmp.set(i, pg1.getTo());
                                        tmp.set(j, pg2.getTo());
                                        Transition<List<L>, A> newTransition = new Transition<>(fromState.getFirst(), (A) act, tmp);
                                        handShakes.add(newTransition);
                                    }
                                }
                            }
                        }
                    }
                }
                for (Transition<List<L>, A> tran : handShakes) {
                    eval = iad.effect(fromState.getSecond(), tran.getAction());
                    if (eval != null) {
                        Pair<List<L>, Map<String, Object>> new_state = Pair.pair(tran.getTo(), eval);
                        ts.addState(new_state);
                        if (!checked.contains(new_state)) {
                            toBeChecked.add(new_state);
                        }
                        ts.addAction(tran.getAction());
                        ts.addTransition(new Transition<>(fromState, tran.getAction(), new_state));
                    }
                }

            }
            toCheck = toBeChecked;
        }

        ts.getStates().forEach(s -> {
            s.first.forEach(loc -> {
                ts.addAtomicProposition(loc.toString());
                ts.addToLabel(s, loc.toString());
            });
            s.getSecond().forEach((k, v) -> {
                String ap = k + " = " + v;
                ts.addAtomicProposition(ap);
                ts.addToLabel(s, ap);
            });
        });

        return ts;
    }

    @Override
    public <Sts, Saut, A, P> TransitionSystem<Pair<Sts, Saut>, A, Saut> product(TransitionSystem<Sts, A, P> ts, Automaton<Saut, P> aut) {
        TransitionSystem<Pair<Sts, Saut>, A, Saut> result = createTransitionSystem();

        ts.getInitialStates().forEach(s ->
                aut.getInitialStates().forEach(q0 -> {
                    Set<Pair<Sts, Saut>> initials = aut.nextStates(q0, ts.getLabel(s)).stream()
                            .map(q -> Pair.pair(s, q))
                            .collect(Collectors.toSet());
                    initials.forEach(result::addState);
                    for (Pair<Sts, Saut> i : initials) {
                        result.setInitial(i, true);

                    }
                }));

        List<Pair<Sts, Saut>> checked = new ArrayList<>();
        List<Pair<Sts, Saut>> toCheck = new ArrayList<>(result.getInitialStates());

        while (!toCheck.isEmpty()) {
            Pair<Sts, Saut> current = toCheck.get(0);
            ts.getTransitions().forEach(trans -> {
                if (trans.getFrom().equals(current.getFirst())) {
                    Set<Saut> next = aut.nextStates(current.getSecond(), ts.getLabel(trans.getTo()));
                    if(next != null){
                        Set<Pair<Sts, Saut>> newStates = aut.nextStates(current.getSecond(), ts.getLabel(trans.getTo())).stream()
                                .map(q -> Pair.pair(trans.getTo(), q))
                                .collect(Collectors.toSet());
                        if (!newStates.isEmpty()) {
                            result.addAction(trans.getAction());
                            newStates.forEach(s -> {
                                result.addState(s);
                                result.addTransition(new Transition<>(current, trans.getAction(), s));
                                result.addAtomicProposition(s.getSecond());
                                result.addToLabel(s, s.getSecond());
                                if (!toCheck.contains(s) && !checked.contains(s)) {
                                    toCheck.add(s);
                                }
                            });
                        }
                    }
                }
            });
            toCheck.remove(current);
            checked.add(current);
        }
        return result;
    }

    @Override
    public ProgramGraph<String, String> programGraphFromNanoPromela(String filename) throws Exception {
        return programGraphFromNanoPromela(NanoPromelaFileReader.pareseNanoPromelaFile(filename));
    }

    @Override
    public ProgramGraph<String, String> programGraphFromNanoPromela(InputStream inputStream) throws Exception {
        return programGraphFromNanoPromela(NanoPromelaFileReader.parseNanoPromelaStream(inputStream));
    }

    private ProgramGraph<String, String> programGraphFromNanoPromela(NanoPromelaParser.StmtContext root) {
        ProgramGraph<String, String> pg = new ProgramGraphImplementation<>();
        pg.addLocation("");
        pg.addLocation(root.getText());
        pg.setInitial(root.getText(), true);
        _programGraphFromNanoPromela(root, root.getText(), "", "", "", pg);
        return pg;
    }

    private void _programGraphFromNanoPromela(ParseTree root, String from, String post, String condition,
                                              String to, ProgramGraph<String, String> pg) {
        if (root instanceof NanoPromelaParser.DostmtContext) {
            String negation = "";
            for (int i = 1; i < root.getChildCount() - 1; i++) {
                String cond = root.getChild(i).getChild(1).getText();
                _programGraphFromNanoPromela(root.getChild(i).getChild(3), from, append(root.getText(), ";", post),
                        append("(" + condition + ")", " && ", "(" + cond + ")"), to, pg);
                _programGraphFromNanoPromela(root.getChild(i).getChild(3), append(append(root.getText(), ";", to), ";", post),
                        append(root.getText(), ";", post), "(" + cond + ")", to, pg);
                negation = append(negation, "||", "(" + cond + ")");
            }
            addTransitionToProgramGraph(pg, append(root.getText(), ";", to + post), "!(" + negation + ")", "", to + post);
            addTransitionToProgramGraph(pg, from, append(condition, " && ", "(!(" + negation + "))"), "", to + post);
        }
        if (root instanceof NanoPromelaParser.IfstmtContext) {
            for (int i = 1; i < root.getChildCount() - 1; i++) {
                _programGraphFromNanoPromela(root.getChild(i).getChild(3), from, post,
                        append("(" + condition + ")", " && ", "(" + root.getChild(i).getChild(1).getText() + ")"), to, pg);
            }
        }
        if (root.getChildCount() > 1 && root.getChild(1).getText().equals(";")) {
            _programGraphFromNanoPromela(root.getChild(0), from, append(root.getChild(2).getText(), ";", post), condition, to, pg);
            _programGraphFromNanoPromela(root.getChild(2), append(root.getChild(2).getText(), ";", post), post, "", to, pg);
        } else if (root instanceof NanoPromelaParser.StmtContext) {
            _programGraphFromNanoPromela(root.getChild(0), from, post, condition, to, pg);
        }
        if (!(root instanceof NanoPromelaParser.DostmtContext || root instanceof NanoPromelaParser.IfstmtContext || root instanceof NanoPromelaParser.StmtContext)) {
            addTransitionToProgramGraph(pg, from, condition, root.getText(), append(to, ";", post));
        }
    }

    private String append(String start, String middle, String end) {
        if (start.equals("") || start.equals("()"))
            return end;
        if (end.equals("") || end.equals("()"))
            return start;
        return start + middle + end;
    }

    private <L, A> void addTransitionToProgramGraph(ProgramGraph<L, A> pg, L from, String condition, A action, L to) {
        PGTransition<L, A> t = new PGTransition<>(from, condition, action, to);
        pg.addLocation(t.getFrom());
        pg.addLocation(t.getTo());
        pg.addTransition(t);
    }

    @Override
    public ProgramGraph<String, String> programGraphFromNanoPromelaString(String nanopromela) throws Exception {
        return programGraphFromNanoPromela(NanoPromelaFileReader.pareseNanoPromelaString(nanopromela));
    }
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public <S, A, P, Saut> VerificationResult<S> verifyAnOmegaRegularProperty(TransitionSystem<S, A, P> ts, Automaton<Saut, P> aut) {
        throw new UnsupportedOperationException("Not supported yet."); // TODO: Implement verifyAnOmegaRegularProperty
    }

    @Override
    public <L> Automaton<?, L> LTL2NBA(LTL<L> ltl) {
        throw new UnsupportedOperationException("Not supported yet."); // TODO: Implement LTL2NBA
    }

    @Override
    public <L> Automaton<?, L> GNBA2NBA(MultiColorAutomaton<?, L> mulAut) {
        throw new UnsupportedOperationException("Not supported yet."); // TODO: Implement GNBA2NBA
    }

    @SafeVarargs
    private final <A> void requireActions(TransitionSystem<?, A, ?> ts, A... actions) {
        for (A a : actions) {
            if (!ts.getActions().contains(a)) {
                throw new ActionNotFoundException(a);
            }
        }
    }
    @SafeVarargs
    private final <S> void requireStates(TransitionSystem<S, ?, ?> ts, S... states) {
        for (S s : states) {
            if (!ts.getStates().contains(s)) {
                throw new StateNotFoundException(s);
            }
        }
    }

}
