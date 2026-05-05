package pl.training.dsl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

/*
Why model with an FSM

- Every business workflow has a state diagram. Encoding it explicitly prevents the canonical bug "the order shipped
  before it was paid".
- The DSL turns "for an order in state X receiving event E, go to state Y if condition C, then run hooks H" into a
  value you can read top-to-bottom, test, and visualise.
- This module's anchor is an order lifecycle: PLACED → PAID → SHIPPED → DELIVERED, with CANCELLED and REFUNDED side
  branches. Real systems (Spring State Machine, Akka FSM) use the same vocabulary.
*/

public final class Mod006StateMachineDsl {

    private Mod006StateMachineDsl() {}

    // =================================================================================================
    // Domain
    // =================================================================================================

    enum State { PLACED, PAID, SHIPPED, DELIVERED, CANCELLED, REFUNDED }
    enum Event { PAY, SHIP, DELIVER, CANCEL, REFUND }

    static final class Order {
        final String id;
        BigDecimal amount;
        State state;
        final List<String> trace = new ArrayList<>();

        Order(String id, BigDecimal amount, State initial) {
            this.id = id; this.amount = amount; this.state = initial;
        }
        @Override public String toString() {
            return "Order(" + id + ", state=" + state + ", amount=" + amount + ")";
        }
    }

    // =================================================================================================
    // Result — sealed
    // =================================================================================================

    public sealed interface Result permits Transitioned, TransitionRefused {}
    public record Transitioned(State newState) implements Result {}
    public record TransitionRefused(Reason reason) implements Result {}
    public enum Reason { NO_TRANSITION, ALL_GUARDS_FAILED }

    // =================================================================================================
    // Transition — internal AST node
    // =================================================================================================

    static final class Transition<C> {
        final State from;
        final Event event;
        final State to;
        final Predicate<C> guard;        // null → always matches
        final Consumer<C> action;        // null → no extra action

        Transition(State from, Event event, State to, Predicate<C> guard, Consumer<C> action) {
            this.from = from; this.event = event; this.to = to; this.guard = guard; this.action = action;
        }
    }

    // =================================================================================================
    // The DSL builder
    // =================================================================================================

    public static <C> Builder<C> stateMachine() { return new Builder<>(); }

    public static final class Builder<C> {
        final Map<State, Map<Event, List<Transition<C>>>> table = new EnumMap<>(State.class);
        final Map<State, List<Consumer<C>>> onEnter = new HashMap<>();
        final Map<State, List<Consumer<C>>> onExit  = new HashMap<>();

        public From<C> from(State s) { return new From<>(this, s); }

        public Builder<C> onEnter(State s, Consumer<C> hook) {
            onEnter.computeIfAbsent(s, k -> new ArrayList<>()).add(hook); return this;
        }
        public Builder<C> onExit(State s, Consumer<C> hook) {
            onExit.computeIfAbsent(s, k -> new ArrayList<>()).add(hook); return this;
        }

        public StateMachine<C> build() { return new StateMachine<>(table, onEnter, onExit); }
    }

    public static final class From<C> {
        private final Builder<C> b; private final State from;
        From(Builder<C> b, State from) { this.b = b; this.from = from; }
        public On<C> on(Event e) { return new On<>(b, from, e); }
    }

    public static final class On<C> {
        private final Builder<C> b; private final State from; private final Event event;
        On(Builder<C> b, State from, Event event) { this.b = b; this.from = from; this.event = event; }
        public To<C> to(State target) { return new To<>(b, from, event, target, null, null); }
    }

    public static final class To<C> {
        private final Builder<C> b;
        private final State from, to;
        private final Event event;
        private final Predicate<C> guard;
        private final Consumer<C> action;

        To(Builder<C> b, State from, Event e, State to, Predicate<C> g, Consumer<C> a) {
            this.b = b; this.from = from; this.event = e; this.to = to; this.guard = g; this.action = a;
        }
        public To<C> when(Predicate<C> guard)  { commit(new Transition<>(from, event, to, guard, action), false);
                                                  return new To<>(b, from, event, to, guard, action); }
        public To<C> doAction(Consumer<C> act) { commit(new Transition<>(from, event, to, guard, act), false);
                                                  return new To<>(b, from, event, to, guard, act); }
        public Builder<C> done() { commit(new Transition<>(from, event, to, guard, action), true); return b; }

        private boolean committed;
        private void commit(Transition<C> t, boolean force) {
            if (committed && !force) return;
            committed = true;
            // If a previous draft of this same fluent chain was registered, replace it with the latest.
            var byEvent = b.table.computeIfAbsent(from, k -> new EnumMap<>(Event.class));
            var list = byEvent.computeIfAbsent(event, k -> new ArrayList<>());
            // Remove a placeholder we may have inserted before .when() was added.
            if (!list.isEmpty()) list.remove(list.size() - 1);
            list.add(t);
        }
    }

