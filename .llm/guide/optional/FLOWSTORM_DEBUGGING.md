# FLOWSTORM_DEBUGGING.md — Optional FlowStorm REPL Debugging Guide

This guide is optional. Use it only when FlowStorm is enabled in the project and ordinary REPL eval, Malli instrumentation, tests, and logs are not enough to explain runtime behavior.

Attribution:

- This guide is based on FlowStorm's official LLM prompt:
  https://github.com/flow-storm/flow-storm-debugger/blob/master/llm-prompt.txt
- The upstream FlowStorm debugger repository is distributed under The Unlicense.

References:

- Official site: https://www.flow-storm.org/
- Repository: https://github.com/flow-storm/flow-storm-debugger
- User guide: https://flow-storm.github.io/flow-storm-debugger/user_guide.html
- Official LLM prompt: https://github.com/flow-storm/flow-storm-debugger/blob/master/llm-prompt.txt
- The Unlicense text: https://github.com/flow-storm/flow-storm-debugger/blob/master/UNLICENSE

## 1. When To Use

Use FlowStorm when the question depends on runtime execution history:

- a value changes through several nested function calls
- an exception path is hard to reconstruct from the stack trace
- asynchronous or multi-threaded execution makes logs insufficient
- understanding the program requires seeing arguments, return values, or expression results over time

Do not use FlowStorm as a replacement for tests. Any behavior learned through FlowStorm that matters should be turned into a reproducible test or a smaller REPL check.

## 2. First Checks

Before using FlowStorm from the LLM workflow:

1. Confirm nREPL is running and use `./.llm/scripts/repl-eval.sh`.
2. Evaluate `(dev.user/status)`.
3. Confirm `:capabilities :trace` or equivalent FlowStorm capability is available.
4. Keep output bounded with `take`, `select-keys`, `*print-level*`, and `*print-length*`.

If FlowStorm is not enabled, do not add the dependency without following the approval rules for dependency changes.

## 3. Core Concepts

Recordings are grouped into flows. A flow groups related recordings of one system execution.

Each flow contains timelines. A timeline is the ordered execution history for one thread. If the recorded program ran on multiple threads, inspect each timeline by `flow-id` and `thread-id`.

List recorded flows and their thread ids:

```clojure
(flow-storm.runtime.indexes.api/all-flows)
```

Retrieve a timeline:

```clojure
(def tl
  (flow-storm.runtime.indexes.api/get-referenced-maps-timeline flow-id thread-id))
```

Timelines implement Clojure collection interfaces:

```clojure
(count tl)
(take 20 tl)
(get tl 5)
(filter #(= (:type %) :fn-call) tl)
```

## 4. Timeline Entry Types

Common entry types:

- `:fn-call`: function call
- `:fn-return`: function return
- `:fn-unwind`: function exited by throwing
- `:expr-exec`: expression execution

The upstream prompt also refers to expression entries as `:expr`. If inspecting a concrete recording, trust the actual `:type` values present in the timeline.

For a function call entry, expect keys like:

- `:type`
- `:fn-name`
- `:fn-ns`
- `:form-id`
- `:fn-args-ref`
- `:parent-idx`
- `:ret-idx`

For expression and function return entries, expect keys like:

- `:type`
- `:result-ref`
- `:fn-call-idx`
- `:form-id`
- `:coord`

For function unwind entries, expect keys like:

- `:type`
- `:throwable-ref`
- `:form-id`
- `:coord`

Use `:parent-idx`, `:ret-idx`, and `:fn-call-idx` to navigate the timeline as a graph.

## 5. Forms And Values

Retrieve a form from a `:form-id` and coordinate:

```clojure
(flow-storm.runtime.indexes.api/get-form-at-coord form-id coord)
```

Use `nil` as the coordinate to retrieve the outer form.

Dereference recorded values carefully and with bounded printing:

```clojure
(binding [*print-level* 5
          *print-length* 5]
  (flow-storm.plugins.mcp.runtime/deref-val-id value-id))
```

The upstream prompt names `flow-storm.runtime.values/deref-val-id` and demonstrates `flow-storm.plugins.mcp.runtime/deref-val-id`. Prefer whichever namespace is available in the active FlowStorm version and verify it in the REPL.

## 6. Useful REPL Patterns

Find calls to a namespace:

```clojure
(->> tl
     (filter #(and (= (:type %) :fn-call)
                   (= (:fn-ns %) "my.app.ns")))
     (take 20))
```

Find calls to a function:

```clojure
(->> tl
     (filter #(and (= (:type %) :fn-call)
                   (= (:fn-name %) "target-fn")))
     (map #(select-keys % [:fn-ns :fn-name :parent-idx :ret-idx :fn-args-ref]))
     (take 20))
```

Find thrown paths:

```clojure
(->> tl
     (filter #(= (:type %) :fn-unwind))
     (map #(select-keys % [:throwable-ref :form-id :coord]))
     (take 20))
```

Inspect a bounded value:

```clojure
(binding [*print-level* 4
          *print-length* 10]
  (flow-storm.plugins.mcp.runtime/deref-val-id value-id))
```
