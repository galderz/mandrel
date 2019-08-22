## Compiler Configuration on JVM

The options for configuring the GraalVM compiler on the JVM are in 3 categories.

### General options

These are general options for setting/getting configuration details.

* `-XX:-UseJVMCICompiler`: This disables use of the GraalVM compiler as the top tier JIT.
This is useful when wanting to compare performance of the GraalVM compiler against the native JIT compilers. 
* `-Dgraal.CompilerConfiguration=<name>`: Selects the GraalVM compiler configuration to use. If omitted, the compiler
configuration with the highest auto-selection priority is used. To see the set
of available configurations, supply the value help to this option.

    The current configurations and their semantics are:
    * `enterprise`: To produce highly optimized code with a possible trade-off to compilation time.
      **This value is only available in GraalVM EE.**
    * `community`: To produce reasonably optimized code with a faster compilation time.
    * `economy`: To compile as fast as possible with less optimal throughput of the generated code.

* `-Dgraal.ShowConfiguration=none`: Prints information about the GraalVM compiler configuration selected.
    This option only produces output when the compiler is initialized. By default, the GraalVM compiler is
    initialized on the first top-tier compilation. For this reason, the way to use this option
    is as follows: `java -XX:+EagerJVMCI -Dgraal.ShowConfiguration=info -version`.

    The accepted values for this option are:
    * `none`: To show no information.
    * `info`: To print one line of output showing the name of the compiler configuration in use
       and the location it is loaded from.
    * `verbose`: To print detailed compiler configuration information.

* `-Dgraal.MitigateSpeculativeExecutionAttacks=None`: Selects a strategy to mitigate speculative
    execution attacks (e.g., SPECTRE).

    Accepted values are:
    * `None`: No mitigations are used in JIT compiled code.
    * `AllTargets`: All branches are protected against speculative attacks. This has a large
      performance impact.
    * `GuardTargets`: Only branches that preserve Java memory safety are protected. This has
      reduced performance impact.
    * `NonDeoptGuardTargets`: Same as GuardTargets except that branches which deoptimize are
      not protected since they can not be executed repeatedly.

* `--engine.Mode=default`: Configures the execution mode of the engine. The execution mode automatically
tunes the polyglot engine towards latency or throughput.
    * `throughput`: To collect the maximum amount of profiling information and compile using the
    maximum number of optimizations. This mode results in slower application startup
    but better throughput. This mode uses the compiler configuration `community` or
    `enterprise` if not specified otherwise.
    * `default`: To use a balanced engine configuration. This mode uses the compiler configuration `community` or
    `enterprise` if not specified otherwise.
    * `latency`: To collect only minimal profiling information and compile as fast as possible
    with less optimal generated code. This mode results in faster application
    startup but less optimal throughput. This mode uses the compiler configuration
    `economy` if not specified otherwise.

### Performance tuning options

* `-Dgraal.UsePriorityInlining=true`: This can be used to disable use of the advanced inlining
algorithm that favors throughput over compilation speed. **This option is only available in
GraalVM EE**.
* `-Dgraal.Vectorization=true`: This can be used to disable the auto vectorization optimization.
**This option is only available in GraalVM EE**.
* `-Dgraal.TraceInlining=false`: Enables tracing of inlining decisions. This can be used
    for advanced tuning where it may be possible to change the source code of the program.
    The output format is shown below:

    ```
compilation of 'Signature of the compilation root method':
  at 'Sig of the root method' ['Bytecode index']: <'Phase'> 'Child method signature': 'Decision made about this callsite'
    at 'Signature of the child method' ['Bytecode index']:
       |--<'Phase 1'> 'Grandchild method signature': 'First decision made about this callsite'
       \--<'Phase 2'> 'Grandchild method signature': 'Second decision made about this callsite'
    at 'Signature of the child method' ['Bytecode index']: <'Phase'> 'Another grandchild method signature': 'The only decision made about this callsite.'
    ```

    For example:
    ```
compilation of java.lang.Character.toUpperCaseEx(int):
  at java.lang.Character.toUpperCaseEx(Character.java:7138) [bci: 22]:
     ├──<GraphBuilderPhase> java.lang.CharacterData.of(int): no, bytecode parser did not replace invoke
     └──<PriorityInliningPhase> java.lang.CharacterData.of(int): yes, worth inlining according to the cost-benefit analysis.
  at java.lang.Character.toUpperCaseEx(Character.java:7138) [bci: 26]:
     ├──<GraphBuilderPhase> java.lang.CharacterDataLatin1.toUpperCaseEx(int): no, bytecode parser did not replace invoke
     └──<PriorityInliningPhase> java.lang.CharacterDataLatin1.toUpperCaseEx(int): yes, worth inlining according to the cost-benefit analysis.
    at java.lang.CharacterDataLatin1.toUpperCaseEx(CharacterDataLatin1.java:223) [bci: 4]:
       ├──<GraphBuilderPhase> java.lang.CharacterDataLatin1.getProperties(int): no, bytecode parser did not replace invoke
       └──<PriorityInliningPhase> java.lang.CharacterDataLatin1.getProperties(int): yes, worth inlining according to the cost-benefit analysis.
     ```

### Diagnostic options

* `-Dgraal.CompilationFailureAction=Silent`: Specifies the action to take when compilation fails by
    throwing an exception.

    The accepted values are:
    * `Silent`: Print nothing to the console.
    * `Print`: Print a stack trace to the console.
    * `Diagnose`: Retry the compilation with extra diagnostics enabled. On VM exit, the collected
       diagnostics are saved to a zip file that can be submitted along with a bug report. A message
       is printed to the console describing where the diagnostics file is saved:
        ```
Graal diagnostic output saved in /Users/graal/graal_dumps/1549459528316/graal_diagnostics_22774.zip
        ```
    * `ExitVM`: Same as `Diagnose` except that the VM process exits after retrying.

    For all values except for `ExitVM`, the VM continues executing.
* `-Dgraal.CompilationBailoutAsFailure=false`: The compiler may not complete compilation of a method due
 to some property or code shape in the method (e.g. exotic uses of the jsr and ret bytecodes). In this
 case the compilation _bails out_. If you want to be informed of such bailouts, this option makes GraalVM
 treat bailouts as failures and thus be subject to the action specified by the
 `-Dgraal.CompilationFailureAction` option.
* `-Dgraal.PrintCompilation=false`: Prints an informational line to the console for each completed compilation.
  For example:
  ```
HotSpotCompilation-11  Ljava/lang/Object;                            wait          ()V       |  591ms    12B    92B  4371kB
HotSpotCompilation-175 Ljava/lang/String;                            lastIndexOf   (II)I     |  590ms   126B   309B  4076kB
HotSpotCompilation-184 Ljava/util/concurrent/ConcurrentHashMap;      setTabAt      ([Ljava/util/concurrent/ConcurrentHashMap$Node;ILjava/util/concurrent/ConcurrentHashMap$Node;)V  |  591ms    38B    67B  3411kB
HotSpotCompilation-136 Lsun/nio/cs/UTF_8$Encoder;                    encode        ([CII[B)I |  591ms   740B   418B  4921
  ```

## Setting Compiler Options with Language Launchers

The GraalVM compiler properties above are usable with some other GraalVM launchers such as
`node`, `js` and `lli`. The prefix for specifying the properties is slightly different.
For example:

```
$ java -XX:+EagerJVMCI -Dgraal.ShowConfiguration=info -version
```

Becomes:

```
$ js --jvm --vm.Dgraal.ShowConfiguration=info -version
```

Note the `-D` prefix is replaced by `--vm.D`.