    // =================================================================================================
    // Runtime
    // =================================================================================================

    public static final class StateMachine<C> {
        private final Map<State, Map<Event, List<Transition<C>>>> table;
        private final Map<State, List<Consumer<C>>> onEnter;
        private final Map<State, List<Consumer<C>>> onExit;

        StateMachine(Map<State, Map<Event, List<Transition<C>>>> table,
                     Map<State, List<Consumer<C>>> onEnter,
                     Map<State, List<Consumer<C>>> onExit) {
            this.table = table; this.onEnter = onEnter; this.onExit = onExit;
        }

        public Result fire(Order ctx, Event event) {
            var byEvent = table.get(ctx.state);
            if (byEvent == null || !byEvent.containsKey(event)) {
                return new TransitionRefused(Reason.NO_TRANSITION);
            }
            for (var t : byEvent.get(event)) {
                @SuppressWarnings("unchecked")
                Predicate<C> guard = (Predicate<C>) t.guard;
                @SuppressWarnings("unchecked")
                Consumer<C> action = (Consumer<C>) t.action;
                @SuppressWarnings("unchecked")
                C castCtx = (C) ctx;

                if (guard == null || guard.test(castCtx)) {
                    State previous = ctx.state;
                    runHooks(onExit.get(previous), castCtx);
                    ctx.state = t.to;
                    if (action != null) action.accept(castCtx);
                    runHooks(onEnter.get(t.to), castCtx);
                    ctx.trace.add(previous + " --" + event + "--> " + t.to);
                    return new Transitioned(t.to);
                }
            }
            return new TransitionRefused(Reason.ALL_GUARDS_FAILED);
        }

        @SuppressWarnings("unchecked")
        private void runHooks(List<Consumer<C>> hooks, Object ctx) {
            if (hooks == null) return;
            for (var h : hooks) ((Consumer<Object>) h).accept(ctx);
        }
    }

    // =================================================================================================
    // Build the order FSM (used in §6)
    // =================================================================================================

    static StateMachine<Order> buildOrderFsm() {
        Builder<Order> b = stateMachine();

        b.from(State.PLACED).on(Event.PAY).to(State.PAID)
                .when(o -> o.amount.signum() > 0)
                .done();

        b.from(State.PLACED).on(Event.CANCEL).to(State.CANCELLED).done();

        b.from(State.PAID).on(Event.SHIP).to(State.SHIPPED).done();
        b.from(State.SHIPPED).on(Event.DELIVER).to(State.DELIVERED).done();

        b.from(State.PAID).on(Event.REFUND).to(State.REFUNDED)
                .doAction(o -> o.amount = BigDecimal.ZERO)
                .done();

        // Hooks
        b.onEnter(State.PAID, o -> System.out.println("    [hook] paid: " + o.id + " " + o.amount));
        b.onEnter(State.SHIPPED, o -> System.out.println("    [hook] shipping label printed for " + o.id));
        b.onExit(State.PLACED,  o -> System.out.println("    [hook] leaving PLACED for " + o.id));

        return b.build();
    }

    // =================================================================================================
    // Sections
    // =================================================================================================

    /*
    States and events as enums

    - States and events are enums — closed sets known to the compiler. The DSL refuses unknown states / events at
      compile time, and the FSM can use EnumMap for fast lookup.
    - The whole transition table is a Map<State, Map<Event, List<Transition>>> where multiple transitions for the
      same (state, event) are tried in order, the first whose guard passes wins.
    */
    static void statesAndEvents() {
        System.out.println("[Section 2] states + events");
        System.out.println("  states = " + java.util.Arrays.toString(State.values()));
        System.out.println("  events = " + java.util.Arrays.toString(Event.values()));
    }

    /*
    The DSL — from(S).on(E).to(S').when(guard).do(action)

    - The chain is a typical fluent builder: each step returns the next stage, the terminal step (done() or implicit
      on to(...)) registers the transition.
    - when(predicate) is optional; without it, the transition has no guard and matches unconditionally.
    - Multiple transitions for the same (state, event) are legal — the FSM tries them in order, useful for "if
      amount > 0 go to PAID, otherwise go to FAILED".
    */
    static void dslChain() {
        System.out.println("[Section 3] DSL chain (illustration)");

        var demo = Mod006StateMachineDsl.<Order>stateMachine()
                .from(State.PLACED).on(Event.PAY).to(State.PAID)
                    .when(o -> o.amount.signum() > 0)
                    .done()
                .from(State.PLACED).on(Event.CANCEL).to(State.CANCELLED).done()
                .build();

        var ok = new Order("o-x", new BigDecimal("10"), State.PLACED);
        System.out.println("  before fire: " + ok);
        System.out.println("  fire(PAY)  -> " + demo.fire(ok, Event.PAY));
        System.out.println("  after fire:  " + ok);
    }

