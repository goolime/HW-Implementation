package il.ac.bgu.cs.fvm.impl;
import il.ac.bgu.cs.fvm.exceptions.FVMException;
import il.ac.bgu.cs.fvm.programgraph.PGTransition;
import il.ac.bgu.cs.fvm.programgraph.ProgramGraph;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
    public class ProgramGraphImplementation<L, A> implements ProgramGraph<L, A> {
        private String name;
        private Set<List<String>> init;

        private Set<L> locations;
        private Set<L> initalLocations;
        private Set<PGTransition<L, A>> transitionsBetweenLocations;

        ProgramGraphImplementation(){
            name = "Program Graph";
            init = new HashSet<>();
            locations = new HashSet<>();
            initalLocations= new HashSet<>();
            transitionsBetweenLocations = new HashSet<>();

        }

        @Override
        public void addInitalization(List<String> init) {
            this.init.add(init);
        }

        @Override
        public void setInitial(L l, boolean b) {
            boolean hasDone = false;
            if (locations.contains(l)) {
                if (b && !initalLocations.contains(l) && !hasDone) {
                    initalLocations.add(l);
                    hasDone = true;
                }
                if (!b && initalLocations.contains(l) && !hasDone) {
                    initalLocations.remove(l);
                    hasDone = true;
                }
                if (!b && !initalLocations.contains(l) && !hasDone) {
                    hasDone = true;
                    throw new FVMException("l is not initial");
                }
                if (b && initalLocations.contains(l) && !hasDone) {
                    hasDone = true;
                    throw new FVMException("l is already initial");
                }
            }
            else
                throw new FVMException("state not found");
        }

        @Override
        public void addLocation(L l) {
            locations.add(l);
        }

        @Override
        public void addTransition(PGTransition<L, A> t) {
            if(!locations.contains(t.getFrom()) || !locations.contains(t.getTo())){
                throw new FVMException("Invalid transition");
            }
            transitionsBetweenLocations.add(t);
        }

        @Override
        public Set<List<String>> getInitalizations() {
            return init;
        }

        @Override
        public Set<L> getInitialLocations() {
            return initalLocations;
        }

        @Override
        public Set<L> getLocations() {
            return locations;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Set<PGTransition<L, A>> getTransitions() {
            return transitionsBetweenLocations;
        }

        @Override
        public void removeLocation(L l) {
            if(transitionsBetweenLocations.stream().anyMatch(t -> t.getFrom().equals(l) || t.getTo().equals(l))){
                throw new FVMException("Unable to remove location.");
            }
            initalLocations.remove(l);
            locations.remove(l);
        }

        @Override
        public void removeTransition(PGTransition<L, A> t) {
            transitionsBetweenLocations.remove(t);
        }

        @Override
        public void setName(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return "ProgramGraphImpl{" +
                    "name='" + name + '\'' +
                    ", init=" + init +
                    ", locations=" + locations +
                    ", initalLocations=" + initalLocations +
                    ", transitionsBetweenLocations=" + transitionsBetweenLocations +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ProgramGraphImplementation<?, ?> that = (ProgramGraphImplementation<?, ?>) o;

            return name.equals(that.name) && init.equals(that.init) && locations.equals(that.locations) && initalLocations.equals(that.initalLocations) && transitionsBetweenLocations.equals(that.transitionsBetweenLocations);
        }

        @Override
        public int hashCode() {
            int result = name.hashCode();
            result = 31 * result + init.hashCode();
            result = 31 * result + locations.hashCode();
            result = 31 * result + initalLocations.hashCode();
            result = 31 * result + transitionsBetweenLocations.hashCode();
            return result;
        }
    }
