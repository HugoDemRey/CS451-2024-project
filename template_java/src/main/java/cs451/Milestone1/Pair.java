package cs451.Milestone1;

import cs451.Host;

public class Pair <A, B> {
    private final A first;
    private final B second;

    public Pair(A first, B second) {
        this.first = first;
        this.second = second;
    }

    public A getFirst() {
        return first;
    }

    public B getSecond() {
        return second;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj.getClass() != this.getClass()) {
            return false;
        }
        Pair other = (Pair) obj;
        return this.first.equals(other.first) && this.second.equals(other.second);
    }
}
