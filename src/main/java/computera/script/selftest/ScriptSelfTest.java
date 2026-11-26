package computera.script.selftest;

import computera.script.Device;
import computera.script.ScriptCompileException;
import computera.script.ScriptCompiler;
import computera.script.ScriptHost;
import computera.script.ScriptProgram;
import computera.script.ScriptRuntimeException;
import computera.script.ScriptVM;
import computera.script.Value;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ScriptSelfTest {
    private static int passed;
    private static int failed;

    public static void main(String[] args) {
        testValueApi();
        testProgramName();
        testArithmeticAndStrings();
        testStringEscapes();
        testIfElifElse();
        testWhileBreakContinue();
        testForLoops();
        testListsAndDicts();
        testFunctionsAndRecursion();
        testRecursionLimit();
        testRuntimeError();
        testNameError();
        testArgumentCountError();
        testDevices();
        testSleep();
        testInstructionQuota();
        testForContinueAndCompoundAssignments();
        testCommentsBlankLinesAndNestedFunctions();
        testPrintAndConversions();
        testTimeBuiltin();
        testHalt();
        testCompileErrors();
        testSafetyAndDosProtections();

        System.out.println();
        int total = passed + failed;
        if (failed == 0) {
            System.out.println("ALL PASS (" + total + " checks, " + passed + " passed)");
        } else {
            System.out.println("FAILED: " + failed + " of " + total + " checks");
            System.exit(1);
        }
    }

    private static void testValueApi() {
        Value integer = Value.of(42L);
        Value floating = Value.of(2.5);
        Value bool = Value.of(true);
        Value string = Value.of("hi");
        Value none = Value.none();
        Value list = Value.list(List.of(Value.of(1L), Value.of("x"), Value.of(false), Value.none()));

        check("Value int predicates",
                integer.isInt() && !integer.isFloat() && integer.asLong() == 42L,
                "isInt && asLong==42", integer.typeName());
        check("Value float predicates",
                floating.isFloat() && !floating.isInt() && floating.asDouble() == 2.5,
                "isFloat && asDouble==2.5", floating.typeName());
        check("Value bool predicates",
                bool.isBool() && bool.asBool() && !Value.of(false).asBool(),
                "bool accessors", bool.typeName());
        check("Value string predicates",
                string.isString() && string.asString().equals("hi"),
                "isString && asString", string.typeName());
        check("Value none predicate", none.isNone(), "isNone", none.typeName());
        check("Value list predicate", list.isList() && list.asList().size() == 4,
                "isList && size==4", list.typeName());

        Map<String, Value> source = new LinkedHashMap<>();
        source.put("b", Value.of(2L));
        source.put("a", Value.of(1L));
        Value map = Value.map(source);
        check("Value map predicate and insertion order",
                map.isMap() && List.copyOf(map.asMap().keySet()).equals(List.of("b", "a")),
                "[b, a]", List.copyOf(map.asMap().keySet()).toString());

        checkEquals("Value int display", "42", integer.display());
        checkEquals("Value float display", "2.5", floating.display());
        checkEquals("Value bool True display", "True", bool.display());
        checkEquals("Value bool False display", "False", Value.of(false).display());
        checkEquals("Value string display", "hi", string.display());
        checkEquals("Value none display", "None", none.display());
        checkEquals("Value list display", "[1, 'x', False, None]", list.display());
        checkEquals("Value map display", "{'b': 2, 'a': 1}", map.display());
        checkEquals("Value type names", "int,float,bool,string,list,dict,none",
                String.join(",", Value.of(1L).typeName(), Value.of(1.0).typeName(),
                        Value.of(true).typeName(), Value.of("s").typeName(),
                        Value.list(List.of()).typeName(), Value.map(Map.of()).typeName(),
                        Value.none().typeName()));
    }

    private static void testProgramName() {
        try {
            ScriptProgram program = ScriptCompiler.compile("print(1)\n", "named.py");
            checkEquals("ScriptProgram.name", "named.py", program.name());
        } catch (ScriptCompileException e) {
            fail("ScriptProgram.name", "compiled program", "compile error: " + e.getMessage());
        }
    }

    private static void testArithmeticAndStrings() {
        Run run = run("""
                print(1 + 2 * 3)
                print(7 / 2)
                print(7 // 2)
                print(-7 // 2)
                print(-7 % 2)
                print(2 ** 10)
                print(1 == 1.0)
                print(1 != 2)
                print("a" + "b" + "c")
                print(3 in [1, 2, 3])
                print("k" in {"k": 1})
                print(not False)
                print(1 < 2 and 2 < 3)
                print(0 or 5)
                print(5 and 6)
                print(2 ** 3 ** 2)
                print(1 + 2.0)
                print(2 < 3.5)
                print("bc" in "abcd")
                """);
        checkRun("arithmetic, comparison and logic", run,
                "7", "3.5", "3", "-4", "1", "1024", "True", "True", "abc",
                "True", "True", "True", "True", "5", "6", "512", "3.0", "True", "True");
    }

    private static void testStringEscapes() {
        Run run = run("""
                print("a\\nb")
                print('x\\ty')
                print("q\\"q")
                print('it\\'s')
                print(len("\\u"))
                """);
        checkRun("string escapes", run, "a\nb", "x\ty", "q\"q", "it's", "2");
    }

    private static void testIfElifElse() {
        Run run = run("""
                x = 0
                if x > 0:
                    print("pos")
                elif x == 0:
                    print("zero")
                else:
                    print("neg")
                if x != 0:
                    print("bad")
                """);
        checkRun("if / elif / else", run, "zero");
    }

    private static void testWhileBreakContinue() {
        Run run = run("""
                i = 0
                s = 0
                while True:
                    i += 1
                    if i > 10:
                        break
                    if i % 2 == 0:
                        continue
                    s += i
                print(s)
                """);
        checkRun("while with break and continue", run, "25");
    }

    private static void testForLoops() {
        Run run = run("""
                s = 0
                for i in range(1, 6):
                    s += i
                print(s)
                for ch in "ab":
                    print(ch)
                for k in {"x": 1, "y": 2}:
                    print(k)
                for i in range(0, 10, 3):
                    print(i)
                for i in range(3, 0, -1):
                    print(i)
                out = ""
                for i in range(3):
                    for j in range(3):
                        if j == 1:
                            break
                        out = out + str(i) + str(j)
                print(out)
                """);
        checkRun("for over range, string, dict and nested break", run,
                "15", "a", "b", "x", "y", "0", "3", "6", "9", "3", "2", "1", "001020");
    }

    private static void testListsAndDicts() {
        Run run = run("""
                a = [10, 20, 30]
                a[0] = 5
                print(a)
                print(a[-1])
                print(a[1] + a[2])
                d = {"name": "lamp", "on": True}
                d["on"] = False
                print(d["name"])
                print(d)
                print(len(a))
                print(len(d))
                print("on" in d)
                print(3 in a)
                print([1, 2] == [1, 2])
                print({"a": 1} == {"a": 1})
                print("abc"[1])
                print(len("abc"))
                print("\u00e9" == "\u00e9")
                """);
        checkRun("lists, dicts, indexing and equality", run,
                "[5, 20, 30]", "30", "50", "lamp", "{'name': 'lamp', 'on': False}",
                "3", "2", "True", "False", "True", "True", "b", "3", "True");
    }

    private static void testFunctionsAndRecursion() {
        Run run = run("""
                def fact(n):
                    if n <= 1:
                        return 1
                    return n * fact(n - 1)
                print(fact(6))

                def fib(n):
                    if n < 2:
                        return n
                    return fib(n - 1) + fib(n - 2)
                print(fib(10))

                base = 10
                def add_base(x):
                    return x + base
                print(add_base(5))

                def no_return(x):
                    x = x + 1
                print(no_return(1))

                def greet(name):
                    return "hi " + name
                print(greet("x"))
                """);
        checkRun("functions, globals and recursion", run, "720", "55", "15", "None", "hi x");
    }

    private static void testRecursionLimit() {
        Run run = run("""
                def r(n):
                    return r(n + 1)
                r(0)
                """);
        check("recursion depth limit produces an error",
                run.compileError == null && run.host.errors.size() == 1
                        && run.host.errors.get(0).contains("RecursionError")
                        && run.host.errors.get(0).contains("maximum recursion depth"),
                "one RecursionError", errorsOf(run));
    }

    private static void testRuntimeError() {
        Run run = run("""
                def f():
                    return 1 / 0
                print("before")
                f()
                print("after")
                """);
        String error = run.host.errors.isEmpty() ? "" : run.host.errors.get(0);
        check("runtime error stops execution",
                run.compileError == null && run.host.prints.equals(List.of("before"))
                        && run.host.errors.size() == 1 && run.vm.isFinished(),
                "prints=[before], one error, finished", "prints=" + run.host.prints
                        + ", errors=" + run.host.errors);
        check("runtime error message has file, line and stack",
                error.contains("test.py:2") && error.contains("ZeroDivisionError")
                        && error.contains("at f (line 2)") && error.contains("at <main> (line 4)"),
                "test.py:2 + ZeroDivisionError + at f + at <main>", error);
    }

    private static void testNameError() {
        Run run = run("""
                print(missing)
                """);
        String error = run.host.errors.isEmpty() ? "" : run.host.errors.get(0);
        check("undefined name raises NameError",
                error.contains("NameError") && error.contains("missing")
                        && error.contains("test.py:1"),
                "NameError with name and line", error);
    }

    private static void testArgumentCountError() {
        Run run = run("""
                def f(a):
                    return a
                f()
                """);
        String error = run.host.errors.isEmpty() ? "" : run.host.errors.get(0);
        check("wrong argument count is reported",
                error.contains("TypeError") && error.contains("takes 1"),
                "TypeError about argument count", error);
    }

    private static void testDevices() {
        FakeDevice lamp = new FakeDevice();
        String source = """
                devices.lamp.set(5)
                print(devices.lamp.level)
                print(devices.lamp.add(3))
                """;
        Run run = runWithDevices(source, Map.of("lamp", lamp));
        checkRun("device methods and properties", run, "5", "8");
        check("device method calls recorded", lamp.calls.equals(List.of("set", "add")),
                "[set, add]", lamp.calls.toString());

        Run missing = runWithDevices("""
                print(devices.missing.level)
                """, Map.of());
        String error = missing.host.errors.isEmpty() ? "" : missing.host.errors.get(0);
        check("unknown device raises NameError",
                error.contains("NameError") && error.contains("devices.missing"),
                "NameError for devices.missing", error);

        Run unknownMethod = runWithDevices("""
                devices.lamp.nope()
                """, Map.of("lamp", lamp));
        String methodError = unknownMethod.host.errors.isEmpty()
                ? "" : unknownMethod.host.errors.get(0);
        check("device runtime errors get a script line",
                methodError.contains("test.py:1") && methodError.contains("unknown method"),
                "test.py:1 + unknown method", methodError);
    }

    private static void testSleep() {
        ScriptProgram program;
        try {
            program = ScriptCompiler.compile("""
                    a = 1
                    print(a)
                    sleep(500)
                    a = 2
                    print(a)
                    """, "test.py");
        } catch (ScriptCompileException e) {
            fail("sleep enters sleeping state", "compiled program", e.getMessage());
            return;
        }

        Host host = new Host();
        host.nowMillis = 1000L;
        ScriptVM vm = new ScriptVM(program, host);
        int guard = 0;
        while (!vm.isFinished() && !vm.isSleeping() && guard++ < 1000) {
            vm.step(1);
        }
        check("sleep enters sleeping state", !vm.isFinished() && vm.isSleeping(),
                "sleeping and running", "finished=" + vm.isFinished() + ", sleeping=" + vm.isSleeping());
        checkEquals("sleep deadline uses host clock", 1500L, vm.sleepUntilMillis());
        checkEquals("print before sleep completed", List.of("1"), host.prints);

        check("step returns true while sleeping", vm.step(1000), "true", "false");
        checkEquals("no work done before deadline", List.of("1"), host.prints);

        host.nowMillis = 1499L;
        check("step still blocked before deadline", vm.step(1000), "true", "false");
        checkEquals("still no work before deadline", List.of("1"), host.prints);

        host.nowMillis = 1500L;
        guard = 0;
        while (!vm.isFinished() && guard++ < 1000) {
            vm.step(1);
        }
        check("sleep resumes and finishes", vm.isFinished() && !vm.isSleeping(),
                "finished and awake", "finished=" + vm.isFinished() + ", sleeping=" + vm.isSleeping());
        checkEquals("print after sleep completed", List.of("1", "2"), host.prints);
    }

    private static void testInstructionQuota() {
        ScriptProgram program;
        try {
            program = ScriptCompiler.compile("""
                    print(1)
                    print(2)
                    print(3)
                    print(4)
                    """, "test.py");
        } catch (ScriptCompileException e) {
            fail("instruction quota", "compiled program", e.getMessage());
            return;
        }

        Host host = new Host();
        ScriptVM vm = new ScriptVM(program, host);
        boolean running = vm.step(3);
        check("quota leaves program runnable", running && !vm.isFinished(),
                "running and unfinished", "running=" + running + ", finished=" + vm.isFinished());
        check("quota limits executed print calls",
                host.prints.size() >= 1 && host.prints.size() <= 2,
                "1 or 2 prints", host.prints.toString());

        int before = host.prints.size();
        boolean stillRunning = vm.step(0);
        check("step(0) makes no progress",
                stillRunning && host.prints.size() == before,
                "running with unchanged output", "running=" + stillRunning
                        + ", prints=" + host.prints);

        int guard = 0;
        while (!vm.isFinished() && guard++ < 1000) {
            vm.step(1);
        }
        check("paused program resumes to completion",
                vm.isFinished() && host.prints.equals(List.of("1", "2", "3", "4")),
                "[1, 2, 3, 4]", host.prints.toString());

        Run loopRun = run("""
                i = 0
                while i < 100:
                    i += 1
                print(i)
                """);
        checkRun("loop across many step batches", loopRun, "100");
    }

    private static void testForContinueAndCompoundAssignments() {
        Run run = run("""
                s = 0
                for i in range(6):
                    if i == 2:
                        continue
                    s += i
                print(s)

                a = 10
                a -= 3
                a *= 2
                a /= 7
                print(a)
                """);
        checkRun("for/continue and compound assignments", run, "13", "2.0");
    }

    private static void testCommentsBlankLinesAndNestedFunctions() {
        Run run = run("""
                # leading comment
                if True:  # inline comment
                    # comment inside a block
                    print(1)

                print(2)

                def outer(x):
                    def inner(y):
                        return y + 1
                    return inner(x) * 2
                print(outer(4))
                """);
        checkRun("comments, blank lines and nested functions", run, "1", "2", "10");
    }

    private static void testPrintAndConversions() {
        Run run = run("""
                print(1, 2, "x")
                print()
                print(int(3.9))
                print(float(2))
                print(bool(""))
                print(str(3.0))
                print(-7.5 % 2)
                print(7 // 2.0)
                """);
        checkRun("print variants and conversions", run,
                "1 2 x", "", "3", "2.0", "False", "3.0", "0.5", "3.0");
    }

    private static void testTimeBuiltin() {
        ScriptProgram program;
        try {
            program = ScriptCompiler.compile("print(time())\n", "test.py");
        } catch (ScriptCompileException e) {
            fail("time() uses the host clock", "compiled program", e.getMessage());
            return;
        }
        Host host = new Host();
        host.nowMillis = 123456L;
        ScriptVM vm = new ScriptVM(program, host);
        while (!vm.isFinished()) {
            vm.step(100);
        }
        checkEquals("time() uses the host clock in milliseconds", List.of("123456"), host.prints);
    }

    private static void testHalt() {
        ScriptProgram program;
        try {
            program = ScriptCompiler.compile("""
                    i = 0
                    while True:
                        i += 1
                    """, "test.py");
        } catch (ScriptCompileException e) {
            fail("halt terminates a running script", "compiled program", e.getMessage());
            return;
        }

        Host host = new Host();
        ScriptVM vm = new ScriptVM(program, host);
        vm.step(100);
        check("script still running before halt", !vm.isFinished(), "running", "finished");
        vm.halt("power lost");
        check("halt marks finished", vm.isFinished() && !vm.isSleeping(),
                "finished and awake", "finished=" + vm.isFinished()
                        + ", sleeping=" + vm.isSleeping());
        check("halt reports through host.error",
                host.errors.size() == 1 && host.errors.get(0).contains("script halted")
                        && host.errors.get(0).contains("power lost"),
                "error with reason", host.errors.toString());
        check("step after halt returns false", !vm.step(100), "false", "true");
    }

    private static void testCompileErrors() {
        ScriptCompileException importError = compileError("import math\n");
        check("import is rejected",
                importError != null && importError.line == 1
                        && importError.getMessage().contains("import is not supported")
                        && importError.getMessage().contains("test.py:1"),
                "line 1 import error", describe(importError));

        ScriptCompileException indentError = compileError("if True:\n    print(1)\n   print(2)\n");
        check("inconsistent indentation has a clear line",
                indentError != null && indentError.line == 3
                        && indentError.getMessage().contains("indent"),
                "line 3 indentation error", describe(indentError));

        ScriptCompileException mixedIndent = compileError("if True:\n \tprint(1)\n");
        check("mixed tabs and spaces are rejected",
                mixedIndent != null && mixedIndent.line == 2
                        && mixedIndent.getMessage().contains("tabs and spaces"),
                "line 2 mixed indentation error", describe(mixedIndent));

        ScriptCompileException missingBlock = compileError("if True:\nprint(1)\n");
        check("missing indented block has a clear line",
                missingBlock != null && missingBlock.line == 2
                        && missingBlock.getMessage().contains("indented block"),
                "line 2 block error", describe(missingBlock));

        ScriptCompileException returnOutside = compileError("return 1\n");
        check("return outside function is a compile error",
                returnOutside != null && returnOutside.line == 1
                        && returnOutside.getMessage().contains("return outside function"),
                "line 1 return error", describe(returnOutside));

        ScriptCompileException missingColon = compileError("if True\n    print(1)\n");
        check("missing colon is a compile error",
                missingColon != null && missingColon.line == 1
                        && missingColon.getMessage().contains("':'"),
                "line 1 colon error", describe(missingColon));

        ScriptCompileException badTarget = compileError("1 = 2\n");
        check("invalid assignment target is a compile error",
                badTarget != null && badTarget.line == 1
                        && badTarget.getMessage().contains("cannot assign"),
                "line 1 assignment error", describe(badTarget));

        ScriptCompileException comparisonChain = compileError("print(1 < 2 < 3)\n");
        check("comparison chaining is rejected clearly",
                comparisonChain != null && comparisonChain.line == 1
                        && comparisonChain.getMessage().contains("comparison chaining"),
                "line 1 comparison chaining error", describe(comparisonChain));
    }

    private static void testSafetyAndDosProtections() {
        // 1. Parser recursion depth limit (6000 nested parens must fail cleanly, never StackOverflowError)
        String deepParens = "(".repeat(6000) + "1" + ")".repeat(6000) + "\n";
        ScriptCompileException depthError = compileError(deepParens);
        check("deeply nested parens rejected without StackOverflowError",
                depthError != null && depthError.getMessage().contains("nesting depth"),
                "nesting depth compile error", describe(depthError));

        // 2. String doubling memory bomb (28 doublings must hit MemoryError, never OOM)
        Run stringBomb = run("""
                s = "AAAAAAAAAAAAAAAA"
                for i in range(28):
                    s = s + s
                print(len(s))
                """);
        String strErr = stringBomb.host != null && !stringBomb.host.errors.isEmpty()
                ? stringBomb.host.errors.get(0) : "";
        check("string doubling blocked by length limit",
                stringBomb.compileError == null && strErr.contains("MemoryError")
                        && stringBomb.vm.isFinished(),
                "MemoryError and finished", errorsOf(stringBomb));

        // 3. Doubly self-referential cyclic list print() must finish immediately with [...]
        long cycleStart = System.nanoTime();
        Run cyclePrint = run("""
                a = [0, 0]
                a[0] = a
                a[1] = a
                print(a)
                """);
        long cycleMs = (System.nanoTime() - cycleStart) / 1_000_000L;
        check("cyclic list print finishes quickly with cycle marker",
                cyclePrint.compileError == null && cyclePrint.host.errors.isEmpty()
                        && cyclePrint.vm.isFinished() && cycleMs < 1000L
                        && cyclePrint.host.prints.equals(List.of("[[...], [...]]")),
                "prints=[[[...], [...]]] in <1000ms",
                "ms=" + cycleMs + ", prints=" + (cyclePrint.host == null ? "null" : cyclePrint.host.prints)
                        + ", errors=" + errorsOf(cyclePrint));

        // 4. Depth-28 shared DAG equality (a == b) must finish quickly without exponential hang
        long dagStart = System.nanoTime();
        Run dagEq = run("""
                a = [1, 1]
                b = [1, 1]
                for i in range(28):
                    a = [a, a]
                    b = [b, b]
                print(a == b)
                """);
        long dagMs = (System.nanoTime() - dagStart) / 1_000_000L;
        check("depth-28 DAG equality finishes quickly without hang",
                dagEq.compileError == null && dagEq.host.errors.isEmpty()
                        && dagEq.vm.isFinished() && dagMs < 1000L
                        && dagEq.host.prints.equals(List.of("True")),
                "prints=[True] in <1000ms",
                "ms=" + dagMs + ", prints=" + (dagEq.host == null ? "null" : dagEq.host.prints)
                        + ", errors=" + errorsOf(dagEq));

        // 5. range(1000000) rejected cleanly by RANGE_LIMIT (10,000) and range cost billed to budget
        Run rangeTooBig = run("""
                x = range(1000000)
                print(len(x))
                """);
        String rangeErr = rangeTooBig.host != null && !rangeTooBig.host.errors.isEmpty()
                ? rangeTooBig.host.errors.get(0) : "";
        check("range(1000000) rejected by safe range limit",
                rangeTooBig.compileError == null && rangeErr.contains("ValueError")
                        && rangeErr.contains("10000"),
                "ValueError with limit 10000", errorsOf(rangeTooBig));

        try {
            ScriptProgram rangeCostProg = ScriptCompiler.compile("""
                    a = range(5000)
                    print("done")
                    """, "range_cost.py");
            Host costHost = new Host();
            ScriptVM costVm = new ScriptVM(rangeCostProg, costHost);
            boolean stillAlive = costVm.step(1000);
            check("range() cost is billed against instruction budget",
                    stillAlive && costHost.prints.isEmpty(),
                    "yielded before print", "stillAlive=" + stillAlive + ", prints=" + costHost.prints);
            while (!costVm.isFinished()) {
                costVm.step(1000);
            }
            checkEquals("range() billed script resumes to completion", List.of("done"), costHost.prints);
        } catch (ScriptCompileException e) {
            fail("range() cost is billed against instruction budget", "compiled program", e.getMessage());
        }
    }

    private static Run run(String source) {
        return runWithDevices(source, Map.of());
    }

    private static Run runWithDevices(String source, Map<String, Device> devices) {
        ScriptProgram program;
        try {
            program = ScriptCompiler.compile(source, "test.py");
        } catch (ScriptCompileException e) {
            return Run.compileFailure(e);
        }
        Host host = new Host();
        host.devices.putAll(devices);
        ScriptVM vm = new ScriptVM(program, host);
        long budget = 2_000_000L;
        while (!vm.isFinished() && budget > 0L) {
            boolean running = vm.step(1000);
            budget -= 1000L;
            if (!running) {
                break;
            }
            if (vm.isSleeping()) {
                host.nowMillis = Math.max(host.nowMillis, vm.sleepUntilMillis());
            }
        }
        if (!vm.isFinished()) {
            vm.halt("selftest instruction budget exhausted");
        }
        return new Run(host, vm, null);
    }

    private static ScriptCompileException compileError(String source) {
        try {
            ScriptCompiler.compile(source, "test.py");
            return null;
        } catch (ScriptCompileException e) {
            return e;
        }
    }

    private static void checkRun(String name, Run run, String... expectedPrints) {
        List<String> expected = List.of(expectedPrints);
        if (run.compileError != null) {
            fail(name, expected.toString(), "compile error: " + run.compileError.getMessage());
            return;
        }
        boolean ok = run.host.errors.isEmpty() && run.host.prints.equals(expected)
                && run.vm.isFinished();
        check(name, ok, "prints=" + expected + ", errors=[], finished", "prints=" + run.host.prints
                + ", errors=" + run.host.errors + ", finished=" + run.vm.isFinished());
    }

    private static String errorsOf(Run run) {
        if (run.compileError != null) {
            return "compile error: " + run.compileError.getMessage();
        }
        return run.host.errors.toString();
    }

    private static String describe(ScriptCompileException exception) {
        return exception == null ? "no error" : exception.getMessage()
                + " [line=" + exception.line + ", column=" + exception.column + "]";
    }

    private static void checkEquals(String name, Object expected, Object actual) {
        check(name, Objects.equals(expected, actual), String.valueOf(expected), String.valueOf(actual));
    }

    private static void check(String name, boolean ok, String expected, String actual) {
        if (ok) {
            passed++;
            System.out.println("PASS " + name);
        } else {
            failed++;
            System.out.println("FAIL " + name);
            System.out.println("  expected: " + expected);
            System.out.println("  actual:   " + actual);
        }
    }

    private static void fail(String name, String expected, String actual) {
        check(name, false, expected, actual);
    }

    private static final class Run {
        final Host host;
        final ScriptVM vm;
        final ScriptCompileException compileError;

        Run(Host host, ScriptVM vm, ScriptCompileException compileError) {
            this.host = host;
            this.vm = vm;
            this.compileError = compileError;
        }

        static Run compileFailure(ScriptCompileException exception) {
            return new Run(null, null, exception);
        }
    }

    private static final class Host implements ScriptHost {
        final List<String> prints = new ArrayList<>();
        final List<String> errors = new ArrayList<>();
        final Map<String, Device> devices = new LinkedHashMap<>();
        long nowMillis = 1_000_000L;

        @Override
        public void print(String text) {
            prints.add(text);
        }

        @Override
        public void error(String text) {
            errors.add(text);
        }

        @Override
        public Device device(String name) {
            return devices.get(name);
        }

        @Override
        public long currentTimeMillis() {
            return nowMillis;
        }
    }

    private static final class FakeDevice implements Device {
        final List<String> calls = new ArrayList<>();
        long level;

        @Override
        public Value call(String method, List<Value> args) {
            calls.add(method);
            switch (method) {
                case "set":
                    level = args.get(0).asLong();
                    return Value.none();
                case "add":
                    return Value.of(level + args.get(0).asLong());
                default:
                    throw new ScriptRuntimeException("unknown method '" + method + "'");
            }
        }

        @Override
        public Value get(String property) {
            if ("level".equals(property)) {
                return Value.of(level);
            }
            throw new ScriptRuntimeException("unknown property '" + property + "'");
        }

        @Override
        public Set<String> methodNames() {
            return Set.of("set", "add");
        }
    }
}
