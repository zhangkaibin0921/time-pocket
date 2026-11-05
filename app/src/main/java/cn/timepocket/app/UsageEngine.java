package cn.timepocket.app;

import java.util.*;

/** Android-independent event reducer. All ranges use [start, end). */
public final class UsageEngine {
    public static final int RESUME = 1, PAUSE = 2, SCREEN_OFF = 16, LOCK = 17,
            STOP = 23, SHUTDOWN = 26, STARTUP = 27;
    public static final long MAX_SESSION = 12 * 60 * 60 * 1000L;
    public record Event(long at, String pkg, String activity, int type) {}
    public record Segment(String pkg, long start, long end) {}
    public static final class State {
        public final Map<String, String> activities = new HashMap<>();
        public final Map<String, Long> starts = new HashMap<>();
        public final Map<String, Long> origins = new HashMap<>();
        public void clear() { activities.clear(); starts.clear(); origins.clear(); }
    }
    public record Result(List<Segment> segments, boolean uncertain) {}

    public static Result reduce(State state, List<Event> events, long begin, long end) {
        return reduceInternal(state,events,begin,end,true);
    }
    /** Replayed raw events: only closed intervals are committed; never invent an open tail. */
    public static Result confirmed(List<Event> events,long begin,long end) {
        return reduceInternal(new State(),events,begin,end,false);
    }
    private static Result reduceInternal(State state,List<Event> events,long begin,long end,boolean flushOpen) {
        List<Segment> out = new ArrayList<>();
        boolean uncertain = false;
        events.sort(Comparator.comparingLong(Event::at));
        for (Event e : events) {
            if (e.at < begin || e.at >= end) continue;
            if (e.type == SHUTDOWN || e.type == STARTUP) {
                // A shutdown timestamp is a persistence marker, not the exact power-off time.
                uncertain |= !state.starts.isEmpty();
                state.clear();
            } else if (e.type == SCREEN_OFF || e.type == LOCK) {
                for (String pkg : new ArrayList<>(state.starts.keySet()))
                    uncertain |= !close(state, pkg, e.at, out,false);
                state.clear();
            } else if (e.pkg != null && !e.pkg.isEmpty()) {
                String key = e.pkg + "/" + e.activity;
                if (e.type == RESUME) {
                    state.activities.put(key, e.pkg);
                    state.starts.putIfAbsent(e.pkg, e.at);
                    state.origins.putIfAbsent(e.pkg, e.at);
                } else if (e.type == PAUSE || e.type == STOP) {
                    state.activities.remove(key);
                    if (!state.activities.containsValue(e.pkg)) {
                        uncertain |= !close(state, e.pkg, e.at, out,false);
                        state.origins.remove(e.pkg);
                    }
                }
            }
        }
        if(flushOpen) for (String pkg : new ArrayList<>(state.starts.keySet())) {
            uncertain |= !close(state, pkg, end, out,true);
            if (state.activities.containsValue(pkg)) state.starts.put(pkg, end);
        }
        return new Result(out, uncertain);
    }
    private static boolean close(State s, String pkg, long end, List<Segment> out,boolean tentative) {
        Long start = s.starts.remove(pkg);
        if (start == null || end <= start) return true;
        if (tentative && end - s.origins.getOrDefault(pkg, start) > MAX_SESSION) {
            s.activities.values().removeIf(pkg::equals);
            s.origins.remove(pkg);
            return false;
        }
        out.add(new Segment(pkg, start, end));
        return true;
    }
    public static long overlap(long a, long b, long from, long to) {
        return Math.max(0, Math.min(b, to) - Math.max(a, from));
    }
}