    /*
    Hooks (onEnter, onExit)

    - onEnter(SHIPPED, ctx -> notify(ctx)) registers a callback that fires whenever the FSM enters the given state.
    - onExit(PLACED, ctx -> ...) fires when leaving.
    - Hooks colocate side effects with the state, not with each handler that might touch it. Adding a new transition
      to SHIPPED automatically picks up the existing on-enter hook.
    */
    static void hooksDemo() {
        System.out.println("[Section 4] hooks fire on enter/exit");
        var fsm = buildOrderFsm();
        var order = new Order("o-h", new BigDecimal("99"), State.PLACED);
        fsm.fire(order, Event.PAY);
        fsm.fire(order, Event.SHIP);
        System.out.println("  trace: " + order.trace);
    }

    /*
    Runtime — fire(context, event) returns Result

    - Result is a sealed type with Transitioned(newState) and TransitionRefused(reason) variants. No exceptions for
      control flow.
    - reason carries an enum-valued explanation: NoTransition, AllGuardsFailed. Callers can pattern-match the reason
      and act appropriately.
    - The context object (Order here) is passed to guards and hooks so they can inspect or mutate domain data.
    */
    static void resultPatternMatch() {
        System.out.println("[Section 5] pattern-match the Result");

        var fsm = buildOrderFsm();
        var paid = new Order("o-1", new BigDecimal("100"), State.PAID);

        // PAY on already-paid → no transition
        Result r = fsm.fire(paid, Event.PAY);
        String description = switch (r) {
            case Transitioned(State s)        -> "moved to " + s;
            case TransitionRefused(Reason rs) -> "refused: " + rs;
        };
        System.out.println("  fire(PAY) on PAID order: " + description);
    }

    /*
    End-to-end on the order lifecycle

    - Build the full order FSM with five transitions and three hooks.
    - Drive a happy-path order through PLACED → PAID → SHIPPED → DELIVERED.
    - Drive a CANCELLED order from PLACED.
    - Show that PAY on an order with amount = 0 is refused (guard fails).
    - Reference table validates each step against an expected (final-state, result-type).
    */
    static void endToEnd() {
        System.out.println("[Section 6] end-to-end with assertions");

        record Step(Event event, State expectedFinal, Class<? extends Result> expectedKind) {}

        var fsm = buildOrderFsm();

        // Happy path
        var happy = new Order("o-1", new BigDecimal("119.50"), State.PLACED);
        var happySteps = List.of(
                new Step(Event.PAY,     State.PAID,      Transitioned.class),
                new Step(Event.SHIP,    State.SHIPPED,   Transitioned.class),
                new Step(Event.DELIVER, State.DELIVERED, Transitioned.class));
        boolean happyOk = runAndCheck(fsm, happy, happySteps, "happy path");

        // Cancel path
        var cancel = new Order("o-2", new BigDecimal("45.00"), State.PLACED);
        var cancelSteps = List.of(
                new Step(Event.CANCEL,  State.CANCELLED, Transitioned.class));
        boolean cancelOk = runAndCheck(fsm, cancel, cancelSteps, "cancel path");

        // Guard-blocked path
        var freebie = new Order("o-3", BigDecimal.ZERO, State.PLACED);
        var blockedSteps = List.of(
                new Step(Event.PAY,     State.PLACED,    TransitionRefused.class));
        boolean blockedOk = runAndCheck(fsm, freebie, blockedSteps, "guard blocks PAY on amount=0");

        System.out.println("  every probe matches expected outcome? " + (happyOk && cancelOk && blockedOk));
    }

    private static boolean runAndCheck(StateMachine<Order> fsm, Order o, List<?> stepsRaw, String label) {
        @SuppressWarnings("unchecked")
        var steps = (List<? extends Record>) stepsRaw;

        boolean ok = true;
        System.out.println("  --- " + label + " — order " + o.id + " ---");
        for (var step : steps) {
            // Step is a record(event, expectedFinal, expectedKind) — read by reflection on its components.
            var components = step.getClass().getRecordComponents();
            try {
                Event ev    = (Event) components[0].getAccessor().invoke(step);
                State exp   = (State) components[1].getAccessor().invoke(step);
                Class<?> exK = (Class<?>) components[2].getAccessor().invoke(step);

                Result r = fsm.fire(o, ev);
                boolean stepOk = exK.isInstance(r) && o.state == exp;
                if (!stepOk) ok = false;
                System.out.printf("    fire(%-7s) → %-20s   final=%-9s   %s%n",
                        ev, r, o.state, stepOk ? "✓" : "✗");
            } catch (Exception ex) { throw new RuntimeException(ex); }
        }
        return ok;
    }

    public static void main(String[] args) {
        statesAndEvents();
        dslChain();
        hooksDemo();
        resultPatternMatch();
        endToEnd();
        System.out.println("Mod006StateMachineDsl finished");
    }

    @SuppressWarnings("unused")
    private static <T> Optional<T> none() { return Optional.empty(); }
}
